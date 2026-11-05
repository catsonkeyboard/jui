package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's radio group: circle radios in a row or a column, driven by an
 * IntRef, after gpui-kit's Radio.
 */
public final class Radio {
    public static Element radio(Context c, jui.ui.IntRef selected, boolean horizontal, String... options) {
        return horizontal
                ? row(c).gap(14).label("kit-radio-row").children(() -> each(c, selected, options))
                : column(c).gap(8).label("kit-radio-col").children(() -> each(c, selected, options));
    }

    public static Element radio(Context c, jui.ui.IntRef selected, String... options) {
        return radio(c, selected, false, options);
    }

    private static void each(Context c, jui.ui.IntRef selected, String[] options) {
        for (int i = 0; i < options.length; i++) {
            int idx = i;
            boolean on = selected.get() == idx;
            Element b = buttonBase(c).height(22).gap(8)
                    .label("kit-radio-" + options[idx])
                    .justify(Justify.START)
                    .children(() -> {
                        circle(c, on);
                        text(c, options[idx]).fontSize(12.5f)
                                .textColor(on ? c.theme().text() : c.theme().textMuted());
                    });
            if (b.clicked()) selected.set(idx);
        }
    }

    private static void circle(Context c, boolean on) {
        box(c).size(16, 16).radius(8).border(1.5f, on ? c.theme().accent() : Kit.line(c))
                .alignItems(Align.CENTER)
                .children(() -> {
                    if (on) {
                        box(c).size(8, 8).radius(4).background(c.theme().accent());
                    }
                });
    }

    private Radio() {}
}
