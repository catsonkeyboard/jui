package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's image: this framework has no image decoder, so the component
 * renders a fitted placeholder tile (photo glyph + optional label) sized as
 * asked — swap in a Painter.Draw of real pixels via {@code image(c, draw, w, h)}.
 */
public final class Image {
    public static Element image(Context c, jui.ui.Painter.Draw pixels, float w, float h, float radius) {
        return box(c).size(w, h).radius(radius).clip().draw(pixels).label("kit-image");
    }

    public static Element placeholder(Context c, float w, float h, float radius, String label) {
        return box(c).size(w, h).radius(radius)
                .background(c.theme().surface())
                .border(1, Kit.line(c))
                .label("kit-image-placeholder")
                .alignItems(Align.CENTER)
                .children(() -> column(c).gap(4).alignItems(Align.CENTER).children(() -> {
                    box(c).size(20, 20).draw(Icon.draw("photo", c.theme().textMuted()));
                    if (label != null && !label.isEmpty()) {
                        text(c, label).fontSize(10.5f).textColor(c.theme().textMuted());
                    }
                }));
    }

    private Image() {}
}
