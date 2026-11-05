package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's button, after gpui-kit's Button: nine variants, four sizes, an
 * outline mode combinable with any variant, and the loading / disabled /
 * selected / dropdown-caret states, with an optional leading icon.
 */
public final class Button {
    /** The one button builder: the overloads all funnel into this. */
    public static Element button(Context c, String label, Kit.Variant v, Kit.Size s, String icon,
                                 boolean outline, boolean loading, boolean disabled, boolean selected,
                                 boolean caret) {
        Kit.Tone tone = Kit.tone(c, v);
        float h = Kit.height(s);
        float px = Kit.padX(s);
        float fs = Kit.font(s);
        boolean plain = v == Kit.Variant.GHOST || v == Kit.Variant.LINK || v == Kit.Variant.TEXT;
        Color bg = plain ? (v == Kit.Variant.GHOST ? Color.rgba(0x808080, 0) : Color.rgba(0x808080, 0))
                : tone.fill();
        Element b = buttonBase(c).height(h).padding(0, plain && !outline ? 4 : px)
                .radius(plain && !outline ? Kit.RADIUS_XS : Kit.RADIUS_SM)
                .label("kit-btn-" + label)
                .justify(Justify.START)
                .gap(Kit.iconSize(s) / 3)
                .children(() -> {
                    if (loading) {
                        box(c).size(Kit.iconSize(s), Kit.iconSize(s)).draw(Spinner.draw(c));
                    } else if (icon != null) {
                        box(c).size(Kit.iconSize(s), Kit.iconSize(s)).draw(Icon.draw(icon, tone.text()));
                    }
                    text(c, label).fontSize(fs).textColor(v == Kit.Variant.LINK ? c.theme().accent() : tone.text());
                    if (caret) {
                        spacer(c).grow(1);
                        box(c).size(Kit.iconSize(s) * 3 / 4, Kit.iconSize(s) * 3 / 4)
                                .draw(Icon.draw("chevron-down", tone.text()));
                    }
                });
        if (selected) {
            b.background(plain ? tone.fillHover() : tone.fill());
        } else if (disabled) {
            b.background(Color.rgba(bg.rgba() >>> 8, 0.35f));
        } else if (!plain || outline) {
            b.background(bg);
        }
        if (outline) {
            b.border(1, plain || disabled ? Kit.line(c) : tone.fill());
        }
        if (!disabled && b.hovered()) {
            if (plain) {
                b.background(tone.fillHover());
            } else if (!loading) {
                b.background(tone.fillHover());
            }
        }
        return b;
    }

    public static Element button(Context c, String label) {
        return button(c, label, Kit.Variant.DEFAULT, Kit.Size.MEDIUM, null, false, false, false, false, false);
    }

    public static Element button(Context c, String label, Kit.Variant v) {
        return button(c, label, v, Kit.Size.MEDIUM, null, false, false, false, false, false);
    }

    public static Element button(Context c, String label, Kit.Variant v, Kit.Size s) {
        return button(c, label, v, s, null, false, false, false, false, false);
    }

    public static Element button(Context c, String label, Kit.Variant v, Kit.Size s, String icon) {
        return button(c, label, v, s, icon, false, false, false, false, false);
    }

    /** A row of buttons sharing one selected index, as gpui-kit's ButtonGroup. */
    public static Element buttonGroup(Context c, jui.ui.IntRef selected, String... labels) {
        return buttonGroup(c, selected, false, labels);
    }

    /** A group where allowOff lets the current pick toggle off (multiple-style). */
    public static Element buttonGroup(Context c, jui.ui.IntRef selected, boolean allowOff, String... labels) {
        return row(c).gap(2).children(() -> {
            for (int i = 0; i < labels.length; i++) {
                int idx = i;
                boolean on = selected.get() == idx;
                Element b = buttonBase(c).height(24).padding(0, 10).radius(Kit.RADIUS_XS)
                        .label("kit-group-" + labels[idx])
                        .justify(Justify.START)
                        .children(() -> text(c, labels[idx]).fontSize(11)
                                .textColor(on ? c.theme().accentText() : c.theme().textMuted()));
                if (on) b.background(c.theme().accent());
                else if (b.hovered()) b.background(c.theme().surfaceHover());
                if (b.clicked()) {
                    selected.set(on && allowOff ? -1 : idx);
                }
            }
        });
    }

    private Button() {}
}
