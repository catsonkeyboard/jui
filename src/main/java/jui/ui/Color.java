package jui.ui;

/**
 * An sRGB color with alpha, packed as 0xRRGGBBAA. Make colors once, as
 * constants or in a theme, rather than in every frame.
 */
public record Color(int rgba) {
    public static final Color TRANSPARENT = new Color(0);

    /** An opaque color from 0xRRGGBB. */
    public static Color rgb(int rgb) {
        return new Color((rgb << 8) | 0xFF);
    }

    /** A color from 0xRRGGBB and an alpha from 0 to 1. */
    public static Color rgba(int rgb, float alpha) {
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return new Color((rgb << 8) | a);
    }

    /** The color a share t (0 to 1) of the way to other, channel by channel, alpha too. */
    public Color mix(Color other, float t) {
        float k = Math.max(0, Math.min(1, t));
        int mixed = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int a = (rgba >>> shift) & 0xFF, b = (other.rgba >>> shift) & 0xFF;
            mixed |= Math.round(a + (b - a) * k) << shift;
        }
        return new Color(mixed);
    }

    public boolean transparent() {
        return (rgba & 0xFF) == 0;
    }
}
