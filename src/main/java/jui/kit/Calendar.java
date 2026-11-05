package jui.kit;

import java.time.LocalDate;
import java.time.YearMonth;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's calendar: a month grid with prev/next month paging and a selected
 * day, after gpui-kit's Calendar. Selection is the day-of-month in a ref;
 * the visible month lives in a month ref (epoch month = year*12+month).
 */
public final class Calendar {
    private static final String[] WEEKDAYS = {"一", "二", "三", "四", "五", "六", "日"};

    public static Element calendar(Context c, IntRef epochMonth, IntRef selectedDay) {
        YearMonth month = YearMonth.of(epochMonth.get() / 12, 1 + (epochMonth.get() % 12));
        int firstDow = month.atDay(1).getDayOfWeek().getValue() - 1;          // Monday = 0
        int days = month.lengthOfMonth();
        Element root = column(c).gap(6).children(() -> {
            row(c).gap(6).alignItems(Align.CENTER).children(() -> {
                nav(c, epochMonth, "kit-cal-prev", "chevron-left", -1);
                spacer(c).grow(1);
                text(c, month.getYear() + " 年 " + month.getMonthValue() + " 月")
                        .fontSize(12.5f).bold().textColor(c.theme().text());
                spacer(c).grow(1);
                nav(c, epochMonth, "kit-cal-next", "chevron-right", 1);
            });
            row(c).gap(2).children(() -> {
                for (String w : WEEKDAYS) {
                    box(c).size(28, 20).alignItems(Align.CENTER)
                            .children(() -> text(c, w).fontSize(10)
                                    .textColor(c.theme().textMuted()));
                }
            });
            int offset = firstDow;
            int cell = 0;
            while (cell < days + offset) {
                final int start = cell;
                row(c).gap(2).children(() -> {
                    for (int k = start; k < start + 7 && k < days + offset; k++) {
                        int day = k - offset + 1;
                        if (day < 1) {
                            box(c).size(28, 24);
                            continue;
                        }
                        boolean sel = day == selectedDay.get();
                        Element d = buttonBase(c).size(28, 24).radius(Kit.RADIUS_XS)
                                .label("kit-cal-" + day)
                                .justify(Justify.START)
                                .children(() -> text(c, Integer.toString(day)).fontSize(11)
                                        .textColor(sel ? c.theme().accentText() : c.theme().text()));
                        if (sel) d.background(c.theme().accent());
                        else if (d.hovered()) d.background(c.theme().surfaceHover());
                        if (d.clicked()) selectedDay.set(day);
                    }
                });
                cell += 7;
            }
        });
        return root;
    }

    private static void nav(Context c, IntRef epochMonth, String id, String glyph, int delta) {
        Element b = buttonBase(c).size(22, 22).radius(Kit.RADIUS_XS).label(id)
                .children(() -> box(c).size(12, 12)
                        .draw(Icon.draw(glyph, c.theme().text())));
        if (b.clicked()) epochMonth.set(epochMonth.get() + delta);
    }

    /** Convenience: today as the initial state. */
    public static IntRef[] today() {
        LocalDate now = LocalDate.now();
        return new IntRef[] { new IntRef(now.getYear() * 12 + now.getMonthValue() - 1),
                new IntRef(now.getDayOfMonth()) };
    }

    private Calendar() {}
}
