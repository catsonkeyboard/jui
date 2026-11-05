package jui.kit;

import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's combobox: a type-to-filter input whose matches list beneath it;
 * picking one fills the field, after gpui-kit's Combobox.
 */
public final class Combobox {
    public static Element combobox(Context c, TextRef query, jui.ui.BoolRef open,
                                   String... allOptions) {
        Element field = textInput(c, query).placeholder("搜索…").fillWidth()
                .label("kit-combo");
        if (field.changed() && !query.get().isEmpty()) open.set(true);
        java.util.List<String> matches = new java.util.ArrayList<>();
        String q = query.get().toLowerCase();
        for (String option : allOptions) {
            if (q.isEmpty() || option.toLowerCase().contains(q)) matches.add(option);
        }
                if (field.clicked()) open.set(true);
        popover(c, field, open, () -> column(c).fillWidth().padding(4).gap(2).children(() -> {
            for (int i = 0; i < Math.min(8, matches.size()); i++) {
                String option = matches.get(i);
                Element b = buttonBase(c).fillWidth().height(26).padding(0, 10).radius(Kit.RADIUS_XS)
                        .label("kit-combo-opt-" + option)
                        .justify(Justify.START)
                        .children(() -> text(c, option).fontSize(12).textColor(c.theme().text()));
                if (b.hovered()) b.background(c.theme().surfaceHover());
                if (b.clicked()) {
                    query.set(option);
                    open.set(false);
                }
            }
            if (matches.isEmpty()) {
                text(c, "无匹配项").fontSize(11).textColor(c.theme().textMuted());
            }
        }));
        return field;
    }

    private Combobox() {}
}
