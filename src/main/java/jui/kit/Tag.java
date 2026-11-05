package jui.kit;

import jui.ui.Context;
import jui.ui.Align;

import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's tag: a labeled pill in a tone color, optionally with a leading
 * dot or a dismiss ×, after gpui-kit's Tag.
 */
public final class Tag {
    /** tagKind picks the pill's look: filled, soft, or outlined. */
    public enum Kind { FILLED, SOFT, OUTLINE }

    public static Element tag(Context c, String text, Kit.Variant v, Kind kind, boolean dot,
                              boolean dismissible, Runnable onDismiss) {
        Kit.Tone tone = Kit.tone(c, v);
        Element t = row(c).height(20).padding(0, 8).gap(5).radius(10)
                .label("kit-tag-" + text)
                .alignItems(Align.CENTER)
                .children(() -> {
                    if (dot) {
                        box(c).size(6, 6).radius(3).background(tone.fill());
                    }
                    text(c, text).fontSize(11).textColor(kind == Kind.FILLED ? tone.text()
                            : kind == Kind.SOFT ? tone.fill() : tone.fill());
                    if (dismissible) {
                        Element x = buttonBase(c).size(12, 12).radius(6)
                                .children(() -> box(c).size(8, 8).draw(Icon.draw("xmark", tone.fill())));
                        if (x.clicked() && onDismiss != null) onDismiss.run();
                    }
                });
        switch (kind) {
            case FILLED -> t.background(tone.fill());
            case SOFT -> t.background(ColorSoft(c, tone.fill()));
            case OUTLINE -> t.border(1, tone.fill());
        }
        return t;
    }

    private static jui.ui.Color ColorSoft(Context c, jui.ui.Color fill) {
        return jui.ui.Color.rgba(fill.rgba() >>> 8, 0.18f);
    }

    private Tag() {}
}
