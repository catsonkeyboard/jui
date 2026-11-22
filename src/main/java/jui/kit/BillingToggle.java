package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's billing toggle, after the BillingToggle: a monthly and yearly
 * switch with a savings badge and prices that roll to the new amount. The
 * track's thumb slides on the snappy run, "Yearly" carries a "-20%" chip,
 * and the caller re-renders its prices as the ref flips.
 */
public final class BillingToggle {

    /** The switch: monthly false, yearly true; the badge text goes on Yearly. */
    public static Element billingToggle(Context c, BoolRef yearly, String badge, boolean disabled) {
        Element host = row(c).gap(8).label("kit-billing-toggle").alignItems(Align.CENTER);
        host.children(() -> {
            Element monthly = buttonBase(c).label("kit-billing-monthly").padding(2, 4);
            monthly.children(() -> text(c, "Monthly").singleLine().fontSize(12.5f)
                    .textColor(!yearly.get() ? c.theme().text() : c.theme().textMuted()));
            if (monthly.clicked() && !disabled) {
                yearly.set(false);
                c.invalidate();
            }
            // The switch itself.
            Element track = buttonBase(c).size(40, 22).radius(11).shrink(0).padding(2)
                    .label("kit-billing-track");
            if (disabled) {
                track.disabled(true);
            }
            float pos = Kit.spring(track, "knob", yearly.get() ? 1 : 0, Kit.SNAPPY);
            track.background(Kit.mix(c.theme().border(), c.theme().accent(), pos));
            track.children(() -> box(c).size(18, 18).radius(9).background(Color.rgb(0xFFFFFF))
                    .shadow(0, 1, 3, 0, Color.rgba(0x000000, 0.25f))
                    .margin(0, 0, 0, 18 * pos));
            if (!disabled && track.clicked()) {
                yearly.set(!yearly.get());
                c.invalidate();
            }
            Element y = row(c).gap(5).alignItems(Align.CENTER).label("kit-billing-yearly");
            y.children(() -> {
                Element label = buttonBase(c).padding(2, 4).label("kit-billing-yearly-btn");
                label.children(() -> text(c, "Yearly").singleLine().fontSize(12.5f)
                        .textColor(yearly.get() ? c.theme().text() : c.theme().textMuted()));
                if (label.clicked() && !disabled) {
                    yearly.set(true);
                    c.invalidate();
                }
                if (badge != null && !badge.isEmpty()) {
                    Element chip = box(c).padding(2, 6).radius(8)
                            .background(Color.rgba(Kit.success(c).rgba() >>> 8, 0.16f))
                            .children(() -> text(c, badge).singleLine().fontSize(10)
                                    .textColor(Kit.success(c)));
                }
            });
        });
        return host;
    }

    public static Element billingToggle(Context c, BoolRef yearly) {
        return billingToggle(c, yearly, "-20%", false);
    }

    private BillingToggle() {}
}
