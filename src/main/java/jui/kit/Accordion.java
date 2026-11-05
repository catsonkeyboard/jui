package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's accordion: titled sections of which one is open at a time
 * (multiple=true lets several stay open), after gpui-kit's Accordion.
 */
public final class Accordion {
    /** One titled section; content builds only while open. */
    public record Item(String title, Runnable content) {}

    public static Item item(String title, Runnable content) {
        return new Item(title, content);
    }

    /** Single-open accordion driven by an IntRef of the open index (-1 = none). */
    public static Element accordion(Context c, jui.ui.IntRef openIndex, boolean multiple,
                                    boolean bordered, Item... items) {
        jui.ui.BoolRef[] open = new jui.ui.BoolRef[items.length];
        for (int i = 0; i < items.length; i++) {
            open[i] = new jui.ui.BoolRef(openIndex.get() == i);
        }
        return column(c).fillWidth().gap(2).children(() -> {
            for (int i = 0; i < items.length; i++) {
                int idx = i;
                Item it = items[idx];
                Element head = buttonBase(c).fillWidth().height(32).padding(0, 12)
                        .label("kit-acc-" + it.title())
                        .justify(Justify.START).gap(8)
                        .children(() -> {
                            text(c, it.title()).fontSize(12.5f).textColor(c.theme().text());
                            spacer(c).grow(1);
                            box(c).size(14, 14)
                                    .draw(Icon.draw(open[idx].get() ? "chevron-up" : "chevron-down",
                                            c.theme().textMuted()));
                        });
                if (bordered) head.border(1, Kit.line(c));
                head.background(c.theme().surface());
                if (head.clicked()) {
                    if (multiple) open[idx].set(!open[idx].get());
                    else {
                        openIndex.set(open[idx].get() ? -1 : idx);
                        for (int k = 0; k < open.length; k++) open[k].set(k == openIndex.get());
                    }
                }
                if (open[idx].get()) {
                    box(c).fillWidth().padding(10, 12).children(it.content());
                }
            }
        });
    }

    private Accordion() {}
}
