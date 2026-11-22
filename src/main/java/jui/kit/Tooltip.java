package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's tooltip, after the Tooltip: short supporting text for
 * unfamiliar controls. The hint sits in a dark panel under the anchor with
 * the shadow; the panel fades in on the snappy run and the popover flips
 * above when the window edge would clip it.
 */
public final class Tooltip {

    public static Element over(Context c, Element anchor, String text) {
        jui.ui.BoolRef open = new jui.ui.BoolRef(anchor.hovered());
        return popover(c, anchor, open, () -> {
            Element tip = box(c).padding(4, 8).radius(6)
                    .background(Color.rgba(0x000000, 0.85f))
                    .label("kit-tooltip")
                    .children(() -> text(c, text).singleLine().fontSize(11)
                            .textColor(Color.rgb(0xFFFFFF)));
            tip.shadow(0, 4, 12, -2, Color.rgba(0x000000, 0.3f));
            float a = Kit.spring(tip, "in", 1, Kit.SNAPPY);
            tip.opacity(a);
        });
    }

    private Tooltip() {}
}
