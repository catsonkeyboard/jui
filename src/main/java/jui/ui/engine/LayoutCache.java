package jui.ui.engine;

import jui.text.TextLayout;

/** Per-pool-slot snapshots: identities and ordered child slots must match before reuse. */
final class LayoutCache {
    final boolean enabled;
    private final NodePool pool;
    long hits, eligible;

    LayoutCache(NodePool pool, boolean enabled) { this.pool = pool; this.enabled = enabled; }
    void begin() { hits = eligible = 0; }

    void prepare() {
        if (!enabled) return;
        // Parents precede children. Volatile nodes block every descendant, even stable text leaves.
        for (int i = 0; i < pool.size(); i++) {
            Node n = pool.node(i);
            if (n.cache == null) n.cache = new Record();
            Record c = n.cache;
            boolean scrollMoved = (n.flags & Node.SCROLL) != 0 && (!c.valid
                    || !equal(n.scrollX, c.previous.scrollX) || !equal(n.scrollY, c.previous.scrollY)
                    || !Float.isNaN(n.scrollRequest) || !Float.isNaN(n.scrollRequestX));
            c.blocked = n.kind == Node.INPUT || (n.flags & (Node.LIST | Node.MODAL | Node.ESCAPABLE)) != 0
                    || n.placeOn != Node.NONE || scrollMoved
                    || n.parent != Node.NONE && pool.node(n.parent).cache.blocked;
            c.cacheable = !c.blocked;
            c.same = c.cacheable && c.valid && matches(n, c.previous);
        }
        for (int i = pool.size() - 1; i >= 0; i--) {
            Node n = pool.node(i);
            for (int child = n.firstChild; child != Node.NONE; child = pool.node(child).nextSibling) {
                n.cache.same &= pool.node(child).cache.same;
                n.cache.cacheable &= pool.node(child).cache.cacheable;
            }
            if (n.cache.cacheable) eligible++;
            if (!n.cache.same) n.cache.clearMeasures();
        }
    }

    boolean reuse(int index) {
        if (!enabled) return false;
        Node n = pool.node(index);
        Record c = n.cache;
        Node old = c.previous;
        if (!c.same || n.x != old.x || n.y != old.y || n.w != old.w || n.h != old.h) return false;
        restore(index);
        return true;
    }

    private void restore(int index) {
        Node n = pool.node(index), old = n.cache.previous;
        n.x = old.x; n.y = old.y; n.w = old.w; n.h = old.h;
        n.contentW = old.contentW; n.contentH = old.contentH;
        n.scrollX = old.scrollX; n.scrollY = old.scrollY; n.layout = old.layout;
        hits++;
        for (int child = n.firstChild; child != Node.NONE; child = pool.node(child).nextSibling) restore(child);
    }

    void save() {
        if (!enabled) return;
        for (int i = 0; i < pool.size(); i++) {
            Node a = pool.node(i), b = a.cache.previous;
            b.width = a.width;
            b.height = a.height;
            b.minWidth = a.minWidth;
            b.maxWidth = a.maxWidth;
            b.minHeight = a.minHeight;
            b.maxHeight = a.maxHeight;
            b.widthPercent = a.widthPercent;
            b.heightPercent = a.heightPercent;
            b.grow = a.grow;
            b.shrink = a.shrink;
            b.basis = a.basis;
            b.gapX = a.gapX;
            b.gapY = a.gapY;
            b.top = a.top;
            b.right = a.right;
            b.bottom = a.bottom;
            b.left = a.left;
            b.padTop = a.padTop;
            b.padRight = a.padRight;
            b.padBottom = a.padBottom;
            b.padLeft = a.padLeft;
            b.marTop = a.marTop;
            b.marRight = a.marRight;
            b.marBottom = a.marBottom;
            b.marLeft = a.marLeft;
            b.border = a.border;
            b.scrollX = a.scrollX;
            b.scrollY = a.scrollY;
            b.scrollRequest = a.scrollRequest;
            b.scrollRequestX = a.scrollRequestX;
            b.resolvedSize = a.resolvedSize;
            b.kind = a.kind;
            b.row = a.row;
            b.reverse = a.reverse;
            b.absolute = a.absolute;
            b.invisible = a.invisible;
            b.clip = a.clip;
            b.wrap = a.wrap;
            b.wrapReverse = a.wrapReverse;
            b.singleLine = a.singleLine;
            b.alignContent = a.alignContent;
            b.alignContentSpread = a.alignContentSpread;
            b.justify = a.justify;
            b.alignItems = a.alignItems;
            b.alignSelf = a.alignSelf;
            b.resolvedWeight = a.resolvedWeight;
            b.resolvedColor = a.resolvedColor;
            b.firstChild = a.firstChild;
            b.lastChild = a.lastChild;
            b.nextSibling = a.nextSibling;
            b.childCount = a.childCount;
            b.parent = a.parent;
            b.id = a.id;
            b.flags = a.flags;
            b.text = a.text;
            b.spans = a.spans;
            b.x = a.x;
            b.y = a.y;
            b.w = a.w;
            b.h = a.h;
            b.contentW = a.contentW;
            b.contentH = a.contentH;
            b.layout = a.layout;
            a.cache.valid = a.cache.cacheable;
        }
    }

    private static boolean equal(float a, float b) { return Float.floatToIntBits(a) == Float.floatToIntBits(b); }

    private static boolean matches(Node a, Node b) {
        return equal(a.width, b.width)
                    && equal(a.height, b.height)
                    && equal(a.minWidth, b.minWidth)
                    && equal(a.maxWidth, b.maxWidth)
                    && equal(a.minHeight, b.minHeight)
                    && equal(a.maxHeight, b.maxHeight)
                    && equal(a.widthPercent, b.widthPercent)
                    && equal(a.heightPercent, b.heightPercent)
                    && equal(a.grow, b.grow)
                    && equal(a.shrink, b.shrink)
                    && equal(a.basis, b.basis)
                    && equal(a.gapX, b.gapX)
                    && equal(a.gapY, b.gapY)
                    && equal(a.top, b.top)
                    && equal(a.right, b.right)
                    && equal(a.bottom, b.bottom)
                    && equal(a.left, b.left)
                    && equal(a.padTop, b.padTop)
                    && equal(a.padRight, b.padRight)
                    && equal(a.padBottom, b.padBottom)
                    && equal(a.padLeft, b.padLeft)
                    && equal(a.marTop, b.marTop)
                    && equal(a.marRight, b.marRight)
                    && equal(a.marBottom, b.marBottom)
                    && equal(a.marLeft, b.marLeft)
                    && equal(a.border, b.border)
                    && equal(a.scrollX, b.scrollX)
                    && equal(a.scrollY, b.scrollY)
                    && equal(a.scrollRequest, b.scrollRequest)
                    && equal(a.scrollRequestX, b.scrollRequestX)
                    && equal(a.resolvedSize, b.resolvedSize)
                    && a.kind == b.kind
                    && a.row == b.row
                    && a.reverse == b.reverse
                    && a.absolute == b.absolute
                    && a.invisible == b.invisible
                    && a.clip == b.clip
                    && a.wrap == b.wrap
                    && a.wrapReverse == b.wrapReverse
                    && a.singleLine == b.singleLine
                    && a.alignContent == b.alignContent
                    && a.alignContentSpread == b.alignContentSpread
                    && a.justify == b.justify
                    && a.alignItems == b.alignItems
                    && a.alignSelf == b.alignSelf
                    && a.resolvedWeight == b.resolvedWeight
                    && a.resolvedColor == b.resolvedColor
                    && a.firstChild == b.firstChild
                    && a.lastChild == b.lastChild
                    && a.nextSibling == b.nextSibling
                    && a.childCount == b.childCount
                    && a.parent == b.parent
                    && a.id == b.id
                    && a.flags == b.flags
                    && java.util.Objects.equals(a.text, b.text)
                    && java.util.Objects.equals(a.spans, b.spans);
    }

    static final class Record {
        final Node previous = new Node();
        boolean valid, same, blocked, cacheable;
        boolean widthValid, heightAValid, heightBValid;
        float contentWidth, heightWidthA, heightWidthB, contentHeightA, contentHeightB;
        TextLayout textA, textB;
        float textWidthA, textWidthB;
        void clearMeasures() { widthValid = heightAValid = heightBValid = false; textA = textB = null; }
        TextLayout text(float width) {
            if (!cacheable) return null;
            return textA != null && equal(width, textWidthA) ? textA
                    : textB != null && equal(width, textWidthB) ? textB : null;
        }
        void text(float width, TextLayout layout) {
            if (!cacheable) return;
            textB = textA; textWidthB = textWidthA;
            textA = layout; textWidthA = width;
        }
        float height(float width) {
            return !cacheable ? Float.NaN : heightAValid && equal(width, heightWidthA) ? contentHeightA
                    : heightBValid && equal(width, heightWidthB) ? contentHeightB : Float.NaN;
        }
        void height(float width, float value) {
            if (!cacheable) return;
            heightBValid = heightAValid; heightWidthB = heightWidthA; contentHeightB = contentHeightA;
            heightAValid = true; heightWidthA = width; contentHeightA = value;
        }
    }
}
