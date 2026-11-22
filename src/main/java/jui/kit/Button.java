package jui.kit;

import java.time.Duration;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's button, after the Button (uiarc.dev/components/button): one
 * primary action per surface, quiet secondary states, press dip, hover fills
 * only on hover-capable pointers, a spinner for loading that keeps focus,
 * and disabled faces per variant. The legacy gpui-kit variants and sizes
 * stay; the jui-kit face is {@link #builder}.
 */
public final class Button {

    /** The jui-kit variants a button takes. */
    public enum Variant { PRIMARY, SECONDARY, GHOST, DANGER }

    /** The jui-kit size steps. */
    public enum Size { SM, MD, LG }

    /** Heights per jui-kit size, inside the border. */
    public static float height(Size s) {
        return switch (s) { case SM -> 30; case MD -> 36; case LG -> 42; };
    }

    public static float padX(Size s) {
        return switch (s) { case SM -> 10; case MD -> 14; case LG -> 18; };
    }

    public static float font(Size s) {
        return switch (s) { case SM -> 12; case MD -> 13; case LG -> 14; };
    }

    public static float iconSize(Size s) {
        return switch (s) { case SM -> 14; case MD -> 16; case LG -> 18; };
    }

    private static float radius(Size s) {
        return switch (s) { case SM -> 7; case MD -> 8; case LG -> 10; };
    }

    /** Resolves an jui-kit variant to fill, hover fill and text on this look. */
    public static Kit.Tone tone(Context c, Variant v) {
        return switch (v) {
            case PRIMARY -> Kit.tone(c, Kit.Variant.PRIMARY);
            case SECONDARY -> Kit.tone(c, Kit.Variant.DEFAULT);
            case GHOST -> Kit.tone(c, Kit.Variant.GHOST);
            case DANGER -> Kit.tone(c, Kit.Variant.DANGER);
        };
    }

    /**
     * The jui-kit button: label, optional leading icon, loading, disabled, and
     * an optional trailing caret. Presses dip the content and deepen the
     * fill; loading swallows clicks and keeps focus; disabled fades the
     * primary and mutes the rest.
     */
    public static Element builder(Context c, String label, Variant v, Size s, String icon,
                              boolean loading, boolean disabled, boolean caret) {
        Kit.Tone tone = tone(c, v);
        float h = height(s);
        boolean plain = v == Variant.GHOST;
        Element b = buttonBase(c).height(h).padding(0, plain && !caret ? 8 : padX(s))
                .radius(radius(s)).gap(7).label("kit-btn-" + label)
                .justify(Justify.CENTER);
        if (disabled) {
            b.disabled(true);
        }
        // Press: the fill deepens, the content dips, and the box scales to
        // 0.97 about its center on the snappy spring, as jui-kit presses do.
        float dip = KitMotion.press(b);
        b.scale(1 - 0.03f * dip, 1 - 0.03f * dip);
        b.children(() -> {
            if (loading) {
                box(c).size(Kit.iconSize(Kit.Size.SMALL), Kit.iconSize(Kit.Size.SMALL))
                        .shrink(0).draw(Spinner.draw(c));
            } else if (icon != null) {
                box(c).size(iconSize(s), iconSize(s)).shrink(0)
                        .opacity(1 - 0.1f * dip).draw(Icon.draw(icon, iconColor(c, v, tone, disabled)));
            }
            Element t = text(c, label).singleLine().fontSize(font(s))
                    .textColor(textColor(c, v, tone, disabled)).opacity(1 - 0.1f * dip);
            t.fontWeight(500);
            if (caret) {
                spacer(c).width(4);
                box(c).size(iconSize(s) * 3 / 4, iconSize(s) * 3 / 4).shrink(0)
                        .draw(Icon.draw("chevron-down", textColor(c, v, tone, disabled)));
            }
        });
        Color bg;
        if (plain) {
            bg = Color.rgba(0x808080, 0);
        } else {
            bg = tone.fill();
        }
        if (disabled) {
            b.background(plain ? Color.rgba(0x808080, 0) : Color.rgba(bg.rgba() >>> 8, 0.4f));
            if (!plain) {
                b.border(1, KitMotion.hairline(c));
            }
        } else {
            if (!plain) {
                b.background(KitMotion.mix(bg, Color.rgb(0x000000), 0.12f * dip));
            }
            if (b.hovered() && !loading) {
                b.background(plain ? tone.fillHover() : KitMotion.mix(tone.fillHover(), Color.rgb(0x000000), 0.12f * dip));
            }
            if (v == Variant.SECONDARY) {
                b.border(1, KitMotion.hairline(c));
            }
        }
        return b;
    }

    private static Color iconColor(Context c, Variant v, Kit.Tone tone, boolean disabled) {
        if (disabled) {
            return c.theme().textMuted();
        }
        return v == Variant.PRIMARY || v == Variant.DANGER ? tone.text() : tone.text();
    }

    private static Color textColor(Context c, Variant v, Kit.Tone tone, boolean disabled) {
        if (disabled) {
            return c.theme().textMuted();
        }
        return v == Variant.GHOST ? tone.text() : tone.text();
    }

    /** The common jui-kit faces as one-call helpers. */
    public static Element builder(Context c, String label) {
        return builder(c, label, Variant.PRIMARY, Size.MD, null, false, false, false);
    }

    public static Element builder(Context c, String label, Variant v) {
        return builder(c, label, v, Size.MD, null, false, false, false);
    }

    public static Element builder(Context c, String label, Variant v, Size s) {
        return builder(c, label, v, s, null, false, false, false);
    }

    public static Element builder(Context c, String label, Variant v, Size s, String icon) {
        return builder(c, label, v, s, icon, false, false, false);
    }

    // ---- The gpui-kit face, kept for the kit's existing callers, now on the
    // ---- jui-kit motion: press dip, hover fill, loading spinner.

    /** The one legacy button builder: the overloads all funnel into this. */
    public static Element button(Context c, String label, Kit.Variant v, Kit.Size s, String icon,
                                 boolean outline, boolean loading, boolean disabled, boolean selected,
                                 boolean caret) {
        Kit.Tone tone = Kit.tone(c, v);
        float h = Kit.height(s);
        float px = Kit.padX(s);
        float fs = Kit.font(s);
        boolean plain = v == Kit.Variant.GHOST || v == Kit.Variant.LINK || v == Kit.Variant.TEXT;
        Color bg = plain ? Color.rgba(0x808080, 0) : tone.fill();
        Element b = buttonBase(c).height(h).padding(0, plain && !outline ? 4 : px)
                .radius(plain && !outline ? Kit.RADIUS_XS : Kit.RADIUS_SM)
                .label("kit-btn-" + label)
                .justify(Justify.START)
                .gap(Kit.iconSize(s) / 3);
        float dip = KitMotion.press(b);
        if (disabled) {
            b.disabled(true);
        }
        b.children(() -> {
            if (loading) {
                box(c).size(Kit.iconSize(s), Kit.iconSize(s)).shrink(0).draw(Spinner.draw(c));
            } else if (icon != null) {
                box(c).size(Kit.iconSize(s), Kit.iconSize(s)).shrink(0).opacity(1 - 0.1f * dip)
                        .draw(Icon.draw(icon, tone.text()));
            }
            Element t = text(c, label).fontSize(fs).singleLine().opacity(1 - 0.1f * dip)
                    .textColor(v == Kit.Variant.LINK ? c.theme().accent() : tone.text());
            if (caret) {
                spacer(c).grow(1);
                box(c).size(Kit.iconSize(s) * 3 / 4, Kit.iconSize(s) * 3 / 4).shrink(0)
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
            b.background(plain ? tone.fillHover() : tone.fillHover());
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

    /** A row of buttons sharing one selected index, now with the glide kept from before. */
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
                        .children(() -> text(c, labels[idx]).fontSize(11).singleLine()
                                .textColor(on ? c.theme().accentText() : c.theme().textMuted()));
                if (on) {
                    b.background(c.theme().accent());
                } else if (b.hovered()) {
                    b.background(c.theme().surfaceHover());
                }
                if (b.clicked()) {
                    selected.set(on && allowOff ? -1 : idx);
                }
            }
        });
    }

    private Button() {}
}
