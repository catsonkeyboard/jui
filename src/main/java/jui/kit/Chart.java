package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's chart: a line or bar series drawn with painter primitives over a
 * soft grid, after gpui-kit's Chart.
 */
public final class Chart {
    /** The two v1 series kinds. */
    public enum Kind { LINE, BAR }

    public static Element chart(Context c, Kind kind, double[] series, float width, float height) {
        return box(c).size(width, height).radius(Kit.RADIUS_SM)
                .background(c.theme().background())
                .border(1, Kit.line(c))
                .label("kit-chart")
                .children(() -> { });
    }

    /** The draw a caller can hand to canvas().draw() for the same series. */
    public static jui.ui.Painter.Draw draw(Kind kind, double[] series) {
        return (p, b) -> {
            double max = 1;
            for (double v : series) max = Math.max(max, v);
            float pad = 8;
            float w = b.width() - pad * 2, h = b.height() - pad * 2;
            Color grid = Color.rgba(0x808080, 0.18f);
            Color ink = Color.rgb(0x3E6FF2);
            for (int g = 1; g < 4; g++) {
                p.line(pad, pad + h * g / 4, pad + w, pad + h * g / 4, 1, grid);
            }
            if (kind == Kind.BAR) {
                float slot = w / series.length;
                for (int i = 0; i < series.length; i++) {
                    float bh = (float) (series[i] / max) * h;
                    p.roundedBox(pad + slot * i + slot * 0.2f, pad + h - bh, slot * 0.6f, bh, 2, ink);
                }
            } else {
                float step = series.length > 1 ? w / (series.length - 1) : w;
                for (int i = 0; i < series.length - 1; i++) {
                    float x0 = pad + step * i, y0 = pad + h - (float) (series[i] / max) * h;
                    float x1 = pad + step * (i + 1), y1 = pad + h - (float) (series[i + 1] / max) * h;
                    p.line(x0, y0, x1, y1, 2, ink);
                    p.circle(x1, y1, 2.5f, ink);
                }
                p.circle(pad, pad + h - (float) (series[0] / max) * h, 2.5f, ink);
            }
        };
    }

    private Chart() {}
}
