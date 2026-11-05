package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's carousel: one page at a time with ‹ › paging and dot indicators,
 * after gpui-kit's Carousel.
 */
public final class Carousel {
    public static Element carousel(Context c, jui.ui.IntRef page, int pages,
                                   java.util.function.IntConsumer pageContent) {
        return column(c).gap(6).children(() -> {
            row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                nav(c, page, "kit-carousel-prev", "chevron-left", pages, -1);
                pageContent.accept(page.get());
                nav(c, page, "kit-carousel-next", "chevron-right", pages, 1);
            });
            row(c).gap(4).justify(Justify.CENTER).children(() -> {
                for (int i = 0; i < pages; i++) {
                    boolean on = i == page.get();
                    Element dot = box(c).size(on ? 8 : 6, on ? 8 : 6)
                            .radius(on ? 4 : 3)
                            .background(on ? c.theme().accent() : c.theme().border())
                            .label("kit-carousel-dot-" + i);
                    if (dot.clicked()) page.set(i);
                }
            });
        });
    }

    private static void nav(Context c, jui.ui.IntRef page, String id, String glyph,
                            int pages, int delta) {
        Element b = buttonBase(c).size(24, 24).radius(Kit.RADIUS_XS).border(1, Kit.line(c))
                .label(id)
                .children(() -> box(c).size(12, 12).draw(Icon.draw(glyph, c.theme().text())));
        if (b.clicked()) page.set(Math.max(0, Math.min(pages - 1, page.get() + delta)));
    }

    private Carousel() {}
}
