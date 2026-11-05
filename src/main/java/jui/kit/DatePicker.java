package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's date picker: a trigger showing the chosen date, opening the
 * calendar beneath, after gpui-kit's DatePicker. State is the epoch month
 * and the selected day, as {@link Calendar#today()} seeds them.
 */
public final class DatePicker {
    public static Element datePicker(Context c, IntRef epochMonth, IntRef selectedDay,
                                     jui.ui.BoolRef open) {
        String label = (epochMonth.get() / 12) + "-" + String.format("%02d", 1 + epochMonth.get() % 12)
                + "-" + String.format("%02d", selectedDay.get());
        Element anchor = buttonBase(c).height(28).padding(0, 10).radius(Kit.RADIUS_SM)
                .border(1, Kit.line(c))
                .label("kit-datepicker-" + label)
                .justify(Justify.START).gap(6)
                .children(() -> {
                    box(c).size(13, 13).draw(Icon.draw("calendar", c.theme().textMuted()));
                    text(c, label).fontSize(12).textColor(c.theme().text());
                });
                if (anchor.clicked()) open.set(true);
        popover(c, anchor, open, () ->
                Calendar.calendar(c, epochMonth, selectedDay));
        return anchor;
    }

    private DatePicker() {}
}
