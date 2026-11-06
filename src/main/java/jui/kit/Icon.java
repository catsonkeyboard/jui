package jui.kit;

import java.util.Map;
import java.util.function.BiConsumer;

import jui.ui.Color;
import jui.ui.Painter;
import jui.ui.Rect;

/**
 * The kit's monochrome icon set: every glyph is drawn with painter primitives
 * on a 16 unit grid at whatever size the box gives it, so icons stay crisp at
 * any scale and read on both looks through the color the caller picks.
 */
public final class Icon {
    private static final Map<String, BiConsumer<Painter, Rect>> GLYPHS = Map.ofEntries(
            entry("check", Icon::check),
            entry("xmark", Icon::xmark),
            entry("plus", Icon::plus),
            entry("minus", Icon::minus),
            entry("chevron-down", Icon::chevronDown),
            entry("chevron-up", Icon::chevronUp),
            entry("chevron-left", Icon::chevronLeft),
            entry("chevron-right", Icon::chevronRight),
            entry("magnifyingglass", Icon::magnifier),
            entry("gearshape", Icon::gear),
            entry("trash", Icon::trash),
            entry("folder", Icon::folder),
            entry("house", Icon::house),
            entry("person", Icon::person),
            entry("star", Icon::star),
            entry("heart", Icon::heart),
            entry("bookmark", Icon::bookmark),
            entry("clock", Icon::clock),
            entry("calendar", Icon::calendar),
            entry("paperplane", Icon::paperplane),
            entry("ellipsis", Icon::ellipsis),
            entry("arrow-up", (p, b) -> arrow(p, b, 0, -1)),
            entry("arrow-down", (p, b) -> arrow(p, b, 0, 1)),
            entry("arrow-left", (p, b) -> arrow(p, b, -1, 0)),
            entry("arrow-right", (p, b) -> arrow(p, b, 1, 0)),
            entry("play", Icon::play),
            entry("pause", Icon::pause),
            entry("download", Icon::download),
            entry("share", Icon::share),
            entry("exclamationmark-triangle", Icon::warning),
            entry("info-circle", Icon::info),
            entry("xmark-circle", Icon::xCircle),
            entry("doc", Icon::doc),
            entry("photo", Icon::photo),
            entry("bolt", Icon::bolt),
            entry("lock", Icon::lock),
            entry("bell", Icon::bell),
            entry("list-bullet", Icon::listBullet),
            entry("square-grid", Icon::grid),
            entry("circle", Icon::dot),
            entry("mic", Icon::mic));

    private static Map.Entry<String, BiConsumer<Painter, Rect>> entry(String n, BiConsumer<Painter, Rect> g) {
        return Map.entry(n, g);
    }

    /** Whether the name is in the set; unknown names draw a plain dot. */
    public static boolean known(String name) { return GLYPHS.containsKey(name); }

    /** Draws the named glyph in color, fitted inside the element's box. */
    public static jui.ui.Painter.Draw draw(String name, Color color) {
        return (p, b) -> {
            BiConsumer<Painter, Rect> g = GLYPHS.getOrDefault(name, Icon::dot);
            g.accept(p, b);
        };
    }

    // Glyphs are designed on a 16 unit square, u = one unit, inset 1 from the box.
    private static float u(Rect b) { return Math.min(b.width(), b.height()) / 16; }
    private static float ox(Rect b) { return b.x() + (b.width() - Math.min(b.width(), b.height())) / 2; }
    private static float oy(Rect b) { return b.y() + (b.height() - Math.min(b.width(), b.height())) / 2; }

    private static void stroke(Painter p, Rect b, float x0, float y0, float x1, float y1, float w, Color c) {
        float u = u(b);
        p.line(ox(b) + x0 * u, oy(b) + y0 * u, ox(b) + x1 * u, oy(b) + y1 * u, w * u, c);
    }

    private static void mic(Painter p, Rect b) {
        // a rounded capsule on a stand
        float u = u(b);
        p.roundedBox(ox(b) + 6 * u, oy(b) + 2.5f * u, 4 * u, 7 * u, 2 * u, Color.rgb(0xFFFFFF));
        p.line(ox(b) + 8 * u, oy(b) + 9.5f * u, ox(b) + 8 * u, oy(b) + 12 * u, 1.4f * u, Color.rgb(0xFFFFFF));
        p.line(ox(b) + 5.5f * u, oy(b) + 12 * u, ox(b) + 10.5f * u, oy(b) + 12 * u, 1.4f * u, Color.rgb(0xFFFFFF));
    }

    private static void dot(Painter p, Rect b) {
        p.circle(b.x() + b.width() / 2, b.y() + b.height() / 2, Math.min(b.width(), b.height()) / 2 - 1,
                Color.rgb(0x808080));
    }

    private static void check(Painter p, Rect b) {
        stroke(p, b, 3, 8.5f, 6.5f, 11.5f, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 6.5f, 11.5f, 13, 4.5f, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void xmark(Painter p, Rect b) {
        stroke(p, b, 4, 4, 12, 12, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 12, 4, 4, 12, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void plus(Painter p, Rect b) {
        stroke(p, b, 8, 3, 8, 13, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 3, 8, 13, 8, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void minus(Painter p, Rect b) {
        stroke(p, b, 3, 8, 13, 8, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void chevronDown(Painter p, Rect b) {
        stroke(p, b, 4, 6, 8, 10, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 10, 12, 6, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void chevronUp(Painter p, Rect b) {
        stroke(p, b, 4, 10, 8, 6, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 6, 12, 10, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void chevronLeft(Painter p, Rect b) {
        stroke(p, b, 10, 4, 6, 8, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 6, 8, 10, 12, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void chevronRight(Painter p, Rect b) {
        stroke(p, b, 6, 4, 10, 8, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 10, 8, 6, 12, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void magnifier(Painter p, Rect b) {
        p.circle(ox(b) + 7 * u(b), oy(b) + 7 * u(b), 4 * u(b), Color.rgb(0xFFFFFF));
        stroke(p, b, 10, 10, 13.5f, 13.5f, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void gear(Painter p, Rect b) {
        p.circle(ox(b) + 8 * u(b), oy(b) + 8 * u(b), 4.5f * u(b), Color.rgb(0xFFFFFF));
        p.circle(ox(b) + 8 * u(b), oy(b) + 8 * u(b), 1.8f * u(b), Color.rgb(0x808080));
        stroke(p, b, 8, 1.5f, 8, 4, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 12, 8, 14.5f, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 1.5f, 8, 4, 8, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 12, 8, 14.5f, 8, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void trash(Painter p, Rect b) {
        stroke(p, b, 3.5f, 4.5f, 12.5f, 4.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 6, 4.5f, 8, 2.8f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 5, 4.5f, 5.6f, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 11, 4.5f, 10.4f, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 5.6f, 13, 10.4f, 13, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void folder(Painter p, Rect b) {
        stroke(p, b, 2.5f, 12.5f, 2.5f, 4.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 2.5f, 4.5f, 6.5f, 4.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 6.5f, 4.5f, 8, 6, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 6, 13.5f, 6, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 13.5f, 6, 13.5f, 12.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 2.5f, 12.5f, 13.5f, 12.5f, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void house(Painter p, Rect b) {
        stroke(p, b, 2.5f, 8.5f, 8, 3, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 3, 13.5f, 8.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4, 7.5f, 4, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 12, 7.5f, 12, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4, 13, 12, 13, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void person(Painter p, Rect b) {
        p.circle(ox(b) + 8 * u(b), oy(b) + 5.5f * u(b), 2.8f * u(b), Color.rgb(0xFFFFFF));
        stroke(p, b, 3.5f, 13.5f, 5, 10, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 5, 10, 11, 10, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 11, 10, 12.5f, 13.5f, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void star(Painter p, Rect b) {
        float u = u(b), cx = ox(b) + 8 * u, cy = oy(b) + 8.6f * u;
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
            double a2 = a + Math.PI / 5;
            p.line(cx + (float) Math.cos(a) * 5.6f * u, cy + (float) Math.sin(a) * 5.6f * u,
                    cx + (float) Math.cos(a2) * 2.3f * u, cy + (float) Math.sin(a2) * 2.3f * u, 1.5f * u,
                    Color.rgb(0xFFFFFF));
            double a3 = a + 2 * Math.PI / 5;
            p.line(cx + (float) Math.cos(a2) * 2.3f * u, cy + (float) Math.sin(a2) * 2.3f * u,
                    cx + (float) Math.cos(a3) * 5.6f * u, cy + (float) Math.sin(a3) * 5.6f * u, 1.5f * u,
                    Color.rgb(0xFFFFFF));
        }
    }

    private static void heart(Painter p, Rect b) {
        float u = u(b);
        p.circle(ox(b) + 5.5f * u, oy(b) + 6.5f * u, 2.7f * u, Color.rgb(0xFFFFFF));
        p.circle(ox(b) + 10.5f * u, oy(b) + 6.5f * u, 2.7f * u, Color.rgb(0xFFFFFF));
        p.box(ox(b) + 3.5f * u, oy(b) + 6.5f * u, 9 * u, 2.7f * u, Color.rgb(0xFFFFFF));
        p.line(ox(b) + 3.5f * u, oy(b) + 8.5f * u, ox(b) + 8 * u, oy(b) + 13 * u, 1.8f * u, Color.rgb(0xFFFFFF));
        p.line(ox(b) + 12.5f * u, oy(b) + 8.5f * u, ox(b) + 8 * u, oy(b) + 13 * u, 1.8f * u, Color.rgb(0xFFFFFF));
    }

    private static void bookmark(Painter p, Rect b) {
        stroke(p, b, 4.5f, 2.8f, 4.5f, 13.2f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 11.5f, 2.8f, 11.5f, 13.2f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4.5f, 2.8f, 11.5f, 2.8f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4.5f, 13.2f, 8, 10.2f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 10.2f, 11.5f, 13.2f, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void clock(Painter p, Rect b) {
        p.circle(ox(b) + 8 * u(b), oy(b) + 8 * u(b), 5.8f * u(b), Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 4.6f, 8, 8.2f, 1.5f, Color.rgb(0x808080));
        stroke(p, b, 8, 8.2f, 10.8f, 9.8f, 1.5f, Color.rgb(0x808080));
    }

    private static void calendar(Painter p, Rect b) {
        stroke(p, b, 3, 5, 3, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 13, 5, 13, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 3, 5, 13, 5, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 3, 13, 13, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 5.5f, 2.5f, 5.5f, 5, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 10.5f, 2.5f, 10.5f, 5, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void paperplane(Painter p, Rect b) {
        stroke(p, b, 2.5f, 8, 13.5f, 3, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 13.5f, 3, 9.5f, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 9.5f, 13, 7.5f, 9.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 7.5f, 9.5f, 2.5f, 8, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 7.5f, 9.5f, 13.5f, 3, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void ellipsis(Painter p, Rect b) {
        float u = u(b);
        p.circle(ox(b) + 3.5f * u, oy(b) + 8 * u, 1.3f * u, Color.rgb(0xFFFFFF));
        p.circle(ox(b) + 8 * u, oy(b) + 8 * u, 1.3f * u, Color.rgb(0xFFFFFF));
        p.circle(ox(b) + 12.5f * u, oy(b) + 8 * u, 1.3f * u, Color.rgb(0xFFFFFF));
    }

    private static void arrow(Painter p, Rect b, float dx, float dy) {
        // one shaft plus a two-stroke head
        stroke(p, b, 8 - 5 * Math.abs(dy), 8 - 5 * Math.abs(dx), 8 + 4 * dx, 8 + 4 * dy, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8 + 4 * dx, 8 + 4 * dy, 8 + 4 * dx - 3 * Math.abs(dy) + 0 * dx, 8 + 4 * dy - 3 * Math.abs(dx), 1.8f,
                Color.rgb(0xFFFFFF));
        stroke(p, b, 8 + 4 * dx, 8 + 4 * dy, 8 + 4 * dx + 3 * Math.abs(dy), 8 + 4 * dy + 3 * Math.abs(dx), 1.8f,
                Color.rgb(0xFFFFFF));
    }

    private static void play(Painter p, Rect b) {
        stroke(p, b, 5.5f, 3.5f, 12, 8, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 12, 8, 5.5f, 12.5f, 1.8f, Color.rgb(0xFFFFFF));
        stroke(p, b, 5.5f, 12.5f, 5.5f, 3.5f, 1.8f, Color.rgb(0xFFFFFF));
    }

    private static void pause(Painter p, Rect b) {
        stroke(p, b, 5.5f, 3.5f, 5.5f, 12.5f, 2.2f, Color.rgb(0xFFFFFF));
        stroke(p, b, 10.5f, 3.5f, 10.5f, 12.5f, 2.2f, Color.rgb(0xFFFFFF));
    }

    private static void download(Painter p, Rect b) {
        stroke(p, b, 8, 3, 8, 9.5f, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 9.5f, 4.8f, 6.3f, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 9.5f, 11.2f, 6.3f, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 3, 12.5f, 13, 12.5f, 1.7f, Color.rgb(0xFFFFFF));
    }

    private static void share(Painter p, Rect b) {
        stroke(p, b, 8, 9.5f, 8, 3, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 3, 4.8f, 6.2f, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 3, 11.2f, 6.2f, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 3, 12.5f, 13, 12.5f, 1.7f, Color.rgb(0xFFFFFF));
    }

    private static void warning(Painter p, Rect b) {
        stroke(p, b, 8, 3, 14, 13, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 14, 13, 2, 13, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 2, 13, 8, 3, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 6.5f, 8, 9.8f, 1.7f, Color.rgb(0x808080));
        p.circle(ox(b) + 8 * u(b), oy(b) + 11.5f * u(b), 0.9f * u(b), Color.rgb(0x808080));
    }

    private static void info(Painter p, Rect b) {
        p.circle(ox(b) + 8 * u(b), oy(b) + 8 * u(b), 5.8f * u(b), Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 7.2f, 8, 11, 1.6f, Color.rgb(0x808080));
        p.circle(ox(b) + 8 * u(b), oy(b) + 5 * u(b), 0.9f * u(b), Color.rgb(0x808080));
    }

    private static void xCircle(Painter p, Rect b) {
        p.circle(ox(b) + 8 * u(b), oy(b) + 8 * u(b), 5.8f * u(b), Color.rgb(0xFFFFFF));
        stroke(p, b, 5.8f, 5.8f, 10.2f, 10.2f, 1.5f, Color.rgb(0x808080));
        stroke(p, b, 10.2f, 5.8f, 5.8f, 10.2f, 1.5f, Color.rgb(0x808080));
    }

    private static void doc(Painter p, Rect b) {
        stroke(p, b, 4, 2.5f, 4, 13.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4, 2.5f, 9.5f, 2.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 9.5f, 2.5f, 12, 5, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 12, 5, 12, 13.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4, 13.5f, 12, 13.5f, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void photo(Painter p, Rect b) {
        stroke(p, b, 2.5f, 3.5f, 2.5f, 12.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 13.5f, 3.5f, 13.5f, 12.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 2.5f, 3.5f, 13.5f, 3.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 2.5f, 12.5f, 13.5f, 12.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4.5f, 10.5f, 8, 6.5f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 6.5f, 10.5f, 9, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 9.5f, 8, 11.5f, 10.5f, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void bolt(Painter p, Rect b) {
        stroke(p, b, 9, 2.5f, 5, 8.5f, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 5, 8.5f, 9.5f, 8.5f, 1.7f, Color.rgb(0xFFFFFF));
        stroke(p, b, 9.5f, 8.5f, 6.5f, 13.5f, 1.7f, Color.rgb(0xFFFFFF));
    }

    private static void lock(Painter p, Rect b) {
        stroke(p, b, 5, 7, 5, 5, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 5, 5, 5.8f, 3.2f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 5.8f, 3.2f, 10.2f, 3.2f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 10.2f, 3.2f, 11, 5, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 11, 5, 11, 7, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4, 7, 12, 7, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4, 7, 4, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 12, 7, 12, 13, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4, 13, 12, 13, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void bell(Painter p, Rect b) {
        stroke(p, b, 4.5f, 11, 4.5f, 7, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4.5f, 7, 5.8f, 4.2f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 5.8f, 4.2f, 8, 3.2f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 8, 3.2f, 10.2f, 4.2f, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 10.2f, 4.2f, 11.5f, 7, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 11.5f, 7, 11.5f, 11, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 4.5f, 11, 11.5f, 11, 1.6f, Color.rgb(0xFFFFFF));
        stroke(p, b, 7, 13, 9, 13, 1.6f, Color.rgb(0xFFFFFF));
    }

    private static void listBullet(Painter p, Rect b) {
        p.circle(ox(b) + 3 * u(b), oy(b) + 4.5f * u(b), 1 * u(b), Color.rgb(0xFFFFFF));
        p.circle(ox(b) + 3 * u(b), oy(b) + 8 * u(b), 1 * u(b), Color.rgb(0xFFFFFF));
        p.circle(ox(b) + 3 * u(b), oy(b) + 11.5f * u(b), 1 * u(b), Color.rgb(0xFFFFFF));
        stroke(p, b, 6, 4.5f, 13, 4.5f, 1.5f, Color.rgb(0xFFFFFF));
        stroke(p, b, 6, 8, 13, 8, 1.5f, Color.rgb(0xFFFFFF));
        stroke(p, b, 6, 11.5f, 13, 11.5f, 1.5f, Color.rgb(0xFFFFFF));
    }

    private static void grid(Painter p, Rect b) {
        stroke(p, b, 2.5f, 2.5f, 6.5f, 2.5f, 3.4f, Color.rgb(0xFFFFFF));
        stroke(p, b, 9.5f, 2.5f, 13.5f, 2.5f, 3.4f, Color.rgb(0xFFFFFF));
        stroke(p, b, 2.5f, 9.5f, 6.5f, 9.5f, 3.4f, Color.rgb(0xFFFFFF));
        stroke(p, b, 9.5f, 9.5f, 13.5f, 9.5f, 3.4f, Color.rgb(0xFFFFFF));
    }

    private Icon() {}
}
