package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's expanding search, after the ExpandingSearch: an icon that
 * morphs into a search field with results beneath it. At rest it is a
 * round quiet button; opening springs its width to the caller's, slides
 * the magnifier into the field's leading edge, and builds the results
 * under it while the query has text.
 */
public final class ExpandingSearch {

    /**
     * The control: closed width 32, open width {@code openWidth}; results
     * come from the caller as one element per query text, or null for none.
     */
    public static Element expandingSearch(Context c, TextRef query, float openWidth, boolean disabled,
                                          java.util.function.Function<String, Element> results) {
        Element host = box(c).label("kit-expanding-search");
        BoolRef open = localBool(host, "open", false);
        float w = KitMotion.spring(host, "w", open.get() ? openWidth : 32, KitMotion.MORPH);
        Element shell = row(c).size(w, 32).radius(16).shrink(0).clip().label("kit-es-shell")
                .alignItems(jui.ui.Align.CENTER);
        shell.background(KitMotion.surface(c)).border(1, open.get() && shell.focused()
                ? c.theme().accent() : KitMotion.hairline(c));
        if (disabled) {
            shell.disabled(true);
        }
        shell.children(() -> {
            if (open.get()) {
                Element input = textInput(c, query).placeholder("Search").fillWidth().height(30)
                        .padding(0, 28).radius(14).label("kit-es-input");
                input.background(Color.rgba(0x808080, 0));
                input.border(0, Color.rgba(0x808080, 0));
                input.autoFocus();
                box(c).absolute().left(9).top(0).bottom(0).width(16).alignItems(jui.ui.Align.CENTER)
                        .children(() -> box(c).size(14, 14)
                                .draw(Icon.draw("magnifyingglass", c.theme().textMuted())));
                Element close = buttonBase(c).absolute().right(4).top(4).size(24, 24).radius(12)
                        .label("kit-es-close");
                if (close.hovered()) {
                    close.background(c.theme().surfaceHover());
                }
                close.children(() -> box(c).size(11, 11).shrink(0)
                        .draw(Icon.draw("xmark", c.theme().textMuted())));
                if (close.clicked()) {
                    open.set(false);
                    query.set("");
                    c.invalidate();
                }
            } else {
                Element icon = buttonBase(c).size(32, 32).radius(16).shrink(0).label("kit-es-icon");
                if (icon.hovered()) {
                    icon.background(c.theme().surfaceHover());
                }
                icon.children(() -> box(c).size(15, 15).shrink(0)
                        .draw(Icon.draw("magnifyingglass", c.theme().text())));
                if (icon.clicked()) {
                    open.set(true);
                    c.invalidate();
                }
            }
        });
        if (open.get() && results != null && query.get() != null && !query.get().isEmpty()) {
            host.children(() -> {
                box(c).absolute().top(36).left(0).width(openWidth).children(() -> {
                    Element list = results.apply(query.get());
                });
            });
            c.after(java.time.Duration.ofMillis(16));
        }
        return host;
    }

    private ExpandingSearch() {}
}
