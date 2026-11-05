package jui.kit;

import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's label: text with a size and a variant, after gpui-kit's Label —
 * the same text stack, but the variant decides the ink.
 */
public final class Label {
    /** The ink a label carries. */
    public enum Variant { DEFAULT, SECONDARY, MUTED, DANGER, WARNING, SUCCESS, INFO, PRIMARY }

    public static Element label(Context c, String text, Kit.Size size, Variant v) {
        jui.ui.Color ink = switch (v) {
            case DEFAULT -> c.theme().text();
            case SECONDARY, MUTED -> c.theme().textMuted();
            case PRIMARY -> c.theme().accent();
            default -> Kit.tone(c, toTone(v)).fill();
        };
        return text(c, text).fontSize(Kit.font(size)).textColor(ink).label("kit-label-" + text);
    }

    public static Element label(Context c, String text) {
        return label(c, text, Kit.Size.MEDIUM, Variant.DEFAULT);
    }

    public static Element label(Context c, String text, Kit.Size size) {
        return label(c, text, size, Variant.DEFAULT);
    }

    static Kit.Variant toTone(Variant v) {
        return switch (v) {
            case DANGER -> Kit.Variant.DANGER;
            case WARNING -> Kit.Variant.WARNING;
            case SUCCESS -> Kit.Variant.SUCCESS;
            case INFO -> Kit.Variant.INFO;
            default -> Kit.Variant.PRIMARY;
        };
    }

    private Label() {}
}
