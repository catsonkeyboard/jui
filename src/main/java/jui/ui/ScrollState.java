package jui.ui;

import jui.ui.engine.ElementState;
import jui.ui.engine.Engine;

/**
 * A scroll container's offset, kept in the app's state: pass it to
 * scroll(c, state) on every build, then read where it is or ask it to move.
 * Requests made before it is first bound wait until it is.
 */
public final class ScrollState {
    private Engine engine;
    private long id;
    private float pending = Float.NaN, pendingX = Float.NaN;
    /** The offsets as last seen bound: a container back after a frame away takes them again. */
    private float remembered = Float.NaN, rememberedX = Float.NaN;

    public ScrollState() {}

    /** The offset the last frame showed, from 0 at the top. */
    public float y() {
        ElementState s = engine == null ? null : engine.stateOf(id);
        return s == null ? 0 : s.scrollY;
    }

    /** The largest offset the last frame allowed: its content's height less its own. */
    public float maxY() {
        ElementState s = engine == null ? null : engine.stateOf(id);
        return s == null ? 0 : Math.max(0, s.contentH - s.h);
    }

    /** The offset across the last frame showed, from 0 at the left; always 0 for a container that scrolls only down. */
    public float x() {
        ElementState s = engine == null ? null : engine.stateOf(id);
        return s == null ? 0 : s.scrollX;
    }

    /** The largest offset across the last frame allowed: its content's width less its own. */
    public float maxX() {
        ElementState s = engine == null ? null : engine.stateOf(id);
        return s == null ? 0 : Math.max(0, s.contentW - s.w);
    }

    /** Scrolls to y in the next frame, held within [0, maxY()]. */
    public void scrollTo(float y) {
        scrollTo(Float.NaN, y);
    }

    /** Scrolls to (x, y) in the next frame, each held within its range; NaN leaves that way be. */
    public void scrollTo(float x, float y) {
        if (engine == null) {
            if (!Float.isNaN(x)) {
                pendingX = x;
            }
            if (!Float.isNaN(y)) {
                pending = y;
            }
        } else {
            engine.requestScroll(id, x, y);
        }
    }

    public void scrollToTop() {
        scrollTo(0);
    }

    public void scrollToEnd() {
        scrollTo(Float.MAX_VALUE);
    }

    void bind(Engine engine, long id) {
        this.engine = engine;
        this.id = id;
        ElementState s = engine.stateOf(id);
        if (!Float.isNaN(pending) || !Float.isNaN(pendingX)) {
            engine.requestScroll(id, pendingX, pending);
            pending = pendingX = Float.NaN;
        } else if (s != null && s.born == engine.frame() && !Float.isNaN(remembered)) {
            engine.requestScroll(id, rememberedX, remembered); // its state is new: the container was away
        }
        if (s != null && s.born < engine.frame()) {
            remembered = s.scrollY;
            rememberedX = s.scrollX;
        }
    }
}
