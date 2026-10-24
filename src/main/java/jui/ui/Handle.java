package jui.ui;

import jui.ui.engine.Engine;

/**
 * A lasting name for a focusable element, kept in the app's state: bind it
 * on every build, and focus it from any action (design §6.7).
 *
 * <pre>{@code
 * final Handle search = new Handle();
 * box(c.key("search")).focusable().bind(search);
 * search.focus();
 * }</pre>
 */
public final class Handle {
    Engine engine;
    long id;
    /** A focus asked for before the handle was ever bound. */
    private boolean pending;

    public Handle() {}

    /**
     * Asks for focus on the bound element. It takes effect after a pass that
     * builds the element enabled, and waits until one does.
     */
    public void focus() {
        if (engine == null) {
            pending = true;
        } else {
            engine.requestFocus(id);
            engine.invalidate();
        }
    }

    /** Whether the bound element has focus. */
    public boolean focused() {
        return engine != null && engine.focused(id);
    }

    void bind(Engine engine, long id) {
        this.engine = engine;
        this.id = id;
        if (pending) {
            pending = false;
            engine.requestFocus(id);
        }
    }
}
