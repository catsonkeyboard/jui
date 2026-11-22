package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's progress: a linear bar or a ring, driven by a 0..1 value ref.
 * The bar's fill glides on the morph run as the value moves, and its tone
 * warms past ninety percent; the ring reads as ticks around the circle.
 */
public final class Progress {
    public static Element bar(Context c, jui.ui.DoubleRef value, float width) {
        Element host = box(c).width(width).height(6).radius(3).background(c.theme().surfaceHover())
                .clip().label("kit-progress");
        double v = Math.max(0, Math.min(1, value.get()));
        float fill = Kit.spring(host, "fill", (float) v, Kit.MORPH);
        Color tone = v > 0.9 ? Kit.danger(c) : c.theme().accent();
        host.children(() -> {
            if (fill > 0) {
                box(c).absolute().left(0).top(0).bottom(0)
                        .width((float) Math.max(6, width * fill)).radius(3).background(tone);
            }
        });
        return host;
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
