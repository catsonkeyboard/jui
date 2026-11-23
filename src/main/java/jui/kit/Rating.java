package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's rating, after the Rating: a simple, accessible way to capture
 * a numeric opinion. Stars fill to the hovered or picked value on the
 * snappy run, the label names the pick, and keyboard focus steps the value
 * with the arrows.
 */
public final class Rating {

    /** The row of stars over the caller's IntRef (0 = unrated). */
    public static Element rating(Context c, IntRef value, int max, float size, boolean disabled,
                                 String[] labels) {
        Element host = row(c).gap(3).label("kit-rating").alignItems(Align.CENTER);
        int v = Math.max(0, Math.min(value.get(), max));
        int[] hover = {v};
        host.children(() -> {
            for (int i = 0; i < max; i++) {
                int ii = i + 1;
                Element star = buttonBase(c).size(size, size).radius(3).shrink(0).label("kit-star-" + ii);
                if (disabled) {
                    star.disabled(true);
                }
                if (star.hovered() && !disabled) {
                    hover[0] = ii;
                }
                float fill = Kit.spring(host, "s" + ii, ii <= Math.max(hover[0], v) ? 1 : 0, Kit.SNAPPY);
                star.children(() -> box(c).size(size - 4, size - 4).shrink(0).draw((p, b) -> {
                    Color col = Kit.mix(c.theme().border(), Color.rgb(0xF5A623), fill);
                    drawStar(p, b, col, fill);
                }));
                if (!disabled && star.clicked()) {
                    value.set(v == ii ? 0 : ii);
                    c.invalidate();
                }
            }
            if (labels != null && v > 0 && v <= labels.length) {
                text(c, labels[v - 1]).singleLine().fontSize(11.5f).textColor(c.theme().textMuted())
                        .margin(0, 6);
            }
        });
        return host;
    }

    /** A five-point star drawn as ten strokes, filled share t. */
    private static void drawStar(jui.ui.Painter p, jui.ui.Rect b, Color col, float t) {
        float u = Math.min(b.width(), b.height()) / 16;
        float cx = b.x() + 8 * u, cy = b.y() + 8.6f * u;
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
            double a2 = a + Math.PI / 5;
            p.line(cx + (float) Math.cos(a) * 5.6f * u, cy + (float) Math.sin(a) * 5.6f * u,
                    cx + (float) Math.cos(a2) * 2.3f * u, cy + (float) Math.sin(a2) * 2.3f * u, 1.5f * u, col);
            double a3 = a + 2 * Math.PI / 5;
            p.line(cx + (float) Math.cos(a2) * 2.3f * u, cy + (float) Math.sin(a2) * 2.3f * u,
                    cx + (float) Math.cos(a3) * 5.6f * u, cy + (float) Math.sin(a3) * 5.6f * u, 1.5f * u, col);
        }
        if (t > 0.5f) {
            p.circle(cx, cy, 2.2f * u, col);
        }
    }

    /** The legacy shape. */
    public static Element rating(Context c, IntRef value, int max, float size) {
        return rating(c, value, max, size, false, null);
    }

    private Rating() {}
}
