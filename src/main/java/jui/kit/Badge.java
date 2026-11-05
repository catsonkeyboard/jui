package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Justify;

import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's badge: a small count or status bubble, after gpui-kit's Badge —
 * variants color it, a dot-only mode drops the text.
 */
public final class Badge {
    public static Element badge(Context c, String text, Kit.Variant v) {
        Kit.Tone tone = Kit.tone(c, v);
        boolean dot = text == null || text.isEmpty();
        return box(c).height(16).padding(0, dot ? 6 : 5).radius(8)
                .background(tone.fill()).label("kit-badge-" + (dot ? "dot" : text))
                .justify(Justify.START)
                .children(() -> {
                    if (!dot) {
                        text(c, text).fontSize(10).textColor(tone.text());
                    }
                });
    }

    /** A count badge that caps at 99+, the notification style. */
    public static Element badge(Context c, int count) {
        return badge(c, count <= 0 ? "" : count > 99 ? "99+" : Integer.toString(count), Kit.Variant.DANGER);
    }

    private Badge() {}
}
