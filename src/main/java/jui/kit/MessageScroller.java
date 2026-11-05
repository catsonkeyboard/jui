package jui.kit;

import jui.ui.Context;

import static jui.ui.Ui.*;
import jui.ui.Element;

/**
 * The kit's message scroller: a virtualized transcript that follows the last
 * message and stops following when the user scrolls up, after gpui-kit's
 * MessageScroller.
 */
public final class MessageScroller {
    /** Builds one message row per index; content is only built while visible. */
    public static Element scroller(Context c, jui.ui.ListState state, int count,
                                   java.util.function.IntConsumer row) {
        state.followEnd(true);
        return box(c).grow(1).fillWidth().clip().children(() ->
                list(c, state, count, row::accept));
    }

    private MessageScroller() {}
}
