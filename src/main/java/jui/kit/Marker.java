package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's marker: a small status dot with an optional count, the color
 * carried by the variant — the annotation marks lists and trees use.
 */
public final class Marker {
    public static Element marker(Context c, Kit.Variant v, String count) {
        Kit.Tone tone = Kit.tone(c, v);
        boolean dotOnly = count == null || count.isEmpty();
        return row(c).gap(4).alignItems(Align.CENTER).shrink(0).label("kit-marker").children(() -> {
            box(c).size(7, 7).radius(3.5f).background(tone.fill());
            if (!dotOnly) {
                text(c, count).fontSize(10.5f).textColor(c.theme().textMuted());
            }
        });
    }

    /** Just the colored dot. */
    public static Element marker(Context c, Kit.Variant v) {
        return marker(c, v, "");
    }

    private Marker() {}
}
