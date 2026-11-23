package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's image compare, after the ImageCompare: drag a divider across
 * two images to see what changed. The divider follows the drag, the before
 * pane clips at it, and the handle reads as a grip of two chevrons. The
 * framework has no image decoder, so the two sides draw through the
 * caller's Painter.Draw pairs.
 */
public final class ImageCompare {

    /**
     * The comparer: width, height, the before and after draws, and the
     * split ref (0..1) the caller keeps.
     */
    public static Element imageCompare(Context c, float width, float height,
                                       jui.ui.Painter.Draw before, jui.ui.Painter.Draw after,
                                       Ref<Float> split) {
        Element host = box(c).size(width, height).radius(Kit.RADIUS + 2).clip()
                .background(c.theme().surface()).label("kit-image-compare");
        // The after pane, full.
        host.children(() -> {
            Element rightPane = box(c).absolute().left(0).top(0).right(0).bottom(0);
            rightPane.draw(after);
            // The before pane, clipped at the split.
            float f = split.get() == null ? 0.5f : split.get();
            float anim = Kit.spring(host, "split", f, Kit.SNAPPY);
            Element leftPane = box(c).absolute().left(0).top(0).bottom(0).clip();
            leftPane.width(width * anim);
            leftPane.draw(before);
            // The divider with its grip.
            Element handle = box(c).absolute().top(0).bottom(0).width(24).alignItems(Align.CENTER)
                    .draggable().cursor(jui.ui.Cursor.RESIZE_EW).label("kit-compare-handle");
            handle.left(width * anim - 12);
            handle.children(() -> {
                box(c).size(24, 24).radius(12).background(Color.rgb(0xFFFFFF))
                        .shadow(0, 1, 4, 0, Color.rgba(0x000000, 0.3f))
                        .alignItems(Align.CENTER)
                        .children(() -> row(c).gap(1).children(() -> {
                            box(c).size(6, 8).shrink(0).draw((p, b) -> {
                                float u = b.height() / 8;
                                p.line(b.x() + 4 * u, b.y() + 2 * u, b.x() + 2 * u, b.y() + 4 * u, 1.4f * u,
                                        Color.rgb(0x333333));
                                p.line(b.x() + 2 * u, b.y() + 4 * u, b.x() + 4 * u, b.y() + 6 * u, 1.4f * u,
                                        Color.rgb(0x333333));
                            });
                            box(c).size(6, 8).shrink(0).draw((p, b) -> {
                                float u = b.height() / 8;
                                p.line(b.x() + 2 * u, b.y() + 2 * u, b.x() + 4 * u, b.y() + 4 * u, 1.4f * u,
                                        Color.rgb(0x333333));
                                p.line(b.x() + 4 * u, b.y() + 4 * u, b.x() + 2 * u, b.y() + 6 * u, 1.4f * u,
                                        Color.rgb(0x333333));
                            });
                        }));
                box(c).absolute().top(0).bottom(0).width(2).background(Color.rgb(0xFFFFFF));
            });
            if (handle.pressed() && handle.dragX() != 0) {
                split.set(Math.max(0, Math.min(1, f + handle.dragX() / width)));
                c.invalidate();
            }
        });
        return host;
    }

    private ImageCompare() {}
}
