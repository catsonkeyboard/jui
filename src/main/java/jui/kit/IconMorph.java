package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Painter;
import jui.ui.Rect;

import static jui.ui.Ui.*;

/**
 * The kit's morphing icons, after the IconMorph: menu to close, play to
 * pause, plus and copy to check, sun to moon, chevron turn, bell ring and
 * heart like. Each is a draw whose lines interpolate with the element's
 * animated progress, so fast toggling reverses from wherever it is.
 * Icons render aria-hidden in the source; put them in a button that names
 * the state.
 */
public final class IconMorph {

    /** The icon names the morph set knows. */
    public static final String MENU = "menu";
    public static final String PLAY = "play";
    public static final String PLUS = "plus";
    public static final String COPY = "copy";
    public static final String THEME = "theme";
    public static final String CHEVRON = "chevron";
    public static final String BELL = "bell";
    public static final String HEART = "heart";

    /**
     * A morphing icon element of the given square size: state false is the
     * first look, true the second. The caller colors it; the heart likes in
     * the danger unless {@code likeColor} says otherwise.
     */
    public static Element icon(Context c, String name, boolean state, Color color, float size, Color likeColor,
                               Color moonBg) {
        Element e = box(c).size(size, size).shrink(0).label("kit-icon-" + name);
        float p = Kit.spring(e, "kit-icon", state ? 1 : 0, Kit.SNAPPY);
        e.draw((painter, b) -> paint(painter, b, name, p, color, likeColor, moonBg, size));
        return e;
    }

    public static Element icon(Context c, String name, boolean state, Color color, float size) {
        return icon(c, name, state, color, size, Kit.danger(c), c.theme().surface());
    }

    /** Draws one morphing glyph at progress p on a 24 unit grid. */
    public static void paint(Painter p, Rect b, String name, float t, Color color, Color like, Color moonBg,
                             float size) {
        float u = Math.min(b.width(), b.height()) / 24;
        float ox = b.x() + (b.width() - Math.min(b.width(), b.height())) / 2;
        float oy = b.y() + (b.height() - Math.min(b.width(), b.height())) / 2;
        float w = 1.9f * u;
        switch (name) {
            case MENU -> {
                // The outer lines rotate into an X; the middle folds away.
                float l = 8, a = (float) (Math.PI / 4) * t;
                float dx = (float) Math.cos(a) * l, dy = (float) Math.sin(a) * l;
                line(p, ox, oy, u, 12 - dx, 12 - dy, 12 + dx, 12 + dy, w, color);
                line(p, ox, oy, u, 12 + dx, 12 - dy, 12 - dx, 12 + dy, w, color);
                float mid = 8 * (1 - t);
                if (mid > 0.4f) {
                    line(p, ox, oy, u, 12 - mid, 12, 12 + mid, 12, w * (1 - t * 0.5f), color);
                }
            }
            case PLAY -> {
                // The triangle collapses its far corner and splits into two bars.
                float ax = 8 + (0.5f - t) * 6, bx = 19 - t * 7.5f;
                line(p, ox, oy, u, 8, 5, ax, 12, w, color);
                line(p, ox, oy, u, ax, 12, 8, 19, w, color);
                float barX = bx;
                line(p, ox, oy, u, barX + 2.2f, 5 + t * 3, barX + 2.2f, 19 - t * 3, 2.6f * u, color);
                if (t > 0.15f) {
                    line(p, ox, oy, u, barX - 2.2f, 5 + t * 3, barX - 2.2f, 19 - t * 3, 2.6f * u * Math.min(1, t * 2),
                            color);
                }
            }
            case PLUS -> {
                // Each bar swings its ends into one leg of the check.
                line(p, ox, oy, u, 12, 4 + (13 - 4) * t, 12 + (10 - 12) * t, 20 + (18 - 20) * t, w, color);
                line(p, ox, oy, u, 4 + (10 - 4) * t, 12 + (18 - 12) * t, 20, 12 + (7 - 12) * t, w, color);
            }
            case COPY -> {
                // The sheets tuck away while the check draws in.
                float off = 5 * t;
                p.roundedBox(ox + (9 + off * 0.6f) * u, oy + (3 + off * 0.3f) * u, 9 * u, 12 * u, 2 * u,
                        rgba(color, 1 - t));
                p.roundedBox(ox + (6 - off * 0.4f) * u, oy + (8 - off * 0.3f) * u, 9 * u, 12 * u, 2 * u,
                        rgba(color, 1 - t * 0.9f));
                if (t > 0.25f) {
                    float k = (t - 0.25f) / 0.75f;
                    float[] pts = {6, 13, 10.5f, 17.5f, 19, 8};
                    float j1 = Math.min(1, k * 2), j2 = Math.max(0, k * 2 - 1);
                    line(p, ox, oy, u, pts[0], pts[1], pts[0] + (pts[2] - pts[0]) * j1,
                            pts[1] + (pts[3] - pts[1]) * j1, w, color);
                    if (j2 > 0) {
                        line(p, ox, oy, u, pts[2], pts[3], pts[2] + (pts[4] - pts[2]) * j2,
                                pts[3] + (pts[5] - pts[3]) * j2, w, color);
                    }
                }
            }
            case THEME -> {
                // The disc grows while the rays fold in; the crescent carves with a bg circle.
                float r = (6 - 1.2f * t) * u;
                p.circle(ox + 12 * u, oy + 12 * u, r, color);
                p.circle(ox + (12 + 4.5f * t) * u, oy + (12 - 3.5f * t) * u, r * 0.82f, moonBg);
                float ray = 4 * (1 - t);
                if (ray > 0.3f) {
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        float x0 = 12 + (float) Math.cos(a) * 9 * u, y0 = 12 + (float) Math.sin(a) * 9 * u;
                        line(p, ox, oy, u, x0, y0, x0 + (float) Math.cos(a) * ray * u,
                                y0 + (float) Math.sin(a) * ray * u, w, color);
                    }
                }
            }
            case CHEVRON -> {
                // A chevron that turns; 180 flips down to up.
                float a = (float) Math.PI * t;
                float l = 4.5f;
                float dx = (float) Math.sin(a) * l, dy = (float) Math.cos(a) * l;
                line(p, ox, oy, u, 12 - 5, 12 - dy, 12 - 5 + dx, 12, w, color);
                line(p, ox, oy, u, 12 - 5 + dx, 12, 12 - 5, 12 + dy, w, color);
            }
            case BELL -> {
                // Turning on rings the bell: it swings once with the clapper lagging.
                float swing = (float) Math.sin(t * Math.PI) * 0.35f;
                float cx = 12 * u, cy = 12 * u;
                p.line(ox + cx + swing * 6 * u, oy + cy + 0, ox + cx, oy + cy, 0.01f, color); // anchor keeps frames
                ring(p, ox, oy, u, swing, color);
                line(p, ox, oy, u, 7, 11, 7, 7 + t * 0.5f, w, color);
                line(p, ox, oy, u, 7, 7, 9, 4.6f, w, color);
                line(p, ox, oy, u, 9, 4.6f, 12, 3.8f, w, color);
                line(p, ox, oy, u, 12, 3.8f, 15, 4.6f, w, color);
                line(p, ox, oy, u, 15, 4.6f, 17, 7, w, color);
                line(p, ox, oy, u, 17, 7 + t * 0.5f, 17, 11, w, color);
                line(p, ox, oy, u, 7, 11, 17, 11, w, color);
                float clap = 10 + (float) Math.sin(t * Math.PI * 2) * 1.4f * t;
                line(p, ox, oy, u, 10.6f, 13.4f, 13.4f, 13.4f, w, color);
                p.circle(ox + 12 * u, oy + clap * u, 1.2f * u, color);
            }
            case HEART -> {
                // The heart fills with a pop and sparks burst once.
                float pop = 1 + 0.18f * (float) Math.sin(Math.min(1, t) * Math.PI);
                float uu = u * pop;
                Color fill = Kit.mix(color, like, t);
                float cx = 12 * uu, cy = 12.4f * uu;
                p.circle(ox + (12 - 2.6f) * uu, oy + (12.4f - 1.8f) * uu, 2.7f * uu, fill);
                p.circle(ox + (12 + 2.6f) * uu, oy + (12.4f - 1.8f) * uu, 2.7f * uu, fill);
                p.box(ox + (12 - 5.1f) * uu, oy + (12.4f - 1.8f) * uu, 10.2f * uu, 2.8f * uu, fill);
                p.line(ox + (12 - 4.6f) * uu, oy + (12.4f + 0.6f) * uu, ox + 12 * uu, oy + (12.4f + 4.8f) * uu,
                        1.8f * uu, fill);
                p.line(ox + (12 + 4.6f) * uu, oy + (12.4f + 0.6f) * uu, ox + 12 * uu, oy + (12.4f + 4.8f) * uu,
                        1.8f * uu, fill);
                if (t > 0.2f && t < 0.95f) {
                    float spark = (float) Math.sin((t - 0.2f) / 0.75f * Math.PI);
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3 + 0.4;
                        float r0 = 7.5f * uu, r1 = r0 + 3 * spark * uu;
                        p.line(ox + 12 * u + (float) Math.cos(a) * r0, oy + 12 * u + (float) Math.sin(a) * r0,
                                ox + 12 * u + (float) Math.cos(a) * r1, oy + 12 * u + (float) Math.sin(a) * r1,
                                1.4f * u, rgba(like, spark));
                    }
                }
            }
            default -> {
                p.circle(b.x() + b.width() / 2, b.y() + b.height() / 2, Math.min(b.width(), b.height()) / 2 - 2,
                        color);
            }
        }
    }

    private static void ring(Painter p, float ox, float oy, float u, float swing, Color color) {
        // The ring lines beside the bell while it swings.
        if (Math.abs(swing) < 0.05f) {
            return;
        }
        float side = swing > 0 ? -1 : 1;
        p.line(ox + (12 + side * 8.2f) * u, oy + 5 * u, ox + (12 + side * 9.6f) * u, oy + 6.4f * u, 1.5f * u, color);
        p.line(ox + (12 + side * 8.2f) * u, oy + 19 * u, ox + (12 + side * 9.6f) * u, oy + 17.6f * u, 1.5f * u,
                color);
    }

    private static void line(Painter p, float ox, float oy, float u, float x0, float y0, float x1, float y1,
                             float w, Color color) {
        p.line(ox + x0 * u, oy + y0 * u, ox + x1 * u, oy + y1 * u, w, color);
    }

    private static Color rgba(Color c, float alpha) {
        return Color.rgba(c.rgba() >>> 8, Math.max(0, Math.min(1, alpha)));
    }

    private IconMorph() {}
}
