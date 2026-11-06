package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's pagination: ‹ 1 … n › with a sliding window around the current
 * page, after gpui-kit's Pagination.
 */
public final class Pagination {
    public static Element pagination(Context c, jui.ui.IntRef page, int totalPages) {
        return row(c).gap(2).alignItems(Align.CENTER).label("kit-pagination").children(() -> {
            int current = page.get();
            nav(c, page, "kit-page-prev", current > 0, "chevron-left", current - 1);
            for (int pg = 0; pg < totalPages; pg++) {
                final int p = pg;
                if (totalPages > 7 && p > 1 && p < totalPages - 2 && Math.abs(p - current) > 1) {
                    if (p == 2 || p == totalPages - 3) {
                        text(c, "…").fontSize(11).textColor(c.theme().textMuted());
                    }
                    continue;
                }
                boolean on = p == current;
                Element b = buttonBase(c).size(24, 24).radius(Kit.RADIUS_XS)
                        .label("kit-page-" + (p + 1))
                        .children(() -> text(c, Integer.toString(p + 1)).fontSize(11)
                                .textColor(on ? c.theme().accentText() : c.theme().text()));
                if (on) b.background(c.theme().accent());
                else if (b.hovered()) b.background(c.theme().surfaceHover());
                if (b.clicked()) page.set(p);
            }
            nav(c, page, "kit-page-next", current < totalPages - 1, "chevron-right", current + 1);
        });
    }

    private static void nav(Context c, jui.ui.IntRef page, String id, boolean enabled,
                            String glyph, int target) {
        Element b = buttonBase(c).size(24, 24).radius(Kit.RADIUS_XS).label(id)
                .alignItems(Align.CENTER)
                .children(() -> box(c).size(12, 12)
                        .draw(Icon.draw(glyph, enabled ? c.theme().text() : c.theme().border())));
        if (enabled && b.clicked()) page.set(target);
    }

    private Pagination() {}
}
