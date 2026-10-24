package jui.ui;

import jui.ui.engine.Engine;
import jui.ui.engine.Identity;

/**
 * A window's building context, one for the window's life. Every control
 * function takes it; while a container builds its children, it is their
 * parent.
 */
public final class Context {
    /**
     * One Element per node index, reused frame after frame, outside
     * development mode: in a native image escape analysis does not remove
     * handles made per element (24 bytes each, measured by AllocSpike).
     * Development mode makes a handle per element, so that one kept and used
     * during a later build still fails rather than reach the element now at
     * its index.
     */
    static final boolean POOLED_HANDLES = !jui.ui.engine.Dev.ENABLED;

    private final Engine engine;
    private Element[] handles = new Element[64];
    private final Element dead = new Element(this, jui.ui.engine.Node.NONE, -1);

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
            return dead;
        }
        long gen = engine.node(index).gen;
        if (!POOLED_HANDLES) {
            return new Element(this, index, gen);
        }
        if (index >= handles.length) {
            handles = java.util.Arrays.copyOf(handles, Math.max(handles.length * 2, index + 1));
        }
        Element e = handles[index];
        if (e == null) {
            e = new Element(this, index, gen);
            handles[index] = e;
        } else {
            e.gen = gen;
        }
        return e;
    }
}
