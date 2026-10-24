package jui.ui.engine;

import jui.render.Scene;
import jui.ui.Theme;

/**
 * Paints a laid-out node tree into a scene, parents before children: each
 * node's shadow, then its box (background and border), then its text.
 */
final class Paint {
    private Paint() {}

    /**
     * Paints the node and its children; {@code parentOpacity} is the
     * ancestors' opacity multiplied, and a disabled subtree halves it once,
     * at its outermost disabled node.
     */
    static void paint(NodePool pool, int index, Scene scene, Theme theme, float parentOpacity,
                      boolean parentDisabled, long ringId) {
        Node n = pool.node(index);
        float o = parentOpacity * n.opacity * (n.disabled && !parentDisabled ? 0.5f : 1);
        int shadowColor = fade(n.shadowColor, o), background = fade(n.background, o);
        int borderColor = fade(n.borderColor, o);
        // A corner rounds at most half the shorter side, as in CSS: radius(999) makes a pill.
        float half = Math.min(n.w, n.h) / 2;
        if (alpha(shadowColor) != 0) {
            float s = n.shadowSpread;
            scene.shadow(n.x + n.shadowX - s, n.y + n.shadowY - s, n.w + 2 * s, n.h + 2 * s,
                    Math.max(0, Math.min(n.radiusTL, half) + s), shadowColor, n.shadowBlur);
        }
        if (n.kind == Node.DIVIDER) {
            scene.box(n.x, n.y, n.w, n.h, 0, fade(theme.border().rgba(), o));
        } else if (alpha(background) != 0 || n.border > 0 && alpha(borderColor) != 0) {
            scene.box(n.x, n.y, n.w, n.h, Math.min(n.radiusTL, half), Math.min(n.radiusTR, half),
                    Math.min(n.radiusBR, half), Math.min(n.radiusBL, half), background, n.border, borderColor);
        }
        if (n.kind == Node.TEXT && n.layout != null) {
            // The text starts inside the padding and the border.
            scene.text(n.layout, n.x + n.padLeft + n.border, n.y + n.padTop + n.border, fade(n.resolvedColor, o));
        }
        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            paint(pool, c, scene, theme, o, n.disabled, ringId);
        }
        // The focus ring goes over the children: 3 DIPs out, 2 wide (as MyGO's paint.go).
        if (n.ring || ringId != 0 && n.id == ringId && !n.noFocusRing) {
            scene.box(n.x - 3, n.y - 3, n.w + 6, n.h + 6, Math.min(n.radiusTL, half) + 3,
                    Math.min(n.radiusTR, half) + 3, Math.min(n.radiusBR, half) + 3, Math.min(n.radiusBL, half) + 3,
                    0, 2, fade(theme.focusRing().rgba(), o));
        }
    }

    /** The color with its alpha multiplied by o. */
    private static int fade(int rgba, float o) {
        return o >= 1 ? rgba : (rgba & 0xFFFFFF00) | Math.round((rgba & 0xFF) * o);
    }

    private static int alpha(int rgba) {
        return rgba & 0xFF;
    }
}
