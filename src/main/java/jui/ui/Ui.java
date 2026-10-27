package jui.ui;

import jui.ui.engine.Engine;
import jui.ui.engine.Node;

/**
 * The controls, as static functions taking the context: import them with
 * {@code import static jui.ui.Ui.*;}.
 */
public final class Ui {
    private Ui() {}

    /** An automatic margin, which takes the free space on its side. */
    public static final float AUTO = Float.POSITIVE_INFINITY;

    /** A box: a column. */
    public static Element box(Context c) {
        return open(c, Node.BOX, false, Align.STRETCH);
    }

    /** Lays its children out from top to bottom, stretched to its width. */
    public static Element column(Context c) {
        return open(c, Node.BOX, false, Align.STRETCH);
    }

    /** Lays its children out from left to right, centered across. */
    public static Element row(Context c) {
        return open(c, Node.BOX, true, Align.CENTER);
    }

    /** Takes the free space of its row or column. */
    public static Element spacer(Context c) {
        Element e = open(c, Node.SPACER, false, Align.STRETCH);
        Node n = e.node();
        if (n != null) {
            n.grow = 1;
        }
        return e;
    }

    /** Text in the system font, wrapped at the width it gets. */
    public static Element text(Context c, String s) {
        Element e = open(c, Node.TEXT, false, Align.STRETCH);
        Node n = e.node();
        if (n != null) {
            n.text = s;
        }
        return e;
    }

    /** A thin line across its row or column. */
    public static Element divider(Context c) {
        return open(c, Node.DIVIDER, false, Align.STRETCH);
    }

    /** A column that scrolls its content up and down, clipped to its padding box (as MyGO's Scroll). */
    public static Element scroll(Context c) {
        Element e = open(c, Node.BOX, false, Align.STRETCH);
        if (e.node() != null) {
            c.engine().makeScroll(e.index);
        }
        return e;
    }

    /** A scroll container whose offset state reads and moves. */
    public static Element scroll(Context c, ScrollState state) {
        Element e = open(c, Node.BOX, false, Align.STRETCH);
        Node n = e.node();
        if (n != null) {
            c.engine().state(e.index); // the container's state first, so a request bound now lands on it
            state.bind(c.engine(), n.id);
            c.engine().makeScroll(e.index);
        }
        return e;
    }

    /**
     * A list of count rows that builds only those in view: row builds row i,
     * as one element. Rows may be of any height; a key set in row (c.key)
     * gives the row its identity, else its index does. A null state keeps
     * the list's state on the element.
     */
    public static Element list(Context c, ListState state, int count, java.util.function.IntConsumer row) {
        Element e = open(c, Node.BOX, false, Align.STRETCH);
        Node n = e.node();
        if (n == null) {
            return e;
        }
        jui.ui.engine.Engine en = c.engine();
        en.makeScroll(e.index);
        ListState st = state != null ? state : local(e, "list", ListState::new).get();
        en.listPlan(e.index, st.model, count);
        int saved = en.enter(e.index);
        try {
            for (int i = st.model.builtFirst(); i <= st.model.builtLast(); i++) {
                int before = n.childCount;
                en.rowIndex(i);
                row.accept(i);
                en.rowDone();
                if (n.childCount != before + 1) {
                    jui.ui.engine.Dev.fail("a list row builds one element: row " + i + " built " + (n.childCount - before));
                }
            }
        } finally {
            en.exit(saved);
        }
        return e;
    }

    // Controls (MyGO's widgets.go and base.go). They build their parts with
    // Engine.enter/exit, not children(Runnable): a capturing lambda per
    // control would allocate in every frame of a native image.

    private static final Color WHITE = Color.rgb(0xFFFFFF);
    private static final Color KNOB_SHADOW = Color.rgba(0x000000, 0.25f);
    private static final java.time.Duration SWITCH_SLIDE = java.time.Duration.ofMillis(140);

    /** A button without looks: a centered row that takes focus and clicks; style it as you like. */
    public static Element buttonBase(Context c) {
        Element e = open(c, Node.BOX, true, Align.CENTER);
        Node n = e.node();
        if (n != null) {
            n.justify = Justify.CENTER;
            n.shrink = 0;
            n.flags |= Node.FOCUSABLE | Node.CLICKABLE | Node.HOVER;
        }
        return e;
    }

    /** A button with a label, on the theme's surface. */
    public static Element button(Context c, String label) {
        return button(c, label, false);
    }

    /** A button for the main action, in the theme's accent. */
    public static Element primaryButton(Context c, String label) {
        return button(c, label, true);
    }

    private static Element button(Context c, String label, boolean primary) {
        Theme t = c.theme();
        Element e = buttonBase(c);
        Node n = e.node();
        if (n == null) {
            return e;
        }
        e.padding(6, 14).gap(6).radius(t.radius());
        if (primary) {
            n.background = t.accent().rgba();
            n.bgHover = t.accentHover().rgba();
            n.bgPressed = t.accentPressed().rgba();
            e.textColor(t.accentText());
        } else {
            n.background = t.surface().rgba();
            n.bgHover = t.surfaceHover().rgba();
            n.bgPressed = t.surfacePressed().rgba();
            n.border = 1;
            n.borderColor = t.border().rgba();
            e.textColor(t.text());
        }
        int saved = c.engine().enter(e.index);
        try {
            text(c, label).singleLine();
        } finally {
            c.engine().exit(saved);
        }
        return e;
    }

    /** A toggle without looks: a row that takes focus and flips ref when clicked. */
    public static Element toggleBase(Context c, BoolRef ref) {
        Element e = open(c, Node.BOX, true, Align.CENTER);
        Node n = e.node();
        if (n != null) {
            n.flags |= Node.FOCUSABLE | Node.CLICKABLE | Node.HOVER | Node.TOGGLE;
            if (e.clicked()) {
                ref.set(!ref.get());
                c.engine().markChanged(e.index);
            }
        }
        return e;
    }

    /** A check box and its label, bound to ref. */
    public static Element checkbox(Context c, BoolRef ref, String label) {
        Theme t = c.theme();
        Element row = toggleBase(c, ref);
        if (row.node() == null) {
            return row;
        }
        row.gap(8);
        row.node().ownRing = true; // the ring goes on the box
        boolean hot = row.hovered();
        int saved = c.engine().enter(row.index);
        try {
            Element box = box(c).size(16, 16).radius(4).shrink(0);
            Node b = box.node();
            b.ringOwner = row.index;
            if (ref.get()) {
                b.background = t.accent().rgba();
                b.checkMark = true; // drawn as a stroke, not a glyph (as MyGO)
            } else {
                b.background = t.background().rgba();
                b.border = 1;
                b.borderColor = hot ? t.accent().rgba() : mix(t.border(), t.text(), 0.25f);
            }
            text(c, label);
        } finally {
            c.engine().exit(saved);
        }
        return row;
    }

    /** A switch and its label, bound to ref. */
    public static Element toggle(Context c, BoolRef ref, String label) {
        Theme t = c.theme();
        Element row = toggleBase(c, ref);
        if (row.node() == null) {
            return row;
        }
        row.gap(8);
        row.node().ownRing = true; // the ring goes on the track
        // The knob slides, and the track's color follows it (as MyGO's Switch, 140 ms).
        float pos = row.animate("knob", ref.get() ? 1 : 0, SWITCH_SLIDE);
        int saved = c.engine().enter(row.index);
        try {
            Element track = row(c).size(36, 20).radius(10).padding(2).shrink(0);
            Node k = track.node();
            k.ringOwner = row.index;
            k.background = mix(mix(t.border(), t.text(), 0.15f), t.accent().rgba(), pos);
            int inner = c.engine().enter(track.index);
            try {
                box(c).size(16, 16).radius(8).background(WHITE).shadow(0, 1, 3, 0, KNOB_SHADOW).margin(0, 0, 0, 16 * pos);
            } finally {
                c.engine().exit(inner);
            }
            text(c, label);
        } finally {
            c.engine().exit(saved);
        }
        return row;
    }

    /**
     * A slider without looks, bound to value between lo and hi: held, it
     * follows the pointer across its content box; with focus, the arrows
     * step it, Page Up and Down by a tenth, Home and End to the ends. A step
     * above 0 rounds what the user sets to lo + k * step.
     */
    public static Element sliderBase(Context c, DoubleRef value, double lo, double hi, double step) {
        Element e = open(c, Node.SLIDER, false, Align.STRETCH);
        Node n = e.node();
        if (n == null) {
            return e;
        }
        n.flags |= Node.FOCUSABLE | Node.DRAG | Node.HOVER | Node.KEYS;
        n.ownRing = true; // the ring goes on the knob (Paint.slider)
        jui.ui.engine.Engine en = c.engine();
        jui.ui.engine.ElementState s = en.state(e.index);
        double v = value.get(), range = hi - lo;
        boolean off = en.disabled(e.index);
        boolean held = s.pressed && !off;
        boolean input = held || s.keyCount > 0 && !off;
        if (held && s.cw > 0) {
            v = lo + Math.max(0, Math.min(1, (en.pointerX() - s.cx) / s.cw)) * range;
        }
        double keyStep = step > 0 ? step : range / 100, page = Math.max(step, range / 10);
        for (int k = 0; input && k < s.keyCount; k++) {
            switch (s.keys[k]) {
                case LEFT, DOWN -> v -= keyStep;
                case RIGHT, UP -> v += keyStep;
                case PAGE_DOWN -> v -= page;
                case PAGE_UP -> v += page;
                case HOME -> v = lo;
                case END -> v = hi;
                default -> { }
            }
        }
        if (input) {
            v = Math.max(lo, Math.min(hi, v));
            if (step > 0) {
                v = Math.max(lo, Math.min(hi, lo + Math.round((v - lo) / step) * step));
            }
            if (v != value.get()) {
                value.set(v);
                en.markChanged(e.index);
            }
        }
        n.fraction = range > 0 ? (float) ((value.get() - lo) / range) : 0;
        n.held = held;
        return e;
    }

    /** A slider bound to value between lo and hi. */
    public static Element slider(Context c, DoubleRef value, double lo, double hi) {
        return slider(c, value, lo, hi, 0);
    }

    /** A slider whose value rounds to lo + k * step. */
    public static Element slider(Context c, DoubleRef value, double lo, double hi, double step) {
        return sliderBase(c, value, lo, hi, step).height(20).minWidth(80).padding(0, 10);
    }

    /**
     * A single-line text input without looks, bound to ref: it takes focus,
     * keys, the input method's text, the clipboard and the pointer; style it
     * as you like (as MyGO's TextInputBase).
     */
    public static Element textInputBase(Context c, TextRef ref) {
        Element e = open(c, Node.INPUT, false, Align.STRETCH);
        Node n = e.node();
        if (n == null) {
            return e;
        }
        n.flags |= Node.FOCUSABLE | Node.HOVER | Node.DRAG | Node.TEXT_INPUT;
        n.clip = true;
        c.engine().textInput(e.index, ref);
        return e;
    }

    /** A single-line text input bound to ref, on the theme's surface (as MyGO's TextInput). */
    public static Element textInput(Context c, TextRef ref) {
        Theme t = c.theme();
        Element e = textInputBase(c, ref);
        Node n = e.node();
        if (n == null) {
            return e;
        }
        e.padding(6, 10).radius(t.radius());
        n.background = t.surface().rgba();
        n.border = 1;
        // Focus shows on the border, in the accent; hover leans it to the text's color (as MyGO's inputBorder).
        n.borderColor = e.focused() ? t.accent().rgba() : e.hovered() ? mix(t.border(), t.text(), 0.25f) : t.border().rgba();
        return e;
    }

    /** a mixed a share t of the way to b, as 0xRRGGBBAA, without making a Color. */
    private static int mix(Color a, Color b, float t) {
        return mix(a.rgba(), b.rgba(), t);
    }

    private static int mix(int a, int b, float t) {
        int mixed = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int x = (a >>> shift) & 0xFF, y = (b >>> shift) & 0xFF;
            mixed |= Math.round(x + (y - x) * t) << shift;
        }
        return mixed;
    }

    // Local state: kept on an element's identity from frame to frame (as MyGO's ui.Local).

    /**
     * A value kept on the element from frame to frame: {@code initial} makes
     * it the first time, and it goes when the element stops being built.
     * {@code name} tells apart several values on one element.
     */
    @SuppressWarnings("unchecked")
    public static <T> Ref<T> local(Element e, String name, java.util.function.Supplier<T> initial) {
        java.util.Map<String, Object> m = e.locals();
        Object v = m == null ? null : m.get(name);
        if (v instanceof Ref<?> r) {
            return (Ref<T>) r;
        }
        Ref<T> r = new Ref<>(initial.get());
        if (m != null) {
            m.put(name, r);
        }
        return r;
    }

    public static BoolRef localBool(Element e, String name, boolean initial) {
        java.util.Map<String, Object> m = e.locals();
        Object v = m == null ? null : m.get(name);
        if (v instanceof BoolRef r) {
            return r;
        }
        BoolRef r = new BoolRef(initial);
        if (m != null) {
            m.put(name, r);
        }
        return r;
    }

    public static FloatRef localFloat(Element e, String name, float initial) {
        java.util.Map<String, Object> m = e.locals();
        Object v = m == null ? null : m.get(name);
        if (v instanceof FloatRef r) {
            return r;
        }
        FloatRef r = new FloatRef(initial);
        if (m != null) {
            m.put(name, r);
        }
        return r;
    }

    static Element open(Context c, byte kind, boolean row, Align alignItems) {
        Engine engine = c.engine();
        int index = engine.open(kind);
        if (index != Node.NONE) {
            Node n = engine.node(index);
            n.row = row;
            n.alignItems = alignItems;
        }
        return c.element(index);
    }
}
