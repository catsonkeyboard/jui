package jui.kit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jui.render.Scene;

/**
 * Finds the foregrounds a frame draws in a color the eye cannot tell from
 * what is behind them: an icon or a run of text that vanishes into its
 * background. It reads the frame's {@link Scene} directly, so it judges
 * what will really be painted rather than what the code asks for.
 */
final class SceneContrast {
    private static final int BOX = 0, GLYPH = 2, STROKE = 3, IMAGE = 4;

    /** One foreground that does not separate from its background, in DIPs. */
    record Invisible(boolean glyph, float x, float y, float w, float h, float[] fg, float[] bg) {
        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT,
                    "%s at (%.0f,%.0f %.0fx%.0f) ink=(%.2f,%.2f,%.2f,%.2f) over (%.2f,%.2f,%.2f)",
                    glyph ? "glyph " : "stroke", x, y, w, h, fg[0], fg[1], fg[2], fg[3], bg[0], bg[1], bg[2]);
        }
    }

    /**
     * A box already painted: its fill, its border where there is one, and
     * its corner radii, which decide how much of its rectangle it covers.
     */
    private record Box(float x, float y, float w, float h, float[] fill, float borderW, float[] border,
                       float[] corners) {
        /** Whether the point is inside the shape: a rounded corner leaves its square's outer corner unpainted. */
        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h
                    && clears(px, py, x, y, 1, 1, corners[0])
                    && clears(px, py, x + w, y, -1, 1, corners[1])
                    && clears(px, py, x + w, y + h, -1, -1, corners[2])
                    && clears(px, py, x, y + h, 1, -1, corners[3]);
        }

        /** Whether the point is inside the corner arc at (cx, cy), whose square extends sx, sy into the box. */
        private static boolean clears(float px, float py, float cx, float cy, float sx, float sy, float r) {
            float dx = (px - cx) * sx, dy = (py - cy) * sy;
            if (r <= 0 || dx <= 0 || dy <= 0 || dx >= r || dy >= r) {
                return true;                 // not in that corner's square
            }
            return dx * dx + dy * dy <= r * r;
        }
    }

    /**
     * The invisible foregrounds of the frame. base is the window's own
     * background, for what no box painted under.
     */
    static List<Invisible> scan(Scene scene, float[] base) {
        return scan(scene, base, false);
    }

    /**
     * The invisible foregrounds of the frame. base is the window's own
     * background, for what no box painted under. strict drops the two
     * deliberate pairings a crude read of the scene cannot tell apart:
     * white ink on a saturated accent (a blue button), and a dark ink on
     * a dark glyph's own filled disc (the theme-switcher crescent).
     */
    static List<Invisible> scan(Scene scene, float[] base, boolean strict) {
        List<Invisible> out = new ArrayList<>();
        List<Box> boxes = new ArrayList<>();
        float scale = scene.scale();
        for (int i = 0; i < scene.count(); i++) {
            float[] d = scene.instance(i);
            int kind = (int) d[18];
            if (kind == BOX) {
                boxes.add(new Box(d[0], d[1], d[2], d[3], Arrays.copyOfRange(d, 8, 12), d[16],
                        Arrays.copyOfRange(d, 12, 16), Arrays.copyOfRange(d, 4, 8)));
                continue;
            }
            if (kind == IMAGE) {
                boxes.add(new Box(d[0], d[1], d[2], d[3], null, 0, null, new float[4]));
                continue;
            }
            if (kind != GLYPH && kind != STROKE) {
                continue;                     // shadows and whatever else is not ink
            }
            float x = d[0], y = d[1], w = d[2], h = d[3];
            if (kind == GLYPH) {              // glyphs are recorded in pixels, the rest in DIPs
                x /= scale;
                y /= scale;
                w /= scale;
                h /= scale;
            }
            float px = x + w / 2, py = y + h / 2;
            float[] bg = background(boxes, base, px, py);
            float[] fg = Arrays.copyOfRange(d, 8, 12);
            if (bg == null || fg[3] < 0.5f) {
                continue;                     // nothing known behind it, or still fading in
            }
            if (strict && onColor(boxes, base, x, y, w, h, fg)) {
                continue;                     // white-on-accent or dark ink on its own disc
            }
            if (vanishes(fg, bg)) {
                out.add(new Invisible(kind == GLYPH, x, y, w, h, fg, bg));
            }
        }
        return out;
    }

    /** What the boxes painted before this point add up to, or null when an image or nothing is under it. */
    private static float[] background(List<Box> boxes, float[] base, float px, float py) {
        float[] bg = {base[0], base[1], base[2]};
        for (Box b : boxes) {
            if (!b.contains(px, py)) {
                continue;
            }
            if (b.fill == null) {
                return null;
            }
            over(bg, b.fill);
            if (b.borderW > 0 && b.border != null) {
                float half = b.borderW / 2;
                boolean onEdge = px < b.x + half || px > b.x + b.w - half
                        || py < b.y + half || py > b.y + b.h - half;
                if (onEdge) {
                    over(bg, b.border);
                }
            }
        }
        return bg;
    }

    /**
     * Whether the ink deliberately sits on a colored control or its own
     * disc. The center of a glyph can fall outside a rounded fill whose
     * corners cover it, so sample the whole foreground rectangle.
     */
    private static boolean onColor(List<Box> boxes, float[] base, float x, float y, float w, float h,
                                   float[] fg) {
        float[][] points = {{x + w / 2, y + h / 2}, {x + w * 0.2f, y + h * 0.2f},
                {x + w * 0.8f, y + h * 0.2f}, {x + w * 0.2f, y + h * 0.8f},
                {x + w * 0.8f, y + h * 0.8f}};
        for (float[] pt : points) {
            float[] bg = background(boxes, base, pt[0], pt[1]);
            if (bg == null) {
                continue;
            }
            if (saturated(bg) || (fg[0] < 0.2f && bg[0] < 0.2f)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the fill is a saturated accent: white ink on a blue or red
     * control is a deliberate pairing, not a vanish. A neutral grey or a
     * background-tinted surface is not.
     */
    private static boolean saturated(float[] rgb) {
        float lo = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
        float hi = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        return hi - lo > 0.22f && hi > 0.25f;
    }

    /** src over dst, in place. */
    private static void over(float[] dst, float[] src) {
        float a = src[3];
        for (int k = 0; k < 3; k++) {
            dst[k] = src[k] * a + dst[k] * (1 - a);
        }
    }

    /** WCAG relative luminance of an sRGB color. */
    static float luminance(float[] rgb) {
        float r = channel(rgb[0]), g = channel(rgb[1]), b = channel(rgb[2]);
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private static float channel(float c) {
        return c <= 0.03928f ? c / 12.92f : (float) Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Their contrast ratio, 1 for identical colors and 21 for black on white. */
    static float contrast(float[] a, float[] b) {
        float la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05f) / (Math.min(la, lb) + 0.05f);
    }

    /**
     * Whether the ink all but disappears into the background: as painted
     * (translucent ink counts as what it blends to), the two read as one.
     */
    private static boolean vanishes(float[] fg, float[] bg) {
        float a = fg[3];                      // translucent ink reads as what it blends to
        float[] asPainted = {fg[0] * a + bg[0] * (1 - a), fg[1] * a + bg[1] * (1 - a),
                fg[2] * a + bg[2] * (1 - a)};
        return contrast(asPainted, bg) < 1.12f;
    }

    private SceneContrast() {}
}
