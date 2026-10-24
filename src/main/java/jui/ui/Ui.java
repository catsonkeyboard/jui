package jui.ui;

import jui.ui.engine.Engine;
import jui.ui.engine.Node;

/**
 * The controls, as static functions taking the context: import them with
 * {@code import static jui.ui.Ui.*;}.
 */
public final class Ui {
    private Ui() {}

    /** An automatic margin, which takes the free space on its side. */
    public static final float AUTO = Float.POSITIVE_INFINITY;

    /** A box: a column. */
    public static Element box(Context c) {
        return open(c, Node.BOX, false, Align.STRETCH);
    }

    /** Lays its children out from top to bottom, stretched to its width. */
    public static Element column(Context c) {
        return open(c, Node.BOX, false, Align.STRETCH);
    }

    /** Lays its children out from left to right, centered across. */
    public static Element row(Context c) {
        return open(c, Node.BOX, true, Align.CENTER);
    }

    /** Takes the free space of its row or column. */
    public static Element spacer(Context c) {
        Element e = open(c, Node.SPACER, false, Align.STRETCH);
        Node n = e.node();
        if (n != null) {
            n.grow = 1;
        }
        return e;
    }

    /** Text in the system font, wrapped at the width it gets. */
    public static Element text(Context c, String s) {
        Element e = open(c, Node.TEXT, false, Align.STRETCH);
        Node n = e.node();
        if (n != null) {
            n.text = s;
        }
        return e;
    }

    /** A thin line across its row or column. */
    public static Element divider(Context c) {
        return open(c, Node.DIVIDER, false, Align.STRETCH);
    }

    static Element open(Context c, byte kind, boolean row, Align alignItems) {
        Engine engine = c.engine();
        int index = engine.open(kind);
        if (index != Node.NONE) {
            Node n = engine.node(index);
            n.row = row;
            n.alignItems = alignItems;
        }
        return c.element(index);
    }
}
