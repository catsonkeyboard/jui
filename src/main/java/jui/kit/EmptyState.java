package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's empty state, after the EmptyState: a useful next step when
 * there is nothing to show yet. A quiet glyph sits in a soft ring, the
 * title states what is missing, the body names the way out, and one action
 * leads there.
 */
public final class EmptyState {

    /** The block: glyph, title, body, action label + handler. */
    public static Element emptyState(Context c, String glyph, String title, String body,
                                     String actionLabel, Runnable onAction) {
        Element host = column(c).fillWidth().gap(4).padding(28).alignItems(Align.CENTER)
                .label("kit-empty-state");
        host.children(() -> {
            box(c).size(52, 52).radius(26).background(c.theme().surface())
                    .alignItems(Align.CENTER).children(() -> box(c).size(22, 22).shrink(0)
                            .draw(Icon.draw(glyph == null ? "doc" : glyph, c.theme().textMuted())));
            spacer(c).height(8);
            text(c, title).singleLine().fontSize(13).fontWeight(500).textColor(c.theme().text());
            if (body != null && !body.isEmpty()) {
                Element b = text(c, body).fontSize(11.5f).maxWidth(300);
                b.textColor(c.theme().textMuted());
            }
            if (actionLabel != null && onAction != null) {
                spacer(c).height(8);
                Element go = Button.builder(c, actionLabel, Button.Variant.PRIMARY, Button.Size.SM);
                if (go.clicked()) {
                    onAction.run();
                }
            }
        });
        return host;
    }

    private EmptyState() {}
}
