package jui.ui.engine;

import jui.ffi.Libc;
import jui.text.TextEngine;
import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Rect;
import jui.ui.Theme;
import jui.ui.View;

/**
 * A window's UI engine: builds the view into the node pool, lays it out and
 * paints it. Internal: its public methods serve the ui package, Tester and
 * the window, not apps.
 */
public final class Engine {
    private final TextEngine text;
    private Theme theme;
    private final Runnable requestFrame;
    private final java.util.function.LongSupplier clock;
    private final NodePool pool = new NodePool();
    private final StateTable states = new StateTable();
    private final HitList hits = new HitList();
    private final Input input = new Input(this, hits);
    private final Actions clickActions = new Actions();
    private final Actions changeActions = new Actions();
    private final Actions submitActions = new Actions();
    /** The platform: clipboard, cursor, input method. A window sets its own; Tester's is in memory. */
    private Host host = new Host.Memory();
    private final Context context;
    private final FlexLayout layout = new FlexLayout(pool, this::measure);

    /** The most passes a frame builds (design §4, MyGO's rule). */
    private static final int MAX_PASSES = 3;

    /** The time the frame being built started, in nanoseconds; and the earliest wake-up it asked for. */
    private long now;
    private long wake = Long.MAX_VALUE;

    /** Frames built so far, the one in progress included; and the pass of it being built. */
    private long frame;
    private int pass;
    /** Whether the pass being built reported input or ran a callback, so another pass must show it. */
    private boolean consumed;
    private int passes;
    /** A focus asked for (by a Handle or autoFocus), applied after a pass that builds it enabled. */
    private long focusRequest;

    // The build in progress.
    private boolean building;
    private int parent = Node.NONE;
    private boolean keyPending;
    private long pendingKey;
    /** A list row's index, the next element's identity unless it is keyed. */
    private boolean rowPending;
    private int rowIndex;
    /** The window's height in this frame: a list's view before it has been laid out. */
    private float windowH;
    /** Dev mode: this frame's nodes by identity, to find siblings with the same key at once. */
    private final java.util.HashMap<Long, Integer> nodesById = Dev.ENABLED ? new java.util.HashMap<>() : null;

    /** {@code text} may be null for views without text, as in layout tests; time is System.nanoTime(). */
    public Engine(TextEngine text, Theme theme, Runnable requestFrame) {
        this(text, theme, requestFrame, System::nanoTime);
    }

    /** As the other, with time in nanoseconds from clock: a Tester's own, say. */
    public Engine(TextEngine text, Theme theme, Runnable requestFrame, java.util.function.LongSupplier clock) {
        this.text = text;
        this.theme = theme;
        this.requestFrame = requestFrame;
        this.clock = clock;
        this.context = new Context(this);
    }

    public Context context() {
        return context;
    }

    public Theme theme() {
        return theme;
    }

    public TextEngine text() {
        return text;
    }

    /**
     * Builds the view for a window of width by height DIPs, then lays it out
     * and commits it. A pass that reports input or runs a callback is built
     * again, at most 3 passes in all, so that the frame shows its result.
     */
    public void buildAndLayout(View view, float width, float height) {
        if (Libc.pthreadMainNp() == 0) {
            Dev.fail("views build only on the main thread");
        }
        frame++;
        now = clock.getAsLong();
        wake = Long.MAX_VALUE; // a frame that asks for no wake-up stops the timer (as MyGO)
        input.advanceGlides(now);
        input.routeKey();
        windowH = height;
        // A round builds, lays out and fixes up lists; a list that did not cover its view
        // in the first round gets a second, in the same frame (at most two rounds).
        for (int round = 0; ; round++) {
            for (pass = 0; pass < MAX_PASSES; pass++) {
                consumed = false;
                input.beginPass();
                buildPass(view, width, height);
                endPass();
                if (pass == 0) {
                    input.dropKey();
                }
                if (!consumed) {
                    break;
                }
            }
            passes = Math.min(pass + 1, MAX_PASSES);
            pass = passes - 1;
            if (consumed) {
                requestFrame.run(); // still reporting after the last pass: show the rest next frame
            }
            inheritTextStyle();
            layout.layout(0, width, height);
            boolean again = afterLayout();
            int revealed = reveal();
            if ((revealed & REVEAL_ROUND) != 0) {
                again = true; // a list moved: another round builds and lays out what reveal asked for
            } else if ((revealed & REVEAL_LAYOUT) != 0) {
                layout.layout(0, width, height); // the offsets reveal set, in this frame
                again |= afterLayout();
            }
            if (!again) {
                break;
            }
            if (round == 1) {
                requestFrame.run(); // still not covered: the next frame goes on
                break;
            }
        }
        commit();
    }

    private void buildPass(View view, float width, float height) {
        pool.reset();
        if (Dev.ENABLED) {
            nodesById.clear();
        }
        int root = pool.open();
        Node r = pool.node(root);
        r.id = Identity.ROOT;
        r.width = width;
        r.height = height;
        r.alignItems = Align.STRETCH;
        building = true;
        parent = root;
        keyPending = false;
        try {
            view.build(context);
            checkKeyUsed();
        } finally {
            building = false;
            parent = Node.NONE;
        }
        // An element disabled after its children were built disables them too:
        // parents come before their children in the pool.
        for (int i = 1; i < pool.size(); i++) {
            Node n = pool.node(i);
            if (!n.disabled && pool.node(n.parent).disabled) {
                n.disabled = true;
            }
        }
        // Callbacks run once the whole view is built, so they may change what it
        // iterated: changes first, then focus requests, then submits, then clicks (as MyGO,
        // which has no submit callbacks).
        try {
            consumed |= changeActions.run(pool);
            applyFocusRequest();
            consumed |= submitActions.run(pool);
            consumed |= clickActions.run(pool);
        } finally {
            changeActions.clear();
            submitActions.clear();
            clickActions.clear();
        }
    }

    /** Runs action once this pass's view is built: the node's control changed its value. */
    public void onChange(int index, Runnable action) {
        if (changed(index)) {
            changeActions.add(index, action);
        }
    }

    /** Whether the node's control changed its value in this pass. */
    public boolean changed(int index) {
        boolean ch = state(index).changed;
        consumed |= ch;
        return ch;
    }

    /** A control changed its bound value in this pass. */
    public void markChanged(int index) {
        state(index).changed = true;
    }

    /** Makes the node the parent of what is built next, and returns the parent to give back to {@link #exit}. */
    public int enter(int index) {
        int saved = parent;
        parent = index;
        return saved;
    }

    public void exit(int saved) {
        checkKeyUsed();
        parent = saved;
    }

    /** Where the pointer is, in DIPs: for controls that follow it. */
    public float pointerX() {
        return input.pointerX;
    }

    /** Gives focus to the element asked for, if this pass built it enabled; else the request waits. */
    private void applyFocusRequest() {
        if (focusRequest == 0) {
            return;
        }
        for (int i = 0; i < pool.size(); i++) {
            Node n = pool.node(i);
            if (n.id == focusRequest) {
                if (!n.disabled) {
                    if (input.focusedId != focusRequest) {
                        input.focusedId = focusRequest;
                        consumed = true;
                    }
                    focusRequest = 0;
                }
                return;
            }
        }
    }

    /** Asks for focus on identity id: see {@link #applyFocusRequest}. */
    public void requestFocus(long id) {
        focusRequest = id;
    }

    /**
     * The node's text input, bound to ref: syncs its editing with the value
     * (the app's change wins; else the user's edits are written back and
     * reported), drops a composition while disabled, and shows the text.
     */
    public void textInput(int index, jui.ui.TextRef ref) {
        Node n = pool.node(index);
        ElementState s = state(index);
        TextEdit e = s.edit;
        String value = ref.get();
        if (e == null) {
            e = s.edit = new TextEdit(value);
        } else if (!value.equals(e.synced)) {
            boolean composing = e.composing();
            e.setText(value); // the app's value, over any edit since (as MyGO)
            if (composing) {
                host.discardMarkedText();
            }
        } else if (e.dirty) {
            e.dirty = false;
            e.synced = e.text();
            ref.set(e.synced);
            markChanged(index);
        }
        if (off(n) && e.composing()) {
            e.setMarked("", 0, 0, -1, 0, now);
            host.discardMarkedText();
        }
        n.text = e.display();
        n.noFocusRing = true; // a text input shows focus with its border, not a ring (as MyGO)
        n.inputCaret = e.displayCaret();
        if (e.composing()) {
            n.inputSelA = n.inputSelB = n.inputCaret;
            n.inputMarkA = e.caret();
            n.inputMarkB = e.caret() + e.compose().length();
        } else {
            n.inputSelA = e.selStart();
            n.inputSelB = e.selEnd();
            n.inputMarkA = n.inputMarkB = -1;
        }
        n.inputFocused = focused(n.id);
        if (n.inputFocused) {
            // The caret blinks from the last key, edit or focus change; after 30 s it rests, shown.
            long phase = Math.max(0, now - input.blinkStart);
            n.caretShown = phase >= BLINK_REST || (phase / BLINK) % 2 == 0;
            if (phase < BLINK_REST) {
                after(now + BLINK - phase % BLINK);
            }
        }
    }

    /** The caret's blink, each way, and how long after the last change it blinks (as MyGO, 530 ms and 30 s). */
    private static final long BLINK = 530_000_000L, BLINK_REST = 30_000_000_000L;

    /** Runs action once this pass's view is built: Enter was pressed in the node's text input. */
    public void onSubmit(int index, Runnable action) {
        if (submitted(index)) {
            submitActions.add(index, action);
        }
    }

    /** Whether Enter was pressed in the node's text input since the last pass that built it. */
    public boolean submitted(int index) {
        Node n = pool.node(index);
        ElementState s = state(index);
        boolean k = !off(n) && s.submits > 0;
        consumed |= k;
        return k;
    }

    /** Runs action once this pass's view is built: the node was clicked. */
    public void onClick(int index, Runnable action) {
        if (clicks(index) > 0) {
            clickActions.add(index, action);
        }
    }

    /** Forgets the input that this pass's elements could report: each input is reported once. */
    private void endPass() {
        for (int i = 0; i < pool.size(); i++) {
            ElementState s = pool.node(i).state;
            if (s != null && s.seenFrame == frame && s.seenPass == pass) {
                s.clicks = s.rightClicks = s.doubleClicks = 0;
                s.submits = 0;
                s.pressPending = false;
                s.changed = false;
                s.dragX = s.dragY = 0;
                s.keyCount = 0;
            }
        }
    }

    /** The time the frame started, in nanoseconds: the same in all its passes. */
    public long now() {
        return now;
    }

    /** Asks for a frame at {@code at} (nanoseconds, on the frame clock), unless an earlier one is asked for. */
    public void after(long at) {
        wake = Math.min(wake, at);
    }

    /** The wake-up the last frame asked for, or Long.MAX_VALUE: the window arms a timer for it. */
    public long nextWake() {
        return wake;
    }

    /**
     * The node's animated value under key, eased out to target over
     * duration nanoseconds from the frame the target changed (as MyGO's
     * Animate): at its target the first time, the old value in the frame
     * the target changes; while it moves it asks for frames.
     */
    public float animate(int index, String key, float target, long duration) {
        ElementState s = state(index);
        if (s.motions == null) {
            s.motions = new java.util.HashMap<>();
        }
        Motion m = s.motions.get(key);
        if (m == null) {
            m = new Motion();
            m.from = m.to = m.value = target;
            s.motions.put(key, m);
            return target;
        }
        m.advance(now);
        if (target != m.to) {
            m.from = m.value;
            m.to = target;
            m.start = now;
            m.duration = Math.max(1, duration);
            m.moving = true;
        }
        if (m.moving) {
            requestFrame.run();
        }
        return m.value;
    }

    /** Passes the last frame built. */
    public int passes() {
        return passes;
    }

    /** Marks the pass as having reported input, so another pass shows its result. */
    public void consume() {
        consumed = true;
    }

    /**
     * Writes each node's box into its state, for the next frame's queries,
     * then lets go of the states this frame did not build.
     */
    private void commit() {
        hits.clear();
        commitHits(0, -1, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY);
        for (int i = 0; i < pool.size(); i++) {
            Node n = pool.node(i);
            ElementState s = n.state;
            if (s != null) {
                s.x = n.x;
                s.y = n.y;
                s.w = n.w;
                s.h = n.h;
                s.cx = n.x + n.padLeft + n.border;
                s.cy = n.y + n.padTop + n.border;
                s.cw = Math.max(0, n.w - n.padLeft - n.padRight - 2 * n.border);
                s.ch = Math.max(0, n.h - n.padTop - n.padBottom - 2 * n.border);
                s.disabledLast = n.disabled;
                if ((n.flags & Node.SCROLL) != 0) {
                    s.scrollY = n.scrollY;
                    s.contentH = n.contentH;
                    s.viewH = s.ch;
                    s.requestY = Float.NaN;
                }
                if (n.kind == Node.INPUT && s.edit != null && n.layout != null) {
                    inputGeometry(n, s);
                }
            }
        }
        states.prune(frame, pass);
        input.commitFocus();
        input.commitShortcuts();
        input.rehover();
        input.updateCursor();
        input.updateTextInput();
        // The looks that hover and press pick at paint, as the pointer stands now.
        for (int i = 0; i < pool.size(); i++) {
            Node n = pool.node(i);
            if (n.bgHover != 0 || n.bgPressed != 0) {
                n.hot = !n.disabled && input.hoveredNode(n);
                n.down = !n.disabled && input.downNode(n);
            }
            if ((n.flags & Node.SCROLL) != 0) {
                // Its bar shows while the pointer is over it or its thumb is dragged, if it has room to scroll.
                n.barDrag = input.dragId == n.id;
                n.bars = (n.barDrag || input.hovering(n.id)) && n.contentH > n.h + 0.5f;
                if (n.state != null) {
                    n.state.barsShown = n.bars;
                }
            }
        }
    }

    /**
     * Adds the node and what it holds to the hit list in paint order (the
     * flow, then absolute children), so that what paints later is on top.
     * An invisible node takes no hits, nor its children, and loses focus.
     */
    private void commitHits(int index, int parentHit, float cx0, float cy0, float cx1, float cy1) {
        Node n = pool.node(index);
        if (n.invisible) {
            if (input.focusedId != 0 && holds(index, input.focusedId)) {
                input.focusedId = 0;
            }
            return;
        }
        int h = hits.size;
        // A text input without a cursor of its own shows the I-beam.
        byte cursor = n.cursor != 0 ? n.cursor : n.kind == Node.INPUT ? (byte) (jui.ui.Cursor.TEXT.ordinal() + 1) : 0;
        hits.add(n.id, n.x, n.y, n.w, n.h, parentHit, n.disabled ? n.flags | HitList.DISABLED : n.flags, cursor,
                cx0, cy0, cx1, cy1);
        if (n.clip) { // what it holds takes the pointer only inside its padding box
            cx0 = Math.max(cx0, n.x + n.border);
            cy0 = Math.max(cy0, n.y + n.border);
            cx1 = Math.min(cx1, n.x + n.w - n.border);
            cy1 = Math.min(cy1, n.y + n.h - n.border);
        }
        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            if (!pool.node(c).absolute) {
                commitHits(c, h, cx0, cy0, cx1, cy1);
            }
        }
        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            if (pool.node(c).absolute) {
                commitHits(c, h, cx0, cy0, cx1, cy1);
            }
        }
    }

    /**
     * A text input's geometry, for paint, presses and the input method: its
     * line, centered in the content box, and its horizontal scroll (as
     * MyGO's layoutInput): with focus, the least move that keeps the caret in
     * view; without, the start of the text.
     */
    private void inputGeometry(Node n, ElementState s) {
        TextEdit e = s.edit;
        jui.text.TextLayout l = n.layout;
        float cx = n.x + n.padLeft + n.border, cy = n.y + n.padTop + n.border;
        float cw = Math.max(0, n.w - n.padLeft - n.padRight - 2 * n.border);
        float ch = Math.max(0, n.h - n.padTop - n.padBottom - 2 * n.border);
        float lineH = Math.max(text.lineHeight(n.resolvedSize, n.resolvedWeight), l.height);
        float top = cy + Math.max((ch - lineH) / 2, 0);
        if (input.focusedId != n.id) {
            e.scrollX = 0;
        } else {
            float x = l.caretX(e.displayCaret());
            if (x - e.scrollX < 0) {
                e.scrollX = x;
            } else if (x - e.scrollX > cw - 1) {
                e.scrollX = x - cw + 1;
            }
        }
        e.scrollX = Math.max(0, Math.min(e.scrollX, Math.max(l.width - cw + 1, 0)));
        e.layout = l;
        e.originX = cx - e.scrollX;
        e.lineTop = top;
        e.lineH = lineH;
        e.clipX0 = n.x + n.border;
        e.clipY0 = n.y + n.border;
        e.clipX1 = n.x + n.w - n.border;
        e.clipY1 = n.y + n.h - n.border;
        n.inputX = e.originX;
        n.inputTop = top;
        n.inputLineH = lineH;
    }

    /** Whether the node or one inside it has identity id. */
    private boolean holds(int index, long id) {
        Node n = pool.node(index);
        if (n.id == id) {
            return true;
        }
        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            if (holds(c, id)) {
                return true;
            }
        }
        return false;
    }

    /** The frame being built, or the last one built: 1 for the first. */
    public long frame() {
        return frame;
    }

    /** The node's element state, made if it has none yet, and marked as seen in this pass. */
    public ElementState state(int index) {
        Node n = pool.node(index);
        if (n.state == null) {
            n.state = states.create(n.id, frame);
        }
        n.state.seenFrame = frame;
        n.state.seenPass = pass;
        return n.state;
    }

    /** The node's local state map, made on first use. */
    public java.util.Map<String, Object> locals(int index) {
        ElementState s = state(index);
        if (s.locals == null) {
            s.locals = new java.util.HashMap<>();
        }
        return s.locals;
    }

    StateTable states() {
        return states;
    }

    // Lists.

    /**
     * Plans a list for this pass, before its rows are built: from the view's
     * height (as last laid out, else the window's), the offset events or the
     * app changed, and the heights it knows, the rows to build (those in
     * view and two more each way) and where the first goes.
     */
    public void listPlan(int index, ListModel m, int count) {
        Node n = pool.node(index);
        n.flags |= Node.LIST;
        n.listModel = m;
        m.resize(count);
        float v = m.viewH > 0 ? m.viewH : windowH;
        ElementState s = state(index);
        if (s.born == frame) {
            s.scrollY = m.wroteY; // a list back after a frame away keeps its place: its state lives in the app
        }
        double max = Math.max(0, m.content() - v);
        if (m.revealed) {
            m.revealed = false; // a reveal set the anchor: keep it
        } else if (count > 0) {
            if (m.requestRow >= 0) {
                m.anchor = Math.min(m.requestRow, count - 1);
                m.anchorY = 0;
                m.atEnd = false;
            } else {
                boolean asked = !Float.isNaN(m.requestY);
                double y = asked ? Math.max(0, Math.min(max, m.requestY)) : s.scrollY;
                m.atEnd = max > 0.5 && y >= max - 0.5;
                if (m.atEnd) {
                    m.anchorEnd(v); // at the end: the last row meets the bottom (as MyGO)
                } else if (asked) {
                    m.anchorAt(y);
                } else {
                    double d = y - m.wroteY;
                    if (Math.abs(d) <= 2 * v) {
                        m.anchorY -= (float) d; // rows move by exactly what was scrolled, whatever the estimates
                    } else {
                        m.anchorAt(y);
                    }
                }
            }
        }
        if (count > 0) {
            m.normalize();
        }
        m.plan(v);
        n.listAnchor = m.anchor - m.builtFirst;
        n.listAnchorY = m.anchorY;
    }

    /** The next element is list row i: its identity is its index, unless it is keyed. */
    public void rowIndex(int i) {
        rowPending = true;
        rowIndex = i;
    }

    /** A row's callback returned: an index it did not use goes. */
    public void rowDone() {
        rowPending = false;
    }

    /** After a layout, each list's fix-up; reports whether one needs another round. */
    private boolean afterLayout() {
        boolean again = false;
        for (int i = 0; i < pool.size(); i++) {
            Node n = pool.node(i);
            if (n.listModel != null) {
                again |= afterList(n, n.listModel);
            }
        }
        return again;
    }

    /**
     * A list after layout: records its rows' heights, anchors to the first
     * row in view, pulls its ends to the view's edges, checks that its rows
     * cover the view, and writes its offset and content height.
     */
    private boolean afterList(Node n, ListModel m) {
        float vt = n.y + n.padTop + n.border, vb = n.y + n.h - n.padBottom - n.border, v = Math.max(0, vb - vt);
        int firstBefore = m.first, lastBefore = m.last;
        m.viewH = v;
        m.gap = n.gapY;
        Node first = null, last = null;
        int idx = m.builtFirst;
        boolean anchored = false;
        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            Node r = pool.node(c);
            if (r.absolute) {
                continue;
            }
            // A row takes its margins too: its height and its top are its margin box's.
            m.setHeight(idx, outerBottom(r) - outerTop(r));
            if (first == null) {
                first = r;
            }
            last = r;
            if (!anchored && outerBottom(r) > vt + 0.001f) {
                m.anchor = idx;
                m.anchorY = outerTop(r) - vt;
                anchored = true;
            }
            idx++;
        }
        boolean again = false;
        if (first == null) {
            m.first = m.last = -1;
        } else {
            if (!anchored) {
                m.anchor = m.builtLast;
                m.anchorY = outerTop(last) - vt;
            }
            float bottom = outerBottom(last), top = outerTop(first);
            if (m.atEnd && m.builtLast == m.count - 1) {
                // At the end the last row meets the bottom exactly: with the heights now measured,
                // another round plans it from them.
                again = Math.abs(vb - bottom) > 0.01f;
            } else if (m.builtLast == m.count - 1 && bottom < vb - 0.01f && m.content() > v + 0.5) {
                float shift = vb - bottom;
                if (m.builtFirst == 0) {
                    shift = Math.min(shift, vt - top); // row 0 never starts below the top
                }
                if (shift > 0.01f) {
                    m.anchorY += shift;
                    again = true;
                }
            } else if (m.builtFirst == 0 && top > vt + 0.01f) {
                m.anchorY -= top - vt;
                again = true;
            }
            if (!again) {
                boolean topCovered = m.builtFirst == 0 || top <= vt + 0.01f;
                boolean covered = m.builtLast == m.count - 1 || bottom >= vb - 0.01f;
                again = !(topCovered && covered);
            }
            m.first = m.anchor;
            m.last = m.anchor;
            int k = m.builtFirst;
            for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
                Node r = pool.node(c);
                if (!r.absolute) {
                    if (r.y < vb - 0.001f && k >= m.anchor) {
                        m.last = k;
                    }
                    k++;
                }
            }
        }
        double max = Math.max(0, m.content() - v);
        if (m.count > 0 && max <= 0.5) {
            // All the rows fit: they start at the top, and there is nothing to scroll.
            m.atEnd = false;
            if (m.anchor != 0 || Math.abs(m.anchorY) > 0.01f) {
                m.anchor = 0;
                m.anchorY = 0;
                again = true;
            }
        }
        double want = m.count == 0 ? 0 : m.top(m.anchor) - m.anchorY;
        float y = m.count == 0 ? 0 : m.atEnd ? (float) max : (float) Math.max(0, Math.min(max, want));
        if (m.count > 0 && !m.atEnd && Math.abs(y - want) > 0.5) {
            // The anchor asks for an offset out of range: anchor again where the offset may be, another round.
            if (max > 0.5 && y >= max - 0.5) {
                m.atEnd = true;
                m.anchorEnd(v);
            } else {
                m.anchorAt(y);
            }
            again = true;
        }
        m.wroteY = y;
        n.scrollY = y;
        n.contentH = (float) m.content() + n.padTop + n.padBottom + 2 * n.border;
        if (n.state != null) {
            n.state.scrollY = y; // so a second round sees no change it has not made
        }
        m.requestY = Float.NaN;
        m.requestRow = -1;
        if (m.rangeRead && (m.first != firstBefore || m.last != lastBefore)) {
            requestFrame.run(); // the view read rows that have changed: show it what they are now (as MyGO)
        }
        m.rangeRead = false;
        return again;
    }

    private static float outerTop(Node r) {
        return r.y - (r.marTop == jui.ui.Ui.AUTO ? 0 : r.marTop);
    }

    private static float outerBottom(Node r) {
        return r.y + r.h + (r.marBottom == jui.ui.Ui.AUTO ? 0 : r.marBottom);
    }

    // Scrolling.

    /** The element state of identity id, or null: for ScrollState. */
    public ElementState stateOf(long id) {
        return states.get(id);
    }

    /** Asks the scroll container of identity id to scroll to y in the next frame. */
    public void requestScroll(long id, float y) {
        ElementState s = states.get(id);
        if (s == null) {
            s = states.create(id, frame + 1);
        }
        s.requestY = y;
        s.wheeling = false; // the app's scroll stops a wheel's glide
        requestFrame.run();
    }

    /** The clock now, in nanoseconds: for input between frames. */
    long clockNow() {
        return clock.getAsLong();
    }

    /**
     * Scrolls the element Tab focused into view: each scroll container
     * around it, innermost first, moves as little as shows it within its
     * border, its top first when it is taller (as MyGO's reveal). Returns
     * REVEAL_LAYOUT when a scroll container moved (lay out again) and
     * REVEAL_ROUND when a list did (another round).
     */
    private static final int REVEAL_LAYOUT = 1, REVEAL_ROUND = 2;

    private int reveal() {
        long id = input.revealId;
        input.revealId = 0;
        if (id == 0) {
            return 0;
        }
        int at = Node.NONE;
        for (int i = 0; i < pool.size(); i++) {
            if (pool.node(i).id == id) {
                at = i;
                break;
            }
        }
        if (at == Node.NONE) {
            return 0;
        }
        Node n = pool.node(at);
        float top = n.y, bottom = n.y + n.h;
        int moved = 0;
        for (int a = n.parent; a != Node.NONE; a = pool.node(a).parent) {
            Node c = pool.node(a);
            if ((c.flags & Node.SCROLL) == 0) {
                continue;
            }
            float vt = c.y + c.border, vb = c.y + c.h - c.border, delta = 0;
            if (top < vt) {
                delta = top - vt;
            } else if (bottom > vb) {
                delta = Math.min(bottom - vb, top - vt);
            }
            float max = Math.max(0, c.contentH - c.h);
            float y = Math.max(0, Math.min(max, c.scrollY + delta));
            float applied = y - c.scrollY;
            if (applied != 0) {
                if (c.listModel != null) {
                    c.listModel.anchorY -= applied; // a list moves by its anchor, in another round
                    c.listModel.atEnd = false;
                    c.listModel.revealed = true;
                    moved |= REVEAL_ROUND;
                } else {
                    c.scrollRequest = y;
                    if (c.state != null) {
                        c.state.requestY = y; // kept if another round builds the container again
                    }
                    moved |= REVEAL_LAYOUT;
                }
                if (c.state != null) {
                    c.state.wheeling = false;
                }
                top -= applied;
                bottom -= applied;
            }
        }
        return moved;
    }

    /** Makes the node a vertical scroll container, taking its offset (and any asked for) from its state. */
    public void makeScroll(int index) {
        Node n = pool.node(index);
        n.flags |= Node.SCROLL | Node.HOVER;
        n.clip = true;
        ElementState s = state(index);
        n.scrollY = s.scrollY;
        n.scrollRequest = s.requestY;
    }

    /**
     * A scroll at (x, y): dy above 0 moves the content up. The innermost
     * scroll container under the point that can still move that way takes
     * all of it; what is left over is dropped (as MyGO's input.go).
     */
    public void scroll(float x, float y, float dx, float dy, boolean precise, int mods) {
        input.scroll(x, y, dy, precise, mods);
    }

    // Pointer input: on the main thread, between frames.

    /** The pointer moved to (x, y), in DIPs from the window's top left. */
    public void pointerMove(float x, float y, int mods) {
        input.pointerMove(x, y, mods);
    }

    /** A button went down: 0 the primary, 1 the secondary; clickCount is the platform's. */
    public void pointerDown(float x, float y, int button, int clickCount, int mods) {
        input.pointerDown(x, y, button, clickCount, mods);
    }

    public void pointerUp(float x, float y, int button, int mods) {
        input.pointerUp(x, y, button, mods);
    }

    /** The pointer left the window. */
    public void pointerLeave() {
        input.pointerLeave();
    }

    // Queries, for Element: during a build.

    public boolean hovered(int index) {
        Node n = pool.node(index);
        n.flags |= Node.HOVER;
        ElementState s = state(index); // its box, committed every frame, decides while another element is held
        return !off(n) && input.hovered(n.id, s);
    }

    public boolean pressed(int index) {
        Node n = pool.node(index);
        n.flags |= Node.CLICKABLE | Node.HOVER;
        ElementState s = state(index);
        boolean p = !off(n) && input.pressed(s);
        if (p && s.pressPending) {
            consumed = true;
        }
        return p;
    }

    public int clicks(int index) {
        Node n = pool.node(index);
        n.flags |= Node.CLICKABLE;
        ElementState s = state(index);
        int k = off(n) ? 0 : s.clicks;
        if (k > 0) {
            consumed = true;
        }
        return k;
    }

    public boolean doubleClicked(int index) {
        Node n = pool.node(index);
        n.flags |= Node.CLICKABLE;
        ElementState s = state(index);
        boolean d = !off(n) && s.doubleClicks > 0;
        consumed |= d;
        return d;
    }

    public boolean rightClicked(int index) {
        Node n = pool.node(index);
        n.flags |= Node.CLICKABLE;
        ElementState s = state(index);
        boolean r = !off(n) && s.rightClicks > 0;
        consumed |= r;
        return r;
    }

    /**
     * Whether the node is disabled: by itself or an element around it, now
     * or in the last frame (as MyGO's disabled(): an element around it may
     * disable it after building it). Controls ask before taking input.
     */
    public boolean disabled(int index) {
        state(index);
        return off(pool.node(index));
    }

    private static boolean off(Node n) {
        return n.disabled || n.state != null && n.state.disabledLast;
    }

    public float dragX(int index) {
        pool.node(index).flags |= Node.DRAG;
        float d = state(index).dragX;
        consumed |= d != 0;
        return d;
    }

    public float dragY(int index) {
        pool.node(index).flags |= Node.DRAG;
        float d = state(index).dragY;
        consumed |= d != 0;
        return d;
    }

    // Focus.

    /** Whether identity id has focus, in a focused window. */
    public boolean focused(long id) {
        return id != 0 && input.focusedId == id && input.windowFocused;
    }

    public boolean focused(int index) {
        return focused(pool.node(index).id);
    }

    public boolean focusVisible(int index) {
        return focused(index) && input.focusVisible;
    }

    /** Whether the node or one inside it had focus in the last frame. */
    public boolean focusWithin(int index) {
        return input.windowFocused && input.inFocusChain(pool.node(index).id);
    }

    /** Asks for focus on the node if its element appears in this frame. */
    public void autoFocus(int index) {
        if (state(index).born == frame) {
            focusRequest = pool.node(index).id;
        }
    }

    /** Whether the element showing or labeled s, or one around it, has focus: for Tester. */
    public boolean focusIn(String s) {
        for (int i = find(s); i != Node.NONE; i = pool.node(i).parent) {
            if (focused(pool.node(i).id)) {
                return true;
            }
        }
        return false;
    }

    /** A key went down; Tab and the activation keys act at once. */
    public void keyDown(jui.ui.Key key, int mods, boolean repeat) {
        input.keyDown(key, mods, repeat);
    }

    /** Follows the system's appearance: the dark theme or the light one. Asks for a frame if it changed. */
    public void setDark(boolean dark) {
        Theme next = Theme.of(dark);
        if (next != theme) {
            theme = next;
            requestFrame.run();
        }
    }

    /** The modifier keys held changed; asks for no frame. */
    public void modifiersChanged(int mods) {
        input.mods = mods;
    }

    /** The modifier keys held, as {@link jui.ui.Mod} bits. */
    public int modifiers() {
        return input.mods;
    }

    /** Whether the node's shortcut s was pressed: focus must be within it. Registers it for routing. */
    public boolean shortcut(int index, jui.ui.Shortcut s) {
        if (disabled(index)) {
            return false; // a disabled element neither registers nor takes keys: they fall through (as MyGO)
        }
        Node n = pool.node(index);
        boolean hit = input.shortcut(n.id, s, pass == 0);
        consumed |= hit;
        return hit;
    }

    /** Whether the window's shortcut s was pressed. Registers it for routing. */
    public boolean windowShortcut(jui.ui.Shortcut s) {
        if (!building) {
            Dev.fail("UI functions run only while a view builds");
            return false;
        }
        boolean hit = input.shortcut(0, s, pass == 0);
        consumed |= hit;
        return hit;
    }

    // Text input, for the platform: on the main thread, between frames. Without a text input that
    // takes text (focused, enabled, in a focused window) they do nothing.

    public void setHost(Host host) {
        this.host = host;
    }

    public Host host() {
        return host;
    }

    /** Whether a text input takes text now: the platform sends keys through its input method then. */
    public boolean textActive() {
        return input.activeEdit() != null;
    }

    /** Whether the text input is composing (has marked text). */
    public boolean textComposing() {
        TextEdit e = input.activeEdit();
        return e != null && e.composing();
    }

    /** Text typed or committed by the input method, over [replStart, replStart + replLength) of the shown text, or none (replStart below 0). */
    public void textInsert(String s, int replStart, int replLength) {
        input.textInsert(s, replStart, replLength);
    }

    /** The input method's marked text, with its selected range; replStart below 0 for no replacement range. */
    public void textMark(String s, int selStart, int selLength, int replStart, int replLength) {
        input.textMark(s, selStart, selLength, replStart, replLength);
    }

    /** The input method commits its marked text as it stands. */
    public void textUnmark() {
        input.textUnmark();
    }

    /** The marked text in the shown text, as {start, length}; false when there is none. */
    public boolean textMarkedRange(int[] out) {
        TextEdit e = input.activeEdit();
        return e != null && e.markedRange(out);
    }

    /** The selection in the shown text, as {start, length}; false without a text input. */
    public boolean textSelectedRange(int[] out) {
        TextEdit e = input.activeEdit();
        if (e == null) {
            return false;
        }
        e.selectedRange(out);
        return true;
    }

    /** The shown text's length, or -1. */
    public int textLength() {
        TextEdit e = input.activeEdit();
        return e == null ? -1 : e.length();
    }

    /** A part of the shown text, clamped, or null. */
    public String textIn(int start, int end) {
        TextEdit e = input.activeEdit();
        return e == null ? null : e.substring(start, end);
    }

    /**
     * The rect of [start, end) of the shown text, in window DIPs, for the
     * input method's candidate window (as MyGO's BoundsForRange): the caret,
     * 1 wide, for an empty range, else the range's first grapheme cluster,
     * whose end goes to out[4] when out has room. False without a text
     * input, or when the rect is outside the input's padding box.
     */
    public boolean textRect(int start, int end, float[] out) {
        TextEdit e = input.activeEdit();
        if (e == null || e.layout == null) {
            return false;
        }
        jui.text.TextLayout l = e.layout;
        int n = l.length();
        int a = Math.max(0, Math.min(start, n)), z = Math.max(a, Math.min(end, n));
        float x = e.originX + l.caretX(a), w = 1;
        int actual = a;
        if (z > a) {
            actual = Math.min(l.stopAfter(a), z);
            w = Math.max(1, l.caretX(actual) - l.caretX(a));
        }
        float y = e.lineTop, h = e.lineH;
        if (x + w < e.clipX0 || x > e.clipX1 || y + h < e.clipY0 || y > e.clipY1) {
            return false;
        }
        out[0] = x;
        out[1] = y;
        out[2] = w;
        out[3] = h;
        if (out.length >= 5) {
            out[4] = actual;
        }
        return true;
    }

    /** The caret's rect in window DIPs, as textRect gives it; false without a text input. */
    public boolean textCaretRect(float[] out) {
        TextEdit e = input.activeEdit();
        return e != null && textRect(e.displayCaret(), e.displayCaret(), out);
    }

    /** The index of the shown text nearest the point in window DIPs, or -1 without a text input. */
    public int textIndexAt(float x, float y) {
        TextEdit e = input.activeEdit();
        if (e == null || e.layout == null) {
            return -1;
        }
        return e.layout.indexAt(x - e.originX);
    }

    /** The horizontal scroll of the text input showing or labeled s, or NaN: for Tester. */
    public float textScroll(String s) {
        int i = find(s);
        if (i == Node.NONE || pool.node(i).state == null || pool.node(i).state.edit == null) {
            return Float.NaN;
        }
        return pool.node(i).state.edit.scrollX;
    }

    /** A command of the Edit menu: for the text input, else as its key, for shortcuts. */
    public void editCommand(jui.ui.EditCommand c) {
        input.editCommand(c);
    }

    /** The window became, or stopped being, the key window. */
    public void windowFocusChanged(boolean focused) {
        input.windowFocusChanged(focused);
    }

    void requestFrame() {
        requestFrame.run();
    }

    /** Element states kept now: for tests. */
    public int stateCount() {
        return states.size();
    }

    /** Paints the last layout into scene, which the caller began at its scale. */
    public void paint(jui.render.Scene scene) {
        if (pool.size() > 0) {
            long ring = input.focusVisible && input.windowFocused ? input.focusedId : 0;
            Paint.paint(pool, 0, scene, theme, 1, false, ring);
        }
    }

    /**
     * Resolves each node's text style from its own and its ancestors': nodes
     * come after their parents in the pool, so one pass in order does it,
     * whether the view set a style before or after building the children.
     */
    private void inheritTextStyle() {
        Node root = pool.node(0);
        root.resolvedSize = Float.isNaN(root.fontSize) ? theme.fontSize() : root.fontSize;
        root.resolvedWeight = root.fontWeight != 0 ? root.fontWeight : 400;
        root.resolvedColor = root.hasTextColor ? root.textColor : theme.text().rgba();
        for (int i = 1; i < pool.size(); i++) {
            Node n = pool.node(i);
            Node p = pool.node(n.parent);
            n.resolvedSize = Float.isNaN(n.fontSize) ? p.resolvedSize : n.fontSize;
            n.resolvedWeight = n.fontWeight != 0 ? n.fontWeight : p.resolvedWeight;
            n.resolvedColor = n.hasTextColor ? n.textColor : p.resolvedColor;
        }
    }

    /** A text input's content width when nothing sets it (as MyGO's intrinsic width of an input). */
    private static final float INPUT_WIDTH = 200;

    /** Measures a node's own content, its text, and keeps the text's layout for paint. */
    private void measure(int index, float maxWidth, float[] out) {
        Node n = pool.node(index);
        if (n.kind == Node.INPUT) {
            if (text == null) {
                throw new IllegalStateException("text inputs need an engine made with a TextEngine");
            }
            // One line, unwrapped; as high as a line of Latin and Han, so it keeps its height as Chinese is typed.
            n.layout = text.layout(n.text == null ? "" : n.text, n.resolvedSize, n.resolvedWeight, 0);
            out[0] = INPUT_WIDTH;
            out[1] = Math.max(text.lineHeight(n.resolvedSize, n.resolvedWeight), n.layout.height);
            return;
        }
        if (n.kind != Node.TEXT || n.text == null) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        if (text == null) {
            throw new IllegalStateException("text needs an engine made with a TextEngine");
        }
        // TextEngine takes 0 for no wrapping; a box squeezed to nothing still
        // wraps, at every chance, as CSS does, rather than run on one line.
        float limit = maxWidth == FlexLayout.UNLIMITED ? 0 : Math.max(maxWidth, 0.01f);
        n.layout = text.layout(n.text, n.resolvedSize, n.resolvedWeight, limit);
        out[0] = n.layout.width;
        out[1] = n.layout.height;
    }

    // Building, for the ui package.

    /** Adds a node of {@code kind} under the current parent, or returns NONE outside a build. */
    public int open(byte kind) {
        if (!building) {
            Dev.fail("UI functions run only while a view builds");
            return Node.NONE;
        }
        Node p = pool.node(parent);
        int index = pool.open();
        Node n = pool.node(index);
        n.kind = kind;
        n.parent = parent;
        n.disabled = p.disabled;
        n.id = keyPending ? Identity.keyed(p.id, pendingKey)
                : rowPending ? Identity.child(p.id, rowIndex) : Identity.child(p.id, p.childCount);
        keyPending = false;
        rowPending = false;
        ElementState s = states.get(n.id);
        if (s != null) {
            n.state = s;
            s.seenFrame = frame;
            s.seenPass = pass;
        }
        if (Dev.ENABLED) {
            Integer other = nodesById.put(n.id, index);
            if (other != null && pool.node(other).parent == parent) {
                Dev.fail("two siblings have the same key");
            }
        }
        if (p.lastChild == Node.NONE) {
            p.firstChild = index;
        } else {
            pool.node(p.lastChild).nextSibling = index;
        }
        p.lastChild = index;
        p.childCount++;
        return index;
    }

    /** Runs content with the node at index as the parent of what it builds. */
    public void children(int index, Runnable content) {
        int saved = parent;
        parent = index;
        try {
            content.run();
            checkKeyUsed();
        } finally {
            parent = saved;
        }
    }

    /** Keys the next node built. */
    public void key(long hash) {
        if (!building) {
            Dev.fail("UI functions run only while a view builds");
            return;
        }
        if (keyPending) {
            Dev.fail("a key was set twice before an element took it");
        }
        keyPending = true;
        pendingKey = hash;
    }

    /** Whether an Element made for this node's use is still valid. */
    public boolean valid(int index, long gen) {
        return building && index >= 0 && index < pool.size() && pool.node(index).gen == gen;
    }

    public Node node(int index) {
        return pool.node(index);
    }

    /** Asks the window for a frame. */
    public void invalidate() {
        requestFrame.run();
    }

    private void checkKeyUsed() {
        if (keyPending) {
            keyPending = false;
            Dev.fail("a key was set but no element was built after it");
        }
    }

    // Queries on the last frame, for tests and Tester.

    /** The identity of the first node whose label or text is s, or 0. */
    public long identity(String s) {
        int i = find(s);
        return i == Node.NONE ? 0 : pool.node(i).id;
    }

    /** The box of the first node whose label or text is s, or null. */
    public Rect bounds(String s) {
        int i = find(s);
        if (i == Node.NONE) {
            return null;
        }
        Node n = pool.node(i);
        return new Rect(n.x, n.y, n.w, n.h);
    }

    /** The texts of the last frame's text elements, in build order. */
    public java.util.List<String> texts() {
        java.util.List<String> texts = new java.util.ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            Node n = pool.node(i);
            if (n.kind == Node.TEXT && n.text != null) {
                texts.add(n.text);
            }
        }
        return texts;
    }

    public int nodeCount() {
        return pool.size();
    }

    public int poolChunks() {
        return pool.chunks();
    }

    private int find(String s) {
        for (int i = 0; i < pool.size(); i++) {
            Node n = pool.node(i);
            if (s.equals(n.label) || s.equals(n.text) || s.equals(n.placeholder)) {
                return i;
            }
        }
        return Node.NONE;
    }
}
