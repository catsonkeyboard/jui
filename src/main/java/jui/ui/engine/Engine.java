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
    private final Theme theme;
    private final Runnable requestFrame;
    private final NodePool pool = new NodePool();
    private final Context context;
    private final FlexLayout layout = new FlexLayout(pool, this::measure);

    // The build in progress.
    private boolean building;
    private int parent = Node.NONE;
    private boolean keyPending;
    private long pendingKey;

    /** {@code text} may be null for views without text, as in layout tests. */
    public Engine(TextEngine text, Theme theme, Runnable requestFrame) {
        this.text = text;
        this.theme = theme;
        this.requestFrame = requestFrame;
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

    /** Builds the view for a window of width by height DIPs, then lays it out. */
    public void buildAndLayout(View view, float width, float height) {
        if (Libc.pthreadMainNp() == 0) {
            Dev.fail("views build only on the main thread");
        }
        pool.reset();
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
        inheritTextStyle();
        layout.layout(root, width, height);
    }

    /** Paints the last layout into scene, which the caller began at its scale. */
    public void paint(jui.render.Scene scene) {
        if (pool.size() > 0) {
            Paint.paint(pool, 0, scene, theme);
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
        root.resolvedColor = root.textColor != 0 ? root.textColor : theme.text().rgba();
        for (int i = 1; i < pool.size(); i++) {
            Node n = pool.node(i);
            Node p = pool.node(n.parent);
            n.resolvedSize = Float.isNaN(n.fontSize) ? p.resolvedSize : n.fontSize;
            n.resolvedWeight = n.fontWeight != 0 ? n.fontWeight : p.resolvedWeight;
            n.resolvedColor = n.textColor != 0 ? n.textColor : p.resolvedColor;
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
        n.layout = text.layout(n.text, n.resolvedSize, n.resolvedWeight, maxWidth);
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
        n.id = keyPending ? Identity.keyed(p.id, pendingKey) : Identity.child(p.id, p.childCount);
        keyPending = false;
        if (Dev.ENABLED) {
            for (int c = p.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
                if (pool.node(c).id == n.id) {
                    Dev.fail("two siblings have the same key");
                }
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

    public void requestFrame() {
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
