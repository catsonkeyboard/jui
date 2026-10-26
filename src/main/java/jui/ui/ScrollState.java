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
    private float pending = Float.NaN;

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

    /** Scrolls to y in the next frame, held within [0, maxY()]. */
    public void scrollTo(float y) {
        if (engine == null) {
            pending = y;
        } else {
            engine.requestScroll(id, y);
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
        if (!Float.isNaN(pending)) {
            engine.requestScroll(id, pending);
            pending = Float.NaN;
        }
    }
}
