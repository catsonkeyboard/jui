package jui.kit;

import java.time.Duration;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

/**
 * Shared motion and surface tokens for the jui-kit series, after uiarc.dev's
 * motion presets: snappy for presses, morph for width and label changes,
 * smooth for panels. The engine eases with one cubic out curve, so the
 * "springs" here are eased runs the caller reads with {@link #spring}.
 * Reduced motion folds to {@link #MOTION_OFF} for hosts that set it.
 */
public final class KitMotion {
    /** Presses, highlights: the shortest run that still reads. */
    public static final Duration SNAPPY = Duration.ofMillis(140);
    /** Widths and label morphs: a touch longer so the change reads. */
    public static final Duration MORPH = Duration.ofMillis(260);
    /** Panels, menus, disclosures. */
    public static final Duration SMOOTH = Duration.ofMillis(200);
    /** Fades: crosses read best short. */
    public static final Duration FADE = Duration.ofMillis(120);
    /** Set by a host that wants every jui-kit motion instant. */
    public static volatile boolean reducedMotion = false;

    /** The duration to use for a preset, honoring reduced motion. */
    public static Duration d(Duration preset) { return reducedMotion ? Duration.ZERO : preset; }

    /** An eased run toward target on the element's named channel. */
    public static float spring(Element e, String key, float target, Duration preset) {
        return e.animate(key, target, d(preset));
    }

    /** 1 while the element is pressed, eased; for press dips and fills. */
    public static float press(Element e) {
        return spring(e, "kit-press", e.pressed() ? 1 : 0, SNAPPY);
    }

    /** The hairline most jui-kit surfaces separate with. */
    public static Color hairline(Context c) {
        return c.theme().border();
    }

    /** The quiet surface jui-kit controls sit on. */
    public static Color surface(Context c) { return c.theme().surface(); }

    /** the floating shadow, for trays and menus over content. */
    public static void floatShadow(Element e) {
        e.shadow(0, 8, 24, -4, Color.rgba(0x000000, 0.18f));
    }

    /** The soft grounded shadow under cards. */
    public static void cardShadow(Element e) {
        e.shadow(0, 1, 2, 0, Color.rgba(0x000000, 0.06f));
        e.shadow(0, 4, 12, -2, Color.rgba(0x000000, 0.08f));
    }

    /** a mixed t of the way from x to y, per channel, as 0xRRGGBBAA. */
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

    /** The danger tone jui-kit marks destructive rows and answers with. */
    public static Color danger(Context c) { return Kit.tone(c, Kit.Variant.DANGER).fill(); }

    /** The success tone confirmations settle on. */
    public static Color success(Context c) { return Kit.tone(c, Kit.Variant.SUCCESS).fill(); }

    /** The warning tone. */
    public static Color warning(Context c) { return Kit.tone(c, Kit.Variant.WARNING).fill(); }

    private jui-kit() {}
}
