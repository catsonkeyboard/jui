package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's badge, after the Badge: a small label for status, category,
 * or metadata. The solid face takes the variant's fill, the soft face a
 * tint of it; a dot-only mode drops the text, and counts cap at 99+.
 */
public final class Badge {

    /** The badge: solid or soft, the text or a dot. */
    public static Element badge(Context c, String text, Kit.Variant v, boolean soft) {
        Kit.Tone tone = Kit.tone(c, v);
        boolean dot = text == null || text.isEmpty();
        Color fill = soft ? Color.rgba(tone.fill().rgba() >>> 8, 0.16f) : tone.fill();
        Color text2 = soft ? tone.fill() : tone.text();
        Element b = box(c).height(16).padding(0, dot ? 6 : 5).radius(8).shrink(0)
                .background(fill).label("kit-badge-" + (dot ? "dot" : text))
                .justify(Justify.START)
                .children(() -> {
                    if (!dot) {
                        text(c, text).fontSize(10).textColor(text2);
                    }
                });
        return b;
    }

    public static Element badge(Context c, String text, Kit.Variant v) {
        return badge(c, text, v, false);
    }

    /** A count badge that caps at 99+, the notification style. */
    public static Element badge(Context c, int count) {
        return badge(c, count <= 0 ? "" : count > 99 ? "99+" : Integer.toString(count),
                Kit.Variant.DANGER);
    }

    /** The dot-only badge, no text at all. */
    public static Element badgeDot(Context c) {
        return badge(c, "", Kit.Variant.DANGER);
    }

    private Badge() {}
}
