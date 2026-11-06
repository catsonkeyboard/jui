package jui.kit;

import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's kbd: a keyboard key chip, as gpui-kit's Kbd — a small bordered
 * square-ish pill with the keycap legend centered.
 */
public final class Kbd {
    public static Element kbd(Context c, String keys) {
        return box(c).height(18).padding(0, 5).radius(4).shrink(0)
                .border(1, Kit.line(c))
                .background(c.theme().surface())
                .label("kit-kbd-" + keys)
                .justify(Justify.START)
                .children(() -> text(c, keys).fontSize(10).textColor(c.theme().textMuted()));
    }

    /** A shortcut shown as separate caps: kbd(c, "⌘", "K"). */
    public static Element kbd(Context c, String... keys) {
        return row(c).gap(2).children(() -> {
            for (String k : keys) {
                kbd(c, k);
            }
        });
    }

    private Kbd() {}
}
