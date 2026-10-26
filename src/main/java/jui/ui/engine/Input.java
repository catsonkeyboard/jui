package jui.ui.engine;

import java.util.Arrays;

/**
 * A window's input between frames, as MyGO's ui/input.go: events change the
 * element states and the hover chain against the hit list the last frame
 * committed, then ask for a frame.
 */
final class Input {
    private static final int RECEIVES = Node.CLICKABLE | Node.FOCUSABLE | Node.DRAG;

    private final Engine engine;
    private final HitList hits;

    float pointerX, pointerY;
    boolean pointerInside;
    int mods;

    /** The hover chain, innermost first: identities and their flags when the chain was found. */
    long[] hoverIds = new long[16];
    int[] hoverFlags = new int[16];
    int hoverCount;

    /** The element a button is held on (0 for none), the button, and its flags at the press. */
    long pressedId;
    int pressButton;
    int pressFlags;
    /** For click counts restarted per element. */
    private long lastPressId;
    private int clickBase;

    private int[][] scratch = {new int[16]};

    /** A scroll bar's thumb being dragged: its container (0 for none), the offset, reach and pointer y it started from, and its travel. */
    long dragId;
    private float dragFrom, dragReach, dragStartY, dragTravel;

    // Focus: the focused identity (0 for none), whether its ring shows, and the window's key state.
    long focusedId;
    boolean focusVisible;
    boolean windowFocused = true;
    /** Committed by the last frame: focusable, enabled identities in order, and the focus chain. */
    long[] focusOrder = new long[16];
    int focusCount;
    long[] focusChain = new long[16];
    int focusChainCount;

    // Shortcuts: registered by this pass, and by the last frame (for routing).
    private long[] regIds = new long[16], lastRegIds = new long[16];
    private int[] regMods = new int[16], lastRegMods = new int[16];
    private jui.ui.Key[] regKeys = new jui.ui.Key[16], lastRegKeys = new jui.ui.Key[16];
    private int regCount, lastRegCount;

    // Keys waiting for a frame to route them, oldest first.
    private jui.ui.Key[] queueKeys = new jui.ui.Key[8];
    private int[] queueMods = new int[8];
    private int queueCount;

    /** The key this frame delivers in its first pass: to an identity (0 the window), or to whoever claims it. */
    private jui.ui.Key deliveredKey;
    private int deliveredMods;
    private long deliveredTo;
    private boolean unrouted;

    Input(Engine engine, HitList hits) {
        this.engine = engine;
        this.hits = hits;
    }

    // Events.

    void pointerMove(float x, float y, int mods) {
        float dx = x - pointerX, dy = y - pointerY;
        place(x, y, mods);
        if (dragId != 0) {
            // The thumb follows the pointer as a share of the travel (as MyGO's dragTo).
            ElementState s = engine.states().get(dragId);
            if (s != null) {
                float reach = Math.max(0, s.contentH - s.h);
                float f = (dragReach > 0 ? dragFrom / dragReach : 0) + (y - dragStartY) / Math.max(1, dragTravel);
                s.scrollY = Math.max(0, Math.min(1, f)) * reach;
                engine.requestFrame();
            }
            return;
        }
        if (pressedId != 0) {
            ElementState s = engine.states().get(pressedId);
            if (s != null) {
                s.dragX += dx;
                s.dragY += dy;
            }
            if ((pressFlags & (Node.HOVER | Node.DRAG)) != 0) {
                engine.requestFrame();
            }
        } else {
            rehover();
        }
    }

    void pointerDown(float x, float y, int button, int clickCount, int mods) {
        place(x, y, mods);
        if (pressedId == 0) {
            rehover();
        }
        if (button == 0 && barPress(x, y)) {
            return;
        }
        if (button == 0) {
            // Focus goes to the innermost enabled focusable element under the pointer, or nowhere.
            long focus = 0;
            for (int k = 0; k < hoverCount; k++) {
                if ((hoverFlags[k] & Node.FOCUSABLE) != 0 && (hoverFlags[k] & HitList.DISABLED) == 0) {
                    focus = hoverIds[k];
                    break;
                }
            }
            if (focus != focusedId) {
                focusedId = focus;
                focusVisible = false;
            }
        }
        int target = -1;
        for (int k = 0; k < hoverCount; k++) {
            if ((hoverFlags[k] & RECEIVES) != 0) {
                target = hits.indexOf(hoverIds[k]);
                break;
            }
        }
        if (target >= 0) {
            long id = hits.ids[target];
            ElementState s = stateAt(target);
            // Counts restart per element, and when the platform's count starts over (as MyGO's countPress).
            if (id != lastPressId || clickCount <= clickBase) {
                lastPressId = id;
                clickBase = clickCount - 1;
            }
            int count = clickCount - clickBase;
            pressedId = id;
            pressButton = button;
            pressFlags = hits.flags[target];
            if (button == 0) {
                s.pressed = true;
                s.pressPending = true;
                if (count == 2) {
                    s.doubleClicks++;
                }
            }
        }
        engine.requestFrame();
    }

    void pointerUp(float x, float y, int button, int mods) {
        place(x, y, mods);
        if (dragId != 0 && button == 0) {
            dragId = 0;
            rehover();
            engine.requestFrame();
            return;
        }
        if (pressedId != 0 && button == pressButton) {
            int i = hits.indexOf(pressedId);
            ElementState s = engine.states().get(pressedId);
            if (s != null) {
                if (i >= 0 && hits.contains(i, x, y) && (hits.flags[i] & HitList.DISABLED) == 0) {
                    if (button == 0) {
                        s.clicks++;
                    } else {
                        s.rightClicks++;
                    }
                }
                s.pressed = false;
            }
            pressedId = 0;
        }
        rehover();
        engine.requestFrame();
    }

    void pointerLeave() {
        pointerInside = false;
        if (pressedId == 0 && hoverCount > 0) {
            boolean hovering = involvesHover();
            hoverCount = 0;
            if (hovering) {
                engine.requestFrame();
            }
        }
    }

    /** A key went down. Tab and the activation keys act at once; the rest wait for shortcuts. */
    void keyDown(jui.ui.Key key, int mods, boolean repeat) {
        this.mods = mods;
        // Keys that act at once yield to a shortcut that claims them (as MyGO's claimedBy).
        if (key == jui.ui.Key.TAB && (mods == 0 || mods == jui.ui.Mod.SHIFT) && !claimedBy(mods, key, true)) {
            moveFocus(mods == 0);
            return;
        }
        int i = focusedId == 0 ? -1 : hits.indexOf(focusedId);
        boolean enabled = i >= 0 && (hits.flags[i] & HitList.DISABLED) == 0;
        // A focused control that takes keys is the innermost: only its own shortcut comes first.
        if (enabled && (hits.flags[i] & Node.KEYS) != 0 && mods == 0 && sliderKey(key) && !registered(focusedId, mods, key)) {
            ElementState s = stateAt(i);
            if (s.keyCount == s.keys.length) {
                s.keys = Arrays.copyOf(s.keys, s.keyCount * 2);
            }
            s.keys[s.keyCount++] = key;
            engine.requestFrame();
            return;
        }
        if ((key == jui.ui.Key.ENTER || key == jui.ui.Key.SPACE) && mods == 0 && enabled
                && (hits.flags[i] & Node.CLICKABLE) != 0) {
            // A button keeps Enter and Space from window shortcuts; a toggle yields Enter to one.
            boolean window = key == jui.ui.Key.ENTER && (hits.flags[i] & Node.TOGGLE) != 0;
            if (!claimedBy(mods, key, window)) {
                stateAt(i).clicks++;
                focusVisible = true;
                engine.requestFrame();
                return;
            }
        }
        // Keys that scroll do, unless a shortcut claims them (as MyGO's scrollKey).
        if (!claimedBy(mods, key, true) && scrollByKey(key, mods)) {
            return;
        }
        if (queueCount == queueKeys.length) {
            queueKeys = Arrays.copyOf(queueKeys, queueCount * 2);
            queueMods = Arrays.copyOf(queueMods, queueCount * 2);
        }
        queueKeys[queueCount] = key;
        queueMods[queueCount] = mods;
        queueCount++;
        engine.requestFrame();
    }

    private static boolean sliderKey(jui.ui.Key key) {
        return switch (key) {
            case LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN -> true;
            default -> false;
        };
    }

    /** Whether a shortcut of the focus chain (or, if window, of the window) claims the key. */
    private boolean claimedBy(int mods, jui.ui.Key key, boolean window) {
        for (int k = 0; k < focusChainCount; k++) {
            if (registered(focusChain[k], mods, key)) {
                return true;
            }
        }
        return window && registered(0, mods, key);
    }

    /** Whether a node, as committed, is hovered: for paint. */
    boolean hoveredNode(Node n) {
        if (pressedId != 0) {
            return n.id == pressedId || inChain(n.id) && pointerInside && insideNode(n);
        }
        return inChain(n.id);
    }

    /** Whether a node, as committed, is held with the pointer over it: for paint. */
    boolean downNode(Node n) {
        return pressedId == n.id && pressButton == 0 && pointerInside && insideNode(n);
    }

    private boolean insideNode(Node n) {
        return pointerX >= n.x && pointerX < n.x + n.w && pointerY >= n.y && pointerY < n.y + n.h;
    }

    /**
     * At a frame's start: takes the oldest waiting key and routes it by the
     * last frame's registrations, to the innermost element of the focus
     * chain that registered it, else the window; a key nobody registered may
     * be claimed in the first pass by a shortcut this frame registers. One
     * key a frame keeps repeated keys apart; the rest ask for more frames.
     */
    void routeKey() {
        deliveredKey = null;
        if (queueCount == 0) {
            return;
        }
        jui.ui.Key key = queueKeys[0];
        int mods = queueMods[0];
        System.arraycopy(queueKeys, 1, queueKeys, 0, queueCount - 1);
        System.arraycopy(queueMods, 1, queueMods, 0, queueCount - 1);
        queueKeys[--queueCount] = null;
        deliveredKey = key;
        deliveredMods = mods;
        unrouted = true;
        for (int k = 0; k < focusChainCount && unrouted; k++) {
            if (registered(focusChain[k], mods, key)) {
                deliveredTo = focusChain[k];
                unrouted = false;
            }
        }
        if (unrouted && registered(0, mods, key)) {
            deliveredTo = 0;
            unrouted = false;
        }
        if (queueCount > 0) {
            engine.requestFrame();
        }
    }

    private boolean registered(long id, int mods, jui.ui.Key key) {
        for (int k = 0; k < lastRegCount; k++) {
            if (lastRegIds[k] == id && lastRegMods[k] == mods && lastRegKeys[k] == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers a shortcut of identity id (0 the window) in this pass, and
     * reports whether this frame's key goes to it: in the first pass only,
     * once. An element takes an unrouted key only with focus within it.
     */
    boolean shortcut(long id, jui.ui.Shortcut s, boolean firstPass) {
        if (regCount == regIds.length) {
            regIds = Arrays.copyOf(regIds, regCount * 2);
            regMods = Arrays.copyOf(regMods, regCount * 2);
            regKeys = Arrays.copyOf(regKeys, regCount * 2);
        }
        regIds[regCount] = id;
        regMods[regCount] = s.mods();
        regKeys[regCount] = s.key();
        regCount++;
        if (!firstPass || deliveredKey == null || !s.matches(deliveredMods, deliveredKey)) {
            return false;
        }
        boolean mine = unrouted ? id == 0 || windowFocused && inFocusChain(id) : deliveredTo == id;
        if (mine) {
            deliveredKey = null;
        }
        return mine;
    }

    /** At a pass's start: its registrations start over. */
    void beginPass() {
        Arrays.fill(regKeys, 0, regCount, null);
        regCount = 0;
    }

    /** After the first pass: a key nobody took is dropped. */
    void dropKey() {
        deliveredKey = null;
    }

    /** At commit: the last pass's registrations route the next frame's keys. */
    void commitShortcuts() {
        long[] ids = lastRegIds;
        int[] ms = lastRegMods;
        jui.ui.Key[] ks = lastRegKeys;
        lastRegIds = regIds;
        lastRegMods = regMods;
        lastRegKeys = regKeys;
        lastRegCount = regCount;
        regIds = ids;
        regMods = ms;
        regKeys = ks;
        Arrays.fill(regKeys, null);
        regCount = 0;
    }

    /** Tab and Shift+Tab: the next or previous focusable element, around the end. */
    private void moveFocus(boolean forward) {
        if (focusCount == 0) {
            return;
        }
        int at = -1;
        for (int k = 0; k < focusCount; k++) {
            if (focusOrder[k] == focusedId) {
                at = k;
                break;
            }
        }
        int next = at < 0 ? (forward ? 0 : focusCount - 1) : (at + (forward ? 1 : focusCount - 1)) % focusCount;
        focusedId = focusOrder[next];
        focusVisible = true;
        revealId = focusedId;
        engine.requestFrame();
    }

    void windowFocusChanged(boolean focused) {
        windowFocused = focused;
        engine.requestFrame();
    }

    /** Commits the focus order and the focus chain from the hit list just committed. */
    void commitFocus() {
        focusCount = 0;
        focusChainCount = 0;
        int focused = -1;
        for (int i = 0; i < hits.size; i++) {
            int f = hits.flags[i];
            if ((f & Node.FOCUSABLE) != 0 && (f & HitList.DISABLED) == 0) {
                if (focusCount == focusOrder.length) {
                    focusOrder = Arrays.copyOf(focusOrder, focusCount * 2);
                }
                focusOrder[focusCount++] = hits.ids[i];
            }
            if (focusedId != 0 && hits.ids[i] == focusedId) {
                focused = i;
            }
        }
        for (int i = focused; i >= 0; i = hits.parent[i]) {
            if (focusChainCount == focusChain.length) {
                focusChain = Arrays.copyOf(focusChain, focusChainCount * 2);
            }
            focusChain[focusChainCount++] = hits.ids[i];
        }
    }

    boolean inFocusChain(long id) {
        for (int k = 0; k < focusChainCount; k++) {
            if (focusChain[k] == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * A press on a shown scroll bar, before anything else takes it (as
     * MyGO's scrollbarPress): on the thumb it starts a drag, elsewhere on
     * the track it pages by 0.9 of the view. Reports whether a bar took it.
     */
    private boolean barPress(float x, float y) {
        float width = engine.theme().scrollbarWidth();
        for (int k = 0; k < hoverCount; k++) {
            if ((hoverFlags[k] & Node.SCROLL) == 0) {
                continue;
            }
            ElementState s = engine.states().get(hoverIds[k]);
            if (s == null || !s.barsShown) {
                continue;
            }
            float right = s.x + s.w;
            if (x < right - width - 6 || x >= right || y < s.y || y >= s.y + s.h) {
                continue;
            }
            float length = ScrollBar.thumbLength(s.h, s.contentH), top = ScrollBar.thumbTop(s.y, s.h, s.contentH, s.scrollY);
            s.wheeling = false;
            if (y >= top && y < top + length) {
                dragId = s.id;
                dragFrom = s.scrollY;
                dragReach = Math.max(0, s.contentH - s.h);
                dragStartY = y;
                dragTravel = Math.max(0, s.h - 4 - length);
            } else {
                float max = Math.max(0, s.contentH - s.h);
                s.scrollY = Math.max(0, Math.min(max, s.scrollY + (y < top ? -0.9f : 0.9f) * s.h));
            }
            engine.requestFrame();
            return true;
        }
        return false;
    }

    boolean hovering(long id) {
        return inChain(id);
    }

    /** A wheel glides over 150 ms, eased out (as MyGO's wheel.go). */
    private static final long GLIDE = 150_000_000L;
    /** Scroll containers whose wheel glides are under way. */
    private long[] gliding = new long[8];
    private int glidingCount;
    /** The element Tab focused, to scroll into view after the next layout (0 for none). */
    long revealId;

    /**
     * A scroll event at (x, y): the innermost scroll container under the
     * point that can still move that way takes it all. A precise one moves
     * at once; a wheel's adds to the container's glide, counting what is
     * still on its way.
     */
    void scroll(float x, float y, float dy, boolean precise, int mods) {
        this.mods = mods;
        if (dy == 0) {
            return;
        }
        int n = hits.chain(x, y, scratch);
        int[] idx = scratch[0];
        for (int k = 0; k < n; k++) {
            int i = idx[k];
            if ((hits.flags[i] & Node.SCROLL) == 0) {
                continue;
            }
            ElementState s = engine.states().get(hits.ids[i]);
            if (s == null) {
                continue;
            }
            float max = Math.max(0, s.contentH - s.h);
            float at = precise || !s.wheeling ? s.scrollY : s.wheelTo;
            if (dy > 0 ? at >= max : at <= 0) {
                continue;
            }
            if (precise) {
                s.scrollY = Math.max(0, Math.min(max, s.scrollY + dy));
                s.wheeling = false;
            } else {
                s.wheelFrom = s.scrollY;
                s.wheelTo = Math.max(0, Math.min(max, at + dy));
                s.wheelStart = engine.clockNow();
                if (!s.wheeling) {
                    s.wheeling = true;
                    if (glidingCount == gliding.length) {
                        gliding = Arrays.copyOf(gliding, glidingCount * 2);
                    }
                    gliding[glidingCount++] = s.id;
                }
            }
            engine.requestFrame();
            return;
        }
    }

    /** At a frame's start: moves each glide along its curve; one that ends, or cannot move, stops. */
    void advanceGlides(long now) {
        int kept = 0;
        for (int g = 0; g < glidingCount; g++) {
            ElementState s = engine.states().get(gliding[g]);
            if (s == null || !s.wheeling) {
                continue;
            }
            double t = (double) (now - s.wheelStart) / GLIDE;
            float v;
            if (t >= 1) {
                v = s.wheelTo;
                s.wheeling = false;
            } else {
                double u = 1 - Math.max(0, t);
                v = (float) (s.wheelFrom + (s.wheelTo - s.wheelFrom) * (1 - u * u * u));
            }
            float max = Math.max(0, s.contentH - s.h);
            float next = Math.max(0, Math.min(max, v));
            if (next == s.scrollY && t > 0 && (next == 0 || next == max)) {
                s.wheeling = false; // it cannot move any further
            }
            s.scrollY = next;
            if (s.wheeling) {
                gliding[kept++] = s.id;
                engine.requestFrame();
            }
        }
        glidingCount = kept;
    }

    /** How far a key scrolls a container h high, or 0 for a key that does not scroll (as MyGO's scrollKey). */
    private static float scrollKey(jui.ui.Key key, int mods, float h) {
        float page = Math.max(h - 40, h / 2);
        if (mods == 0) {
            switch (key) {
                case DOWN: return 40;
                case UP: return -40;
                case PAGE_DOWN: case SPACE: return page;
                case PAGE_UP: return -page;
                case HOME: return -1e9f;
                case END: return 1e9f;
                default: return 0;
            }
        }
        if (mods == jui.ui.Mod.SHIFT && key == jui.ui.Key.SPACE) {
            return -page;
        }
        if (mods == jui.ui.Mod.CMD && (key == jui.ui.Key.UP || key == jui.ui.Key.DOWN)) {
            return key == jui.ui.Key.UP ? -1e9f : 1e9f;
        }
        return 0;
    }

    /**
     * Scrolls by the key the innermost container of the focus chain that can
     * move that way, else of the pointer's chain; reports whether one did.
     */
    private boolean scrollByKey(jui.ui.Key key, int mods) {
        if (scrollKey(key, mods, 100) == 0) {
            return false;
        }
        for (int k = 0; k < focusChainCount; k++) {
            int i = hits.indexOf(focusChain[k]);
            if (i >= 0 && (hits.flags[i] & Node.SCROLL) != 0 && scrollContainer(hits.ids[i], key, mods)) {
                return true;
            }
        }
        for (int k = 0; k < hoverCount; k++) {
            if ((hoverFlags[k] & Node.SCROLL) != 0 && scrollContainer(hoverIds[k], key, mods)) {
                return true;
            }
        }
        return false;
    }

    private boolean scrollContainer(long id, jui.ui.Key key, int mods) {
        ElementState s = engine.states().get(id);
        if (s == null) {
            return false;
        }
        float by = scrollKey(key, mods, s.h), max = Math.max(0, s.contentH - s.h);
        if (by > 0 ? s.scrollY >= max : s.scrollY <= 0) {
            return false;
        }
        s.scrollY = Math.max(0, Math.min(max, s.scrollY + by));
        s.wheeling = false;
        engine.requestFrame();
        return true;
    }

    // Queries, for the frame being built.

    /** Whether the element is hovered; s is its state, for its box while another element is pressed. */
    boolean hovered(long id, ElementState s) {
        if (pressedId != 0) {
            if (id == pressedId) {
                return true;
            }
            return inChain(id) && pointerInside && inside(s);
        }
        return inChain(id);
    }

    boolean pressed(ElementState s) {
        return s.pressed && pointerInside && inside(s);
    }

    /**
     * After a commit: the hover chain at the pointer by the new layout (as
     * MyGO's rehover), unless a press holds it. Asks for a frame when the
     * chain changes and an element in it shows hover.
     */
    void rehover() {
        if (pressedId != 0) {
            return;
        }
        if (!pointerInside) {
            return;
        }
        int n = hits.chain(pointerX, pointerY, scratch);
        int[] idx = scratch[0];
        boolean same = n == hoverCount;
        for (int k = 0; same && k < n; k++) {
            same = hits.ids[idx[k]] == hoverIds[k];
        }
        if (same) {
            for (int k = 0; k < n; k++) {
                hoverFlags[k] = hits.flags[idx[k]];
            }
            return;
        }
        boolean hovering = involvesHover();
        if (n > hoverIds.length) {
            hoverIds = Arrays.copyOf(hoverIds, n * 2);
            hoverFlags = Arrays.copyOf(hoverFlags, n * 2);
        }
        for (int k = 0; k < n; k++) {
            hoverIds[k] = hits.ids[idx[k]];
            hoverFlags[k] = hits.flags[idx[k]];
        }
        hoverCount = n;
        if (hovering || involvesHover()) {
            engine.requestFrame();
        }
    }

    private boolean involvesHover() {
        for (int k = 0; k < hoverCount; k++) {
            if ((hoverFlags[k] & Node.HOVER) != 0) {
                return true;
            }
        }
        return false;
    }

    private boolean inChain(long id) {
        for (int k = 0; k < hoverCount; k++) {
            if (hoverIds[k] == id) {
                return true;
            }
        }
        return false;
    }

    private boolean inside(ElementState s) {
        return pointerX >= s.x && pointerX < s.x + s.w && pointerY >= s.y && pointerY < s.y + s.h;
    }

    private void place(float x, float y, int mods) {
        pointerX = x;
        pointerY = y;
        pointerInside = true;
        this.mods = mods;
    }

    /** The state of hit list entry i, made if it has none, with the entry's box. */
    private ElementState stateAt(int i) {
        long id = hits.ids[i];
        ElementState s = engine.states().get(id);
        if (s == null) {
            s = engine.states().create(id, engine.frame() + 1);
        }
        s.x = hits.x[i];
        s.y = hits.y[i];
        s.w = hits.w[i];
        s.h = hits.h[i];
        return s;
    }
}
