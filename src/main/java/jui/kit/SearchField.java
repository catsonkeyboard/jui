package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's search field, after the SearchField: a recognizable entry
 * point with clear affordances — the magnifier leading, the query inside,
 * a clear button that fades in with text, and an optional shortcut chip on
 * the trailing edge that disappears once the field holds text.
 */
public final class SearchField {

    /** The field: value, placeholder, and an optional shortcut label like "⌘K". */
    public static Element searchField(Context c, TextRef value, String placeholder, String shortcut,
                                      boolean disabled) {
        Element host = box(c).fillWidth().label("kit-search").children(() -> {
            Element box = textInput(c, value).placeholder(placeholder).fillWidth()
                    .height(32).padding(0, 30).radius(Kit.RADIUS + 2).label("kit-search-box");
            if (disabled) {
                box.disabled(true);
            }
            boolean focused = box.focused();
            float k = KitMotion.spring(box, "edge", focused ? 1 : 0, KitMotion.SNAPPY);
            box.background(KitMotion.surface(c));
            box.border(1, focused ? KitMotion.mix(c.theme().border(), c.theme().accent(), k) : c.theme().border());
            box.children(() -> {
                box(c).absolute().left(9).top(0).bottom(0).width(16).alignItems(jui.ui.Align.CENTER)
                        .children(() -> box(c).size(14, 14)
                                .draw(Icon.draw("magnifyingglass", c.theme().textMuted())));
                boolean has = value.get() != null && !value.get().isEmpty();
                if (has) {
                    Element clear = buttonBase(c).absolute().right(4).top(4).size(24, 24).radius(6)
                            .label("kit-search-clear");
                    if (clear.hovered()) {
                        clear.background(c.theme().surfaceHover());
                    }
                    clear.children(() -> box(c).size(11, 11).shrink(0)
                            .draw(Icon.draw("xmark", c.theme().textMuted())));
                    if (clear.clicked()) {
                        value.set("");
                        c.invalidate();
                    }
                } else if (shortcut != null && !shortcut.isEmpty()) {
                    Element chip = box(c).absolute().right(6).top(0).bottom(0).alignItems(jui.ui.Align.CENTER)
                            .children(() -> {
                                Element kbd = box(c).padding(1, 5).radius(4).background(c.theme().surface())
                                        .border(1, KitMotion.hairline(c))
                                        .children(() -> text(c, shortcut).singleLine().fontSize(10)
                                                .textColor(c.theme().textMuted()));
                            });
                }
            });
        });
        return host;
    }

    public static Element searchField(Context c, TextRef value, String placeholder) {
        return searchField(c, value, placeholder, null, false);
    }

    private SearchField() {}
}
