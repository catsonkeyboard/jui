package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Theme;

/**
 * Shared design tokens for the kit: the gpui-kit-style variant and size enums,
 * and the tone colors each variant resolves to on the current look.
 */
public final class Kit {
    /** How a control reads: the flavor of its fill and text. */
    public enum Variant { DEFAULT, PRIMARY, DANGER, WARNING, SUCCESS, INFO, GHOST, LINK, TEXT }

    /** Step of the size scale most kit controls accept. */
    public enum Size { XSMALL, SMALL, MEDIUM, LARGE }

    // The size scale (heights in DIP, matching gpui-kit's xsmall..large rhythm).
    public static float height(Size s) {
        return switch (s) { case XSMALL -> 20; case SMALL -> 24; case MEDIUM -> 30; case LARGE -> 36; };
    }

    public static float padX(Size s) {
        return switch (s) { case XSMALL -> 8; case SMALL -> 10; case MEDIUM -> 12; case LARGE -> 14; };
    }

    public static float font(Size s) {
        return switch (s) { case XSMALL -> 10.5f; case SMALL -> 11.5f; case MEDIUM -> 12.5f; case LARGE -> 13.5f; };
    }

    public static float iconSize(Size s) {
        return switch (s) { case XSMALL -> 12; case SMALL -> 14; case MEDIUM -> 16; case LARGE -> 18; };
    }

    public static final float RADIUS = 8, RADIUS_SM = 6, RADIUS_XS = 4;

    /** The control tint: fill, its hover, and the readable text on it. */
    public record Tone(Color fill, Color fillHover, Color text) {}

    /** Resolves a variant against the window's current look. */
    public static Tone tone(Context c, Variant v) {
        Theme t = c.theme();
        boolean dark = t.isDark();
        return switch (v) {
            case PRIMARY -> new Tone(t.accent(), t.accentHover(), t.accentText());
            case DANGER -> new Tone(rgb(dark, 0xB32D33, 0xE5484D), rgb(dark, 0xC24145, 0xF0616B),
                    Color.rgb(0xFFFFFF));
            case WARNING -> new Tone(rgb(dark, 0xB4590F, 0xF76B15), rgb(dark, 0xC66A1C, 0xFF8433),
                    Color.rgb(0xFFFFFF));
            case SUCCESS -> new Tone(rgb(dark, 0x26805B, 0x30A46C), rgb(dark, 0x2D9068, 0x3DD68C),
                    Color.rgb(0xFFFFFF));
            case INFO -> new Tone(rgb(dark, 0x2563EB, 0x3B82F6), rgb(dark, 0x2E6FD8, 0x5A97F8),
                    Color.rgb(0xFFFFFF));
            case GHOST, LINK, TEXT -> new Tone(Color.rgba(0x808080, 0), Color.rgba(0x808080, 0x12),
                    t.text());
            case DEFAULT -> new Tone(t.surface(), t.surfaceHover(), t.text());
        };
    }

    /** The muted outline most bordered controls use. */
    public static Color line(Context c) {
        return c.theme().border();
    }

    private static Color rgb(boolean dark, int light, int darkHex) {
        return Color.rgb(dark ? darkHex : light);
    }

    private Kit() {}
}
