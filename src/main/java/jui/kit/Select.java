package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's select, after the Select: a compact choice field with a
 * keyboard friendly menu. The trigger reads the current option with a
 * chevron that turns; the menu rows arrive with a short stagger, the
 * picked row keeps a quiet check, and the panel clips at its max height.
 */
public final class Select {

    /** The trigger + menu. The caller owns open so the menu can be driven. */
    public static Element select(Context c, IntRef selected, BoolRef open, String[] options,
                                 String label, String placeholder, boolean disabled, float width) {
        Element host = column(c).gap(4).label("kit-select-" + label);
        host.children(() -> {
            if (label != null && !label.isEmpty()) {
                text(c, label).fontSize(12).fontWeight(500).textColor(c.theme().text());
            }
            int idx = Math.max(0, Math.min(selected.get(), Math.max(0, options.length - 1)));
            Element trigger = buttonBase(c).width(width).height(34).padding(0, 11).gap(6).radius(Kit.RADIUS_XS + 2)
                    .justify(jui.ui.Justify.START).label("kit-select-trigger");
            if (disabled) {
                trigger.disabled(true);
                trigger.background(Color.rgba(Kit.surface(c).rgba() >>> 8, 0.5f));
            } else {
                trigger.background(Kit.surface(c));
                if (trigger.hovered() || open.get()) {
                    trigger.background(c.theme().surfaceHover());
                }
                trigger.border(1, open.get() ? c.theme().accent() : Kit.hairline(c));
            }
            trigger.children(() -> {
                String shown = options.length == 0 ? (placeholder == null ? "Select" : placeholder)
                        : options[idx];
                text(c, shown).singleLine().fontSize(12.5f)
                        .textColor(options.length == 0 ? c.theme().textMuted() : c.theme().text());
                spacer(c).grow(1);
                IconMorph.icon(c, IconMorph.CHEVRON, open.get(), c.theme().textMuted(), 14);
            });
            if (!disabled && trigger.clicked()) {
                open.set(!open.get());
            }
            popover(c, trigger, open, () -> {
                Element panel = box(c).padding(4).gap(1).minWidth(width).maxHeight(280).clip()
                        .label("kit-select-menu");
                for (int i = 0; i < options.length; i++) {
                    int ii = i;
                    boolean on = ii == selected.get();
                    Element r = row(c).gap(8).padding(7, 9).radius(Kit.RADIUS_XS).fillWidth()
                            .label("kit-select-row");
                    if (r.hovered()) {
                        r.background(c.theme().surfaceHover());
                    }
                    float rise = Kit.spring(panel, "rise" + ii, 1, Kit.SNAPPY);
                    r.opacity(Math.min(1, 0.4f + 0.6f * rise));
                    r.children(() -> {
                        text(c, options[ii]).singleLine().fontSize(12.5f)
                                .textColor(on ? c.theme().accent() : c.theme().text());
                        spacer(c).grow(1);
                        if (on) {
                            box(c).size(13, 13).shrink(0).draw(Icon.draw("check", c.theme().accent()));
                        }
                    });
                    if (r.clicked()) {
                        selected.set(ii);
                        open.set(false);
                        c.invalidate();
                    }
                }
            });
        });
        return host;
    }

    /** The legacy shape: trigger + menu over the given options. */
    public static Element select(Context c, IntRef selected, BoolRef open, String... options) {
        return select(c, selected, open, options, null, "请选择", false, 190);
    }

    private Select() {}
}
