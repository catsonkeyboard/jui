package jui.ui.engine;

import jui.ui.Align;
import jui.ui.Justify;
import jui.ui.Ui;

/**
 * Flexbox, as CSS Flexbox Level 1 §9 lays out a single line: flex base
 * sizes, resolving flexible lengths with freezing (§9.7), automatic margins,
 * justification and alignment. Sizes are in DIPs; minimum sizes default to
 * 0, as in Yoga, rather than CSS's min-width: auto. grow(g) works as CSS's
 * flex: g, its basis 0 unless set.
 *
 * <p>It allocates nothing: what it works out per child lives in the child's
 * scratch fields, and sizes it returns pack two floats into a long.
 */
public final class FlexLayout {

    /** A width without limit, for text on one line per paragraph; any finite width, 0 too, wraps. */
    public static final float UNLIMITED = Float.POSITIVE_INFINITY;

    /** Measures a node's own content, its text: writes {width, height} to out. */
    public interface Measure {
        /** {@code maxWidth} is {@link #UNLIMITED} for no limit: one line per paragraph. */
        void measure(int node, float maxWidth, float[] out);
    }

    private final NodePool pool;
    private final Measure measure;
    private final float[] out = new float[2];

    public FlexLayout(NodePool pool, Measure measure) {
        this.pool = pool;
        this.measure = measure;
    }

    /** Lays the tree under root out in a box of width by height at the origin. */
    public void layout(int root, float width, float height) {
        Node r = pool.node(root);
        r.x = 0;
        r.y = 0;
        r.w = Math.max(0, width);
        r.h = Math.max(0, height);
        place(root);
    }

    /** Lays out the children of a node whose box is set, then theirs. */
    private void place(int index) {
        Node p = pool.node(index);
        if (p.kind == Node.TEXT || p.kind == Node.INPUT) {
            // The text's layout at its final width, for paint.
            measure.measure(index, p.singleLine ? UNLIMITED : Math.max(0, p.w - insetX(p)), out);
        }
        if (p.firstChild == Node.NONE) {
            return;
        }
        float pl = p.padLeft + p.border, pt = p.padTop + p.border;
        float innerW = Math.max(0, p.w - insetX(p)), innerH = Math.max(0, p.h - insetY(p));
        if (p.wrap && (p.flags & Node.SCROLL) == 0) { // a scroll container's height has no limit: one line (as MyGO)
            placeWrapped(p, innerW, innerH, pl, pt);
            return;
        }
        if ((p.flags & Node.LIST) != 0) {
            placeList(p, innerW, pl, pt);
            return;
        }
        boolean row = p.row;
        boolean scroll = (p.flags & Node.SCROLL) != 0;
        float main = row ? innerW : innerH, cross = row ? innerH : innerW;
        resolve(p, innerW, scroll ? Float.NaN : innerH); // a scroll container's content has no height limit

        int count = 0, autos = 0;
        float used = 0;
        for (int c = firstFlow(p); c != Node.NONE; c = nextFlow(c)) {
            Node n = pool.node(c);
            float ms = startMargin(n, row, p.reverse), me = endMargin(n, row, p.reverse);
            used += n.flexTarget + m(ms) + m(me);
            autos += (ms == Ui.AUTO ? 1 : 0) + (me == Ui.AUTO ? 1 : 0);
            count++;
        }
        used += mainGap(p) * Math.max(0, count - 1);
        float free = main - used;
        float shift = 0;
        if (scroll) {
            // Its content is as high as its children; the offset stays within it (as MyGO's place).
            p.contentH = used + insetY(p);
            float max = Math.max(0, p.contentH - p.h);
            float y = Float.isNaN(p.scrollRequest) ? p.scrollY : p.scrollRequest;
            p.scrollY = Math.max(0, Math.min(max, y));
            shift = p.scrollY;
            free = Math.max(0, free);
        }
        float auto = 0;
        if (free > 0 && autos > 0) {
            auto = free / autos;
            free = 0;
        }
        float offset = 0, between = 0;
        switch (p.justify) {
            case CENTER -> offset = free / 2;
            case END -> offset = free;
            case SPACE_BETWEEN -> between = free > 0 && count > 1 ? free / (count - 1) : 0;
            // Without room to share, these center, as CSS Flexbox §8.2 says.
            case SPACE_AROUND -> {
                if (free > 0) {
                    between = free / count;
                    offset = between / 2;
                } else {
                    offset = free / 2;
                }
            }
            case SPACE_EVENLY -> {
                if (free > 0) {
                    between = free / (count + 1);
                    offset = between;
                } else {
                    offset = free / 2;
                }
            }
            default -> { }
        }

        float pos = offset;
        for (int c = firstFlow(p); c != Node.NONE; c = nextFlow(c)) {
            Node n = pool.node(c);
            float ms = startMargin(n, row, p.reverse), me = endMargin(n, row, p.reverse);
            pos += ms == Ui.AUTO ? auto : ms;
            float size = n.flexTarget;
            float cs = n.crossSize;
            if (Float.isNaN(cs)) { // a row's child: as high as its content at its width
                cs = clamp(contentHeight(c, size), n.minHeight, n.maxHeight);
            }
            float cms = row ? n.marTop : n.marLeft, cme = row ? n.marBottom : n.marRight;
            Align a = n.alignSelf != null ? n.alignSelf : p.alignItems;
            float crossPos;
            if (cms == Ui.AUTO && cme == Ui.AUTO) {
                crossPos = (cross - cs) / 2;
            } else if (cms == Ui.AUTO) {
                crossPos = cross - cs - m(cme);
            } else if (cme == Ui.AUTO) {
                crossPos = cms;
            } else {
                crossPos = switch (a) {
                    case CENTER -> (cross - cs - cms - cme) / 2 + cms;
                    case END -> cross - cs - cme;
                    default -> cms;
                };
            }
            float mainPos = (p.reverse ? main - pos - size : pos) - shift;
            if (row) {
                set(n, p.x + pl + mainPos, p.y + pt + crossPos, size, cs);
            } else {
                set(n, p.x + pl + crossPos, p.y + pt + mainPos, cs, size);
            }
            pos += size + (me == Ui.AUTO ? auto : me) + mainGap(p) + between;
            place(c);
        }
        placeAbsolute(p);
    }

    /**
     * Works out each child's main size (flexTarget) and, where it is known
     * before that, its cross size (crossSize, else NaN), in the parent's
     * content box of innerW by innerH.
     */
    private void resolve(Node p, float innerW, float innerH) {
        boolean row = p.row;
        boolean scroll = (p.flags & Node.SCROLL) != 0;
        float main = row ? innerW : innerH, cross = row ? innerH : innerW;
        int count = 0;
        for (int c = firstFlow(p); c != Node.NONE; c = nextFlow(c)) {
            Node n = pool.node(c);
            count++;
            float w = resolved(n.width, n.widthPercent, innerW);
            float h = resolved(n.height, n.heightPercent, innerH);
            if (n.kind == Node.DIVIDER) { // a line across: 1 DIP along the parent's direction
                if (row && Float.isNaN(w)) {
                    w = 1;
                } else if (!row && Float.isNaN(h)) {
                    h = 1;
                }
            }
            float mainSize = row ? w : h;
            float cs;
            if (!row) {
                cs = columnChildWidth(p, c, innerW);
            } else if (!Float.isNaN(h)) {
                cs = clamp(h, n.minHeight, n.maxHeight);
            } else if (alignOf(p, n) == Align.STRETCH && !Float.isNaN(cross) && n.marTop != Ui.AUTO
                    && n.marBottom != Ui.AUTO) {
                cs = clamp(cross - n.marTop - n.marBottom, n.minHeight, n.maxHeight);
            } else {
                cs = Float.NaN; // a row's child: as high as its content, once its width is known
            }
            n.crossSize = cs;

            float basis;
            if (!Float.isNaN(n.basis)) {
                basis = n.basis;
            } else if (n.grow > 0 && !scroll) {
                basis = 0; // in a scroll container there is no room to grow into: the content height stands
            } else if (!Float.isNaN(mainSize)) {
                basis = mainSize;
            } else if (row) {
                basis = contentWidth(c);
            } else {
                basis = contentHeight(c, cs);
            }
            n.flexBasis = Math.max(0, basis);
        }
        if (scroll) {
            for (int c = firstFlow(p); c != Node.NONE; c = nextFlow(c)) {
                Node n = pool.node(c);
                n.flexTarget = clamp(n.flexBasis, minMain(n, row), maxMain(n, row));
            }
            return;
        }
        flex(firstFlow(p), Node.NONE, row, main - mainGap(p) * Math.max(0, count - 1));
    }

    /**
     * Resolves flexible lengths (CSS Flexbox §9.7) into the flexTarget of
     * the siblings from first up to end (exclusive): a line of them.
     */
    private void flex(int first, int end, boolean row, float space) {
        // Hypothetical main sizes, and which way the line flexes.
        float hypothetical = 0;
        for (int c = first; c != end; c = nextFlow(c)) {
            Node n = pool.node(c);
            n.flexTarget = clamp(n.flexBasis, minMain(n, row), maxMain(n, row));
            hypothetical += n.flexTarget + margins(n, row);
        }
        boolean growing = hypothetical < space;
        // Inflexible items freeze at their hypothetical size.
        for (int c = first; c != end; c = nextFlow(c)) {
            Node n = pool.node(c);
            n.frozen = growing
                    ? n.grow == 0 || n.flexBasis > n.flexTarget
                    : n.shrink == 0 || n.flexBasis < n.flexTarget;
        }
        float initialFree = remaining(first, end, row, space);
        while (true) {
            float factors = 0, scaled = 0;
            boolean unfrozen = false;
            for (int c = first; c != end; c = nextFlow(c)) {
                Node n = pool.node(c);
                if (!n.frozen) {
                    unfrozen = true;
                    factors += growing ? n.grow : n.shrink;
                    scaled += n.shrink * n.flexBasis;
                }
            }
            if (!unfrozen) {
                return;
            }
            float free = remaining(first, end, row, space);
            if (factors < 1 && Math.abs(initialFree * factors) < Math.abs(free)) {
                free = initialFree * factors; // factors summing below 1 take only that share
            }
            // Share the free space, then clamp to the min and max sizes.
            float total = 0;
            for (int c = first; c != end; c = nextFlow(c)) {
                Node n = pool.node(c);
                if (n.frozen) {
                    continue;
                }
                float target = n.flexBasis;
                if (growing && factors > 0) {
                    target += free * n.grow / factors;
                } else if (!growing && scaled > 0) {
                    target += free * (n.shrink * n.flexBasis) / scaled;
                }
                float clamped = clamp(target, minMain(n, row), maxMain(n, row));
                n.violation = clamped - target;
                n.flexTarget = clamped;
                total += n.violation;
            }
            // No violation freezes them all; else the violators of the total's kind freeze.
            for (int c = first; c != end; c = nextFlow(c)) {
                Node n = pool.node(c);
                if (!n.frozen && (total == 0 || total > 0 && n.violation > 0 || total < 0 && n.violation < 0)) {
                    n.frozen = true;
                }
            }
        }
    }

    /** The space left once frozen siblings take their size and the others their basis. */
    private float remaining(int first, int end, boolean row, float space) {
        float free = space;
        for (int c = first; c != end; c = nextFlow(c)) {
            Node n = pool.node(c);
            free -= margins(n, row) + (n.frozen ? n.flexTarget : n.flexBasis);
        }
        return free;
    }

    /**
     * The width of child c of column p, whose content box is innerW wide:
     * its own width, else stretched across, else its content's within the
     * column; always within its min and max width. Layout and measurement
     * share it, so a column is as high as its children will be.
     */
    private float columnChildWidth(Node p, int c, float innerW) {
        Node k = pool.node(c);
        float w = resolved(k.width, k.widthPercent, innerW);
        if (Float.isNaN(w)) {
            if (alignOf(p, k) == Align.STRETCH && !Float.isNaN(innerW) && k.marLeft != Ui.AUTO
                    && k.marRight != Ui.AUTO) {
                w = innerW - k.marLeft - k.marRight;
            } else {
                float room = Float.isNaN(innerW) ? Float.POSITIVE_INFINITY : innerW - m(k.marLeft) - m(k.marRight);
                w = Math.min(contentWidth(c), Math.max(0, room));
            }
        }
        return clamp(w, k.minWidth, k.maxWidth);
    }

    private static Align alignOf(Node parent, Node child) {
        return child.alignSelf != null ? child.alignSelf : parent.alignItems;
    }

    /**
     * A list's rows, one under another across its content box, each as high
     * as its content: its anchor row where the list's plan put it, the rows
     * above it stacked up by their own heights, so estimates never move it.
     * They do not flex; the list scrolls by its plan, not by an offset here.
     */
    private void placeList(Node p, float innerW, float pl, float pt) {
        float gap = mainGap(p), above = 0;
        int k = 0;
        for (int c = firstFlow(p); c != Node.NONE; c = nextFlow(c)) {
            Node n = pool.node(c);
            float w = columnChildWidth(p, c, innerW);
            float h = resolved(n.height, n.heightPercent, Float.NaN);
            n.crossSize = w;
            n.flexTarget = Float.isNaN(h) ? contentHeight(c, w) : clamp(h, n.minHeight, n.maxHeight);
            if (k++ < p.listAnchor) {
                above += m(n.marTop) + n.flexTarget + m(n.marBottom) + gap;
            }
        }
        float y = p.y + pt + p.listAnchorY - above;
        for (int c = firstFlow(p); c != Node.NONE; c = nextFlow(c)) {
            Node n = pool.node(c);
            y += m(n.marTop);
            set(n, p.x + pl + m(n.marLeft), y, n.crossSize, n.flexTarget);
            y += n.flexTarget + m(n.marBottom) + gap;
            place(c);
        }
        placeAbsolute(p);
    }

    // Absolute children: out of the flow (as MyGO's layoutAbsolute).

    /** The first child of p in the flow, or NONE. */
    private int firstFlow(Node p) {
        return flowFrom(p.firstChild);
    }

    /** The next sibling of c in the flow, or NONE: absolute children are skipped. */
    private int nextFlow(int c) {
        return flowFrom(pool.node(c).nextSibling);
    }

    private int flowFrom(int c) {
        while (c != Node.NONE && pool.node(c).absolute) {
            c = pool.node(c).nextSibling;
        }
        return c;
    }

    /**
     * Places p's absolute children in its padding box: a set size wins,
     * else both insets of an axis stretch it, else it shrinks to fit; an
     * inset places it from its side, the left and top by default.
     */
    private void placeAbsolute(Node p) {
        float bx = p.x + p.border, by = p.y + p.border;
        float pw = Math.max(0, p.w - 2 * p.border), ph = Math.max(0, p.h - 2 * p.border);
        for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            Node n = pool.node(c);
            if (!n.absolute) {
                continue;
            }
            float ml = m(n.marLeft), mr = m(n.marRight), mt = m(n.marTop), mb = m(n.marBottom);
            float w = resolved(n.width, n.widthPercent, pw);
            if (Float.isNaN(w)) {
                w = !Float.isNaN(n.left) && !Float.isNaN(n.right) ? pw - n.left - n.right - ml - mr
                        : Math.min(contentWidth(c), Math.max(0, pw - ml - mr));
            }
            w = clamp(w, n.minWidth, n.maxWidth);
            float h = resolved(n.height, n.heightPercent, ph);
            if (Float.isNaN(h)) {
                h = !Float.isNaN(n.top) && !Float.isNaN(n.bottom) ? ph - n.top - n.bottom - mt - mb : contentHeight(c, w);
            }
            h = clamp(h, n.minHeight, n.maxHeight);
            float x = !Float.isNaN(n.left) ? n.left + ml : !Float.isNaN(n.right) ? pw - n.right - w - mr : ml;
            float y = !Float.isNaN(n.top) ? n.top + mt : !Float.isNaN(n.bottom) ? ph - n.bottom - h - mb : mt;
            set(n, bx + x, by + y, w, h);
            place(c);
        }
    }

    // Wrapping: children on lines (CSS Flexbox §9.3, as MyGO's ui/layout.go).

    private float[] lineCross = new float[8];
    private int[] lineFirst = new int[8], lineEnd = new int[8];

    private static float mainGap(Node n) {
        return n.row ? n.gapX : n.gapY;
    }

    private static float crossGap(Node n) {
        return n.row ? n.gapY : n.gapX;
    }

    /**
     * Lays out a wrapping container's children: lines broken by their
     * hypothetical sizes, each flexed on its own, as high as its tallest
     * child, then placed by alignContent; children stretch to their line.
     * The line arrays are filled and read before any child is placed, so
     * the recursion into the children cannot clobber them.
     */
    private void placeWrapped(Node p, float innerW, float innerH, float pl, float pt) {
        boolean row = p.row;
        float main = row ? innerW : innerH, cross = row ? innerH : innerW;
        float gapM = mainGap(p), gapC = crossGap(p);
        wrapBases(p, innerW, innerH);
        int lines = 0;
        for (int first = firstFlow(p); first != Node.NONE; ) {
            int end = breakLine(p, first, main);
            flex(first, end, row, main - gapM * (count(first, end) - 1));
            float lc = 0;
            for (int c = first; c != end; c = nextFlow(c)) {
                Node k = pool.node(c);
                k.crossSize = naturalCross(p, k, c, innerW, innerH);
                lc = Math.max(lc, k.crossSize + (row ? m(k.marTop) + m(k.marBottom) : m(k.marLeft) + m(k.marRight)));
            }
            if (lines == lineCross.length) {
                lineCross = java.util.Arrays.copyOf(lineCross, lines * 2);
                lineFirst = java.util.Arrays.copyOf(lineFirst, lines * 2);
                lineEnd = java.util.Arrays.copyOf(lineEnd, lines * 2);
            }
            lineFirst[lines] = first;
            lineEnd[lines] = end;
            lineCross[lines] = lc;
            lines++;
            first = end;
        }
        float used = gapC * Math.max(0, lines - 1);
        for (int i = 0; i < lines; i++) {
            used += lineCross[i];
        }
        float free = cross - used, offset = 0, extra = 0, between = 0;
        if (p.alignContentSpread != null) {
            // MyGO's justifyOffsets: lines that do not fit start at the start.
            if (free > 0) {
                switch (p.alignContentSpread) {
                    case SPACE_BETWEEN -> between = lines > 1 ? free / (lines - 1) : 0;
                    case SPACE_AROUND -> {
                        between = free / lines;
                        offset = between / 2;
                    }
                    default -> { // SPACE_EVENLY
                        between = free / (lines + 1);
                        offset = between;
                    }
                }
            }
        } else {
            switch (p.alignContent) {
                case CENTER -> offset = free / 2;
                case END -> offset = free;
                case STRETCH -> extra = free > 0 ? free / lines : 0;
                default -> { }
            }
        }
        float crossPos = offset;
        for (int i = 0; i < lines; i++) {
            float size = lineCross[i] + extra;
            placeLine(p, lineFirst[i], lineEnd[i], main, crossPos, size, pl, pt, innerW, innerH);
            crossPos += size + gapC + between;
        }
        for (int c = firstFlow(p); c != Node.NONE; c = nextFlow(c)) {
            place(c);
        }
        placeAbsolute(p);
    }

    /** Places a line's children: justified along it (never overflowing), aligned across its size. */
    private void placeLine(Node p, int first, int end, float main, float crossPos, float size, float pl, float pt,
                           float innerW, float innerH) {
        boolean row = p.row;
        int count = 0, autos = 0;
        float used = 0;
        for (int c = first; c != end; c = nextFlow(c)) {
            Node n = pool.node(c);
            float ms = startMargin(n, row, p.reverse), me = endMargin(n, row, p.reverse);
            used += n.flexTarget + m(ms) + m(me);
            autos += (ms == Ui.AUTO ? 1 : 0) + (me == Ui.AUTO ? 1 : 0);
            count++;
        }
        used += mainGap(p) * (count - 1);
        float free = Math.max(0, main - used), auto = 0;
        if (free > 0 && autos > 0) {
            auto = free / autos;
            free = 0;
        }
        float offset = 0, between = 0;
        switch (p.justify) {
            case CENTER -> offset = free / 2;
            case END -> offset = free;
            case SPACE_BETWEEN -> between = count > 1 ? free / (count - 1) : 0;
            case SPACE_AROUND -> {
                between = free / count;
                offset = between / 2;
            }
            case SPACE_EVENLY -> {
                between = free / (count + 1);
                offset = between;
            }
            default -> { }
        }
        float pos = offset;
        for (int c = first; c != end; c = nextFlow(c)) {
            Node n = pool.node(c);
            float ms = startMargin(n, row, p.reverse), me = endMargin(n, row, p.reverse);
            pos += ms == Ui.AUTO ? auto : ms;
            float sz = n.flexTarget, cs = n.crossSize;
            float cms = row ? n.marTop : n.marLeft, cme = row ? n.marBottom : n.marRight;
            Align a = alignOf(p, n);
            boolean fixedCross = row ? !Float.isNaN(resolved(n.height, n.heightPercent, innerH))
                    : !Float.isNaN(resolved(n.width, n.widthPercent, innerW));
            if (a == Align.STRETCH && !fixedCross && cms != Ui.AUTO && cme != Ui.AUTO) {
                cs = row ? clamp(size - cms - cme, n.minHeight, n.maxHeight) : clamp(size - cms - cme, n.minWidth, n.maxWidth);
            }
            float cp;
            if (cms == Ui.AUTO && cme == Ui.AUTO) {
                cp = (size - cs) / 2;
            } else if (cms == Ui.AUTO) {
                cp = size - cs - m(cme);
            } else if (cme == Ui.AUTO) {
                cp = cms;
            } else {
                cp = switch (a) {
                    case CENTER -> (size - cs - cms - cme) / 2 + cms;
                    case END -> size - cs - cme;
                    default -> cms;
                };
            }
            float mainPos = p.reverse ? main - pos - sz : pos;
            float crossAt = crossPos + cp;
            if (p.wrapReverse) {
                // MyGO's flipCross: mirrored across the content box, so the margins swap sides too.
                crossAt = (row ? innerH : innerW) - crossAt - cs;
            }
            if (row) {
                set(n, p.x + pl + mainPos, p.y + pt + crossAt, sz, cs);
            } else {
                set(n, p.x + pl + crossAt, p.y + pt + mainPos, cs, sz);
            }
            pos += sz + (me == Ui.AUTO ? auto : me) + mainGap(p) + between;
        }
    }

    /** Each child's flex basis in a wrapping container whose content box is innerW by innerH. */
    private void wrapBases(Node p, float innerW, float innerH) {
        boolean row = p.row;
        for (int c = firstFlow(p); c != Node.NONE; c = nextFlow(c)) {
            Node n = pool.node(c);
            float w = resolved(n.width, n.widthPercent, innerW);
            float h = resolved(n.height, n.heightPercent, innerH);
            if (n.kind == Node.DIVIDER) {
                if (row && Float.isNaN(w)) {
                    w = 1;
                } else if (!row && Float.isNaN(h)) {
                    h = 1;
                }
            }
            float mainSize = row ? w : h, basis;
            if (!Float.isNaN(n.basis)) {
                basis = n.basis;
            } else if (n.grow > 0) {
                basis = 0;
            } else if (!Float.isNaN(mainSize)) {
                basis = mainSize;
            } else if (row) {
                basis = contentWidth(c);
            } else {
                basis = contentHeight(c, fitWidth(n, c, innerW));
            }
            n.flexBasis = Math.max(0, basis);
        }
    }

    /** Where the line from child first ends (exclusive): before the child that would make it longer than main. */
    private int breakLine(Node p, int first, float main) {
        boolean row = p.row;
        float gap = mainGap(p), used = 0;
        int count = 0;
        for (int c = first; c != Node.NONE; c = nextFlow(c)) {
            Node n = pool.node(c);
            float outer = clamp(n.flexBasis, minMain(n, row), maxMain(n, row)) + margins(n, row);
            if (count > 0 && used + gap + outer > main) {
                return c;
            }
            used += (count > 0 ? gap : 0) + outer;
            count++;
        }
        return Node.NONE;
    }

    private int count(int first, int end) {
        int k = 0;
        for (int c = first; c != end; c = nextFlow(c)) {
            k++;
        }
        return k;
    }

    /** A child's own size across its line, once its size along it (flexTarget) is known. */
    private float naturalCross(Node p, Node n, int c, float innerW, float innerH) {
        if (p.row) {
            float h = resolved(n.height, n.heightPercent, innerH);
            return Float.isNaN(h) ? contentHeight(c, n.flexTarget) : clamp(h, n.minHeight, n.maxHeight);
        }
        return fitWidth(n, c, innerW);
    }

    /** A wrapping column child's width: its own, else its content's within the column (it does not stretch). */
    private float fitWidth(Node k, int c, float innerW) {
        float w = resolved(k.width, k.widthPercent, innerW);
        if (Float.isNaN(w)) {
            float room = Float.isNaN(innerW) ? Float.POSITIVE_INFINITY : innerW - m(k.marLeft) - m(k.marRight);
            w = Math.min(contentWidth(c), Math.max(0, room));
        }
        return clamp(w, k.minWidth, k.maxWidth);
    }

    /** A wrapping row's content height at innerW: its lines' heights and the gaps between them. */
    private float wrappedRowHeight(Node n, float innerW) {
        wrapBases(n, innerW, Float.NaN);
        float h = 0;
        int lines = 0;
        for (int first = firstFlow(n); first != Node.NONE; ) {
            int end = breakLine(n, first, innerW);
            flex(first, end, true, innerW - n.gapX * (count(first, end) - 1));
            float lc = 0;
            for (int c = first; c != end; c = nextFlow(c)) {
                Node k = pool.node(c);
                lc = Math.max(lc, naturalCross(n, k, c, innerW, Float.NaN) + m(k.marTop) + m(k.marBottom));
            }
            h += lc;
            lines++;
            first = end;
        }
        return h + n.gapY * Math.max(0, lines - 1);
    }

    // Content sizes.

    /** The width a node takes for its content, on one line: max-content. */
    private float contentWidth(int index) {
        Node n = pool.node(index);
        if (!Float.isNaN(n.width)) {
            return clamp(n.width, n.minWidth, n.maxWidth);
        }
        float w;
        switch (n.kind) {
            case Node.TEXT, Node.INPUT -> {
                measure.measure(index, UNLIMITED, out);
                w = out[0];
            }
            case Node.DIVIDER -> w = 0;
            default -> {
                w = 0;
                int count = 0;
                for (int c = firstFlow(n); c != Node.NONE; c = nextFlow(c)) {
                    Node k = pool.node(c);
                    float cw = contentWidth(c) + m(k.marLeft) + m(k.marRight);
                    w = n.row ? w + cw : Math.max(w, cw);
                    count++;
                }
                if (n.row && count > 1) {
                    w += n.gapX * (count - 1);
                }
            }
        }
        return clamp(w + insetX(n), n.minWidth, n.maxWidth);
    }

    /** The height a node takes for its content when it is width wide. */
    private float contentHeight(int index, float width) {
        Node n = pool.node(index);
        if (!Float.isNaN(n.height)) {
            return clamp(n.height, n.minHeight, n.maxHeight);
        }
        if (n.listModel != null) {
            return clamp((float) n.listModel.content() + insetY(n), n.minHeight, n.maxHeight); // its rows, estimated
        }
        float innerW = Math.max(0, width - insetX(n));
        float h;
        switch (n.kind) {
            case Node.TEXT, Node.INPUT -> {
                measure.measure(index, n.singleLine ? UNLIMITED : innerW, out);
                h = out[1];
            }
            case Node.DIVIDER -> h = 0;
            default -> {
                h = 0;
                if (n.firstChild != Node.NONE) {
                    if (n.row && n.wrap && Float.isFinite(innerW)) {
                        h = wrappedRowHeight(n, innerW);
                    } else if (n.row) {
                        resolve(n, innerW, Float.NaN);
                        for (int c = firstFlow(n); c != Node.NONE; c = nextFlow(c)) {
                            Node k = pool.node(c);
                            float kh = Float.isNaN(k.crossSize) ? contentHeight(c, k.flexTarget) : k.crossSize;
                            h = Math.max(h, kh + m(k.marTop) + m(k.marBottom));
                        }
                    } else {
                        int count = 0;
                        for (int c = firstFlow(n); c != Node.NONE; c = nextFlow(c)) {
                            Node k = pool.node(c);
                            float kw = columnChildWidth(n, c, innerW);
                            float kh = resolved(k.height, k.heightPercent, Float.NaN);
                            if (Float.isNaN(kh)) {
                                kh = k.kind == Node.DIVIDER ? 1 : contentHeight(c, kw);
                            }
                            h += kh + m(k.marTop) + m(k.marBottom);
                            count++;
                        }
                        h += n.gapY * Math.max(0, count - 1);
                    }
                }
            }
        }
        return clamp(h + insetY(n), n.minHeight, n.maxHeight);
    }

    // Helpers.

    private static void set(Node n, float x, float y, float w, float h) {
        n.x = Float.isNaN(x) ? 0 : x;
        n.y = Float.isNaN(y) ? 0 : y;
        n.w = Float.isNaN(w) ? 0 : Math.max(0, w);
        n.h = Float.isNaN(h) ? 0 : Math.max(0, h);
    }

    private static float insetX(Node n) {
        return n.padLeft + n.padRight + 2 * n.border;
    }

    private static float insetY(Node n) {
        return n.padTop + n.padBottom + 2 * n.border;
    }

    /** A margin's room: an automatic margin takes none until free space is shared. */
    private static float m(float margin) {
        return margin == Ui.AUTO ? 0 : margin;
    }

    /**
     * The margin on the side the line starts from: the left or top, or with
     * reverse() the right or bottom, so margins stay on their own sides as
     * CSS's row-reverse keeps them.
     */
    private static float startMargin(Node n, boolean row, boolean reverse) {
        return row ? (reverse ? n.marRight : n.marLeft) : (reverse ? n.marBottom : n.marTop);
    }

    private static float endMargin(Node n, boolean row, boolean reverse) {
        return row ? (reverse ? n.marLeft : n.marRight) : (reverse ? n.marTop : n.marBottom);
    }

    private static float margins(Node n, boolean row) {
        return row ? m(n.marLeft) + m(n.marRight) : m(n.marTop) + m(n.marBottom);
    }

    private static float minMain(Node n, boolean row) {
        float v = row ? n.minWidth : n.minHeight;
        return Float.isNaN(v) ? 0 : v;
    }

    private static float maxMain(Node n, boolean row) {
        float v = row ? n.maxWidth : n.maxHeight;
        return Float.isNaN(v) ? Float.POSITIVE_INFINITY : v;
    }

    /** A size set in DIPs, or as a percentage of base; NaN when neither (or the base is unknown). */
    private static float resolved(float fixed, float percent, float base) {
        if (!Float.isNaN(percent)) {
            return Float.isNaN(base) ? Float.NaN : base * percent / 100;
        }
        return fixed;
    }

    /** v within [min, max], min winning, as CSS does; NaN bounds are none, and nothing goes below 0. */
    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) {
            v = 0;
        }
        if (!Float.isNaN(max)) {
            v = Math.min(v, max);
        }
        if (!Float.isNaN(min)) {
            v = Math.max(v, min);
        }
        return Math.max(0, v);
    }
}
