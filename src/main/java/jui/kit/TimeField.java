package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's time field: hour and minute dropdowns, 24 hour clock, the pair
 * held in two refs, after gpui-kit's TimeField.
 */
public final class TimeField {
    public static Element timeField(Context c, IntRef hour, IntRef minute, jui.ui.BoolRef openHour,
                                    jui.ui.BoolRef openMinute) {
        return row(c).gap(4).alignItems(Align.CENTER).label("kit-time").children(() -> {
            dropdown(c, hour, openHour, 24, "kit-time-h", "%02d 时");
            box(c).width(10).alignItems(Align.CENTER)
                    .children(() -> text(c, ":").fontSize(13).textColor(c.theme().textMuted()));
            dropdown(c, minute, openMinute, 60, "kit-time-m", "%02d 分");
        });
    }

    private static void dropdown(Context c, IntRef value, jui.ui.BoolRef open, int count,
                                 String id, String shown) {
        Element anchor = buttonBase(c).width(58).height(26).radius(Kit.RADIUS_XS)
                .border(1, Kit.line(c))
                .label(id)
                .justify(Justify.START)
                .children(() -> text(c, String.format(shown, value.get()))
                        .fontSize(12).textColor(c.theme().text()));
                if (anchor.clicked()) open.set(true);
        popover(c, anchor, open, () -> scroll(c).height(160).width(58).children(() ->
                column(c).gap(1).children(() -> {
                    for (int i = 0; i < count; i++) {
                        int v = i;
                        Element b = buttonBase(c).fillWidth().height(22).radius(3)
                                .label(id + "-" + v)
                                .justify(Justify.START)
                                .children(() -> text(c, String.format(shown, v))
                                        .fontSize(11).textColor(c.theme().text()));
                        if (v == value.get()) b.background(c.theme().selection());
                        if (b.clicked()) {
                            value.set(v);
                            open.set(false);
                        }
                    }
                })));
    }

    private TimeField() {}
}
