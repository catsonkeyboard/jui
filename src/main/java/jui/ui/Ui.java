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

    public static Element canvas(Context context) { return box(context); }

    public static Element richText(Context context, Spans spans) {
        java.util.Objects.requireNonNull(spans);
        Element element = text(context, spans.text());
        Node node = element.node();
        if (node != null) node.spans = spans;
        return element;
    }

    public static void contextMenu(Context context, Element target, java.util.function.Consumer<Menu> build) {
        if (target == null || target.c.engine() != context.engine()) {
            jui.ui.engine.Dev.fail("contextMenu requires an element from this context");
            return;
        }
        Node node = target.node();
        if (node == null) return;
        node.contextMenu = build;
        if (build == null) node.flags &= ~Node.CONTEXT_MENU;
        else {
            node.flags |= Node.CONTEXT_MENU;
            context.engine().state(target.index);
        }
    }

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
        return scroll(c, null, false, Align.STRETCH, false, true);
    }

    /** A scroll container whose offset state reads and moves. */
    public static Element scroll(Context c, ScrollState state) {
        return scroll(c, state, false, Align.STRETCH, false, true);
    }

    /**
     * A row that scrolls across when its content is wider than it: its
     * children centered down it, as row's (MyGO's ScrollHorizontal). A
     * vertical wheel passes on to a container around it; Shift turns it.
     */
    public static Element scrollHorizontal(Context c) {
        return scroll(c, null, true, Align.CENTER, true, false);
    }

    /** A horizontal scroll container whose offset state reads and moves. */
    public static Element scrollHorizontal(Context c, ScrollState state) {
        return scroll(c, state, true, Align.CENTER, true, false);
    }

    /**
     * A column that scrolls both ways: its children as wide as their
     * content, those that stretch as wide as the widest (MyGO's ScrollBoth).
     */
    public static Element scrollBoth(Context c) {
        return scroll(c, null, false, Align.STRETCH, true, true);
    }

    /** A scroll container both ways whose offset state reads and moves. */
    public static Element scrollBoth(Context c, ScrollState state) {
        return scroll(c, state, false, Align.STRETCH, true, true);
    }

    private static Element scroll(Context c, ScrollState state, boolean row, Align align, boolean x, boolean y) {
        Element e = open(c, Node.BOX, row, align);
        Node n = e.node();
        if (n != null) {
            if (state != null) {
                c.engine().state(e.index); // the container's state first, so a request bound now lands on it
                state.bind(c.engine(), n.id);
            }
            c.engine().makeScroll(e.index, x, y);
        }
        return e;
    }

    /**
     * A row that shares its room between first and second at a divider the
     * user drags, or moves with the arrows once it has the focus: firstWidth
     * is the first's width, which the divider keeps 40 DIPs of room on either
     * side of itself. With less room than that the first gives way (the
     * second keeps its 40), holding firstWidth for when there is room again.
     * changed() reports a move.
     */
    public static Element split(Context c, FloatRef firstWidth, Runnable first, Runnable second) {
        Element e = row(c).alignItems(Align.STRETCH);
        e.children(() -> {
            jui.ui.engine.Engine en = c.engine();
            // The room the panes share, as the last frame laid it out.
            Element room = row(c).grow(1).alignItems(Align.STRETCH);
            room.children(() -> {
                float size = firstWidth.get();
                float total = en.state(room.index).w;       // the room as last laid out, 0 before the first
                float shown = total <= 0 || size <= MIN_PANE ? size : Math.max(MIN_PANE, Math.min(size, total - 1 - MIN_PANE));
                // The panes meet at the line, whatever the room: the flexbox gives way for them.
                box(c).width(size).minWidth(Math.min(size, MIN_PANE)).shrink(1).clip().label("first-pane").children(first);
                box(c).width(1); // the line's place in the flow
                box(c).grow(1).minWidth(MIN_PANE).shrink(1).clip().children(second);

                // The divider: an 8 DIP handle over the line, with the line as its child.
                Element div = box(c).absolute().alignItems(Align.CENTER).width(8).left(shown - 3.5f).top(0).bottom(0)
                        .focusable().cursor(Cursor.RESIZE_EW).label("divider");
                Node n = div.node();
                n.flags |= Node.DRAG | Node.HOVER | Node.KEYS;
                jui.ui.engine.ElementState ds = en.state(div.index);
                boolean off = en.disabled(div.index);
                boolean held = ds.pressed && !off;
                float move = held ? ds.dragX : 0;
                if (!off && move != 0) {
                    setSplitWidth(en, e.index, firstWidth, shown + move, total);
                }
                for (int k = 0; !off && k < ds.keyCount; k++) {
                    if (ds.keys[k] == Key.LEFT) {
                        setSplitWidth(en, e.index, firstWidth, shown - 8, total);
                    }
                    if (ds.keys[k] == Key.RIGHT) {
                        setSplitWidth(en, e.index, firstWidth, shown + 8, total);
                    }
                }
                Theme t = c.theme();
                int line = held || en.hovered(div.index) ? t.accent().rgba() : t.border().rgba();
                div.children(() -> {
                    Element l = box(c).width(1).fillHeight().label("line");
                    if (l.node() != null) {
                        l.node().background = line;         // the 1 DIP line, centered in the handle
                    }
                });
            });
        });
        return e;
    }

    /**
     * A row of tabs showing titles, of which selected is the index of the
     * one chosen: a click chooses a tab, as do the arrows, Home and End
     * while one has the focus, which follows the choice. changed() reports
     * a new choice.
     */
    public static Element tabs(Context c, IntRef selected, String... titles) {
        Theme t = c.theme();
        Element e = row(c).gap(4);
        e.children(() -> {
            int n = titles.length;
            if (n == 0) {
                return;
            }
            jui.ui.engine.Engine en = c.engine();
            int clamped = Math.max(0, Math.min(selected.get(), n - 1));
            if (clamped != selected.get()) {
                selected.set(clamped);
            }
            // The tabs' identities, for the focus to follow a keyboard choice (from local state: no
            // steady-frame allocation).
            Ref<long[]> ids = local(e, "tabs", () -> new long[n]);
            long[] tabIds = ids.get().length == n ? ids.get() : new long[n];
            int follow = -1;
            for (int i = 0; i < n; i++) {
                boolean on = i == selected.get();
                Element tab = buttonBase(c).padding(8, 12).label(titles[i]);
                Node tn = tab.node();
                tn.flags |= Node.KEYS;
                jui.ui.engine.ElementState s = en.state(tab.index);
                int pick = -1;
                if (tab.clicked()) {
                    pick = i;
                }
                for (int k = 0; k < s.keyCount; k++) {
                    switch (s.keys[k]) {
                        case LEFT, UP -> pick = (i - 1 + n) % n;
                        case RIGHT, DOWN -> pick = (i + 1) % n;
                        case HOME -> pick = 0;
                        case END -> pick = n - 1;
                        default -> { }
                    }
                }
                if (pick >= 0 && pick != selected.get()) {
                    selected.set(pick);
                    en.markChanged(e.index);
                    follow = pick;
                    on = i == selected.get();
                }
                tabIds[i] = tn.id;
                if (follow == i) {
                    en.requestFocus(tn.id);                  // the target was built after the source
                    follow = -1;
                }
                int saved = en.enter(tab.index);
                try {
                    int color = on || en.hovered(tab.index) ? t.text().rgba() : t.textMuted().rgba();
                    text(c, titles[i]).singleLine().textColor(new Color(color)).fontWeight(on ? 600 : 400);
                    if (on) {                                // the 2 DIP accent underline
                        Element u = box(c).absolute().height(2).left(6).right(6).bottom(0).radius(1);
                        if (u.node() != null) {
                            u.node().background = t.accent().rgba();
                        }
                    }
                } finally {
                    en.exit(saved);
                }
            }
            if (follow >= 0 && follow < n) {
                en.requestFocus(tabIds[follow]);             // the target was built before the choice
            }
        });
        return e;
    }

    /** Sets the split's width, clamped to leave 40 DIPs of room on either side of the divider. */
    private static void setSplitWidth(jui.ui.engine.Engine en, int index, FloatRef firstWidth, float v, float total) {
        if (total > 0) {
            v = Math.max(MIN_PANE, Math.min(v, total - 1 - MIN_PANE));
        }
        if (v != firstWidth.get()) {
            firstWidth.set(v);
            en.markChanged(index);
        }
    }

    /** The least a split's pane is given when the room runs short. */
    static final float MIN_PANE = 40;

    /**
     * A dialog over the window while open is true: a dimmed backdrop with
     * the content centered on it, which clicking, or pressing Escape,
     * closes; dismissed() reports that once. While it shows, the focus
     * stays in it and comes back to what had it when it opened. Style the
     * panel on what this returns; build nothing while it is closed.
     */
    public static Element modal(Context c, BoolRef open, Runnable content) {
        Theme t = c.theme();
        jui.ui.engine.Engine en = c.engine();
        Element back = box(c).absolute().left(0).top(0).right(0).bottom(0).center();
        Node bn = back.node();
        bn.flags |= Node.CLICKABLE | Node.MODAL;
        jui.ui.engine.ElementState bs = en.state(back.index);
        boolean on = open.get();
        if (!on) {
            back.invisible();
        } else {
            back.background(Color.rgba(0x000000, 0.4f));
        }
        int saved = en.enter(back.index);
        Element panel;
        try {
            panel = box(c).padding(20).gap(12).radius(t.radius()).background(t.surface())
                    .shadow(0, 10, 30, 0, Color.rgba(0x000000, 0.3f))
                    .maxWidth(en.windowWidth() - 40).maxHeight(en.windowHeight() - 40);
            if (!on) {
                panel.invisible();
            } else {
                panel.node().flags |= Node.CLICKABLE;        // presses on the panel stay in it
                if (!bs.modalOpen) {
                    bs.modalOpen = true;                     // where the focus returns when it closes
                    bs.modalReturn = en.focusedIdentity();
                }
                if (bs.escaped > 0 || back.clicked()) {
                    bs.escaped = 0;
                    open.set(false);
                    en.markDismissed(panel.index);
                } else {
                    int into = en.enter(panel.index);
                    try {
                        content.run();
                    } finally {
                        en.exit(into);
                    }
                }
            }
        } finally {
            en.exit(saved);
        }
        if (bs.modalOpen && !on) {
            bs.modalOpen = false;
            if (bs.modalReturn != 0) {
                en.requestFocus(bs.modalReturn);
            }
        }
        return panel;
    }

    /**
     * A panel over the window below anchor while open is true, as wide as
     * the anchor at least, start-aligned with it; where that would overflow
     * the window it goes above the anchor, or slides along it. A press
     * outside it and the anchor, or Escape, closes it, which dismissed()
     * reports once; the anchor going away closes it too. Style the panel on
     * what this returns; build nothing while it is closed. Its margin is
     * its gap from the anchor.
     */
    public static Element popover(Context c, Element anchor, BoolRef open, Runnable content) {
        Theme t = c.theme();
        jui.ui.engine.Engine en = c.engine();
        if (!open.get() || anchor == null) {
            if (open.get()) {
                open.set(false);                             // the anchor went away
            }
            return box(c).invisible();
        }
        Element panel = box(c).absolute().margin(4, 0, 0, 0).padding(4).gap(4)
                .radius(t.radius() + 2).background(t.background()).border(1, t.border())
                .shadow(0, 6, 20, 0, Color.rgba(0x000000, 0.18f)).label("popover");
        Node pn = panel.node();
        pn.flags |= Node.CLICKABLE | Node.ESCAPABLE;         // presses on it stay in it
        pn.placeOn = anchor.index;
        panel.minWidth(anchor.bounds().width());
        jui.ui.engine.ElementState ps = en.state(panel.index);
        ps.anchorId = anchor.node().id;
        if (ps.escaped > 0 || ps.outside > 0) {
            ps.escaped = 0;
            ps.outside = 0;
            open.set(false);
            en.markDismissed(panel.index);
            return panel;
        }
        int saved = en.enter(panel.index);
        try {
            content.run();
        } finally {
            en.exit(saved);
        }
        return panel;
    }

    /**
     * A list of count rows that builds only those in view: row builds row i,
     * as one element. Rows may be of any height. A row's identity is its
     * key when the state has keys (ListState.keys), else a key set in row
     * (c.key), else its index. The row the focus is in stays built when it
     * scrolls out of view. A null state keeps the list's state on the
     * element.
     */
    public static Element list(Context c, ListState state, int count, java.util.function.IntConsumer row) {
        Element e = open(c, Node.BOX, false, Align.STRETCH);
        Node n = e.node();
        if (n == null) {
            return e;
        }
        jui.ui.engine.Engine en = c.engine();
        en.makeScroll(e.index, false, true);
        ListState st = state != null ? state : local(e, "list", ListState::new).get();
        jui.ui.engine.ListModel m = st.model;
        en.listPlan(e.index, m, count);
        int saved = en.enter(e.index);
        try {
            // In row order: the focus's row out of view goes before the rows in view or after them.
            int extra = m.extraRow();
            if (extra >= 0 && extra < m.builtFirst()) {
                row(en, n, m, row, extra);
            }
            for (int i = m.builtFirst(); i <= m.builtLast(); i++) {
                row(en, n, m, row, i);
            }
            if (extra > m.builtLast()) {
                row(en, n, m, row, extra);
            }
        } finally {
            en.exit(saved);
        }
        return e;
    }

    private static void row(jui.ui.engine.Engine en, Node n, jui.ui.engine.ListModel m,
                            java.util.function.IntConsumer row, int i) {
        int before = n.childCount;
        java.util.function.IntToLongFunction keyOf = m.keyOf();
        if (keyOf != null) {
            en.key(keyOf.applyAsLong(i)); // the row is its item: a key in row is one too many
        } else {
            en.rowIndex(i);
        }
        row.accept(i);
        en.rowDone();
        if (n.childCount != before + 1) {
            jui.ui.engine.Dev.fail("a list row builds one element: row " + i + " built " + (n.childCount - before));
        }
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

    public static Element textAreaBase(Context context, TextRef value) {
        Element element = open(context, Node.INPUT, false, Align.STRETCH);
        Node node = element.node();
        if (node != null) {
            node.multiline = true;
            node.flags |= Node.FOCUSABLE | Node.HOVER | Node.DRAG | Node.TEXT_INPUT;
            context.engine().textInput(element.index, value, true);
            context.engine().makeScroll(element.index, false, true);
        }
        return element;
    }

    public static Element textArea(Context context, TextRef value) {
        Element element = textAreaBase(context, value);
        Node node = element.node();
        if (node != null) {
            Theme theme = context.theme();
            element.padding(6, 10).radius(theme.radius());
            node.inputStyled = true;
            node.background = theme.surface().rgba();
            node.border = 1;
            node.borderColor = element.focused() ? theme.accent().rgba()
                    : element.hovered() ? mix(theme.border(), theme.text(), 0.25f) : theme.border().rgba();
        }
        return element;
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
