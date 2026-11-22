package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's morph select, after the MorphSelect: a select whose trigger
 * grows into the list, with a gliding highlight and type-ahead. The panel
 * opens at the trigger's width and widens to its own on the morph run, the
 * highlight glides under the rows, and typing a letter jumps to the next
 * option that starts with it.
 */
public final class MorphSelect {

    public static Element morphSelect(Context c, IntRef selected, BoolRef open, String[] options,
                                      String placeholder, boolean disabled, float width) {
        Element host = box(c).label("kit-morph-select");
        int idx = Math.max(0, Math.min(selected.get(), Math.max(0, options.length - 1)));
        float w = Kit.spring(host, "w", open.get() ? Math.max(width, 210) : width, Kit.MORPH);
        Element trigger = buttonBase(c).width(w).height(34).padding(0, 11).gap(6).radius(Kit.RADIUS_XS + 2)
                .justify(jui.ui.Justify.START).label("kit-morph-trigger");
        if (disabled) {
            trigger.disabled(true);
        } else {
            trigger.background(Kit.surface(c));
            if (trigger.hovered() || open.get()) {
                trigger.background(c.theme().surfaceHover());
            }
            trigger.border(1, open.get() ? c.theme().accent() : Kit.hairline(c));
        }
        trigger.children(() -> {
            String shown = options.length == 0 ? placeholder : options[idx];
            text(c, shown).singleLine().fontSize(12.5f).textColor(c.theme().text());
            spacer(c).grow(1);
            IconMorph.icon(c, IconMorph.CHEVRON, open.get(), c.theme().textMuted(), 14);
        });
        if (!disabled && trigger.clicked()) {
            open.set(!open.get());
        }
        popover(c, trigger, open, () -> {
            Element panel = box(c).padding(4).gap(1).minWidth(w).maxWidth(260).maxHeight(280).clip()
                    .label("kit-morph-menu");
            int[] hover = {-1};
            for (int i = 0; i < options.length; i++) {
                int ii = i;
                boolean on = ii == selected.get();
                Element r = row(c).padding(7, 9).radius(Kit.RADIUS_XS).fillWidth()
                        .label("kit-morph-row");
                if (r.hovered()) {
                    hover[0] = ii;
                    r.background(c.theme().surfaceHover());
                }
                r.children(() -> {
                    text(c, options[ii]).singleLine().fontSize(12.5f)
                            .textColor(on ? c.theme().accent() : c.theme().text());
                    if (on) {
                        spacer(c).grow(1);
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
        return host;
    }

    private MorphSelect() {}
}
