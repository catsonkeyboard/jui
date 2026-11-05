package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.ui.Color;
import jui.ui.Painter;
import jui.ui.Rect;

/** Every icon the app draws itself, at 18 or 48 DIP, from fixed colors that read on both looks. */
final class FileIcons {
    private static final Color BLUE = Color.rgb(0x3E6FF2), PURPLE = Color.rgb(0x8B5CF6),
            TAB = Color.rgb(0x2F57C9), WHITE = Color.rgb(0xFFFFFF), GREY = Color.rgb(0xB9BDC4),
            DARKGREY = Color.rgb(0x6B7078), RED = Color.rgb(0xE5484D), GREEN = Color.rgb(0x30A46C),
            ORANGE = Color.rgb(0xF76B15), CYAN = Color.rgb(0x00A2C7), TEAL = Color.rgb(0x12A594);

    static Painter.Draw draw(FileEntry.IconType type, boolean large) {
        return switch (type) {
            case FOLDER -> FileIcons::folder;
            case DMG -> FileIcons::dmg;
            case APK -> FileIcons::apk;
            case APP -> FileIcons::app;
            case PDF -> (p, b) -> badge(p, b, "PDF", RED);
            case DOC -> (p, b) -> badge(p, b, "W", BLUE);
            case IMAGE -> FileIcons::image;
            case VIDEO -> FileIcons::video;
            case AUDIO -> FileIcons::audio;
            case ARCHIVE -> FileIcons::archive;
            case MODEL3D -> FileIcons::model3d;
            case CODE -> (p, b) -> badge(p, b, "{}", DARKGREY);
            case TEXT -> FileIcons::text;
            case GENERIC -> FileIcons::generic;
        };
    }

    /** The grid: every icon is designed on 18 units, scaled into whatever box it gets. */
    private static float u(Rect b) { return Math.min(b.width(), b.height()) / 18; }
    private static float x0(Rect b) { return b.x() + (b.width() - Math.min(b.width(), b.height())) / 2; }
    private static float y0(Rect b) { return b.y() + (b.height() - Math.min(b.width(), b.height())) / 2; }

    static void folder(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + u, y + 3 * u, 16 * u, 11 * u, 2 * u, PURPLE);           // back
        p.roundedBox(x, y + 5 * u, 18 * u, 12 * u, 2 * u, BLUE);                 // front, over it
        p.roundedBox(x, y + 5 * u, 7 * u, 3 * u, 1.5f * u, TAB);                 // tab
    }

    static void dmg(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + u, y + 3 * u, 16 * u, 13 * u, 2.5f * u, GREY);
        p.circle(x + 9 * u, y + 9.5f * u, 4 * u, WHITE);
        p.circle(x + 9 * u, y + 9.5f * u, 1.2f * u, DARKGREY);
    }

    static void apk(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + u, y + 2 * u, 16 * u, 14 * u, 3 * u, GREEN);
        p.line(x + 6 * u, y + 6 * u, x + 5 * u, y + 3.5f * u, u, GREEN);         // antennae
        p.line(x + 12 * u, y + 6 * u, x + 13 * u, y + 3.5f * u, u, GREEN);
        p.circle(x + 6.5f * u, y + 9 * u, 1.2f * u, WHITE);                      // eyes
        p.circle(x + 11.5f * u, y + 9 * u, 1.2f * u, WHITE);
        p.box(x + 5.5f * u, y + 11 * u, 7 * u, 3.5f * u, WHITE);                 // body
    }

    static void app(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + u, y + 3 * u, 16 * u, 13 * u, 3 * u, BLUE);
        p.circle(x + 9 * u, y + 9.5f * u, 4 * u, WHITE);
    }

    /** A colored tile with up to three letters on it. */
    static void badge(Painter p, Rect b, String label, Color bg) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + u, y + 3 * u, 16 * u, 13 * u, 2.5f * u, bg);
        jui.text.TextLayout l = p.text(label, x, y, 9 * u, 700, WHITE, 14 * u);
        p.text(l, x + 9 * u - l.width / 2, y + 9.5f * u - 4.5f * u, WHITE);
    }

    static void image(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + u, y + 3 * u, 16 * u, 13 * u, 2.5f * u, CYAN);
        p.circle(x + 5.5f * u, y + 7 * u, 1.3f * u, WHITE);                      // sun
        p.line(x + 3.5f * u, y + 13 * u, x + 8 * u, y + 8 * u, 1.6f * u, WHITE); // mountain
        p.line(x + 8 * u, y + 8 * u, x + 11 * u, y + 11 * u, 1.6f * u, WHITE);
        p.line(x + 10 * u, y + 13 * u, x + 13 * u, y + 9 * u, 1.6f * u, WHITE);
    }

    static void video(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + u, y + 3 * u, 16 * u, 13 * u, 2.5f * u, PURPLE);
        p.line(x + 7 * u, y + 6.5f * u, x + 12.5f * u, y + 9.5f * u, 1.8f * u, WHITE);  // a play triangle
        p.line(x + 12.5f * u, y + 9.5f * u, x + 7 * u, y + 12.5f * u, 1.8f * u, WHITE);
        p.line(x + 7 * u, y + 12.5f * u, x + 7 * u, y + 6.5f * u, 1.8f * u, WHITE);
    }

    static void audio(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + u, y + 3 * u, 16 * u, 13 * u, 2.5f * u, ORANGE);
        p.circle(x + 6.5f * u, y + 12 * u, 2 * u, WHITE);                        // note head
        p.line(x + 8.5f * u, y + 12 * u, x + 8.5f * u, y + 6 * u, 1.6f * u, WHITE);     // stem
        p.line(x + 8.5f * u, y + 6 * u, x + 12.5f * u, y + 7.5f * u, 1.6f * u, WHITE);  // flag
    }

    static void archive(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + u, y + 3 * u, 16 * u, 13 * u, 2.5f * u, GREY);
        p.box(x + 8 * u, y + 4 * u, 2 * u, 11 * u, DARKGREY);                    // the zipper
        p.circle(x + 9 * u, y + 7 * u, 0.8f * u, WHITE);
        p.circle(x + 9 * u, y + 10 * u, 0.8f * u, WHITE);
        p.circle(x + 9 * u, y + 13 * u, 0.8f * u, WHITE);
    }

    static void model3d(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        float cx = x + 9 * u, top = y + 3.5f * u, bot = y + 13.5f * u, hw = 4.5f * u, skew = 3 * u;
        p.line(cx - hw, top + 3 * u, cx + hw, top + 3 * u, 1.4f * u, TEAL);      // top face
        p.line(cx - hw, top + 3 * u, cx, top, 1.4f * u, TEAL);
        p.line(cx + hw, top + 3 * u, cx, top, 1.4f * u, TEAL);
        p.line(cx - hw, top + 3 * u, cx - skew, bot, 1.4f * u, TEAL);            // sides
        p.line(cx + hw, top + 3 * u, cx + skew, bot, 1.4f * u, TEAL);
        p.line(cx - skew, bot, cx + skew, bot, 1.4f * u, TEAL);                  // bottom
        p.line(cx, top + 3 * u, cx, bot, 1.4f * u, TEAL);                        // middle edge
    }

    static void text(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + 2.5f * u, y + 2 * u, 13 * u, 15 * u, 1.5f * u, WHITE);
        p.line(x + 5 * u, y + 6 * u, x + 12.5f * u, y + 6 * u, u, GREY);
        p.line(x + 5 * u, y + 9 * u, x + 12.5f * u, y + 9 * u, u, GREY);
        p.line(x + 5 * u, y + 12 * u, x + 10 * u, y + 12 * u, u, GREY);
    }

    static void generic(Painter p, Rect b) {
        float u = u(b), x = x0(b), y = y0(b);
        p.roundedBox(x + 2.5f * u, y + 2 * u, 13 * u, 15 * u, 1.5f * u, GREY);
        p.line(x + 9 * u, y + 2 * u, x + 15.5f * u, y + 8 * u, 3 * u, GREY);     // folded corner
        p.line(x + 5 * u, y + 11 * u, x + 12.5f * u, y + 11 * u, u, WHITE);
        p.line(x + 5 * u, y + 14 * u, x + 10 * u, y + 14 * u, u, WHITE);
    }

    // The sidebar's 16 DIP monochrome marks -------------------------------------

    static Painter.Draw mini(String kind, Color color) {
        return switch (kind) {
            case "home" -> (p, b) -> house(p, b, color);
            case "desktop" -> (p, b) -> monitor(p, b, color);
            case "downloads" -> (p, b) -> download(p, b, color);
            case "docs" -> (p, b) -> page(p, b, color);
            case "pictures" -> (p, b) -> mountain(p, b, color);
            case "music" -> (p, b) -> note(p, b, color);
            case "movies" -> (p, b) -> film(p, b, color);
            case "apps" -> (p, b) -> grid4(p, b, color);
            case "trash" -> (p, b) -> trash(p, b, color);
            case "disk" -> (p, b) -> disk(p, b, color);
            case "cloud" -> (p, b) -> cloud(p, b, color);
            case "tag" -> (p, b) -> p.circle(b.x() + b.width() / 2, b.y() + b.height() / 2, b.width() / 3, color);
            default -> (p, b) -> p.circle(b.x() + b.width() / 2, b.y() + b.height() / 2, 3, color);
        };
    }

    private static void house(Painter p, Rect b, Color col) {
        p.line(b.x() + 1, b.y() + b.height() / 2, b.x() + b.width() / 2, b.y() + 2, 1.5f, col);
        p.line(b.x() + b.width() / 2, b.y() + 2, b.x() + b.width() - 1, b.y() + b.height() / 2, 1.5f, col);
        p.line(b.x() + 3, b.y() + b.height() / 2 - 1, b.x() + 3, b.y() + b.height() - 1, 1.5f, col);
        p.line(b.x() + b.width() - 3, b.y() + b.height() / 2 - 1, b.x() + b.width() - 3, b.y() + b.height() - 1, 1.5f, col);
        p.line(b.x() + 3, b.y() + b.height() - 1, b.x() + b.width() - 3, b.y() + b.height() - 1, 1.5f, col);
    }

    private static void monitor(Painter p, Rect b, Color col) {
        p.line(b.x() + 1, b.y() + 2, b.x() + b.width() - 1, b.y() + 2, 1.5f, col);
        p.line(b.x() + 1, b.y() + b.height() - 4, b.x() + b.width() - 1, b.y() + b.height() - 4, 1.5f, col);
        p.line(b.x() + 1, b.y() + 2, b.x() + 1, b.y() + b.height() - 4, 1.5f, col);
        p.line(b.x() + b.width() - 1, b.y() + 2, b.x() + b.width() - 1, b.y() + b.height() - 4, 1.5f, col);
        p.line(b.x() + b.width() / 2, b.y() + b.height() - 4, b.x() + b.width() / 2, b.y() + b.height() - 1, 1.5f, col);
        p.line(b.x() + b.width() / 2 - 2, b.y() + b.height() - 1, b.x() + b.width() / 2 + 2, b.y() + b.height() - 1, 1.5f, col);
    }

    private static void download(Painter p, Rect b, Color col) {
        p.line(b.x() + b.width() / 2, b.y() + 1, b.x() + b.width() / 2, b.y() + b.height() - 5, 1.5f, col);
        p.line(b.x() + b.width() / 2, b.y() + b.height() - 5, b.x() + b.width() / 2 - 3, b.y() + b.height() - 8, 1.5f, col);
        p.line(b.x() + b.width() / 2, b.y() + b.height() - 5, b.x() + b.width() / 2 + 3, b.y() + b.height() - 8, 1.5f, col);
        p.line(b.x() + 1, b.y() + b.height() - 1, b.x() + b.width() - 1, b.y() + b.height() - 1, 1.5f, col);
    }

    private static void page(Painter p, Rect b, Color col) {
        p.line(b.x() + 3, b.y() + 1, b.x() + 3, b.y() + b.height() - 1, 1.5f, col);
        p.line(b.x() + b.width() - 3, b.y() + 1, b.x() + b.width() - 3, b.y() + b.height() - 1, 1.5f, col);
        p.line(b.x() + 3, b.y() + 1, b.x() + b.width() - 3, b.y() + 1, 1.5f, col);
        p.line(b.x() + 3, b.y() + b.height() - 1, b.x() + b.width() - 3, b.y() + b.height() - 1, 1.5f, col);
        p.line(b.x() + 6, b.y() + b.height() / 2, b.x() + b.width() - 6, b.y() + b.height() / 2, 1.2f, col);
    }

    private static void mountain(Painter p, Rect b, Color col) {
        p.line(b.x() + 1, b.y() + b.height() - 3, b.x() + b.width() / 2 - 1, b.y() + 3, 1.5f, col);
        p.line(b.x() + b.width() / 2 - 1, b.y() + 3, b.x() + b.width() / 2 + 3, b.y() + b.height() - 3, 1.5f, col);
        p.line(b.x() + b.width() / 2, b.y() + b.height() - 3, b.x() + b.width() - 2, b.y() + 6, 1.5f, col);
        p.line(b.x() + 1, b.y() + b.height() - 3, b.x() + b.width() - 2, b.y() + b.height() - 3, 1.5f, col);
    }

    private static void note(Painter p, Rect b, Color col) {
        p.circle(b.x() + b.width() / 2 - 2, b.y() + b.height() - 4, 2.4f, col);
        p.line(b.x() + b.width() / 2, b.y() + b.height() - 4, b.x() + b.width() / 2, b.y() + 2, 1.4f, col);
        p.line(b.x() + b.width() / 2, b.y() + 2, b.x() + b.width() - 3, b.y() + 4, 1.4f, col);
    }

    private static void film(Painter p, Rect b, Color col) {
        p.line(b.x() + 1, b.y() + 2, b.x() + b.width() - 1, b.y() + 2, 1.5f, col);
        p.line(b.x() + 1, b.y() + b.height() - 2, b.x() + b.width() - 1, b.y() + b.height() - 2, 1.5f, col);
        p.line(b.x() + 1, b.y() + 2, b.x() + 1, b.y() + b.height() - 2, 1.5f, col);
        p.line(b.x() + b.width() - 1, b.y() + 2, b.x() + b.width() - 1, b.y() + b.height() - 2, 1.5f, col);
        p.line(b.x() + b.width() / 2, b.y() + 2, b.x() + b.width() / 2, b.y() + b.height() - 2, 1.2f, col);
        p.line(b.x() + 3, b.y() + b.height() / 2, b.x() + b.width() - 3, b.y() + b.height() / 2, 1.2f, col);
    }

    private static void grid4(Painter p, Rect b, Color col) {
        p.box(b.x() + 1, b.y() + 1, b.width() / 2 - 1.5f, b.height() / 2 - 1.5f, col);
        p.box(b.x() + b.width() / 2 + 1, b.y() + 1, b.width() / 2 - 1.5f, b.height() / 2 - 1.5f, col);
        p.box(b.x() + 1, b.y() + b.height() / 2 + 1, b.width() / 2 - 1.5f, b.height() / 2 - 1.5f, col);
        p.box(b.x() + b.width() / 2 + 1, b.y() + b.height() / 2 + 1, b.width() / 2 - 1.5f, b.height() / 2 - 1.5f, col);
    }

    private static void trash(Painter p, Rect b, Color col) {
        p.line(b.x() + 2, b.y() + 2, b.x() + b.width() - 2, b.y() + 2, 1.5f, col);             // lid
        p.line(b.x() + b.width() / 2 - 2, b.y() + 1, b.x() + b.width() / 2 + 2, b.y() + 1, 1.5f, col);
        p.line(b.x() + 3, b.y() + 4, b.x() + b.width() / 2, b.y() + b.height() - 1, 1.5f, col);       // can
        p.line(b.x() + b.width() - 3, b.y() + 4, b.x() + b.width() / 2, b.y() + b.height() - 1, 1.5f, col);
        p.line(b.x() + 3, b.y() + 4, b.x() + b.width() - 3, b.y() + 4, 1.5f, col);
    }

    private static void disk(Painter p, Rect b, Color col) {
        p.circle(b.x() + b.width() / 2, b.y() + b.height() / 2, b.width() / 2 - 1, col);
        p.circle(b.x() + b.width() / 2, b.y() + b.height() / 2, b.width() / 6, WHITE);
    }

    private static void cloud(Painter p, Rect b, Color col) {
        p.circle(b.x() + b.width() / 3, b.y() + b.height() / 2 + 1, 2.6f, col);
        p.circle(b.x() + b.width() / 2 + 1, b.y() + b.height() / 2 - 1, 3.2f, col);
        p.circle(b.x() + 2 * b.width() / 3 + 1, b.y() + b.height() / 2 + 1, 2.4f, col);
        p.box(b.x() + b.width() / 3 - 2, b.y() + b.height() / 2 + 1, 2 * b.width() / 3 - 2, 2.4f, col);
    }

    private FileIcons() {}
}
