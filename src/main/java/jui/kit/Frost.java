package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's frost panel, the closest the engine's primitives come to
 * backdrop blur: a translucent fill over a tint, a hairline rim, and the
 * floating shadow, stacked so what sits behind reads through softened.
 * A true blur samples the backdrop in the renderer (an offscreen pass on
 * both backends); until then, dialogs and drawers frost through here.
 */
public final class Frost {

    /**
     * The panel: the tint strength 0..1 (how much of the surface's color
     * washes over what is behind), the corner radius, and the content. The
     * wash rides in the panel's own draw, so it sits under the content.
     */
    public static Element panel(Context c, float strength, float radius, Runnable content) {
        Element host = column(c).radius(radius).clip().label("kit-frost");
        host.background(Color.rgba(c.theme().background().rgba() >>> 8,
                0.72f + 0.2f * Math.max(0, Math.min(1, strength))));
        host.border(1, Color.rgba(Color.rgb(0xFFFFFF).rgba() >>> 8,
                c.theme().isDark() ? 0.10f : 0.55f));
        Kit.floatShadow(host);
        float s = Math.max(0, Math.min(1, strength));
        host.draw((p, b) -> {
            // The soft wash, a translucent layer that rounds the read.
            p.roundedBox(b.x(), b.y(), b.width(), b.height(), radius,
                    Color.rgba(Kit.surface(c).rgba() >>> 8, 0.25f * s + 0.1f));
        });
        host.children(content);
        return host;
    }

    /** A centered frost card of the given width, for dialogs that sit over content. */
    public static Element card(Context c, float width, Runnable content) {
        return panel(c, 0.5f, Kit.RADIUS + 6, () -> {
            Element body = column(c).width(width).padding(18).gap(10).alignItems(Align.STRETCH);
            body.children(content);
        });
    }

    private Frost() {}
}
