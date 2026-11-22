package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's card, after the Card: a contained group of related content
 * and actions. The card reads as one raised surface with the grounded
 * shadow; a header row carries the title and its trailing actions, the
 * body builds between, and an optional footer holds the meta row.
 */
public final class Card {

    /** The card: title, header actions, body, footer. */
    public static Element card(Context c, String title, Runnable headerActions,
                               Runnable body, Runnable footer) {
        Element host = column(c).fillWidth().radius(Kit.RADIUS + 3).background(c.theme().background())
                .border(1, Kit.hairline(c)).clip().label("kit-card-" + title);
        Kit.cardShadow(host);
        host.children(() -> {
            if (title != null) {
                row(c).padding(12, 14).gap(8).alignItems(Align.CENTER).children(() -> {
                    text(c, title).singleLine().fontSize(13).fontWeight(500).textColor(c.theme().text());
                    spacer(c).grow(1);
                    if (headerActions != null) {
                        headerActions.run();
                    }
                });
                divider(c);
            }
            if (body != null) {
                box(c).padding(14).children(body);
            }
            if (footer != null) {
                divider(c);
                box(c).padding(10, 14).children(footer);
            }
        });
        return host;
    }

    /** The plain card: just the body. */
    public static Element card(Context c, Runnable body) {
        return card(c, null, null, body, null);
    }

    private Card() {}
}
