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
        input.routeKey();
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
        // iterated: changes first, then focus requests, then clicks (as MyGO).
        try {
            consumed |= changeActions.run(pool);
            applyFocusRequest();
            consumed |= clickActions.run(pool);
        } finally {
            changeActions.clear();
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
            }
        }
        states.prune(frame, pass);
        input.commitFocus();
        input.commitShortcuts();
        input.rehover();
        // The looks that hover and press pick at paint, as the pointer stands now.
        for (int i = 0; i < pool.size(); i++) {
            Node n = pool.node(i);
            if (n.bgHover != 0 || n.bgPressed != 0) {
                n.hot = !n.disabled && input.hoveredNode(n);
                n.down = !n.disabled && input.downNode(n);
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
        hits.add(n.id, n.x, n.y, n.w, n.h, parentHit, n.disabled ? n.flags | HitList.DISABLED : n.flags,
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
        requestFrame.run();
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
        input.scroll(x, y, dy, mods);
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

    /** Measures a node's own content, its text, and keeps the text's layout for paint. */
    private void measure(int index, float maxWidth, float[] out) {
        Node n = pool.node(index);
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
        n.id = keyPending ? Identity.keyed(p.id, pendingKey) : Identity.child(p.id, p.childCount);
        keyPending = false;
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
            if (s.equals(n.label) || s.equals(n.text)) {
                return i;
            }
        }
        return Node.NONE;
    }
}
