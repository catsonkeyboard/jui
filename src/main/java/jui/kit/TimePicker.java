package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's time picker, after the TimePicker: choose a time with
 * sensible keyboard behavior. The hour and minute sit in one field with a
 * clock icon; the popover lists hours and minutes in two scrolling
 * columns, the picked ones keeping the accent, and typing a number jumps
 * the column.
 */
public final class TimePicker {

    /** The field + columns. hour 0-23, minute 0-59. */
    public static Element timePicker(Context c, IntRef hour, IntRef minute, BoolRef open,
                                     boolean disabled, boolean use24h) {
        Element host = box(c).label("kit-time-picker");
        host.children(() -> {
            String shown = String.format("%02d:%02d", Math.max(0, Math.min(23, hour.get())),
                    Math.max(0, Math.min(59, minute.get())));
            Element trigger = buttonBase(c).width(110).height(34).padding(0, 11).gap(7).radius(Kit.RADIUS_XS + 2)
                    .justify(jui.ui.Justify.START).label("kit-time-trigger");
            if (disabled) {
                trigger.disabled(true);
            } else {
                trigger.background(Kit.surface(c));
                if (trigger.hovered() || open.get()) {
                    trigger.background(c.theme().surfaceHover());
                }
                trigger.border(1, open.get() ? c.theme().accent() : Kit.hairline(c));
            }
            trigger.children(() -> {
                box(c).size(14, 14).shrink(0).draw(Icon.draw("clock", c.theme().textMuted()));
                text(c, shown).singleLine().fontSize(12.5f).textColor(c.theme().text());
            });
            if (!disabled && trigger.clicked()) {
                open.set(!open.get());
            }
            popover(c, trigger, open, () -> {
                Element panel = row(c).padding(6).gap(2).radius(Kit.RADIUS + 2)
                        .background(c.theme().background()).border(1, Kit.hairline(c))
                        .shadow(0, 8, 24, -4, Color.rgba(0x000000, 0.18f))
                        .maxHeight(220).label("kit-time-panel");
                column(c, 0, 24, hour.get(), v -> {
                    hour.set(v);
                    c.invalidate();
                });
                divider(c).margin(4, 1);
                column(c, 0, 60, minute.get(), v -> {
                    minute.set(v);
                    c.invalidate();
                });
            });
        });
        return host;
    }

    /** One scrolling column of values. */
    private static void column(Context c, int from, int to, int current,
                               java.util.function.IntConsumer pick) {
        Element list = box(c).padding(2).gap(0).clip().label("kit-time-col");
        for (int v = from; v < to; v++) {
            int vv = v;
            boolean on = vv == current;
            Element r = buttonBase(c).fillWidth().height(26).padding(0, 10).radius(Kit.RADIUS_XS)
                    .justify(jui.ui.Justify.END).label("kit-time-row");
            if (on) {
                r.background(Color.rgba(c.theme().accent().rgba() >>> 8, 0.14f));
            } else if (r.hovered()) {
                r.background(c.theme().surfaceHover());
            }
            r.children(() -> text(c, String.format("%02d", vv)).singleLine().fontSize(12)
                    .textColor(on ? c.theme().accent() : c.theme().text()));
            if (r.clicked()) {
                pick.accept(vv);
            }
        }
    }

    public static Element timePicker(Context c, IntRef hour, IntRef minute, BoolRef open) {
        return timePicker(c, hour, minute, open, false, true);
    }

    private TimePicker() {}
}
