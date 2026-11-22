package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's metric card, after the MetricCard: a compact summary for a
 * number that needs context. The value reads large, the delta sits beside
 * it in its color with an arrow, and the label sits over the whole; a
 * sparkline rides underneath when the caller brings one.
 */
public final class MetricCard {

    /** The card: label, value, delta as +x%, optional sparkline points 0..1. */
    public static Element metricCard(Context c, String label, String value, double delta,
                                     float[] sparkline) {
        boolean up = delta >= 0;
        Color tone = up ? KitMotion.success(c) : KitMotion.danger(c);
        Element host = column(c).fillWidth().gap(2).padding(14).radius(Kit.RADIUS + 3)
                .background(c.theme().background()).border(1, KitMotion.hairline(c))
                .label("kit-metric-" + label);
        KitMotion.cardShadow(host);
        host.children(() -> {
            text(c, label).singleLine().fontSize(11.5f).textColor(c.theme().textMuted());
            row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                text(c, value).singleLine().fontSize(22).fontWeight(600).textColor(c.theme().text());
                row(c).gap(3).padding(2, 6).radius(9)
                        .background(Color.rgba(tone.rgba() >>> 8, 0.14f)).children(() -> {
                            box(c).size(10, 10).shrink(0)
                                    .draw(Icon.draw(up ? "arrow-up" : "arrow-down", tone));
                            text(c, String.format("%.1f%%", Math.abs(delta))).singleLine().fontSize(10.5f)
                                    .textColor(tone);
                        });
            });
            if (sparkline != null && sparkline.length > 1) {
                box(c).fillWidth().height(34).label("kit-metric-spark")
                        .draw((p, b) -> {
                            float w = b.width(), h = b.height();
                            float step = w / (sparkline.length - 1);
                            Color col = Color.rgba(tone.rgba() >>> 8, 0.7f);
                            for (int i = 1; i < sparkline.length; i++) {
                                float x0 = b.x() + (i - 1) * step, x1 = b.x() + i * step;
                                float y0 = b.y() + h - Math.max(0, Math.min(1, sparkline[i - 1])) * h;
                                float y1 = b.y() + h - Math.max(0, Math.min(1, sparkline[i])) * h;
                                p.line(x0, y0, x1, y1, 1.6f, col);
                            }
                        });
            }
        });
        return host;
    }

    private MetricCard() {}
}
