package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Cursor;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's scrub input, after the ScrubInput: a Figma-style number
 * field. Drag the label to scrub the value, or type into the box; the
 * scrub cursor shows over the label and the value keeps one decimal rhythm
 * while it drags.
 */
public final class ScrubInput {

    private static final java.text.DecimalFormat FMT =
            new java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance());

    /**
     * The field: label (the scrub handle), value with min, max and step,
     * the typed text, an optional unit, and a disabled state.
     */
    public static Element scrubInput(Context c, String label, jui.ui.DoubleRef value, TextRef text,
                                     double min, double max, double step, String unit, boolean disabled) {
        Element host = box(c).fillWidth().label("kit-scrub");
        Element box = textInput(c, text).fillWidth().height(30).padding(0, 10).radius(Kit.RADIUS_XS + 2)
                .label("kit-scrub-box");
        if (disabled) {
            box.disabled(true);
        }
        boolean focused = box.focused();
        float k = KitMotion.spring(box, "edge", focused ? 1 : 0, KitMotion.SNAPPY);
        box.background(KitMotion.surface(c));
        box.border(1, focused ? KitMotion.mix(c.theme().border(), c.theme().accent(), k) : c.theme().border());
        // The label rides the leading edge as the scrub handle.
        box.children(() -> {
            Element handle = box(c).absolute().left(0).top(0).bottom(0).gap(4).padding(0, 9)
                    .alignItems(jui.ui.Align.CENTER).label("kit-scrub-handle");
            if (!disabled) {
                handle.cursor(Cursor.RESIZE_EW).draggable();
            }
            handle.children(() -> text(c, label).singleLine().fontSize(11)
                    .textColor(c.theme().textMuted()));
            if (!disabled && handle.pressed()) {
                double v = Math.max(min, Math.min(max, value.get() + handle.dragX() * step));
                if (v != value.get()) {
                    value.set(v);
                    text.set(FMT.format(v) + (unit != null ? unit : ""));
                    c.invalidate();
                }
            }
        });
        if (box.submitted()) {
            String t = (text.get() == null ? "" : text.get()).replace(unit == null ? "\u0000" : unit, "").trim();
            try {
                double v = Math.max(min, Math.min(max, Double.parseDouble(t.replace(",", "."))));
                value.set(v);
                text.set(FMT.format(v) + (unit != null ? unit : ""));
            } catch (NumberFormatException e) {
                text.set(FMT.format(value.get()) + (unit != null ? unit : ""));
            }
            c.invalidate();
        }
        return host;
    }

    private ScrubInput() {}
}
