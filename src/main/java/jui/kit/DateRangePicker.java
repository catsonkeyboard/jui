package jui.kit;

import java.time.LocalDate;
import java.time.YearMonth;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's date range picker, after the DateRangePicker: a range picker
 * that grows from its trigger into one or two months with presets and a
 * stretching range highlight. The first click starts the range, the second
 * ends it (earlier first, the highlight re-stretches), and the presets set
 * the span in one press.
 */
public final class DateRangePicker {

    /** A picked range, as epoch days; from > to means nothing picked yet. */
    public record Range(long from, long to) {}

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};
    private static final String[] DOW = {"M", "T", "W", "T", "F", "S", "S"};

    private static final class St {
        long first = -1;   // the first click of a range, -1 none
        long from = -1;
        long to = -1;
        int month;         // epoch month of the left month
    }

    /** The trigger + panel. The caller keeps the Range ref for the result. */
    public static Element dateRangePicker(Context c, Ref<Range> range, BoolRef open, boolean twoMonths,
                                          String label) {
        Element host = box(c).label("kit-range-picker");
        host.children(() -> {
            Element trigger = buttonBase(c).width(230).height(34).padding(0, 11).gap(8).radius(Kit.RADIUS_XS + 2)
                    .justify(jui.ui.Justify.START).label("kit-range-trigger");
            trigger.background(KitMotion.surface(c));
            if (trigger.hovered() || open.get()) {
                trigger.background(c.theme().surfaceHover());
            }
            trigger.border(1, open.get() ? c.theme().accent() : KitMotion.hairline(c));
            Range r = range.get();
            trigger.children(() -> {
                box(c).size(15, 15).shrink(0).draw(Icon.draw("calendar", c.theme().textMuted()));
                String shown = r == null || r.from() < 0 ? (label == null ? "Select dates" : label)
                        : day(r.from()) + " – " + day(r.to());
                text(c, shown).singleLine().fontSize(12.5f)
                        .textColor(r == null || r.from() < 0 ? c.theme().textMuted() : c.theme().text());
                spacer(c).grow(1);
                IconMorph.icon(c, IconMorph.CHEVRON, open.get(), c.theme().textMuted(), 14);
            });
            if (trigger.clicked()) {
                open.set(!open.get());
            }
            popover(c, trigger, open, () -> {
                Ref<St> st = local(host, "st", () -> {
                    St t0 = new St();
                    t0.month = YearMonth.now().getYear() * 12 + YearMonth.now().getMonthValue() - 1;
                    return t0;
                });
                St s = st.get();
                Element panel = column(c).padding(10).gap(8).radius(Kit.RADIUS + 2)
                        .background(c.theme().background()).border(1, KitMotion.hairline(c))
                        .shadow(0, 8, 24, -4, Color.rgba(0x000000, 0.18f)).label("kit-range-panel");
                // Presets.
                row(c).gap(4).children(() -> {
                    preset(c, s, range, "Today", 0);
                    preset(c, s, range, "7d", 7);
                    preset(c, s, range, "30d", 30);
                    preset(c, s, range, "90d", 90);
                });
                row(c).gap(16).alignItems(Align.START).children(() -> {
                    month(c, s, range, s.month);
                    if (twoMonths) {
                        month(c, s, range, s.month + 1);
                    }
                });
            });
        });
        return host;
    }

    private static String day(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).toString();
    }

    private static void preset(Context c, St s, Ref<Range> range, String name, int days) {
        Element p = buttonBase(c).padding(4, 8).radius(Kit.RADIUS_XS).label("kit-range-preset");
        if (p.hovered()) {
            p.background(c.theme().surfaceHover());
        }
        p.children(() -> text(c, name).singleLine().fontSize(11).textColor(c.theme().accent()));
        if (p.clicked()) {
            long today = LocalDate.now().toEpochDay();
            range.set(new Range(today - days, today));
            s.first = -1;
            c.invalidate();
        }
    }

    private static void month(Context c, St s, Ref<Range> range, int epochMonth) {
        YearMonth ym = YearMonth.of(epochMonth / 12, epochMonth % 12 + 1);
        column(c).gap(4).children(() -> {
            row(c).gap(6).alignItems(Align.CENTER).children(() -> {
                if (epochMonth == s.month) {
                    Element prev = buttonBase(c).size(22, 22).radius(5).label("kit-range-prev");
                    prev.children(() -> box(c).size(11, 11).shrink(0)
                            .draw(Icon.draw("chevron-left", c.theme().textMuted())));
                    if (prev.clicked()) {
                        s.month--;
                        c.invalidate();
                    }
                }
                text(c, MONTHS[ym.getMonthValue() - 1] + " " + ym.getYear()).singleLine().fontSize(12)
                        .fontWeight(500).textColor(c.theme().text());
                if (epochMonth == s.month && true) {
                    spacer(c).grow(1);
                    Element next = buttonBase(c).size(22, 22).radius(5).label("kit-range-next");
                    next.children(() -> box(c).size(11, 11).shrink(0)
                            .draw(Icon.draw("chevron-right", c.theme().textMuted())));
                    if (next.clicked()) {
                        s.month++;
                        c.invalidate();
                    }
                }
            });
            row(c).gap(2).children(() -> {
                for (String d : DOW) {
                    box(c).size(28, 18).alignItems(Align.CENTER)
                            .children(() -> text(c, d).singleLine().fontSize(9.5f)
                                    .textColor(c.theme().textMuted()));
                }
            });
            LocalDate first = ym.atDay(1);
            int lead = (first.getDayOfWeek().getValue() + 6) % 7;
            int days = ym.lengthOfMonth();
            long today = LocalDate.now().toEpochDay();
            Range r = range.get();
            for (int week = 0; week < 6; week++) {
                int wk = week;
                row(c).gap(2).children(() -> {
                    for (int dow = 0; dow < 7; dow++) {
                        int cell = wk * 7 + dow - lead;
                        if (cell < 0 || cell >= days) {
                            box(c).size(28, 28);
                            continue;
                        }
                        long epoch = ym.atDay(cell + 1).toEpochDay();
                        boolean inRange = r != null && r.from() >= 0 && r.to() >= 0
                                && epoch >= r.from() && epoch <= r.to();
                        boolean isEdge = r != null && r.from() >= 0
                                && (epoch == r.from() || epoch == r.to());
                        Element d = buttonBase(c).size(28, 28).radius(6).label("kit-range-day")
                                .alignItems(Align.CENTER);
                        if (inRange && !isEdge) {
                            d.background(Color.rgba(c.theme().accent().rgba() >>> 8, 0.14f));
                        }
                        if (isEdge) {
                            d.background(c.theme().accent());
                        } else if (d.hovered()) {
                            d.background(c.theme().surfaceHover());
                        }
                        d.children(() -> text(c, String.valueOf(cell + 1)).singleLine().fontSize(11)
                                .textColor(isEdge ? c.theme().accentText()
                                        : epoch == today ? c.theme().accent() : c.theme().text()));
                        if (d.clicked()) {
                            pick(s, range, epoch);
                            c.invalidate();
                        }
                    }
                });
            }
        });
    }

    private static void pick(St s, Ref<Range> range, long epoch) {
        if (s.first < 0) {
            s.first = epoch;
            range.set(new Range(-1, -1));
            return;
        }
        long a = Math.min(s.first, epoch), b = Math.max(s.first, epoch);
        range.set(new Range(a, b));
        s.first = -1;
    }

    public static Element dateRangePicker(Context c, Ref<Range> range, BoolRef open) {
        return dateRangePicker(c, range, open, true, null);
    }

    private DateRangePicker() {}
}
