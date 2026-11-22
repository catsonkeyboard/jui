package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's accordion, after the Accordion: progressively reveal
 * supporting information in place. The chevron turns on the snappy run,
 * the section's height glides on the morph run, and the divider under each
 * header keeps the rows reading as one surface.
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
        Element host = column(c).fillWidth().gap(0).label("kit-accordion");
        host.children(() -> {
            for (int i = 0; i < items.length; i++) {
                int idx = i;
                Item it = items[idx];
                column(c).fillWidth().children(() -> {
                    Element head = buttonBase(c).fillWidth().height(38).padding(0, 12)
                            .label("kit-acc-" + it.title())
                            .justify(Justify.START).gap(8);
                    if (head.hovered()) {
                        head.background(c.theme().surfaceHover());
                    }
                    head.children(() -> {
                        text(c, it.title()).fontSize(12.5f).textColor(c.theme().text());
                        spacer(c).grow(1);
                        // The chevron turns while the section opens.
                        float t = KitMotion.spring(host, "chev" + idx, open[idx].get() ? 1 : 0, KitMotion.SNAPPY);
                        box(c).size(14, 14).shrink(0).draw((p, b) -> {
                            float u = Math.min(b.width(), b.height()) / 16;
                            float a = (float) Math.PI * t;
                            float dy = (float) Math.cos(a) * 3 * u;
                            p.line(b.x() + 4.5f * u, b.y() + (8 - dy) * u, b.x() + 8 * u, b.y() + 8 * u,
                                    1.5f * u, c.theme().textMuted());
                            p.line(b.x() + 8 * u, b.y() + 8 * u, b.x() + 11.5f * u,
                                    b.y() + (8 - dy) * u, 1.5f * u, c.theme().textMuted());
                        });
                    });
                    if (head.clicked()) {
                        if (multiple) {
                            open[idx].set(!open[idx].get());
                        } else {
                            openIndex.set(open[idx].get() ? -1 : idx);
                            for (int k = 0; k < open.length; k++) {
                                open[k].set(k == openIndex.get());
                            }
                        }
                        c.invalidate();
                    }
                    // The section's height glides open and shut; the body
                    // builds as soon as it is open, clipped by the height.
                    float h = KitMotion.spring(host, "h" + idx, open[idx].get() ? 1 : 0, KitMotion.MORPH);
                    if (open[idx].get() || h > 0.01f) {
                        Element body = box(c).fillWidth().clip().padding(0, 12, 10, 12)
                                .children(it.content());
                        body.opacity(Math.max(0.5f, h));
                    }
                    divider(c);
                });
            }
        });
        return host;
    }

    private Accordion() {}
}
