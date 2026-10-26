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
        if (n.invisible) {
            return;
        }
        float o = parentOpacity * n.opacity * (n.disabled && !parentDisabled ? 0.5f : 1);
        int bg = n.down && n.bgPressed != 0 ? n.bgPressed : n.hot && n.bgHover != 0 ? n.bgHover : n.background;
        int shadowColor = fade(n.shadowColor, o), background = fade(bg, o);
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
        } else if (n.clip) {
            // A clipping box draws its border over what it holds, once that is drawn (as MyGO).
            if (alpha(background) != 0) {
                scene.box(n.x, n.y, n.w, n.h, Math.min(n.radiusTL, half), Math.min(n.radiusTR, half),
                        Math.min(n.radiusBR, half), Math.min(n.radiusBL, half), background, 0, 0);
            }
        } else if (alpha(background) != 0 || n.border > 0 && alpha(borderColor) != 0) {
            scene.box(n.x, n.y, n.w, n.h, Math.min(n.radiusTL, half), Math.min(n.radiusTR, half),
                    Math.min(n.radiusBR, half), Math.min(n.radiusBL, half), background, n.border, borderColor);
        }
        if (n.kind == Node.TEXT && n.layout != null) {
            // The text starts inside the padding and the border.
            scene.text(n.layout, n.x + n.padLeft + n.border, n.y + n.padTop + n.border, fade(n.resolvedColor, o));
        }
        if (n.kind == Node.SLIDER) {
            slider(n, scene, theme, o, ringId);
        }
        if (n.clip) {
            scene.pushClip(n.x + n.border, n.y + n.border, n.w - 2 * n.border, n.h - 2 * n.border);
        }
        // The flow first, then absolute children over it, in the order built (as MyGO).
        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            if (!pool.node(c).absolute) {
                paint(pool, c, scene, theme, o, n.disabled, ringId);
            }
        }
        for (int c = n.firstChild; c != Node.NONE; c = pool.node(c).nextSibling) {
            if (pool.node(c).absolute) {
                paint(pool, c, scene, theme, o, n.disabled, ringId);
            }
        }
        if (n.clip) {
            scene.popClip();
            if (n.border > 0 && alpha(borderColor) != 0) {
                scene.box(n.x, n.y, n.w, n.h, Math.min(n.radiusTL, half), Math.min(n.radiusTR, half),
                        Math.min(n.radiusBR, half), Math.min(n.radiusBL, half), 0, n.border, borderColor);
            }
        }
        // The focus ring goes over the children: 3 DIPs out, 2 wide (as MyGO's paint.go).
        if (n.ring || ringId != 0 && n.id == ringId && !n.noFocusRing) {
            scene.box(n.x - 3, n.y - 3, n.w + 6, n.h + 6, Math.min(n.radiusTL, half) + 3,
                    Math.min(n.radiusTR, half) + 3, Math.min(n.radiusBR, half) + 3, Math.min(n.radiusBL, half) + 3,
                    0, 2, fade(theme.focusRing().rgba(), o));
        }
    }

    private static final int KNOB_LIGHT = 0xFFFFFFFF, KNOB_DARK = 0xE0E1E1FF;
    private static final int KNOB_EDGE = 0x00000014, KNOB_LIFT = 0x0000001A; // black at 0.08 and 0.1

    /**
     * A slider: a 6-DIP track across its content box, filled up to the
     * knob, and a 20 by 16 capsule knob, grown 1.35 times while held (as
     * MyGO's widgets.go, without its animation).
     */
    private static void slider(Node n, Scene scene, Theme theme, float o, long ringId) {
        float cx = n.x + n.padLeft + n.border, cy = n.y + n.padTop + n.border;
        float cw = Math.max(0, n.w - n.padLeft - n.padRight - 2 * n.border);
        float ch = Math.max(0, n.h - n.padTop - n.padBottom - 2 * n.border);
        float ty = cy + (ch - 6) / 2, at = cw * Math.max(0, Math.min(1, n.fraction));
        scene.box(cx, ty, cw, 6, 3, fade(theme.border().rgba(), o));
        if (at > 0) {
            scene.box(cx, ty, at, 6, 3, fade(theme.accent().rgba(), o));
        }
        float g = n.held ? 1.35f : 1, kw = 20 * g, kh = 16 * g;
        float kx = cx + at - kw / 2, ky = cy + ch / 2 - kh / 2, r = kh / 2;
        scene.shadow(kx, ky + 0.5f, kw, kh, r, fade(KNOB_EDGE, o), 1);
        scene.shadow(kx, ky + 1.5f, kw, kh, r, fade(KNOB_LIFT, o), 7);
        scene.box(kx, ky, kw, kh, r, fade(theme.isDark() ? KNOB_DARK : KNOB_LIGHT, o));
        if (ringId != 0 && n.id == ringId) {
            scene.box(kx - 3, ky - 3, kw + 6, kh + 6, r + 3, 0, 2, fade(theme.focusRing().rgba(), o));
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
