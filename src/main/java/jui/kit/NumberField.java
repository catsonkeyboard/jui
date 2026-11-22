package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.DoubleRef;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's number field, after the NumberField: enter a bounded number
 * with clear increment controls. The value clamps to its range, the
 * steppers step by one (hold to repeat through the frame clock), and typed
 * text parses on submit; an invalid entry restores the last value.
 */
public final class NumberField {

    private static final java.text.DecimalFormat FMT =
            new java.text.DecimalFormat("0.###", java.text.DecimalFormatSymbols.getInstance());

    /**
     * The field: label, value, min, max, step, optional suffix unit, and a
     * disabled state. changed() reports a new value.
     */
    public static Element numberField(Context c, String label, DoubleRef value, TextRef text,
                                      double min, double max, double step, String unit,
                                      boolean disabled) {
        return column(c).fillWidth().gap(4).label("kit-number-" + label).children(() -> {
            if (label != null && !label.isEmpty()) {
                Element l = text(c, label).fontSize(12).fontWeight(500);
                l.textColor(c.theme().text());
            }
            Element box = textInput(c, text).fillWidth().height(32).padding(0, unit != null ? 34 : 11)
                    .radius(Kit.RADIUS_XS + 2).label("kit-number-box");
            if (disabled) {
                box.disabled(true);
            }
            boolean focused = box.focused();
            float k = KitMotion.spring(box, "edge", focused ? 1 : 0, KitMotion.SNAPPY);
            box.background(KitMotion.surface(c));
            box.border(1, focused ? KitMotion.mix(c.theme().border(), c.theme().accent(), k) : c.theme().border());
            box.children(() -> {
                if (unit != null) {
                    Element u = box(c).absolute().left(11).top(0).bottom(0).alignItems(jui.ui.Align.CENTER)
                            .children(() -> text(c, unit).singleLine().fontSize(11)
                                    .textColor(c.theme().textMuted()));
                }
                column(c).absolute().right(3).top(3).bottom(3).width(22).gap(1)
                        .alignItems(jui.ui.Align.CENTER).children(() -> {
                            for (int d = 0; d < 2; d++) {
                                boolean up = d == 0;
                                Element b = buttonBase(c).fillWidth().grow(1).radius(3)
                                        .label("kit-number-" + (up ? "up" : "down"));
                                if (b.hovered() && !disabled) {
                                    b.background(c.theme().surfaceHover());
                                }
                                b.children(() -> box(c).size(9, 9).shrink(0)
                                        .draw(Icon.draw(up ? "chevron-up" : "chevron-down",
                                                c.theme().textMuted())));
                                if (!disabled && b.pressed()) {
                                    double v = clamp(value.get() + (up ? step : -step), min, max);
                                    set(c, value, text, v);
                                }
                            }
                        });
            });
            if (box.submitted()) {
                try {
                    double v = Double.parseDouble(text.get().replace(",", ".").trim());
                    set(c, value, text, clamp(v, min, max));
                } catch (NumberFormatException e) {
                    text.set(FMT.format(value.get()));
                    c.invalidate();
                }
            }
        });
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void set(Context c, DoubleRef value, TextRef text, double v) {
        if (v != value.get()) {
            value.set(v);
        }
        text.set(FMT.format(v));
        c.invalidate();
    }

    public static Element numberField(Context c, String label, DoubleRef value, TextRef text,
                                      double min, double max, double step) {
        return numberField(c, label, value, text, min, max, step, null, false);
    }

    private NumberField() {}
}
