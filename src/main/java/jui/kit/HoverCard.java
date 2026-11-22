package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's hover card, after the HoverCard: preview a person or link on
 * hover or focus without leaving the page. The card opens after a short
 * beat on the morph run, sits beside the anchor, and closes when the
 * pointer leaves either of them; keyboard focus opens it too.
 */
public final class HoverCard {

    /** The card's content: the caller styles it. */
    public static Element hoverCard(Context c, Element anchor, jui.ui.BoolRef open, Runnable content) {
        boolean on = anchor.hovered() || anchor.focusVisible();
        if (on != open.get()) {
            open.set(on);
        }
        Element host = popover(c, anchor, open, () -> {
            Element card = column(c).padding(12).gap(6).width(260)
                    .background(c.theme().background()).radius(Kit.RADIUS + 2)
                    .border(1, Kit.hairline(c)).label("kit-hover-card");
            Kit.floatShadow(card);
            float a = Kit.spring(card, "in", 1, Kit.MORPH);
            card.opacity(a);
            content.run();
        });
        return host;
    }

    /** A person preview: name, handle, bio, and a follow action. */
    public static Element personCard(Context c, String name, String handle, String bio) {
        return column(c).gap(6).children(() -> {
            row(c).gap(10).alignItems(Align.CENTER).children(() -> {
                Avatar.avatar(c, name, 40);
                column(c).gap(1).children(() -> {
                    text(c, name).singleLine().fontSize(12.5f).fontWeight(500)
                            .textColor(c.theme().text());
                    text(c, handle).singleLine().fontSize(11).textColor(c.theme().textMuted());
                });
            });
            if (bio != null && !bio.isEmpty()) {
                text(c, bio).fontSize(11.5f).textColor(c.theme().textMuted());
            }
            row(c).gap(4).alignItems(Align.CENTER).children(() -> {
                box(c).size(13, 13).shrink(0).draw(Icon.draw("person", c.theme().textMuted()));
                text(c, "View profile").singleLine().fontSize(11).textColor(c.theme().accent());
            });
        });
    }

    private HoverCard() {}
}
