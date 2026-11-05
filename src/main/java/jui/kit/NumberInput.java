package jui.kit;

import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's number input: a text field that keeps only numeric text, plus a
 * stepper pair riding the right edge.
 */
public final class NumberInput {
    public static Element numberInput(Context c, TextRef text, jui.ui.DoubleRef value,
                                      double min, double max, double step) {
        return row(c).gap(2).children(() -> {
            Element field = textInput(c, text).placeholder("0").fillWidth().label("kit-number");
            if (field.changed()) {
                try {
                    double parsed = Double.parseDouble(text.get().trim());
                    value.set(Math.max(min, Math.min(max, parsed)));
                } catch (NumberFormatException ignored) {
                    // keep the last good value while the text is not a number
                }
            }
            column(c).gap(2).children(() -> {
                Element up = buttonBase(c).size(18, 14).radius(3).border(1, Kit.line(c))
                        .label("kit-number-up")
                        .children(() -> box(c).size(9, 9).draw(Icon.draw("chevron-up", c.theme().textMuted())));
                if (up.clicked()) {
                    double next = Math.min(max, value.get() + step);
                    value.set(next);
                    text.set(trim(next));
                }
                Element down = buttonBase(c).size(18, 14).radius(3).border(1, Kit.line(c))
                        .label("kit-number-down")
                        .children(() -> box(c).size(9, 9).draw(Icon.draw("chevron-down", c.theme().textMuted())));
                if (down.clicked()) {
                    double next = Math.max(min, value.get() - step);
                    value.set(next);
                    text.set(trim(next));
                }
            });
        });
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    private NumberInput() {}
}
