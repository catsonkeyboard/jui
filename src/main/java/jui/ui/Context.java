package jui.ui;

import jui.ui.engine.Engine;
import jui.ui.engine.Identity;

/**
 * A window's building context, one for the window's life. Every control
 * function takes it; while a container builds its children, it is their
 * parent.
 */
public final class Context {
    private final Engine engine;

    /** Made by the window's engine. */
    public Context(Engine engine) {
        this.engine = engine;
    }

    public Theme theme() {
        return engine.theme();
    }

    /** Keys the next element built, so that its state follows the key rather than its position. */
    public Context key(long key) {
        engine.key(key);
        return this;
    }

    public Context key(int key) {
        return key((long) key);
    }

    public Context key(String key) {
        return key(Identity.hash(key));
    }

    /** Keys by an object's value: equal strings, numbers and other objects key alike. */
    public Context key(Object key) {
        if (key instanceof String s) {
            return key(s);
        }
        if (key instanceof Long l) {
            return key((long) l);
        }
        return key((long) key.hashCode());
    }

    /** Asks for another frame. */
    public void invalidate() {
        engine.requestFrame();
    }

    Engine engine() {
        return engine;
    }

    Element element(int index) {
        if (index == jui.ui.engine.Node.NONE) {
            return new Element(this, index, -1);
        }
        return new Element(this, index, engine.node(index).gen);
    }
}
