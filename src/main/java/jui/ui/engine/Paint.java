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
        if (n.checkMark) {
            // MyGO's checkPath, in the box's own proportions.
            float w = n.w, h = n.h;
            scene.stroke(n.x + 0.22f * w, n.y + 0.52f * h, n.x + 0.42f * w, n.y + 0.71f * h,
                    n.x + 0.78f * w, n.y + 0.31f * h, 2, fade(theme.accentText().rgba(), o));
        }
        if (n.kind == Node.TEXT && n.layout != null) {
            // The text starts inside the padding and the border.
            scene.text(n.layout, n.x + n.padLeft + n.border, n.y + n.padTop + n.border, fade(n.resolvedColor, o));
        }
        if (n.kind == Node.SLIDER) {
            slider(n, scene, theme, o, ringId);
        }
        if (n.kind == Node.INPUT) {
            input(n, scene, theme, o);
        }
        if (n.clip) {
            // Inside the border, its corners rounded as the border's inner edge (as MyGO's clipRect).
            float b = n.border;
            scene.pushClip(n.x + b, n.y + b, n.w - 2 * b, n.h - 2 * b, inner(n.radiusTL, half, b),
                    inner(n.radiusTR, half, b), inner(n.radiusBR, half, b), inner(n.radiusBL, half, b));
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
        if (n.bars) {
            // The scroll bars' thumbs, over the content (as MyGO's paint.go); a dragged one is 2 wider.
            float width = theme.scrollbarWidth();
            boolean v = ScrollBar.vertical(n.flags, n.h, n.contentH), h = ScrollBar.horizontal(n.flags, n.w, n.contentW);
            float corner = ScrollBar.corner(v, h, width);
            int color = fade(theme.scrollbar().rgba(), o);
            if (v) {
                float w = width + (n.barDrag ? 2 : 0), track = Math.max(0, n.h - corner);
                float length = ScrollBar.thumbLength(track, n.h, n.contentH);
                float top = ScrollBar.thumbStart(n.y, track, n.h, n.contentH, n.scrollY);
                scene.box(n.x + n.w - width - 3 - (n.barDrag ? 1 : 0), top, w, length, w / 2, color);
            }
            if (h) {
                float w = width + (n.barDragX ? 2 : 0), track = Math.max(0, n.w - corner);
                float length = ScrollBar.thumbLength(track, n.w, n.contentW);
                float left = ScrollBar.thumbStart(n.x, track, n.w, n.contentW, n.scrollX);
                scene.box(left, n.y + n.h - width - 3 - (n.barDragX ? 1 : 0), length, w, w / 2, color);
            }
        }
        // The focus ring goes over the children: 3 DIPs out, 2 wide (as MyGO's paint.go).
        boolean ring = ringId != 0 && (n.ringOwner != Node.NONE
                ? pool.node(n.ringOwner).id == ringId && !pool.node(n.ringOwner).noFocusRing
                : n.id == ringId && !n.noFocusRing && !n.ownRing);
        if (ring) {
            scene.box(n.x - 3, n.y - 3, n.w + 6, n.h + 6, Math.min(n.radiusTL, half) + 3,
                    Math.min(n.radiusTR, half) + 3, Math.min(n.radiusBR, half) + 3, Math.min(n.radiusBL, half) + 3,
                    0, 2, fade(theme.focusRing().rgba(), o));
        }
    }

    /**
     * A text input, clipped to its padding box with square corners (as
     * MyGO's paintInput): the placeholder while it is empty, the selection
     * while it has focus, the text, the composition's underline, and the
     * blinking caret, one device pixel wide.
     */
    private static void input(Node n, Scene scene, Theme theme, float o) {
        if (n.multiline) {
            textArea(n, scene, theme, o);
            return;
        }
        jui.text.TextLayout l = n.layout;
        scene.pushClip(n.x + n.border, n.y + n.border, n.w - 2 * n.border, n.h - 2 * n.border);
        int color = fade(n.resolvedColor, o);
        float top = n.inputTop, lineH = n.inputLineH;
        if ((n.text == null || n.text.isEmpty()) && n.placeholder != null) {
            jui.text.TextLayout p = scene.layout(n.placeholder, n.resolvedSize, n.resolvedWeight, 0);
            scene.text(p, n.x + n.padLeft + n.border, top + (lineH - p.height) / 2, fade(theme.textMuted().rgba(), o));
        }
        if (l != null) {
            if (n.inputFocused && n.inputSelA != n.inputSelB) {
                float a = n.inputX + l.caretX(n.inputSelA), b = n.inputX + l.caretX(n.inputSelB);
                scene.box(a, top, b - a, lineH, 0, fade(theme.selection().rgba(), o));
            }
            scene.text(l, n.inputX, top + (lineH - l.height) / 2, color);
            if (n.inputMarkA >= 0) {
                float a = n.inputX + l.caretX(n.inputMarkA), b = n.inputX + l.caretX(n.inputMarkB);
                scene.box(a, top + lineH - 2, b - a, 1, 0, color);
            }
            if (n.inputFocused && n.caretShown && !n.disabled) {
                float s = scene.scale(), w = Math.max(Math.round(s), 1) / s;
                scene.box(n.inputX + l.caretX(n.inputCaret), top, w, lineH, 0, fade(theme.accent().rgba(), o));
            }
        }
        scene.popClip();
    }

    private static final float[] INPUT_RECT = new float[4];

    private static void textArea(Node node, Scene scene, Theme theme, float opacity) {
        jui.text.TextLayout layout = node.layout;
        scene.pushClip(node.x + node.border, node.y + node.border, node.w - 2 * node.border, node.h - 2 * node.border);
        float contentWidth = Math.max(0.01f, node.w - node.padLeft - node.padRight - 2 * node.border);
        if (node.text.isEmpty() && node.placeholder != null) {
            jui.text.TextLayout placeholder = scene.layout(node.placeholder, node.resolvedSize, node.resolvedWeight, contentWidth);
            scene.text(placeholder, node.inputX, node.inputTop, fade(theme.textMuted().rgba(), opacity));
        }
        int first = Math.max(0, layout.lineForY(node.y + node.border - node.inputTop) - 1);
        int end = Math.min(layout.lines, layout.lineForY(node.y + node.h - node.border - node.inputTop) + 2);
        if (node.inputFocused) {
            for (int line = first; line < end; line++) {
                if (layout.selectionRect(node.inputSelA, node.inputSelB, line, INPUT_RECT)) {
                    scene.box(node.inputX + INPUT_RECT[0], node.inputTop + INPUT_RECT[1], INPUT_RECT[2], INPUT_RECT[3],
                            0, fade(theme.selection().rgba(), opacity));
                }
            }
        }
        int color = fade(node.resolvedColor, opacity);
        scene.text(layout, node.inputX, node.inputTop, color, first, end);
        if (node.inputMarkA >= 0) {
            for (int line = first; line < end; line++) {
                if (layout.selectionRect(node.inputMarkA, node.inputMarkB, line, INPUT_RECT)) {
                    scene.box(node.inputX + INPUT_RECT[0], node.inputTop + INPUT_RECT[1] + INPUT_RECT[3] - 2,
                            INPUT_RECT[2], 1, 0, color);
                }
            }
        }
        if (node.inputFocused && node.caretShown && !node.disabled) {
            TextEdit edit = node.state.edit;
            int line = layout.lineAt(node.inputCaret, edit.caretUpstream);
            float width = Math.max(Math.round(scene.scale()), 1) / scene.scale();
            scene.box(node.inputX + layout.caretX(node.inputCaret, edit.caretUpstream),
                    node.inputTop + layout.lineTop(line), width, layout.lineHeight(line), 0,
                    fade(theme.accent().rgba(), opacity));
        }
        scene.popClip();
    }

    /** A corner's radius inside a border: the outer one, at most half the shorter side, less the border. */
    static float inner(float radius, float half, float border) {
        return Math.max(Math.min(radius, half) - border, 0);
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
        if (ringId != 0 && n.id == ringId && !n.noFocusRing) {
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
