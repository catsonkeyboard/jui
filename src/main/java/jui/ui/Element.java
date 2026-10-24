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

    // Behavior.

    /**
     * Disables the element and what it holds: no input, no focus, half
     * opacity. An element inside a disabled one stays disabled.
     */
    public Element disabled(boolean disabled) {
        Node n = node();
        if (n != null) {
            n.disabled = disabled || n.parent != Node.NONE && c.engine().node(n.parent).disabled;
        }
        return this;
    }

    /** Lets the element take focus: by a click, by Tab, or by a {@link Handle}. */
    public Element focusable() {
        Node n = node();
        if (n != null) {
            n.flags |= Node.FOCUSABLE;
        }
        return this;
    }

    /** Takes focus as the element appears, as the first field of a dialog does. */
    public Element autoFocus() {
        if (node() != null) {
            c.engine().autoFocus(index);
        }
        return this;
    }

    /** Binds the handle to this element, for this build: call it on every build. */
    public Element bind(Handle handle) {
        Node n = node();
        if (n != null) {
            handle.bind(c.engine(), n.id);
        }
        return this;
    }

    /** Whether the element shows a focus ring when it has focus from the keyboard (it does by default). */
    public Element focusRing(boolean show) {
        Node n = node();
        if (n != null) {
            n.noFocusRing = !show;
        }
        return this;
    }

    /** Runs action once this pass's view is built, if the element's control changed its value. */
    public Element onChange(Runnable action) {
        if (node() != null) {
            c.engine().onChange(index, action);
        }
        return this;
    }

    /** Runs action once this pass's view is built, if the element was clicked. */
    public Element onClick(Runnable action) {
        if (node() != null) {
            c.engine().onClick(index, action);
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
            n.bgHover = n.bgPressed = 0; // a background set later wins over a control's hover looks
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

    /** The element's opacity, from 0 to 1, multiplied into what it holds. */
    public Element opacity(float opacity) {
        Node n = node();
        if (n != null) {
            n.opacity = Math.max(0, Math.min(1, opacity));
        }
        return this;
    }

    // Text style: set on any element, the text inside inherits it.

    public Element textColor(Color color) {
        Node n = node();
        if (n != null) {
            n.textColor = color.rgba();
            n.hasTextColor = true;
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

    // Queries: what happened since the last frame.

    /** The element's box in the last frame, in DIPs from the window's top left; zero in its first frame. */
    public Rect bounds() {
        Node n = node();
        if (n == null) {
            return new Rect(0, 0, 0, 0);
        }
        jui.ui.engine.ElementState s = c.engine().state(index);
        return new Rect(s.x, s.y, s.w, s.h);
    }

    /** Whether the pointer is over the element (held on it, while a button is down). */
    public boolean hovered() {
        return node() != null && c.engine().hovered(index);
    }

    /** Whether a button is held on the element, with the pointer still over it. */
    public boolean pressed() {
        return node() != null && c.engine().pressed(index);
    }

    /** Whether the element was clicked: pressed and released on it, or activated by the keyboard. */
    public boolean clicked() {
        return clicks() > 0;
    }

    /** How many clicks the element took since the last pass that built it. */
    public int clicks() {
        return node() == null ? 0 : c.engine().clicks(index);
    }

    /** Whether the element was double-clicked; its clicks are reported too. */
    public boolean doubleClicked() {
        return node() != null && c.engine().doubleClicked(index);
    }

    /** Whether the element was clicked with the secondary button (or control-click). */
    public boolean rightClicked() {
        return node() != null && c.engine().rightClicked(index);
    }

    /** How far the pointer moved across while held on the element, since the last pass. */
    public float dragX() {
        return node() == null ? 0 : c.engine().dragX(index);
    }

    public float dragY() {
        return node() == null ? 0 : c.engine().dragY(index);
    }

    /** Whether the element's control changed its bound value in this pass. */
    public boolean changed() {
        return node() != null && c.engine().changed(index);
    }

    /** Whether the element has focus, in a focused window. */
    public boolean focused() {
        return node() != null && c.engine().focused(index);
    }

    /** Whether the element has focus that came from the keyboard, so its ring shows. */
    public boolean focusVisible() {
        return node() != null && c.engine().focusVisible(index);
    }

    /** Whether the element or one inside it had focus in the last frame. */
    public boolean focusWithin() {
        return node() != null && c.engine().focusWithin(index);
    }

    /**
     * Whether the shortcut was pressed while focus was within the element
     * (the element or one inside it). The innermost such element takes it
     * before the window does.
     */
    public boolean shortcut(Shortcut s) {
        return node() != null && c.engine().shortcut(index, s);
    }

    /** The element's local state map, or null when this element is stale. */
    java.util.Map<String, Object> locals() {
        return node() == null ? null : c.engine().locals(index);
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
