package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's skeleton: grey rounded placeholders that pulse, shown while real
 * content loads, after gpui-kit's Skeleton.
 */
public final class Skeleton {
    /** A pulsing block of the given size. */
    public static Element block(Context c, float w, float h, float radius) {
        long t0 = c.now();
        Element e = box(c).width(w).height(h).radius(radius)
                .label("kit-skeleton")
                .children(() -> { });
        e.opacity(pulse(t0, c));
        e.background(c.theme().surfaceHover());
        return e;
    }

    /** Three text lines: one wide, one full, one short. */
    public static Element textLines(Context c, float width) {
        long t0 = c.now();
        float op = pulse(t0, c);
        Color ink = c.theme().surfaceHover();
        return column(c).width(width).gap(8).children(() -> {
            Element l1 = box(c).height(12).radius(6).background(ink);
            l1.opacity(op);
            Element l2 = box(c).height(12).radius(6).background(ink);
            l2.opacity(op);
            Element l3 = box(c).width(width * 0.6f).height(12).radius(6).background(ink);
            l3.opacity(op);
        });
    }

    private static float pulse(long t0, Context c) {
        float t = ((c.now() - t0) / 900_000_000L) % 100L / 100f;
        float wave = (float) Math.sin(t * Math.PI * 2);
        return 0.55f + 0.45f * (wave * 0.5f + 0.5f);
    }

    private Skeleton() {}
}
