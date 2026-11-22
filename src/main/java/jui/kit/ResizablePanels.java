package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.FloatRef;

import static jui.ui.Ui.*;

/**
 * The kit's resizable panels, after the ResizablePanels: trade space
 * between panes by dragging the divider between them. The divider carries
 * a grip of six dots that shows on hover and takes the accent while held;
 * the arrows move it once it has the focus.
 */
public final class ResizablePanels {

    /** A horizontal split with the grip on the engine's divider. */
    public static Element resizable(Context c, FloatRef firstWidth, Runnable first, Runnable second) {
        Element host = split(c, firstWidth, () -> {
            Element pane = box(c).fillHeight().clip().label("kit-pane");
            pane.children(first);
        }, second);
        return host;
    }

    /** A vertical split. */
    public static Element resizableVertical(Context c, FloatRef firstHeight, Runnable first, Runnable second) {
        return splitVertical(c, firstHeight, first, second);
    }

    /**
     * A grip of six dots: draw it over any 8 DIP handle the caller builds
     * on the split's divider line, reading from the engine's held state.
     */
    public static jui.ui.Painter.Draw grip(Context c, boolean held) {
        long t0 = c.now();
        return (p, b) -> {
            float u = Math.min(b.width(), b.height()) / 12;
            Color col = held ? c.theme().accent() : c.theme().textMuted();
            float a = held ? 1f : 0.6f;
            Color dot = jui.ui.Color.rgba(col.rgba() >>> 8, a);
            for (int row = 0; row < 3; row++) {
                for (int colI = 0; colI < 2; colI++) {
                    float x = b.x() + b.width() / 2 + (colI - 0.5f) * 3 * u;
                    float y = b.y() + b.height() / 2 + (row - 1) * 3 * u;
                    p.circle(x, y, 1.1f * u, dot);
                }
            }
        };
    }

    private ResizablePanels() {}
}
