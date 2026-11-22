package jui.kit;

import java.nio.file.Path;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's image: a fitted picture behind a rounded box, decoded from a
 * PNG by the framework's pure-Java reader — real pixels now, where the
 * framework once had no decoder at all. The placeholder face stays for
 * paths that fail to read, and a Painter.Draw still fits custom pixels.
 */
public final class Image {

    /**
     * The picture: an image atlas id (from {@code Images.load}), a fitted
     * size, and one corner radius.
     */
    public static Element image(Context c, int atlasId, float w, float h, float radius) {
        return box(c).size(w, h).radius(radius).background(c.theme().surface())
                .image(atlasId, radius).label("kit-image");
    }

    /**
     * The picture from a path, decoded once and cached by the atlas; a
     * failed read falls back to the placeholder tile.
     */
    public static Element imageFile(Context c, Path path, float w, float h, float radius, String label) {
        try {
            int id = jui.render.Images.load(path);
            return image(c, id, w, h, radius);
        } catch (Exception e) {
            return placeholder(c, w, h, radius, label != null ? label : path.getFileName().toString());
        }
    }

    /** The fitted pixels: a Painter.Draw clipped to a rounded box. */
    public static Element image(Context c, jui.ui.Painter.Draw pixels, float w, float h, float radius) {
        return box(c).size(w, h).radius(radius).clip().draw(pixels).label("kit-image");
    }

    public static Element placeholder(Context c, float w, float h, float radius, String label) {
        return box(c).size(w, h).radius(radius)
                .background(c.theme().surface())
                .border(1, Kit.line(c))
                .label("kit-image-placeholder")
                .alignItems(Align.CENTER)
                .justify(Justify.CENTER)
                .children(() -> column(c).gap(4).alignItems(Align.CENTER).children(() -> {
                    box(c).size(20, 20).draw(Icon.draw("photo", c.theme().textMuted()));
                    if (label != null && !label.isEmpty()) {
                        text(c, label).fontSize(10.5f).textColor(c.theme().textMuted());
                    }
                }));
    }

    private Image() {}
}
