"""Static audit of jui/kit: in a method that returns an Element, every element
must be built inside the returned root's children(() -> {...}) (or inside a
descendant's). An element built at method depth lands in the CALLER's
container instead, which leaks it.

A method is flagged when it assigns more than one element variable, or builds
a bare element statement, at method depth (outside every lambda).
"""
import glob
import json
import os
import re
import sys

CTOR = (r'(?:box|row|column|text|spacer|divider|canvas|richText|buttonBase|scroll|list'
        r'|sliderBase|toggleBase|textInput|textArea)')
ELEM_STMT = re.compile(
    r'^(?:final\s+)?'
    r'(?:(?:jui\.ui\.)?Element\s+(\w+)\s*=\s*)?'          # 1: assigned variable
    r'(?:'
    r'([A-Z]\w*)\.(\w+)\s*\(\s*c\b'                       # 2,3: Class.method(c
    r'|(' + CTOR + r')\s*\(\s*c\b'                        # 4: ctor(c
    r')')
# Static calls whose first argument is the context but that build no element.
NON_ELEM = {'textWidth', 'layoutText'}
CONTROL = ('return', 'if', 'for', 'while', 'else', 'switch', 'try', '}', ')', 'new')


def mask(src):
    """Blank comments and string/char contents, keeping every character position."""
    out = list(src)
    i, n = 0, len(src)

    def blank(a, b):
        for k in range(a, min(b, n)):
            if src[k] != '\n':
                out[k] = ' '

    while i < n:
        if src.startswith('//', i):
            j = src.find('\n', i)
            blank(i, n if j < 0 else j)
            i = n if j < 0 else j
        elif src.startswith('/*', i):
            j = src.find('*/', i)
            end = n if j < 0 else j + 2
            blank(i, end)
            i = end
        elif src[i] == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3)
                end = n if j < 0 else j + 3
            else:
                j = i + 1
                while j < n and src[j] != '"':
                    j += 2 if src[j] == '\\' else 1
                end = j + 1
            blank(i + 1, end - 1)
            i = end
        elif src[i] == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == '\\' else 1
            blank(i + 1, j)
            i = j + 1
        else:
            i += 1
    return ''.join(out)


def brace_kind(text, i):
    """What the brace at text[i] opens: lambda, anon class, array init or block."""
    k = i - 1
    while k >= 0 and text[k] in ' \t\r\n':
        k -= 1
    if k >= 1 and text[k - 1:k + 1] == '->':
        j = max(text.rfind(';', 0, k), text.rfind('{', 0, k), text.rfind('}', 0, k)) + 1
        return 'block' if text[j:k].lstrip().startswith('case ') else 'lambda'
    if k >= 0 and text[k] in '](,=':
        return 'array'
    if k >= 0 and text[k] == ')' and re.search(r'new\s+[\w.<>]+\s*\([^()]*$', text[max(0, k - 200):k + 1]):
        return 'anon'
    return 'block'


def matching_brace(text, b):
    stack = 0
    e = b
    while e < len(text):
        if text[e] == '{':
            stack += 1
        elif text[e] == '}':
            stack -= 1
            if stack == 0:
                return e
        e += 1
    return len(text) - 1


def audit(path):
    m = mask(open(path, encoding='utf-8').read())
    flagged, clean = [], []
    for sig in re.finditer(r'static\s+(?!class)([\w.<>\[\], ?]+?)\s+(\w+)\s*\(', m):
        ret, name = sig.group(1), sig.group(2)
        if not re.search(r'\bElement\b', ret) or name in ('children', 'draw'):
            continue
        p = m.index('(', sig.start())
        depth, q = 0, p
        while q < len(m):
            if m[q] == '(':
                depth += 1
            elif m[q] == ')':
                depth -= 1
                if depth == 0:
                    break
            q += 1
        b = m.find('{', q)
        semi = m.find(';', q)
        if b < 0 or (0 <= semi < b):
            clean.append(name + ' (delegates)')
            continue
        body = m[b + 1:matching_brace(m, b)]

        roots, loose = [], []
        stack, start, i = [], 0, 0
        method_depth = True            # the statement being read started outside every lambda

        def flush(s):
            s = ' '.join(s.split())
            if not s or s.startswith(CONTROL):
                return
            mm = ELEM_STMT.match(s)
            if not mm:
                return
            var, cls, meth, ctor = mm.groups()
            call = (cls + '.' + meth) if cls else ctor
            if call.rsplit('.', 1)[-1] in NON_ELEM:
                return
            if var:
                roots.append((var, call))
            else:
                loose.append((call, s))

        while i < len(body):
            ch = body[i]
            if ch == '{':
                if method_depth:
                    flush(body[start:i])
                stack.append(brace_kind(body, i))
                start = i + 1
            elif ch == '}':
                if method_depth:
                    flush(body[start:i])
                if stack:
                    stack.pop()
                start = i + 1
            elif ch == ';' and method_depth:
                flush(body[start:i])
                start = i + 1
            method_depth = not any(k in ('lambda', 'anon') for k in stack)
            i += 1
        returned = re.findall(r'\breturn\s+(\w+)\s*;', body)
        ret = returned[-1] if returned else None
        leaks, holders = [], []
        for var, call in roots:
            if var == ret:
                continue
            has = re.search(r'\b' + re.escape(var) + r'\s*\.\s*(?:children|draw)\s*\(', body)
            if not has:                      # chained: Element v = box(c)....children(() -> ...
                decl = body.find(' ' + var + ' =')
                if decl >= 0:
                    has = re.search(r'\.(?:children|draw)\s*\(', body[decl:body.find(';', decl)])
            (leaks if has else holders).append(var)
        for call, stmt in loose:
            if re.search(r'\.(?:children|draw)\s*\(', stmt):
                leaks.append(call)
            else:
                holders.append(call)
        if leaks:
            flagged.append((name, leaks))
        else:
            clean.append(name + (' holder:' + ','.join(holders) if holders else ''))
    return flagged, clean


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if a != '--json']
    json_mode = '--json' in sys.argv
    root = args[0] if args else 'src/main/java/jui/kit'
    files = sorted(glob.glob(os.path.join(root, '*.java')))
    total, methods = 0, 0
    for path in files:
        if path.endswith(('Kit.java', 'Icon.java')):
            continue
        flagged, clean = audit(path)
        methods += len(flagged) + len(clean)
        for name, leaks in flagged:
            total += 1
            if not json_mode:
                print('%-26s %-22s leaks %s' % (os.path.basename(path)[:-5], name, leaks))
        holders = sum(1 for c in clean if 'holder:' in c)
        if '-v' in sys.argv and not json_mode:
            print('%-26s %d clean (%d with an empty state holder)' % (os.path.basename(path)[:-5], len(clean), holders))
    if json_mode:
        print(json.dumps({'files': len(files), 'methods': methods, 'leaks': total}))
    else:
        print('REAL LEAKS: %d of %d element methods in %d files' % (total, methods, len(files)))
