package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's progress: a linear bar or a ring, driven by a 0..1 value ref,
 * after gpui-kit's Progress and ProgressCircle.
 */
public final class Progress {
    public static Element bar(Context c, jui.ui.DoubleRef value, float width) {
        return box(c).width(width).height(6).radius(3).background(c.theme().surfaceHover())
                .label("kit-progress")
                .children(() -> {
                    double v = Math.max(0, Math.min(1, value.get()));
                    if (v > 0) {
                        box(c).width((float) Math.max(6, width * v)).fillHeight().radius(3)
                                .background(c.theme().accent());
                    }
                });
    }

    /** A progress ring of the given square size, the fill as tick marks around the circle. */
    public static Element ring(Context c, jui.ui.DoubleRef value, float size) {
        double initial = Math.max(0, Math.min(1, value.get()));
        Element e = box(c).size(size, size).label("kit-progress-ring")
                .alignItems(Align.CENTER)
                .children(() -> {
                    double v = Math.max(0, Math.min(1, value.get()));
                    box(c).size(size, size).absolute().left(0).top(0).draw((p, b) -> {
                        float cx = b.x() + b.width() / 2, cy = b.y() + b.height() / 2;
                        float r = size / 2 - 1;
                        Color base = c.theme().surfaceHover();
                        Color fill = c.theme().accent();
                        int ticks = 12;
                        int lit = (int) Math.round(v * ticks);
                        for (int i = 0; i < ticks; i++) {
                            double a = -Math.PI / 2 + i * 2 * Math.PI / ticks;
                            p.line(cx + (float) Math.cos(a) * r * 0.62f, cy + (float) Math.sin(a) * r * 0.62f,
                                    cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r,
                                    Math.max(1, size / 12), i < lit ? fill : base);
                        }
                    });
                });
        return e;
    }

    private Progress() {}
}
