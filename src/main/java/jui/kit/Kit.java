package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import java.time.Duration;

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


    // ---- Motion and surface tokens ------------------

    /** Presses, highlights: the shortest run that still reads. */
    public static final Duration SNAPPY = Duration.ofMillis(140);
    /** Widths and label morphs: a touch longer so the change reads. */
    public static final Duration MORPH = Duration.ofMillis(260);
    /** Panels, menus, disclosures. */
    public static final Duration SMOOTH = Duration.ofMillis(200);
    /** Fades: crosses read best short. */
    public static final Duration FADE = Duration.ofMillis(120);
    /** Set by a host that wants every kit motion instant. */
    public static volatile boolean reducedMotion = false;

    /** The duration to use for a preset, honoring reduced motion. */
    public static Duration d(Duration preset) { return reducedMotion ? Duration.ZERO : preset; }

    /**
     * A spring run toward target on the element's named channel, the preset
     * mapping to stiffness/damping: morph and smooth keep a little
     * overshoot, snappy stays tight, fade stays a plain tween. Reversing
     * mid-flight keeps the velocity, so widths glide through instead of
     * restarting.
     */
    public static float spring(Element e, String key, float target, Duration preset) {
        if (reducedMotion || preset == FADE) {
            return e.animate(key, target, d(preset));
        }
        if (preset == SNAPPY) {
            return e.spring(key, target, 320, 26);
        }
        if (preset == MORPH) {
            return e.spring(key, target, 170, 20);
        }
        return e.spring(key, target, 120, 24);               // smooth
    }

    /** 1 while the element is pressed, eased; for press dips and fills. */
    public static float press(Element e) {
        return spring(e, "kit-press", e.pressed() ? 1 : 0, SNAPPY);
    }

    /** The hairline most kit surfaces separate with. */
    public static Color hairline(Context c) {
        return c.theme().border();
    }

    /** The quiet surface kit controls sit on. */
    public static Color surface(Context c) { return c.theme().surface(); }

    /** The floating shadow, for trays and menus over content. */
    public static void floatShadow(Element e) {
        e.shadow(0, 8, 24, -4, Color.rgba(0x000000, 0.18f));
    }

    /** The soft grounded shadow under cards. */
    public static void cardShadow(Element e) {
        e.shadow(0, 1, 2, 0, Color.rgba(0x000000, 0.06f));
        e.shadow(0, 4, 12, -2, Color.rgba(0x000000, 0.08f));
    }

    /** A mix of t of the way from x to y, per channel, as 0xRRGGBBAA. */
    public static int mix(int x, int y, float t) {
        int mixed = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int a = (x >>> shift) & 0xFF, b = (y >>> shift) & 0xFF;
            mixed |= Math.round(a + (b - a) * t) << shift;
        }
        return mixed;
    }

    public static Color mix(Color x, Color y, float t) {
        int v = mix(x.rgba(), y.rgba(), t);
        return Color.rgba(v >>> 8, (v & 255) / 255f);
    }

    /** The danger tone destructive rows and answers carry. */
    public static Color danger(Context c) { return tone(c, Kit.Variant.DANGER).fill(); }

    /** The success tone confirmations settle on. */
    public static Color success(Context c) { return tone(c, Kit.Variant.SUCCESS).fill(); }

    /** The warning tone. */
    public static Color warning(Context c) { return tone(c, Kit.Variant.WARNING).fill(); }
}
