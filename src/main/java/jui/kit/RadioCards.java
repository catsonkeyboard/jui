package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's radio cards, after the RadioCards: selectable option cards
 * with a sliding selection ring, price and description slots, and radio
 * keyboard behavior. The picked card takes the accent border that glides
 * from card to card on the morph run, and its radio dot fills.
 */
public final class RadioCards {

    /** One card: title, description, optional price, optional icon. */
    public record Card(String title, String description, String price, String icon) {}

    /** The row of cards over one IntRef; columns sets the grid width. */
    public static Element radioCards(Context c, IntRef selected, Card[] cards, float cardWidth,
                                     boolean disabled) {
        Element host = row(c).gap(8).wrap().alignContent(Align.START).label("kit-radio-cards");
        int sel = Math.max(0, Math.min(selected.get(), cards.length - 1));
        for (int i = 0; i < cards.length; i++) {
            int ii = i;
            boolean on = ii == sel;
            Card card = cards[ii];
            Element cell = buttonBase(c).width(cardWidth).padding(10).gap(6).radius(Kit.RADIUS)
                    .alignItems(Align.START).label("kit-card-" + card.title());
            if (disabled) {
                cell.disabled(true);
            }
            // The selection ring reads through the border; the fill tints when on.
            cell.border(1.5f, on ? c.theme().accent() : KitMotion.hairline(c));
            cell.background(on ? Color.rgba(c.theme().accent().rgba() >>> 8, 0.07f)
                    : KitMotion.surface(c));
            if (!disabled && !on && cell.hovered()) {
                cell.background(c.theme().surfaceHover());
            }
            cell.children(() -> {
                row(c).fillWidth().gap(8).alignItems(Align.CENTER).children(() -> {
                    if (card.icon() != null) {
                        box(c).size(16, 16).shrink(0).draw(Icon.draw(card.icon(),
                                on ? c.theme().accent() : c.theme().textMuted()));
                    }
                    text(c, card.title()).singleLine().fontSize(12.5f).fontWeight(500)
                            .textColor(c.theme().text());
                    spacer(c).grow(1);
                    // The radio dot.
                    Element dot = box(c).size(16, 16).radius(8).shrink(0);
                    float t = KitMotion.spring(host, "dot" + ii, on ? 1 : 0, KitMotion.SNAPPY);
                    dot.border(1.5f, on ? c.theme().accent() : KitMotion.mix(c.theme().border(), c.theme().text(), 0.25f));
                    dot.children(() -> box(c).size(6 + 4 * t, 6 + 4 * t).radius(4 + 4 * t)
                            .background(c.theme().accent()));
                });
                if (card.description() != null) {
                    Element d = text(c, card.description()).fontSize(11);
                    d.textColor(c.theme().textMuted());
                }
                if (card.price() != null) {
                    Element p = text(c, card.price()).singleLine().fontSize(12.5f).fontWeight(500);
                    p.textColor(on ? c.theme().accent() : c.theme().text());
                }
            });
            if (!disabled && cell.clicked()) {
                selected.set(ii);
                c.invalidate();
            }
        }
        return host;
    }

    public static Element radioCards(Context c, IntRef selected, Card[] cards) {
        return radioCards(c, selected, cards, 190, false);
    }

    private RadioCards() {}
}
