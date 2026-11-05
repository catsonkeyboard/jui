package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's stepper: a value flanked by − and + buttons, bounded and stepping,
 * after gpui-kit's Stepper.
 */
public final class Stepper {
    public static Element stepper(Context c, jui.ui.DoubleRef value, double min, double max,
                                  double step, String format) {
        Element root = row(c).gap(6).alignItems(Align.CENTER).label("kit-stepper").children(() -> {
            Element minus = buttonBase(c).size(24, 24).radius(Kit.RADIUS_XS)
                    .border(1, Kit.line(c))
                    .label("kit-stepper-minus")
                    .children(() -> box(c).size(12, 12).draw(Icon.draw("minus", c.theme().text())));
            if (minus.clicked()) value.set(Math.max(min, value.get() - step));
            text(c, String.format(format, value.get())).fontSize(12.5f).textColor(c.theme().text());
            Element plus = buttonBase(c).size(24, 24).radius(Kit.RADIUS_XS)
                    .border(1, Kit.line(c))
                    .label("kit-stepper-plus")
                    .children(() -> box(c).size(12, 12).draw(Icon.draw("plus", c.theme().text())));
            if (plus.clicked()) value.set(Math.min(max, value.get() + step));
        });
        return root;
    }

    private Stepper() {}
}
