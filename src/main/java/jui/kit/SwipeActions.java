package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's swipe actions, after the SwipeActions: reveal row actions
 * with a swipe, or from the same actions in a menu. The row's content
 * slides with the pointer, the actions sit underneath on the trailing
 * edge, and a drag past half a button opens the row; a tap on an action
 * runs it and snaps the row shut.
 */
public final class SwipeActions {

    /** One revealed action. */
    public record Action(String label, String icon, boolean destructive, Runnable onSelect) {}

    private static final class St {
        float offset;
        boolean open;
    }

    /**
     * A swipeable row: content slides over the actions behind its trailing
     * edge. A drag left opens toward the first action; a right drag or a
     * tap on the closed row shuts it.
     */
    public static Element swipeRow(Context c, Action[] actions, float rowHeight, Runnable content) {
        float actW = 72;
        Element host = box(c).height(rowHeight).clip().label("kit-swipe-row").alignItems(Align.STRETCH);
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        host.children(() -> {
        // The actions live under the content, on the trailing edge.
        row(c).absolute().right(0).top(0).bottom(0).gap(1).children(() -> {
            for (Action a : actions) {
                Element b = buttonBase(c).size(actW, rowHeight).shrink(0).label("kit-swipe-act")
                        .background(a.destructive() ? Kit.danger(c) : c.theme().accent());
                b.children(() -> {
                    column(c).gap(3).alignItems(Align.CENTER).children(() -> {
                        box(c).size(16, 16).shrink(0).draw(Icon.draw(a.icon(), Color.rgb(0xFFFFFF)));
                        text(c, a.label()).singleLine().fontSize(10.5f).textColor(Color.rgb(0xFFFFFF));
                    });
                });
                if (b.clicked() && a.onSelect() != null) {
                    st.offset = 0;
                    st.open = false;
                    a.onSelect().run();
                    c.invalidate();
                }
            }
        });
        // The content: draggable, sliding between closed and the first action.
        Element contentBox = box(c).absolute().left(0).top(0).bottom(0).clip().draggable()
                .background(c.theme().background());
        float target;
        if (contentBox.pressed()) {
            target = Math.max(-actW, Math.min(0, st.offset + contentBox.dragX()));
            st.offset = target;
        } else {
            st.open = st.offset < -actW / 2;
            target = st.open ? -actW : 0;
        }
        float x = Kit.spring(host, "x", target, Kit.MORPH);
        contentBox.left(x);
        contentBox.children(content);
        });
        return host;
    }

    public static Element swipeRow(Context c, Action[] actions, Runnable content) {
        return swipeRow(c, actions, 56, content);
    }

    private SwipeActions() {}
}
