package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's switch, after the Switch: a tactile toggle for settings that
 * take effect immediately. The knob slides with a slight overshoot feel on
 * the snappy run, the track takes the accent as it fills, and the label
 * and description read on the leading side.
 */
public final class Switch {

    /** The control: label, optional description, ref, disabled. */
    public static Element toggle(Context c, String label, String description, jui.ui.BoolRef ref,
                                 boolean disabled) {
        Element row = toggleBase(c, ref);
        row.gap(10).label("kit-switch-" + label);
        if (disabled) {
            row.disabled(true);
        }
        row.children(() -> {
            column(c).gap(1).children(() -> {
                Element l = text(c, label).fontSize(12.5f);
                l.textColor(disabled ? c.theme().textMuted() : c.theme().text());
                if (description != null && !description.isEmpty()) {
                    text(c, description).fontSize(11).textColor(c.theme().textMuted());
                }
            });
            spacer(c).grow(1);
            // The track and knob; the fill and slide run together.
            Element track = row(c).size(38, 22).radius(11).padding(2).shrink(0)
                    .label("kit-switch-track");
            float pos = KitMotion.spring(track, "knob", ref.get() ? 1 : 0, KitMotion.SNAPPY);
            Color off = KitMotion.mix(c.theme().border(), c.theme().text(), 0.15f);
            track.background(disabled ? Color.rgba(off.rgba() >>> 8, 0.5f)
                    : KitMotion.mix(off, c.theme().accent(), pos));
            track.children(() -> box(c).size(18, 18).radius(9).background(Color.rgb(0xFFFFFF))
                    .shadow(0, 1, 3, 0, Color.rgba(0x000000, 0.25f))
                    .margin(0, 0, 0, 16 * pos));
        });
        return row;
    }

    public static Element toggle(Context c, String label, jui.ui.BoolRef ref) {
        return toggle(c, label, null, ref, false);
    }

    private Switch() {}
}
