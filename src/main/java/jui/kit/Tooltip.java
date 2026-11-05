package jui.kit;

import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's tooltip: hover an anchor and a small dark hint appears beneath it,
 * after gpui-kit's Tooltip (placement bottom first; the framework's popover
 * flips above when the window edge would clip it).
 */
public final class Tooltip {
    public static Element over(Context c, Element anchor, String text) {
        jui.ui.BoolRef open = new jui.ui.BoolRef(anchor.hovered());
        return popover(c, anchor, open, () -> {
            box(c).padding(4, 8).radius(6)
                    .background(jui.ui.Color.rgba(0x000000, 0.85f))
                    .label("kit-tooltip")
                    .children(() -> text(c, text).fontSize(11)
                            .textColor(jui.ui.Color.rgb(0xFFFFFF)));
        });
    }

    private Tooltip() {}
}
