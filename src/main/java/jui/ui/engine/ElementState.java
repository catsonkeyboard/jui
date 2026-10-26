package jui.ui.engine;

import java.util.HashMap;

import jui.ui.Key;

/**
 * What a window keeps of an element from frame to frame, found by its
 * identity (design §7.2): its last box, the input it has not yet reported,
 * and its local state. States of elements that a frame no longer builds are
 * reset and reused.
 */
public final class ElementState {
    public long id;
    /** The frame that made this state. */
    public long born;
    /** The last frame and pass that built the element. */
    long seenFrame;
    int seenPass;

    // The last frame's box and content box, in DIPs from the window's top left.
    public float x, y, w, h;
    public float cx, cy, cw, ch;

    // Input not yet reported; a pass that builds the element forgets it.
    public int clicks, rightClicks, doubleClicks;
    public boolean pressed, pressPending, changed;
    public float dragX, dragY;
    /** A scroll container's offset, its content's height and its content box's height as committed; an offset asked for (NaN none). */
    public float scrollY, contentH, viewH;
    public float requestY = Float.NaN;
    /** Whether the element was disabled in the last frame: an element around it may disable it after building it. */
    public boolean disabledLast;
    /** Keys sent to this element while it had focus, for controls that take keys (sliders). */
    public Key[] keys = new Key[4];
    public int keyCount;

    /** Local state by name, made on first use. */
    HashMap<String, Object> locals;
    /** Animated values by key, made on first use. */
    HashMap<String, Motion> motions;

    /** Back to a new state's defaults; the local state map is kept, empty, for reuse. */
    void reset() {
        id = 0;
        born = 0;
        seenFrame = -1;
        seenPass = -1;
        x = y = w = h = 0;
        cx = cy = cw = ch = 0;
        clicks = rightClicks = doubleClicks = 0;
        pressed = pressPending = changed = false;
        dragX = dragY = 0;
        disabledLast = false;
        scrollY = contentH = viewH = 0;
        requestY = Float.NaN;
        keyCount = 0;
        if (locals != null) {
            locals.clear();
        }
        if (motions != null) {
            motions.clear();
        }
    }
}
