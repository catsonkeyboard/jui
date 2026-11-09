package jui.lobe;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Theme;

/** Entry point for lobe-jui. Apply once at the beginning of each window view. */
public final class Lobe {
    public enum Size {
        SMALL(24, 8, 12, 6), MIDDLE(32, 12, 14, 8), LARGE(40, 16, 16, 12);
        public final float height, padding, font, radius;
        Size(float height, float padding, float font, float radius) {
            this.height = height; this.padding = padding; this.font = font; this.radius = radius;
        }
    }
    public enum Variant { DEFAULT, PRIMARY, DASHED, FILLED, TEXT, LINK }
    public enum Status { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }

    private static final Theme LIGHT = theme(Tokens.light()), DARK = theme(Tokens.dark());
    public static Theme light() { return LIGHT; }
    public static Theme dark() { return DARK; }
    public static void apply(Context c) { c.setThemePair(LIGHT, DARK); }

    private static Theme theme(Tokens t) {
        return new Theme(t.dark, t.bgContainer, t.bgContainer, t.bgTextHover, t.bgTextActive,
                t.border, t.text, t.textTertiary, t.primary, t.primaryHover, t.primaryActive,
                t.dark ? t.bgBase : t.white, t.primaryFillTertiary, t.primaryFill,
                14, 8, t.fill, 4);
    }

    public static Color color(Context c, Status status) {
        Tokens t = Tokens.of(c);
        return switch (status) {
            case NEUTRAL -> t.textSecondary; case INFO -> t.infoText;
            case SUCCESS -> t.successText; case WARNING -> t.warningText; case ERROR -> t.errorText;
        };
    }
    public static Color background(Context c, Status status) {
        Tokens t = Tokens.of(c);
        return switch (status) {
            case NEUTRAL -> t.fillTertiary; case INFO -> t.infoBg;
            case SUCCESS -> t.successBg; case WARNING -> t.warningBg; case ERROR -> t.errorBg;
        };
    }
    private Lobe() {}
}
