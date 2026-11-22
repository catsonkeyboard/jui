package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's scroll area, after the ScrollArea: a native scroll container
 * with edge fades that appear at the top and bottom of the view. The fades
 * read as two alpha steps of the background, the closest the engine's
 * primitives come to a gradient.
 */
public final class ScrollArea {

    /** The container: a fixed height box that scrolls its content. */
    public static Element scrollArea(Context c, float height, Runnable content) {
        Element host = box(c).height(height).clip().label("kit-scroll-area").alignItems(Align.STRETCH);
        host.children(() -> {
            Element inner = scroll(c).fillWidth().alignItems(Align.STRETCH);
            inner.children(content);
            box(c).absolute().left(0).right(0).top(0).height(10)
                    .background(fade(c, 0.85f)).label("kit-fade-top");
            box(c).absolute().left(0).right(0).bottom(0).height(10)
                    .background(fade(c, 0.85f)).label("kit-fade-bottom");
        });
        return host;
    }

    private static Color fade(Context c, float a) {
        return Color.rgba(c.theme().background().rgba() >>> 8, a);
    }

    private ScrollArea() {}
}
