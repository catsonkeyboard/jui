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

    public Element handleInput(InputHandler handler) {
        Node node = node();
        if (node != null) {
            if (node.kind == Node.INPUT) { Dev.fail("built-in inputs use onTextKey/onPaste"); return this; }
            node.inputHandler = handler;
            if (handler == null) node.flags &= ~Node.RAW_INPUT;
            else { node.flags |= Node.RAW_INPUT; c.engine().state(index); }
        }
        return this;
    }

    public Element draw(Painter.Draw draw) {
        Node node = node();
        if (node != null) {
            node.draw = draw;
            if (draw != null) {
                var state = c.engine().state(index);
                if (state.painter == null) state.painter = new Painter(c.engine());
            }
        }
        return this;
    }

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

    /**
     * Scrolls the scroll containers around the element, innermost first, as
     * little as shows it, both ways (its start when it is larger than the
     * view), once this frame is laid out, without an animation; it stops the
     * wheel glides and scrollTo animations of the containers it moves.
     * Several calls in a frame go in order. Called on every frame, it keeps
     * the user from scrolling the element away. A list's row must be built
     * to be revealed: ListState.scrollTo(row) reaches the others.
     */
    public Element scrollIntoView() {
        Node n = node();
        if (n != null) {
            c.engine().requestReveal(n.id);
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

    /** Runs action once this pass's view is built, if Enter was pressed in the element's text input. */
    public Element onSubmit(Runnable action) {
        if (node() != null) {
            c.engine().onSubmit(index, action);
        }
        return this;
    }

    /** The pointer's shape over the element (a text input's is TEXT unless set). */
    public Element cursor(Cursor shape) {
        Node n = node();
        if (n != null) {
            n.cursor = (byte) (shape.ordinal() + 1);
        }
        return this;
    }

    /** What an empty text input shows, in the theme's muted text color. Only for text inputs. */
    /**
     * Turns a text input's input method on (the default) or off (as MyGO's
     * InputMethod): off, keys type the characters the keyboard layout gives
     * and nothing is composed, as in a code editor's normal mode. Turned off
     * while it composes, what it composed stays.
     */
    public Element inputMethod(boolean on) {
        if (node() != null) {
            c.engine().inputMethod(index, on);
        }
        return this;
    }

    public Element lines(int min, int max) {
        Node current = node();
        if (current != null) {
            if (!current.multiline || min < 1 || max < min) {
                Dev.fail("lines requires a text area and 1 <= min <= max");
            } else {
                current.areaMinLines = min;
                current.areaMaxLines = max;
            }
        }
        return this;
    }

    /**
     * Has fn see a key pressed in a text input before the input takes it, as
     * it comes (as MyGO's OnTextKey): the keys that type no text — those
     * with ⌘ or Ctrl, and Enter, Tab, Escape, Backspace, Delete, the arrows
     * and the other keys that move the caret. fn reports whether it took the
     * key; one taken does nothing else (no submit, Tab does not move the
     * focus). While the input method composes, the keys are its own; a
     * shortcut around the input takes a key first, unless the input edits
     * with it. fn reads and replaces the text through its TextKey, as a chat
     * box sends with Enter and clears. It holds for the input that comes
     * until the next frame.
     */
    public Element onTextKey(java.util.function.Predicate<TextKey> fn) {
        if (node() != null) {
            c.engine().onTextKey(index, fn);
        }
        return this;
    }

    /**
     * Has fn see a paste into a text input (⌘V, Paste, Paste and Match
     * Style) before it goes in, as it comes (as MyGO's OnPaste): text is the
     * clipboard's text, "" when it holds none. fn reports whether it took
     * the paste; one taken changes nothing, the selection and the undo
     * history included. A read-only input takes no paste, so never asks. It
     * holds for the input that comes until the next frame.
     */
    public Element onPaste(java.util.function.Predicate<String> fn) {
        if (node() != null) {
            c.engine().onPaste(index, fn);
        }
        return this;
    }

    /**
     * Hides what a text input holds, in the frames that call it (as MyGO's
     * Password): it shows a bullet for each character, Copy and Cut do
     * nothing, and the input method sees only what it composes. An input
     * that stops calling it shows its text again.
     */
    public Element password() {
        if (node() != null) {
            c.engine().password(index);
        }
        return this;
    }

    /**
     * Makes a text input show its text without letting the user change it
     * (as MyGO's ReadOnly): it takes the focus, and its text can be selected
     * and copied, from the keyboard too, without a caret; it takes no text
     * input, and the Edit menu's other commands do nothing to it. It holds
     * for the input that comes until the next frame.
     */
    public Element readOnly(boolean on) {
        if (node() != null) {
            c.engine().readOnly(index, on);
        }
        return this;
    }

    /**
     * Selects from start (the anchor) to end (the caret) in a text input, in
     * UTF-16 indices of its text, each held within it and on a grapheme
     * boundary: start greater than end is a backward selection, equal ends
     * put the caret there. It shows in this frame; it does not move the
     * focus. Called on every frame, it keeps the user from moving the
     * selection.
     */
    public Element setTextSelection(int start, int end) {
        if (node() != null) {
            c.engine().setTextSelection(index, start, end);
        }
        return this;
    }

    /** The start of a text input's selection, its smaller end, in UTF-16 indices; 0 for another element. */
    public int textSelectionStart() {
        return node() == null ? 0 : c.engine().textSelection(index, false);
    }

    /** The end of a text input's selection, its larger end. */
    public int textSelectionEnd() {
        return node() == null ? 0 : c.engine().textSelection(index, true);
    }

    public Element placeholder(String text) {
        Node n = node();
        if (n != null) {
            if (n.kind != Node.INPUT) {
                Dev.fail("placeholder is for text inputs");
            } else {
                n.placeholder = text;
            }
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

    /** Spaces the children apart, along and between lines. */
    public Element gap(float g) {
        Node n = node();
        if (n != null) {
            n.gapX = n.gapY = g;
        }
        return this;
    }

    /** The gap across: between a row's children, or between a wrapping column's columns. */
    public Element gapX(float g) {
        Node n = node();
        if (n != null) {
            n.gapX = g;
        }
        return this;
    }

    /** The gap down: between a column's children, or between a wrapping row's lines. */
    public Element gapY(float g) {
        Node n = node();
        if (n != null) {
            n.gapY = g;
        }
        return this;
    }

    /**
     * Takes the element out of the flow: it sizes nothing around it, sits in
     * its parent's padding box by top/right/bottom/left (the top left when
     * none is set), and paints and takes the pointer over the flow.
     */
    public Element absolute() {
        Node n = node();
        if (n != null) {
            n.absolute = true;
        }
        return this;
    }

    /**
     * For an absolute element, the distance from the top of the parent's
     * padding box. For an element in the flow, how far down to move it from
     * where the flow put it, as CSS's position: relative: its siblings and
     * its parent stay. Top wins over bottom and left over right, which move
     * it up and left. A list's rows do not move.
     */
    public Element top(float v) {
        Node n = node();
        if (n != null) {
            n.top = v;
        }
        return this;
    }

    public Element right(float v) {
        Node n = node();
        if (n != null) {
            n.right = v;
        }
        return this;
    }

    public Element bottom(float v) {
        Node n = node();
        if (n != null) {
            n.bottom = v;
        }
        return this;
    }

    public Element left(float v) {
        Node n = node();
        if (n != null) {
            n.left = v;
        }
        return this;
    }

    /** Clips what the element holds to its padding box: nothing of it shows, or takes the pointer, outside. */
    public Element clip() {
        Node n = node();
        if (n != null) {
            n.clip = true;
        }
        return this;
    }

    /** Keeps the element's room but shows nothing of it: not painted, not hit, not focused. */
    public Element invisible() {
        Node n = node();
        if (n != null) {
            n.invisible = true;
        }
        return this;
    }

    /** Wraps the children onto more lines when they do not fit, as CSS's flex-wrap: wrap. */
    public Element wrap() {
        Node n = node();
        if (n != null) {
            n.wrap = true;
        }
        return this;
    }

    /** Where a wrapping container's lines go across: START (the default, as MyGO), CENTER, END or STRETCH. */
    public Element alignContent(Align a) {
        Node n = node();
        if (n != null) {
            n.alignContent = a;
            n.alignContentSpread = null;
        }
        return this;
    }

    /**
     * Spreads a wrapping container's lines across as {@link #justify} spreads
     * children: SPACE_BETWEEN, SPACE_AROUND or SPACE_EVENLY; START, CENTER
     * and END are as {@link #alignContent(Align)}. Lines that do not fit
     * start at the start; a single line is centered, or at the start for
     * SPACE_BETWEEN.
     */
    public Element alignContent(Justify j) {
        Node n = node();
        if (n != null) {
            switch (j) {
                case START -> alignContent(Align.START);
                case CENTER -> alignContent(Align.CENTER);
                case END -> alignContent(Align.END);
                default -> n.alignContentSpread = j;
            }
        }
        return this;
    }

    /**
     * Wraps the children, the lines going from the cross end: in a row, the
     * first line at the bottom, as CSS's flex-wrap: wrap-reverse.
     */
    public Element wrapReverse() {
        Node n = node();
        if (n != null) {
            n.wrap = n.wrapReverse = true;
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

    private static final Rect NO_BOUNDS = new Rect(0, 0, 0, 0);

    /**
     * The element's box in the last frame, in DIPs from the window's top
     * left; zero in its first frame. The same Rect comes back while the box
     * stays, so a frame that asks makes none.
     */
    public Rect bounds() {
        Node n = node();
        if (n == null) {
            return NO_BOUNDS;
        }
        jui.ui.engine.ElementState s = c.engine().state(index);
        Rect r = s.bounds;
        if (r == null || r.x() != s.x || r.y() != s.y || r.width() != s.w || r.height() != s.h) {
            r = s.bounds = new Rect(s.x, s.y, s.w, s.h);
        }
        return r;
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

    /**
     * A value under key that eases out to target over d, from the frame the
     * target changes; it starts at its target, and asks for frames while it
     * moves (as MyGO's Animate).
     */
    public float animate(String key, float target, java.time.Duration d) {
        return node() == null ? target : c.engine().animate(index, key, target, Math.max(1_000_000, d.toNanos()));
    }

    /** Whether Enter was pressed in the element's text input (never while composing). */
    public boolean submitted() {
        return node() != null && c.engine().submitted(index);
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
