package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's dropdown button: a caret button that opens a menu of items below
 * it, after gpui-kit's DropdownButton. The pick lands in the IntConsumer as
 * the item index, once.
 */
public final class DropdownButton {
    public static Element dropdownButton(Context c, String label, Kit.Variant v, Kit.Size s,
                                         jui.ui.BoolRef open, java.util.function.IntConsumer pick,
                                         String... items) {
        Element anchor = Button.button(c, label, v, s, null, false, false, false, false, true);
        popover(c, anchor, open, () -> column(c).width(160).padding(4).gap(2).children(() -> {
            for (int i = 0; i < items.length; i++) {
                int idx = i;
                Element b = buttonBase(c).fillWidth().height(26).padding(0, 10).radius(Kit.RADIUS_XS)
                        .label("kit-dd-" + items[idx])
                        .justify(Justify.START)
                        .children(() -> text(c, items[idx]).fontSize(12).textColor(c.theme().text()));
                if (b.hovered()) b.background(c.theme().surfaceHover());
                if (b.clicked()) {
                    open.set(false);
                    pick.accept(idx);
                }
            }
        }));
        return anchor;
    }

    private DropdownButton() {}
}
