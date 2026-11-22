package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's popover, after the Popover: a small anchored surface for
 * contextual information. The panel sits under the anchor with the
 * floating shadow, fades in on the morph run, and closes on Escape, an
 * outside press, or a pick inside; dismissed() reports the close.
 */
public final class Popover {

    /** The panel: the caller styles it; open is the caller's ref. */
    public static Element popover(Context c, Element anchor, BoolRef open, Runnable content) {
        return popover(c, anchor, open, content, 240);
    }

    public static Element popover(Context c, Element anchor, BoolRef open, Runnable content,
                                  float minWidth) {
        Element panel = jui.ui.Ui.popover(c, anchor, open, () -> {
            Element p = box(c).padding(12).gap(6).minWidth(minWidth)
                    .background(c.theme().background()).radius(Kit.RADIUS + 2)
                    .border(1, Kit.hairline(c)).label("kit-popover");
            Kit.floatShadow(p);
            float a = Kit.spring(p, "in", 1, Kit.MORPH);
            p.opacity(a);
            content.run();
        });
        return panel;
    }

    /** A trigger + popover in one: a quiet button that opens the panel. */
    public static Element info(Context c, String label, String text) {
        Element host = row(c).label("kit-popover-info");
        BoolRef open = localBool(host, "open", false);
        Element trigger = buttonBase(c).height(28).padding(0, 9).gap(5).radius(Kit.RADIUS_XS + 2)
                .label("kit-popover-trigger");
        if (trigger.hovered() || open.get()) {
            trigger.background(c.theme().surfaceHover());
        }
        trigger.children(() -> {
            box(c).size(13, 13).shrink(0).draw(Icon.draw("info-circle",
                    open.get() ? c.theme().accent() : c.theme().textMuted()));
            text(c, label).singleLine().fontSize(12).textColor(c.theme().text());
        });
        if (trigger.clicked()) {
            open.set(!open.get());
        }
        popover(c, trigger, open, () -> text(c, text).fontSize(11.5f)
                .textColor(c.theme().textMuted()));
        return host;
    }

    private Popover() {}
}
