package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's avatar: a circle with the person's initials, a size, and an
 * optional presence dot — gpui-kit's Avatar without image loading, which
 * this framework has no decoder for.
 */
public final class Avatar {
    /** Extracts up to two leading letters for the fallback face. */
    static String initials(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "?";
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return trimmed.substring(0, 1).toUpperCase() + (trimmed.length() > 1 ? trimmed.substring(1, 2) : "");
    }

    /** A deterministic pastel from the name, so the same person keeps their color. */
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

    public static Element avatar(Context c, String name, float size, boolean presence) {
        String face = initials(name);
        float d = size / 3.2f;
        Element a = box(c).size(size, size).radius(size / 2).background(face(name))
                .label("kit-avatar-" + name)
                .alignItems(Align.CENTER)
                .children(() -> {
                    text(c, face).fontSize(size * 0.38f).textColor(Color.rgb(0xFFFFFF));
                    if (presence) {
                        box(c).absolute().size(d, d).radius(d / 2)
                                .left(size - d - 1).top(size - d - 1)
                                .background(Color.rgb(0x30D158))
                                .border(Math.max(1, size / 16), c.theme().background());
                    }
                });
        return a;
    }

    public static Element avatar(Context c, String name, float size) {
        return avatar(c, name, size, false);
    }

    private Avatar() {}
}
