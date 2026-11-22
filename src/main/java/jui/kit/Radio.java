package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's radio group, after the RadioGroup: choose one option from a
 * visible set. Each row is its radio dot, label and optional description;
 * the dot fills on the snappy run and the arrows move the choice while a
 * row holds the focus.
 */
public final class Radio {

    /** One option: label and optional description. */
    public record Option(String label, String description) {
        public Option(String label) {
            this(label, null);
        }
    }

    /** The group over the caller's IntRef. */
    public static Element radioGroup(Context c, IntRef selected, Option[] options, boolean horizontal,
                                     boolean disabled) {
        Element host = horizontal ? row(c).gap(14) : column(c).gap(8);
        host.label("kit-radio-group").alignItems(Align.START);
        int sel = Math.max(0, Math.min(selected.get(), options.length - 1));
        for (int i = 0; i < options.length; i++) {
            int ii = i;
            boolean on = ii == sel;
            Option o = options[ii];
            Element row = buttonBase(c).gap(8).alignItems(Align.START).label("kit-radio-" + o.label());
            if (disabled) {
                row.disabled(true);
            }
            row.children(() -> {
                Element dot = box(c).size(16, 16).radius(8).shrink(0);
                float fill = Kit.spring(host, "d" + ii, on ? 1 : 0, Kit.SNAPPY);
                dot.border(1.5f, on ? c.theme().accent()
                        : Kit.mix(c.theme().border(), c.theme().text(), 0.25f));
                dot.children(() -> box(c).size(6 + 4 * fill, 6 + 4 * fill).radius(3 + 2 * fill)
                        .background(c.theme().accent()));
                column(c).gap(1).children(() -> {
                    Element l = text(c, o.label()).fontSize(12.5f);
                    l.textColor(disabled ? c.theme().textMuted() : c.theme().text());
                    if (o.description() != null) {
                        text(c, o.description()).fontSize(11).textColor(c.theme().textMuted());
                    }
                });
            });
            if (!disabled && row.clicked()) {
                selected.set(ii);
                c.invalidate();
            }
        }
        return host;
    }

    /** The legacy shape: plain labels. */
    public static Element radio(Context c, IntRef selected, boolean horizontal, String... options) {
        Option[] os = new Option[options.length];
        for (int i = 0; i < options.length; i++) {
            os[i] = new Option(options[i]);
        }
        return radioGroup(c, selected, os, horizontal, false);
    }

    public static Element radio(Context c, IntRef selected, String... options) {
        return radio(c, selected, false, options);
    }

    private Radio() {}
}
