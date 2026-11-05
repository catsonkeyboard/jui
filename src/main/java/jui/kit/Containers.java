package jui.kit;

import jui.ui.Context;
import jui.ui.Element;

/**
 * The kit's thin wrappers over the framework's own containers, so the whole
 * gpui-kit surface has a home here: Popover, Resizable, Scrollable, Theme
 * and Root map one-to-one onto pieces that already exist.
 */
public final class Containers {
    private Containers() {}

    /** gpui-kit Popover: the framework's popover, anchored and caller-owned. */
    public static Element popover(jui.ui.Context c, jui.ui.Element anchor, jui.ui.BoolRef open,
                                  Runnable content) {
        return jui.ui.Ui.popover(c, anchor, open, content);
    }

    /** gpui-kit Resizable: the framework's split, first pane width in a ref. */
    public static Element resizable(jui.ui.Context c, jui.ui.FloatRef firstWidth,
                                    Runnable first, Runnable second) {
        return jui.ui.Ui.split(c, firstWidth, first, second);
    }

    /** gpui-kit Resizable (vertical): first pane height in a ref. */
    public static Element resizableVertical(jui.ui.Context c, jui.ui.FloatRef firstHeight,
                                            Runnable first, Runnable second) {
        return jui.ui.Ui.splitVertical(c, firstHeight, first, second);
    }

    /** gpui-kit Scrollable: the framework's vertical scroll. */
    public static Element scrollable(jui.ui.Context c, jui.ui.ScrollState state, Runnable content) {
        return jui.ui.Ui.scroll(c, state).children(content);
    }

    /** gpui-kit List: the framework's virtualized list. */
    public static Element list(jui.ui.Context c, jui.ui.ListState state, int count,
                               java.util.function.IntConsumer row) {
        return jui.ui.Ui.list(c, state, count, row);
    }

    /** gpui-kit Menu: the framework's native context menu. */
    public static void menu(jui.ui.Context c, jui.ui.Element target,
                            java.util.function.Consumer<jui.ui.Menu> build) {
        jui.ui.Ui.contextMenu(c, target, build);
    }
}
