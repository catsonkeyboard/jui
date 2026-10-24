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
        if (p.kind == Node.TEXT) {
            // The text's layout at its final width, for paint.
            measure.measure(index, p.singleLine ? UNLIMITED : Math.max(0, p.w - insetX(p)), out);
        }
        if (p.firstChild == Node.NONE) {
            return;
        }
        float pl = p.padLeft + p.border, pt = p.padTop + p.border;
        float innerW = Math.max(0, p.w - insetX(p)), innerH = Math.max(0, p.h - insetY(p));
        boolean row = p.row;
        float main = row ? innerW : innerH, cross = row ? innerH : innerW;
        resolve(p, innerW, innerH);

        int count = 0, autos = 0;
        float used = 0;
        for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            Node n = pool.node(c);
            float ms = startMargin(n, row, p.reverse), me = endMargin(n, row, p.reverse);
            used += n.flexTarget + m(ms) + m(me);
            autos += (ms == Ui.AUTO ? 1 : 0) + (me == Ui.AUTO ? 1 : 0);
            count++;
        }
        used += p.gap * (count - 1);
        float free = main - used;
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
        for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
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
            float mainPos = p.reverse ? main - pos - size : pos;
            if (row) {
                set(n, p.x + pl + mainPos, p.y + pt + crossPos, size, cs);
            } else {
                set(n, p.x + pl + crossPos, p.y + pt + mainPos, cs, size);
            }
            pos += size + (me == Ui.AUTO ? auto : me) + p.gap + between;
            place(c);
        }
    }

    /**
     * Works out each child's main size (flexTarget) and, where it is known
     * before that, its cross size (crossSize, else NaN), in the parent's
     * content box of innerW by innerH.
     */
    private void resolve(Node p, float innerW, float innerH) {
        boolean row = p.row;
        float main = row ? innerW : innerH, cross = row ? innerH : innerW;
        int count = 0;
        for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
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
            } else if (n.grow > 0) {
                basis = 0;
            } else if (!Float.isNaN(mainSize)) {
                basis = mainSize;
            } else if (row) {
                basis = contentWidth(c);
            } else {
                basis = contentHeight(c, cs);
            }
            n.flexBasis = Math.max(0, basis);
        }
        flex(p, main - p.gap * Math.max(0, count - 1));
    }

    /** Resolves flexible lengths (CSS Flexbox §9.7) into each child's flexTarget. */
    private void flex(Node p, float space) {
        boolean row = p.row;
        // Hypothetical main sizes, and which way the line flexes.
        float hypothetical = 0;
        for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            Node n = pool.node(c);
            n.flexTarget = clamp(n.flexBasis, minMain(n, row), maxMain(n, row));
            hypothetical += n.flexTarget + margins(n, row);
        }
        boolean growing = hypothetical < space;
        // Inflexible items freeze at their hypothetical size.
        for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            Node n = pool.node(c);
            n.frozen = growing
                    ? n.grow == 0 || n.flexBasis > n.flexTarget
                    : n.shrink == 0 || n.flexBasis < n.flexTarget;
        }
        float initialFree = remaining(p, space);
        while (true) {
            float factors = 0, scaled = 0;
            boolean unfrozen = false;
            for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
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
            float free = remaining(p, space);
            if (factors < 1 && Math.abs(initialFree * factors) < Math.abs(free)) {
                free = initialFree * factors; // factors summing below 1 take only that share
            }
            // Share the free space, then clamp to the min and max sizes.
            float total = 0;
            for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
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
            for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
                Node n = pool.node(c);
                if (!n.frozen && (total == 0 || total > 0 && n.violation > 0 || total < 0 && n.violation < 0)) {
                    n.frozen = true;
                }
            }
        }
    }

    /** The space left once frozen children take their size and the others their basis. */
    private float remaining(Node p, float space) {
        float free = space;
        for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            Node n = pool.node(c);
            free -= margins(n, p.row) + (n.frozen ? n.flexTarget : n.flexBasis);
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

    // Content sizes.

    /** The width a node takes for its content, on one line: max-content. */
    private float contentWidth(int index) {
        Node n = pool.node(index);
        if (!Float.isNaN(n.width)) {
            return clamp(n.width, n.minWidth, n.maxWidth);
        }
        float w;
        switch (n.kind) {
            case Node.TEXT -> {
                measure.measure(index, UNLIMITED, out);
                w = out[0];
            }
            case Node.DIVIDER -> w = 0;
            default -> {
                w = 0;
                int count = 0;
                for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
                    Node k = pool.node(c);
                    float cw = contentWidth(c) + m(k.marLeft) + m(k.marRight);
                    w = n.row ? w + cw : Math.max(w, cw);
                    count++;
                }
                if (n.row && count > 1) {
                    w += n.gap * (count - 1);
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
        float innerW = Math.max(0, width - insetX(n));
        float h;
        switch (n.kind) {
            case Node.TEXT -> {
                measure.measure(index, n.singleLine ? UNLIMITED : innerW, out);
                h = out[1];
            }
            case Node.DIVIDER -> h = 0;
            default -> {
                h = 0;
                if (n.firstChild != Node.NONE) {
                    if (n.row) {
                        resolve(n, innerW, Float.NaN);
                        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
                            Node k = pool.node(c);
                            float kh = Float.isNaN(k.crossSize) ? contentHeight(c, k.flexTarget) : k.crossSize;
                            h = Math.max(h, kh + m(k.marTop) + m(k.marBottom));
                        }
                    } else {
                        int count = 0;
                        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
                            Node k = pool.node(c);
                            float kw = columnChildWidth(n, c, innerW);
                            float kh = resolved(k.height, k.heightPercent, Float.NaN);
                            if (Float.isNaN(kh)) {
                                kh = k.kind == Node.DIVIDER ? 1 : contentHeight(c, kw);
                            }
                            h += kh + m(k.marTop) + m(k.marBottom);
                            count++;
                        }
                        h += n.gap * Math.max(0, count - 1);
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
