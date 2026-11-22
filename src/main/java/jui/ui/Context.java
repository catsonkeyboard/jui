package jui.ui;

import jui.ui.engine.Dev;
import jui.ui.engine.Engine;
import jui.ui.engine.Identity;

/**
 * A window's building context, one for the window's life. Every control
 * function takes it; while a container builds its children, it is their
 * parent.
 */
public final class Context {
    /**
     * One Element per node, kept on the node and reused frame after frame, outside
     * development mode: in a native image escape analysis does not remove
     * handles made per element (24 bytes each, measured by AllocSpike).
     * Development mode makes a handle per element, so that one kept and used
     * during a later build still fails rather than reach the element now at
     * its index.
     */
    static final boolean POOLED_HANDLES = !Dev.ENABLED;

    private final Engine engine;
    private final Element dead = new Element(this, jui.ui.engine.Node.NONE, -1);

    /** Made by the window's engine. */
    public Context(Engine engine) {
        this.engine = engine;
    }

    public Theme theme() {
        return engine.theme();
    }

    /** Installs an application's palette pair while retaining system appearance changes. */
    public void setThemePair(Theme light, Theme dark) {
        if (serviceThread()) engine.setThemePair(light, dark);
    }

    /** Moves this window to the dark or light theme of its pair (as the theme switcher needs). */
    public void setDark(boolean dark) {
        if (serviceThread()) engine.setDark(dark);
    }

    public jui.text.TextLayout layoutText(String text, float size, int weight, float maxWidth) {
        return serviceThread() ? engine.text().layout(text, size, weight, maxWidth) : null;
    }

    public jui.text.TextLayout layoutText(Spans spans, float size, int weight, float maxWidth) {
        return serviceThread() ? engine.text().layout(spans, size, weight, maxWidth) : null;
    }

    public String clipboardText() { return serviceThread() ? engine.host().readClipboard() : null; }

    public void copyText(String text) { if (serviceThread()) engine.host().writeClipboard(text); }

    private boolean serviceThread() {
        if (jui.ffi.Libc.pthreadMainNp() != 0) return true;
        Dev.fail("Context services require the UI thread");
        return false;
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
        if (key == null) {
            return nullKey();
        }
        return key(Identity.hash(key));
    }

    /** Keys by an object's value: equal strings, numbers and other objects key alike. */
    public Context key(Object key) {
        if (key == null) {
            return nullKey();
        }
        if (key instanceof String s) {
            return key(s);
        }
        if (key instanceof Long l) {
            return key((long) l);
        }
        return key((long) key.hashCode());
    }

    /** A null key is a mistake in dev mode; otherwise the element keeps its position's identity. */
    private Context nullKey() {
        Dev.fail("an element was keyed by null");
        return this;
    }

    /** Whether the window's shortcut was pressed; an element with focus that takes it comes first. */
    public boolean shortcut(Shortcut s) {
        return engine.windowShortcut(s);
    }

    /** The modifier keys held, as {@link Mod} bits. */
    public int modifiers() {
        return engine.modifiers();
    }

    /** The time this frame started, in nanoseconds (as MyGO's c.Now()): compare it with times you keep. */
    public long now() {
        return engine.now();
    }

    /** Where the pointer is, in window DIPs, for controls that follow it. */
    public float pointerX() {
        return engine.pointerX();
    }

    /** Where the pointer is, in window DIPs. */
    public float pointerY() {
        return engine.pointerY();
    }

    /** The window's width, in DIPs. */
    public float windowWidth() {
        return engine.windowWidth();
    }

    /** The window's height, in DIPs. */
    public float windowHeight() {
        return engine.windowHeight();
    }

    /** Asks for a frame d from now; a frame that does not ask again lets the request go (as MyGO's After). */
    public void after(java.time.Duration d) {
        engine.after(engine.now() + d.toNanos());
    }

    /** Asks for another frame. */
    public void invalidate() {
        engine.invalidate();
    }

    /**
     * Posts work onto the UI thread, which runs it at the start of the next
     * frame: how a background task's result lands back in the view. Safe
     * from any thread, as MyGO's After from off the main thread would be.
     */
    public void runLater(Runnable action) {
        engine.runLater(action);
    }

    /**
     * The shared background pool: two daemon threads for file and network
     * work off the UI thread. Return from the task through
     * {@link #runLater}; the view never touches the pool's threads.
     */
    public java.util.concurrent.Executor worker() {
        return Worker.POOL;
    }

    private static final class Worker {
        static final java.util.concurrent.Executor POOL =
                java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "jui-worker");
                    t.setDaemon(true);
                    return t;
                });
    }

    Engine engine() {
        return engine;
    }

    Element element(int index) {
        if (index == jui.ui.engine.Node.NONE) {
            return dead;
        }
        jui.ui.engine.Node n = engine.node(index);
        if (!POOLED_HANDLES) {
            return new Element(this, index, n.gen);
        }
        Element e = (Element) n.handle;
        if (e == null) {
            e = new Element(this, index, n.gen);
            n.handle = e;
        } else {
            e.gen = n.gen;
        }
        return e;
    }
}
