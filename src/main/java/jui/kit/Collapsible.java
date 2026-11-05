package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's collapsible: one titled section that folds away, after gpui-kit's
 * Collapsible — a one-item accordion with the openness owned by the caller.
 */
public final class Collapsible {
    public static Element collapsible(Context c, jui.ui.BoolRef open, String title,
                                      boolean bordered, Runnable content) {
        return column(c).fillWidth().gap(2).children(() -> {
            Element head = buttonBase(c).fillWidth().height(32).padding(0, 12)
                    .label("kit-collapse-" + title)
                    .justify(Justify.START).gap(8)
                    .children(() -> {
                        text(c, title).fontSize(12.5f).textColor(c.theme().text());
                        spacer(c).grow(1);
                        box(c).size(14, 14).draw(Icon.draw(open.get() ? "chevron-up" : "chevron-down",
                                c.theme().textMuted()));
                    });
            if (bordered) head.border(1, Kit.line(c));
            head.background(c.theme().surface());
            if (head.clicked()) open.set(!open.get());
            if (open.get()) {
                box(c).fillWidth().padding(10, 12).children(content);
            }
        });
    }

    private Collapsible() {}
}
