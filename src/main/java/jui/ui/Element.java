package jui.ui;

import jui.ui.engine.Dev;
import jui.ui.engine.Node;

/**
 * What a control function returns: the element it built, to style, lay out
 * and query. Valid only during the build pass that made it; used later, it
 * fails in development mode and does nothing otherwise.
 */
public final class Element {
    final Context c;
    final int index;
    final long gen;

    Element(Context c, int index, long gen) {
        this.c = c;
        this.index = index;
        this.gen = gen;
    }

    /** Builds the element's children: what content builds goes inside it. */
    public Element children(Runnable content) {
        if (node() != null) {
            c.engine().children(index, content);
        }
        return this;
    }

    /** Names the element, for tests (and, later, assistive technology). */
    public Element label(String label) {
        Node n = node();
        if (n != null) {
            n.label = label;
        }
        return this;
    }

    /** The node, or null when this element is stale. */
    Node node() {
        if (c.engine().valid(index, gen)) {
            return c.engine().node(index);
        }
        Dev.fail("an Element is valid only during the build pass that made it");
        return null;
    }
}
