package jui.kit;

import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's rating: N clickable stars driven by an IntRef (1-based, 0 = no
 * rating; clicking the current star clears it), after gpui-kit's Rating.
 */
public final class Rating {
    public static Element rating(Context c, jui.ui.IntRef value, int max, float size) {
        return row(c).gap(2).label("kit-rating").children(() -> {
            int current = value.get();
            for (int i = 1; i <= max; i++) {
                int star = i;
                boolean filled = star <= current;
                Element b = buttonBase(c).size(size, size).shrink(0).label("kit-star-" + star)
                        .children(() -> box(c).size(size, size).draw(starDraw(filled)));
                if (b.clicked()) {
                    value.set(current == star ? 0 : star);           // click the same star to clear
                }
            }
        });
    }

    static jui.ui.Painter.Draw starDraw(boolean filled) {
        return (p, b) -> {
            float u = Math.min(b.width(), b.height()) / 16;
            float cx = b.x() + b.width() / 2, cy = b.y() + b.height() / 2;
            jui.ui.Color ink = filled ? jui.ui.Color.rgb(0xF5A623) : jui.ui.Color.rgb(0x808080);
            for (int i = 0; i < 5; i++) {
                double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
                double a2 = a + Math.PI / 5;
                double a3 = a + 2 * Math.PI / 5;
                p.line(cx + (float) Math.cos(a) * 7 * u, cy + (float) Math.sin(a) * 7 * u,
                        cx + (float) Math.cos(a2) * 2.8f * u, cy + (float) Math.sin(a2) * 2.8f * u, 1.8f * u, ink);
                p.line(cx + (float) Math.cos(a2) * 2.8f * u, cy + (float) Math.sin(a2) * 2.8f * u,
                        cx + (float) Math.cos(a3) * 7 * u, cy + (float) Math.sin(a3) * 7 * u, 1.8f * u, ink);
            }
        };
    }

    private Rating() {}
}
