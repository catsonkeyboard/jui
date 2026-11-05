package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Painter;
import jui.ui.Rect;

/**
 * The kit's spinner: twelve tick marks around a circle, rotating with the
 * frame clock and fading along the tail, as gpui-kit's Spinner.
 */
public final class Spinner {
    /** A draw that spins for as long as the frame clock runs. */
    public static Painter.Draw draw(Context c) {
        long t0 = c.now();
        return (p, b) -> {
            float size = Math.min(b.width(), b.height());
            float cx = b.x() + b.width() / 2, cy = b.y() + b.height() / 2;
            float r = size / 2 - Math.max(1, size / 14);
            float turn = (t0 / 66_000_000L) % 12L;
            Color base = c.theme().textMuted();
            for (int i = 0; i < 12; i++) {
                int k = (int) ((i + turn) % 12);
                double a = k * Math.PI / 6;
                float fade = 0.25f + 0.75f * (i / 11f);
                Color col = Color.rgba(base.rgba() >>> 8, fade);
                float w = Math.max(1, size / 12);
                p.line(cx + (float) Math.cos(a) * r * 0.55f, cy + (float) Math.sin(a) * r * 0.55f,
                        cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, w, col);
            }
        };
    }

    /** A spinning element of the given square size. */
    public static jui.ui.Element spinner(Context c, float size) {
        return jui.ui.Ui.box(c).size(size, size).draw(draw(c)).label("kit-spinner");
    }

    private Spinner() {}
}
