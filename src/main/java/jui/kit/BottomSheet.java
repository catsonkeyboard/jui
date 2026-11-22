package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Theme;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's bottom sheet, after the BottomSheet: a sheet that rests at a
 * peek or full height and follows your finger. The sheet rises from the
 * bottom edge of its container on the morph run; dragging it down past a
 * quarter dismisses, and a grab handle sits under the title.
 */
public final class BottomSheet {

    private static final class St {
        float dragFrom;
    }

    /**
     * The sheet: peek height while resting, full height when opened, and
     * the content builder. The caller should build it inside a full-area
     * box.
     */
    public static Element bottomSheet(Context c, jui.ui.BoolRef open, float peekHeight,
                                      float fullHeight, String title, Runnable content) {
        Theme t = c.theme();
        boolean on = open.get();
        Element back = box(c).absolute().left(0).top(0).right(0).bottom(0).label("kit-sheet-back");
        if (!on) {
            back.invisible();
        } else {
            back.background(Color.rgba(0x000000, 0.35f));
            c.after(java.time.Duration.ofMillis(16));
        }
        back.onClick(() -> {
            open.set(false);
            c.invalidate();
        });
        back.children(() -> {
            // The sheet: its height runs between peek and full; a drag down
            // from full lowers it and past a quarter dismisses.
            Ref<St> stRef = local(back, "st", St::new);
            St st = stRef.get();
            Element sheet = column(c).absolute().left(0).right(0).bottom(0)
                    .background(t.background()).clip().label("kit-sheet-panel");
            sheet.onClick(() -> { });
            float target = on ? fullHeight : peekHeight;
            float h = KitMotion.spring(sheet, "h", target, KitMotion.MORPH);
            sheet.height(Math.max(peekHeight, h));
            KitMotion.floatShadow(sheet);
            sheet.radius(Kit.RADIUS + 4, Kit.RADIUS + 4, 0, 0);
            sheet.children(() -> {
                // The grab handle doubles as the drag surface.
                Element grab = box(c).fillWidth().height(18).alignItems(Align.CENTER)
                        .label("kit-sheet-grab").draggable();
                grab.children(() -> box(c).size(36, 4).radius(2).background(t.border()));
                if (grab.pressed() && grab.dragY() != 0) {
                    if (st.dragFrom == 0) {
                        st.dragFrom = h;
                    }
                    sheet.height(Math.max(peekHeight, st.dragFrom - grab.dragY()));
                    c.invalidate();
                } else if (st.dragFrom != 0) {
                    float dropped = st.dragFrom - grab.dragY();
                    st.dragFrom = 0;
                    if (dropped < fullHeight - fullHeight * 0.25f) {
                        open.set(false);
                    }
                    c.invalidate();
                }
                if (title != null) {
                    text(c, title).singleLine().fontSize(13.5f).fontWeight(600)
                            .textColor(t.text()).padding(4, 16);
                }
                box(c).grow(1).padding(12, 16).children(content);
            });
        });
        return back;
    }

    public static Element bottomSheet(Context c, jui.ui.BoolRef open, String title, Runnable content) {
        return bottomSheet(c, open, 96, 420, title, content);
    }

    private BottomSheet() {}
}
