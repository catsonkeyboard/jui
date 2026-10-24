package jui.ui;

/** The colors and sizes controls take by default. */
public record Theme(Color background, Color surface, Color surfaceHover, Color border, Color text,
                    Color textMuted, Color accent, Color accentText, Color selection, Color focusRing,
                    float fontSize, float radius) {

    private static final Theme LIGHT = new Theme(
            Color.rgb(0xFFFFFF), Color.rgb(0xF3F4F6), Color.rgb(0xE5E7EB), Color.rgb(0xE5E7EB),
            Color.rgb(0x111827), Color.rgb(0x6B7280), Color.rgb(0x2563EB), Color.rgb(0xFFFFFF),
            Color.rgb(0xBFDBFE), Color.rgb(0x3B82F6), 13, 6);

    public static Theme light() {
        return LIGHT;
    }
}
