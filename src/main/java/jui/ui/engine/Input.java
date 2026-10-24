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

    Input(Engine engine, HitList hits) {
        this.engine = engine;
        this.hits = hits;
    }

    // Events.

    void pointerMove(float x, float y, int mods) {
        float dx = x - pointerX, dy = y - pointerY;
        place(x, y, mods);
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
            if (id != lastPressId) {
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
