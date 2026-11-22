package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's combobox, after the Combobox: search and select from a list
 * without leaving the field. The query filters as it types, matches list
 * under the field with a short stagger, the picked match fills the field,
 * and an empty result reads "No matches" in the muted tone.
 */
public final class Combobox {

    public static Element combobox(Context c, TextRef query, BoolRef open, String[] allOptions,
                                   String placeholder, boolean disabled, float width) {
        Element host = box(c).label("kit-combobox");
        Element field = textInput(c, query).placeholder(placeholder).width(width).height(34)
                .padding(0, 11).radius(Kit.RADIUS_XS + 2).singleLine().label("kit-combo-field");
        if (disabled) {
            field.disabled(true);
        }
        boolean focused = field.focused();
        float k = Kit.spring(field, "edge", focused || open.get() ? 1 : 0, Kit.SNAPPY);
        field.background(Kit.surface(c));
        field.border(1, focused || open.get()
                ? Kit.mix(c.theme().border(), c.theme().accent(), k) : Kit.hairline(c));
        if (field.changed() && !query.get().isEmpty()) {
            open.set(true);
        }
        if (field.clicked() && !disabled) {
            open.set(true);
        }
        java.util.List<String> matches = new java.util.ArrayList<>();
        String q = query.get() == null ? "" : query.get().toLowerCase();
        for (String option : allOptions) {
            if (q.isEmpty() || option.toLowerCase().contains(q)) {
                matches.add(option);
            }
        }
        popover(c, field, open, () -> {
            Element panel = box(c).padding(4).gap(1).minWidth(width).maxHeight(260).clip()
                    .label("kit-combo-menu");
            for (int i = 0; i < Math.min(8, matches.size()); i++) {
                String option = matches.get(i);
                Element r = row(c).padding(7, 9).radius(Kit.RADIUS_XS).fillWidth()
                        .label("kit-combo-row");
                if (r.hovered()) {
                    r.background(c.theme().surfaceHover());
                }
                float rise = Kit.spring(panel, "st" + i, 1, Kit.SNAPPY);
                r.opacity(Math.min(1, 0.4f + 0.6f * rise));
                r.children(() -> text(c, option).singleLine().fontSize(12.5f).textColor(c.theme().text()));
                if (r.clicked()) {
                    query.set(option);
                    open.set(false);
                    c.invalidate();
                }
            }
            if (matches.isEmpty()) {
                text(c, "No matches").fontSize(11.5f).textColor(c.theme().textMuted()).padding(6, 8);
            }
        });
        return host;
    }

    /** The legacy shape. */
    public static Element combobox(Context c, TextRef query, BoolRef open, String... allOptions) {
        return combobox(c, query, open, allOptions, "Search…", false, 200);
    }

    private Combobox() {}
}
