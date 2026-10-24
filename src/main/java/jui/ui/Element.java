package jui.ui;

import jui.ui.engine.Dev;
import jui.ui.engine.Node;

/**
 * What a control function returns: the element it built, to style, lay out
 * and query. Valid only during the build pass that made it; used later, it
 * fails in development mode and does nothing otherwise.
 */
public final class Element {
    final Context c;
    final int index;
    /** The node's generation when this element was handed out; a pooled handle takes each new one. */
    long gen;

    Element(Context c, int index, long gen) {
        this.c = c;
        this.index = index;
        this.gen = gen;
    }

    /** Builds the element's children: what content builds goes inside it. */
    public Element children(Runnable content) {
        if (node() != null) {
            c.engine().children(index, content);
        }
        return this;
    }

    /** Names the element, for tests (and, later, assistive technology). */
    public Element label(String label) {
        Node n = node();
        if (n != null) {
            n.label = label;
        }
        return this;
    }

    // Layout, as CSS flexbox. Lengths are in DIPs.

    public Element width(float w) {
        Node n = node();
        if (n != null) {
            n.width = w;
        }
        return this;
    }

    public Element height(float h) {
        Node n = node();
        if (n != null) {
            n.height = h;
        }
        return this;
    }

    public Element size(float w, float h) {
        return width(w).height(h);
    }

    public Element minWidth(float w) {
        Node n = node();
        if (n != null) {
            n.minWidth = w;
        }
        return this;
    }

    public Element maxWidth(float w) {
        Node n = node();
        if (n != null) {
            n.maxWidth = w;
        }
        return this;
    }

    public Element minHeight(float h) {
        Node n = node();
        if (n != null) {
            n.minHeight = h;
        }
        return this;
    }

    public Element maxHeight(float h) {
        Node n = node();
        if (n != null) {
            n.maxHeight = h;
        }
        return this;
    }

    /** A share of the parent's content width, from 0 to 100. */
    public Element widthPercent(float percent) {
        Node n = node();
        if (n != null) {
            n.widthPercent = percent;
        }
        return this;
    }

    public Element heightPercent(float percent) {
        Node n = node();
        if (n != null) {
            n.heightPercent = percent;
        }
        return this;
    }

    /** Takes the parent's whole content box. */
    public Element fill() {
        return widthPercent(100).heightPercent(100);
    }

    public Element fillWidth() {
        return widthPercent(100);
    }

    public Element fillHeight() {
        return heightPercent(100);
    }

    /** Takes a share of the free space along the parent's direction, as CSS's flex: g. */
    public Element grow(float g) {
        Node n = node();
        if (n != null) {
            n.grow = g;
        }
        return this;
    }

    public Element shrink(float s) {
        Node n = node();
        if (n != null) {
            n.shrink = s;
        }
        return this;
    }

    public Element basis(float b) {
        Node n = node();
        if (n != null) {
            n.basis = b;
        }
        return this;
    }

    /** Spaces the children apart. */
    public Element gap(float g) {
        Node n = node();
        if (n != null) {
            n.gap = g;
        }
        return this;
    }

    public Element padding(float all) {
        return padding(all, all, all, all);
    }

    public Element padding(float vertical, float horizontal) {
        return padding(vertical, horizontal, vertical, horizontal);
    }

    public Element padding(float top, float right, float bottom, float left) {
        Node n = node();
        if (n != null) {
            n.padTop = top;
            n.padRight = right;
            n.padBottom = bottom;
            n.padLeft = left;
        }
        return this;
    }

    /** Margins take {@link Ui#AUTO}, which takes the free space on its side. */
    public Element margin(float all) {
        return margin(all, all, all, all);
    }

    public Element margin(float vertical, float horizontal) {
        return margin(vertical, horizontal, vertical, horizontal);
    }

    public Element margin(float top, float right, float bottom, float left) {
        Node n = node();
        if (n != null) {
            n.marTop = top;
            n.marRight = right;
            n.marBottom = bottom;
            n.marLeft = left;
        }
        return this;
    }

    public Element justify(Justify j) {
        Node n = node();
        if (n != null) {
            n.justify = j;
        }
        return this;
    }

    public Element alignItems(Align a) {
        Node n = node();
        if (n != null) {
            n.alignItems = a;
        }
        return this;
    }

    public Element alignSelf(Align a) {
        Node n = node();
        if (n != null) {
            n.alignSelf = a;
        }
        return this;
    }

    /** Centers the children both ways. */
    public Element center() {
        return justify(Justify.CENTER).alignItems(Align.CENTER);
    }

    /** Lays the children out from the other end: the right of a row, the bottom of a column. */
    public Element reverse() {
        Node n = node();
        if (n != null) {
            n.reverse = true;
        }
        return this;
    }

    // The box's look.

    public Element background(Color color) {
        Node n = node();
        if (n != null) {
            n.background = color.rgba();
        }
        return this;
    }

    /** A border inside the box's edge, which insets the content as padding does. */
    public Element border(float width, Color color) {
        Node n = node();
        if (n != null) {
            n.border = width;
            n.borderColor = color.rgba();
        }
        return this;
    }

    public Element radius(float r) {
        return radius(r, r, r, r);
    }

    public Element radius(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        Node n = node();
        if (n != null) {
            n.radiusTL = topLeft;
            n.radiusTR = topRight;
            n.radiusBR = bottomRight;
            n.radiusBL = bottomLeft;
        }
        return this;
    }

    /** A box shadow, as CSS's box-shadow: offset, blur and spread in DIPs. */
    public Element shadow(float x, float y, float blur, float spread, Color color) {
        Node n = node();
        if (n != null) {
            n.shadowX = x;
            n.shadowY = y;
            n.shadowBlur = blur;
            n.shadowSpread = spread;
            n.shadowColor = color.rgba();
        }
        return this;
    }

    // Text style: set on any element, the text inside inherits it.

    public Element textColor(Color color) {
        Node n = node();
        if (n != null) {
            n.textColor = color.rgba();
        }
        return this;
    }

    public Element fontSize(float size) {
        Node n = node();
        if (n != null) {
            n.fontSize = size;
        }
        return this;
    }

    /** A CSS weight, from 100 to 900. */
    public Element fontWeight(int weight) {
        Node n = node();
        if (n != null) {
            n.fontWeight = weight;
        }
        return this;
    }

    public Element bold() {
        return fontWeight(700);
    }

    /** Keeps a text on one line. */
    public Element singleLine() {
        Node n = node();
        if (n != null) {
            n.singleLine = true;
        }
        return this;
    }

    /** The node, or null when this element is stale. */
    Node node() {
        if (c.engine().valid(index, gen)) {
            return c.engine().node(index);
        }
        Dev.fail("an Element is valid only during the build pass that made it");
        return null;
    }
}
