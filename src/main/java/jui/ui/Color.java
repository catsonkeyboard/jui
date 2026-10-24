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

    public boolean transparent() {
        return (rgba & 0xFF) == 0;
    }
}
