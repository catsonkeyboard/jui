package jui.ui;

/**
 * The colors and sizes controls take by default; {@link #light()} and
 * {@link #dark()} are MyGO's LightTheme and DarkTheme (ui/theme.go).
 */
public record Theme(boolean isDark, Color background, Color surface, Color surfaceHover, Color surfacePressed,
                    Color border, Color text, Color textMuted, Color accent, Color accentHover, Color accentPressed,
                    Color accentText, Color selection, Color focusRing, float fontSize, float radius,
                    Color scrollbar, float scrollbarWidth) {

    private static final Theme LIGHT = new Theme(false,
            Color.rgb(0xFFFFFF), Color.rgb(0xF4F4F5), Color.rgb(0xE9E9EC), Color.rgb(0xDDDDE1),
            Color.rgb(0xD9D9DE), Color.rgb(0x18181B), Color.rgb(0x71717A),
            Color.rgb(0x2563EB), Color.rgb(0x1D4ED8), Color.rgb(0x1E40AF), Color.rgb(0xFFFFFF),
            Color.rgba(0x2563EB, 0.25f), Color.rgba(0x2563EB, 0.55f), 13, 6,
            Color.rgba(0x000000, 0.32f), 6);

    private static final Theme DARK = new Theme(true,
            Color.rgb(0x18181B), Color.rgb(0x27272A), Color.rgb(0x323236), Color.rgb(0x3C3C41),
            Color.rgb(0x3F3F46), Color.rgb(0xF4F4F5), Color.rgb(0xA1A1AA),
            Color.rgb(0x3B82F6), Color.rgb(0x60A5FA), Color.rgb(0x2563EB), Color.rgb(0xFFFFFF),
            Color.rgba(0x3B82F6, 0.4f), Color.rgba(0x60A5FA, 0.6f), 13, 6,
            Color.rgba(0xFFFFFF, 0.35f), 6);

    public static Theme light() {
        return LIGHT;
    }

    public static Theme dark() {
        return DARK;
    }

    /** The dark theme if dark, else the light one. */
    public static Theme of(boolean dark) {
        return dark ? DARK : LIGHT;
    }
}
