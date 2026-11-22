package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

/**
 * Shared face pieces for the kit button family: the fill and text per
 * variant, and half-rounded bases for joined surfaces such as the split
 * button and the button group.
 */
public final class ButtonBase {

    /** A face's fill on this look. */
    public static Color faceFill(Context c, Button.Variant v) {
        return Button.tone(c, v).fill();
    }

    /** A face's hover fill. */
    public static Color faceHover(Context c, Button.Variant v) {
        return Button.tone(c, v).fillHover();
    }

    /** A face's readable text. */
    public static Color faceText(Context c, Button.Variant v) {
        return Button.tone(c, v).text();
    }

    /**
     * A base for one half of a joined surface: a buttonBase with the
     * variant's fill and radius only on its outer corners. {@code right}
     * shapes the right half; pressing deepens like a full face.
     */
    public static Element half(Context c, Button.Variant v, boolean disabled, boolean right) {
        Element e = jui.ui.Ui.buttonBase(c);
        if (disabled) {
            e.disabled(true);
        }
        if (v != Button.Variant.GHOST) {
            Color fill = faceFill(c, v);
            e.background(disabled ? Color.rgba(fill.rgba() >>> 8, 0.4f) : fill);
        }
        e.radius(right ? 8 : 0, right ? 0 : 8, right ? 0 : 8, right ? 8 : 0);
        return e;
    }

    /** A quiet soft button for floating trays: rounded, surface fill, hover. */
    public static Element soft(Context c, boolean floating) {
        Element e = jui.ui.Ui.buttonBase(c).justify(Justify.CENTER);
        if (floating) {
            e.background(c.theme().background());
        } else {
            e.background(c.theme().surface());
        }
        return e;
    }

    private ButtonBase() {}
}
