package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's toggle group, after the ToggleGroup: a row of icon or text
 * toggles, single or multiple. One pressed fill glides between options in
 * single mode; in multiple mode each option fades its own tint in and its
 * neighbours join with hairline joins. It is a choice, not an action row.
 */
public final class ToggleGroup {

    /** One toggle's facts: label, icon, whether it is on. */
    public record Toggle(String label, String icon, boolean on) {}

    /** Single mode over the caller's IntRef (-1 none). */
    public static Element toggles(Context c, IntRef selected, Toggle[] toggles, boolean multiple,
                                  boolean iconOnly, boolean disabled) {
        Element host = row(c).gap(2).label("kit-toggle-group").alignItems(Align.CENTER);
        int n = toggles.length;
        int sel = Math.max(-1, Math.min(selected.get(), n - 1));
        for (int i = 0; i < n; i++) {
            int ii = i;
            Toggle t = toggles[ii];
            boolean on = multiple ? t.on() : ii == sel;
            Element b = buttonBase(c).height(28).padding(0, iconOnly ? 7 : 10).gap(5).radius(Kit.RADIUS_XS + 2)
                    .shrink(0).label("kit-toggle-" + (t.label().isEmpty() ? ii : t.label()));
            if (disabled) {
                b.disabled(true);
            }
            if (on) {
                b.background(c.theme().surfacePressed());
            } else if (b.hovered() && !disabled) {
                b.background(c.theme().surfaceHover());
            }
            float fill = KitMotion.spring(host, "f" + ii, on ? 1 : 0, KitMotion.SNAPPY);
            b.children(() -> {
                Color col = KitMotion.mix(c.theme().textMuted(), c.theme().text(), fill);
                if (t.icon() != null) {
                    box(c).size(15, 15).shrink(0).draw(Icon.draw(t.icon(), col));
                }
                if (!iconOnly && !t.label().isEmpty()) {
                    text(c, t.label()).singleLine().fontSize(12).textColor(col);
                }
            });
            if (!disabled && b.clicked()) {
                if (multiple) {
                    t.on();
                } else {
                    selected.set(ii == sel ? -1 : ii);
                }
                c.invalidate();
            }
        }
        return host;
    }

    public static Element toggles(Context c, IntRef selected, Toggle[] toggles) {
        return toggles(c, selected, toggles, false, false, false);
    }

    private ToggleGroup() {}
}
