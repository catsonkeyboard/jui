package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's usage meter, after the UsageMeter: show what fills an
 * allowance and how close it is to the limit. The bar fills on the morph
 * run and its tone warms as the used share climbs, the used and total
 * figures sit above, and a note names what remains.
 */
public final class UsageMeter {

    /** The meter: label, used, total, and the unit word. */
    public static Element usageMeter(Context c, String label, double used, double total, String unit) {
        float share = total > 0 ? (float) Math.max(0, Math.min(1, used / total)) : 0;
        Color tone = share > 0.9f ? Kit.danger(c) : share > 0.7f ? Kit.warning(c) : c.theme().accent();
        Element host = column(c).fillWidth().gap(5).label("kit-usage-meter");
        host.children(() -> {
            row(c).alignItems(Align.CENTER).children(() -> {
                text(c, label).singleLine().fontSize(11.5f).textColor(c.theme().text());
                spacer(c).grow(1);
                Element nums = text(c, trim(used) + " / " + trim(total) + (unit == null ? "" : " " + unit));
                nums.singleLine().fontSize(11).textColor(c.theme().textMuted());
            });
            Element track = box(c).fillWidth().height(8).radius(4).background(c.theme().surfacePressed())
                    .clip().label("kit-usage-track");
            float w = track.bounds().width();
            float fill = Kit.spring(track, "fill", share, Kit.MORPH);
            track.children(() -> box(c).absolute().left(0).top(0).bottom(0)
                    .width(Math.max(4, w * fill)).radius(4).background(tone));
            if (share > 0.9f) {
                row(c).gap(4).alignItems(Align.CENTER).children(() -> {
                    box(c).size(11, 11).shrink(0)
                            .draw(Icon.draw("exclamationmark-triangle", Kit.danger(c)));
                    text(c, "Almost out — upgrade for more").singleLine().fontSize(10.5f)
                            .textColor(Kit.danger(c));
                });
            } else {
                text(c, trim(Math.max(0, total - used)) + (unit == null ? "" : " " + unit)
                        + " remaining").singleLine().fontSize(10.5f)
                        .textColor(c.theme().textMuted());
            }
        });
        return host;
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private UsageMeter() {}
}
