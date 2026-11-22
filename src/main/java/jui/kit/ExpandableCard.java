package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's expandable card, after the ExpandableCard: give a dense card
 * more room when requested. A "Show more" line sits in the footer; asking
 * for it grows the card's body height on the morph run and swaps the line
 * to "Show less", without moving anything outside the card.
 */
public final class ExpandableCard {

    private static final class St {
        boolean expanded;
    }

    /** The card: title, collapsed and expanded body builders, min and max heights. */
    public static Element expandableCard(Context c, String title, Runnable collapsedBody,
                                         Runnable expandedBody, float collapsedHeight, float expandedHeight) {
        Element host = column(c).label("kit-expandable-card");
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        float h = Kit.spring(host, "h", st.expanded ? expandedHeight : collapsedHeight, Kit.MORPH);
        Element card = column(c).height(h).clip().radius(Kit.RADIUS + 2).background(Kit.surface(c))
                .border(1, Kit.hairline(c)).padding(12).gap(8).label("kit-expandable-body");
        Kit.cardShadow(card);
        card.children(() -> {
            row(c).alignItems(Align.CENTER).children(() -> {
                text(c, title).singleLine().fontSize(13).fontWeight(500).textColor(c.theme().text());
                spacer(c).grow(1);
            });
            if (st.expanded) {
                expandedBody.run();
            } else {
                collapsedBody.run();
            }
            divider(c);
            Element more = buttonBase(c).fillWidth().height(26).radius(Kit.RADIUS_XS)
                    .label("kit-expandable-more");
            if (more.hovered()) {
                more.background(c.theme().surfaceHover());
            }
            more.children(() -> {
                text(c, st.expanded ? "Show less" : "Show more").singleLine().fontSize(11.5f)
                        .textColor(c.theme().accent());
                spacer(c).grow(1);
                IconMorph.icon(c, IconMorph.CHEVRON, st.expanded, c.theme().accent(), 12);
            });
            if (more.clicked()) {
                st.expanded = !st.expanded;
                c.invalidate();
            }
        });
        return host;
    }

    private ExpandableCard() {}
}
