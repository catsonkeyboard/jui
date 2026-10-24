package jui.ui.engine;

import jui.render.Scene;
import jui.ui.Theme;

/**
 * Paints a laid-out node tree into a scene, parents before children: each
 * node's shadow, then its box (background and border), then its text.
 */
final class Paint {
    private Paint() {}

    static void paint(NodePool pool, int index, Scene scene, Theme theme) {
        Node n = pool.node(index);
        if (alpha(n.shadowColor) != 0) {
            float s = n.shadowSpread;
            scene.shadow(n.x + n.shadowX - s, n.y + n.shadowY - s, n.w + 2 * s, n.h + 2 * s,
                    n.radiusTL + s, n.shadowColor, n.shadowBlur);
        }
        if (n.kind == Node.DIVIDER) {
            scene.box(n.x, n.y, n.w, n.h, 0, theme.border().rgba());
        } else if (alpha(n.background) != 0 || n.border > 0 && alpha(n.borderColor) != 0) {
            scene.box(n.x, n.y, n.w, n.h, n.radiusTL, n.radiusTR, n.radiusBR, n.radiusBL,
                    n.background, n.border, n.borderColor);
        }
        if (n.kind == Node.TEXT && n.layout != null) {
            // The text starts inside the padding and the border.
            scene.text(n.layout, n.x + n.padLeft + n.border, n.y + n.padTop + n.border, n.resolvedColor);
        }
        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            paint(pool, c, scene, theme);
        }
    }

    private static int alpha(int rgba) {
        return rgba & 0xFF;
    }
}
