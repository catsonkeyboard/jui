package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's checkbox, after the Checkbox: a binary choice with a
 * precise, legible state. The box fills with the accent on the snappy run
 * while the check draws its stroke in, the label sits leading, and an
 * indeterminate mode shows a dash for tri-state rows.
 */
public final class Checkbox {

    /**
     * The control: label, ref, optional description, and indeterminate.
     * changed() reports a flip.
     */
    public static Element checkbox(Context c, String label, String description, jui.ui.BoolRef ref,
                                   boolean indeterminate, boolean disabled) {
        Element row = toggleBase(c, ref);
        row.gap(8).label("kit-checkbox-" + label);
        if (disabled) {
            row.disabled(true);
        }
        boolean hot = row.hovered() && !disabled;
        boolean on = ref.get() && !indeterminate;
        float fill = KitMotion.spring(row, "fill", on || indeterminate ? 1 : 0, KitMotion.SNAPPY);
        row.children(() -> {
            Element box = box(c).size(17, 17).radius(5).shrink(0);
            box.background(KitMotion.mix(c.theme().background(), c.theme().accent(), fill));
            box.border(1, on || indeterminate ? c.theme().accent()
                    : hot ? c.theme().accent() : KitMotion.mix(c.theme().border(), c.theme().text(), 0.25f));
            box.children(() -> {
                if (indeterminate && fill > 0.2f) {
                    box(c).absolute().width(9 * fill).height(2).radius(1)
                            .background(Color.rgb(0xFFFFFF));
                } else if (on) {
                    // The check draws in with the fill.
                    box(c).size(13, 13).shrink(0).opacity(fill).draw((p, b) -> {
                        float u = Math.min(b.width(), b.height()) / 16;
                        float k1 = Math.min(1, fill * 2), k2 = Math.max(0, fill * 2 - 1);
                        p.line(b.x() + 3.5f * u, b.y() + 8.5f * u,
                                b.x() + (3.5f + 3f * k1) * u, b.y() + (8.5f + 3f * k1) * u,
                                1.8f * u, Color.rgb(0xFFFFFF));
                        if (k2 > 0) {
                            p.line(b.x() + 6.5f * u, b.y() + 11.5f * u,
                                    b.x() + (6.5f + 6.5f * k2) * u, b.y() + (11.5f - 7f * k2) * u,
                                    1.8f * u, Color.rgb(0xFFFFFF));
                        }
                    });
                }
            });
            column(c).gap(1).children(() -> {
                Element l = text(c, label).fontSize(12.5f);
                l.textColor(disabled ? c.theme().textMuted() : c.theme().text());
                if (description != null && !description.isEmpty()) {
                    text(c, description).fontSize(11).textColor(c.theme().textMuted());
                }
            });
        });
        return row;
    }

    public static Element checkbox(Context c, String label, jui.ui.BoolRef ref) {
        return checkbox(c, label, null, ref, false, false);
    }

    private Checkbox() {}
}
