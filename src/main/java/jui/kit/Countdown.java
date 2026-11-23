package jui.kit;

import java.time.Duration;
import java.time.Instant;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's countdown, after the Countdown: a launch countdown with
 * rolling digits that morphs into a live state at zero. The digits cross
 * on the snappy run as each unit changes; at zero the pill takes the
 * success tone and the label reads the live state.
 */
public final class Countdown {

    private static final class St {
        long lastNow;
        boolean live;
    }

    /** The countdown toward an instant; the live label shows at zero. */
    public static Element countdown(Context c, Instant target, String liveLabel, boolean compact) {
        Element host = row(c).gap(4).alignItems(Align.CENTER).label("kit-countdown");
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        long now = c.now();
        long left = Math.max(0, target.toEpochMilli() * 1_000_000L - now);
        boolean live = left <= 0;
        if (live != st.live) {
            st.live = live;
            c.invalidate();
        }
        if (live) {
            host.children(() -> {
                Element pill = row(c).gap(6).padding(4, 10).radius(Kit.RADIUS + 2)
                        .background(Color_r(c, Kit.success(c))).label("kit-countdown-live");
                pill.children(() -> {
                    box(c).size(13, 13).shrink(0).draw(Icon.draw("bolt", Kit.success(c)));
                    text(c, liveLabel == null ? "Live now" : liveLabel).singleLine().fontSize(12)
                            .textColor(Kit.success(c));
                });
            });
            return host;
        }
        c.after(Duration.ofMillis(200));
        long days = left / 86_400_000_000L;
        long hours = left % 86_400_000_000L / 3_600_000_000L;
        long mins = left % 3_600_000_000L / 60_000_000_000L / 1000;
        long secs = left % 60_000_000_000L / 1_000_000_000L;
        unit(c, host, days, "days", compact);
        unit(c, host, hours, "hrs", compact);
        unit(c, host, mins, "min", compact);
        if (!compact) {
            unit(c, host, secs, "sec", false);
        }
        return host;
    }

    private static void unit(Context c, Element host, long value, String name, boolean compact) {
        Element cell = column(c).padding(compact ? 2 : 4, compact ? 6 : 10).radius(Kit.RADIUS_XS + 2)
                .background(c.theme().surface()).alignItems(Align.CENTER).label("kit-countdown-unit");
        cell.children(() -> {
            // The digits cross on the snappy run as the value changes.
            float roll = Kit.spring(cell, "roll" + name, value, Kit.SNAPPY);
            Element num = text(c, String.valueOf(value)).singleLine()
                    .fontSize(compact ? 12 : 16).fontWeight(600).textColor(c.theme().text());
            num.opacity(1 - Math.min(0.6f, Math.abs(roll - value) * 0.1f));
            if (!compact) {
                text(c, name).singleLine().fontSize(9.5f).textColor(c.theme().textMuted());
            }
        });
    }

    private static jui.ui.Color Color_r(Context c, jui.ui.Color base) {
        return jui.ui.Color.rgba(base.rgba() >>> 8, 0.14f);
    }

    public static Element countdown(Context c, Instant target) {
        return countdown(c, target, null, false);
    }

    private Countdown() {}
}
