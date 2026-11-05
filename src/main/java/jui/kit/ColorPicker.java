package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's color picker: a swatch over R/G/B sliders, the color reported to
 * the caller on any channel change, after gpui-kit's ColorPicker (whose SV
 * square needs pointer picking this v1 builds from sliders).
 */
public final class ColorPicker {
    public static Element colorPicker(Context c, Color initial, float width,
                                      java.util.function.Consumer<Color> onChange) {
        int rgba = initial.rgba() >>> 8;
        jui.ui.DoubleRef r = new jui.ui.DoubleRef((rgba >> 16) & 0xFF);
        jui.ui.DoubleRef g = new jui.ui.DoubleRef((rgba >> 8) & 0xFF);
        jui.ui.DoubleRef b = new jui.ui.DoubleRef(rgba & 0xFF);
        return column(c).width(width).gap(8).children(() -> {
            Color now = Color.rgb(((int) r.get() << 16) | ((int) g.get() << 8) | (int) b.get());
            box(c).height(28).radius(Kit.RADIUS_SM).border(1, Kit.line(c))
                    .background(now).label("kit-color-swatch");
            channel(c, "R", r, () -> onChange.accept(now));
            channel(c, "G", g, () -> onChange.accept(now));
            channel(c, "B", b, () -> onChange.accept(now));
        });
    }

    private static void channel(Context c, String name, jui.ui.DoubleRef v, Runnable changed) {
        row(c).gap(8).alignItems(Align.CENTER).children(() -> {
            text(c, name).fontSize(10.5f).textColor(c.theme().textMuted()).width(14);
            Element s = sliderBase(c, v, 0, 255, 1).fillWidth();
            if (s.changed()) changed.run();
        });
    }

    private ColorPicker() {}
}
