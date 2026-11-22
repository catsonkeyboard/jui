package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's avatar, after the Avatar: a compact identity marker — a
 * circle with deterministic initials face, sizes from xsmall to xxlarge,
 * an optional presence dot, and a square variant for organizations. The
 * framework has no image decoder, so a name always renders its face.
 */
public final class Avatar {

    /** Extracts up to two leading letters for the fallback face. */
    public static String initials(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "?";
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return trimmed.substring(0, 1).toUpperCase() + (trimmed.length() > 1 ? trimmed.substring(1, 2) : "");
    }

    /** A deterministic pastel from the name, so the same person keeps their color. */
    public static Color tint(Context c, String name) {
        return face(name);
    }

    /** The hover lift of a face. */
    public static Color tintHover(Context c, String name) {
        return Kit.mix(face(name), Color.rgb(0x000000), 0.08f);
    }

    static Color face(String name) {
        int h = (name == null ? 0 : name.hashCode()) & 0xFFFFFF;
        float hue = (h % 360) / 360f;
        return hsl(hue, 0.45f, 0.55f);
    }

    static Color hsl(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = l - c / 2;
        float r = 0, g = 0, b = 0;
        int sextant = (int) (h * 6) % 6;
        switch (sextant) {
            case 0 -> { r = c; g = x; }
            case 1 -> { r = x; g = c; }
            case 2 -> { g = c; b = x; }
            case 3 -> { g = x; b = c; }
            case 4 -> { r = x; b = c; }
            default -> { r = c; b = x; }
        }
        return Color.rgba(((int) ((r + m) * 255) << 16) | ((int) ((g + m) * 255) << 8) | (int) ((b + m) * 255),
                0xFF);
    }

    /** The presence dot colors a status carries. */
    public static Color presence(String status) {
        return switch (status == null ? "online" : status) {
            case "away" -> Color.rgb(0xF76B15);
            case "busy", "dnd" -> Color.rgb(0xE5484D);
            case "offline" -> Color.rgb(0x8D8D8D);
            default -> Color.rgb(0x30A46C);
        };
    }

    /**
     * The avatar: name, size, an optional presence status ("online",
     * "away", "busy", "offline"; null for none), square for organizations.
     */
    public static Element avatar(Context c, String name, float size, String presence, boolean square) {
        String face = initials(name);
        float d = Math.max(6, size / 3.2f);
        Element a = box(c).size(size, size).radius(square ? size / 5 : size / 2).shrink(0)
                .background(tint(c, name))
                .label("kit-avatar-" + name)
                .alignItems(Align.CENTER)
                .children(() -> {
                    text(c, face).fontSize(size * 0.38f).textColor(Color.rgb(0xFFFFFF));
                    if (presence != null) {
                        box(c).absolute().size(d, d).radius(d / 2)
                                .left(size - d - 1).top(size - d - 1)
                                .background(presence(presence))
                                .border(Math.max(1, size / 16), c.theme().background());
                    }
                });
        return a;
    }

    public static Element avatar(Context c, String name, float size, boolean presence) {
        return avatar(c, name, size, presence ? "online" : null, false);
    }

    public static Element avatar(Context c, String name, float size) {
        return avatar(c, name, size, null, false);
    }

    private Avatar() {}
}
