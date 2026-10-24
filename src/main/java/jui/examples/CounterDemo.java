package jui.examples;

import java.util.Arrays;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.Context;
import jui.ui.DoubleRef;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Theme;

import static jui.ui.Ui.button;
import static jui.ui.Ui.column;
import static jui.ui.Ui.primaryButton;
import static jui.ui.Ui.row;
import static jui.ui.Ui.slider;
import static jui.ui.Ui.text;

/**
 * A counter, as MyGO's examples/counter-native, with a slider for its step:
 * the buttons and the arrow keys count by it.
 *
 * <pre>./build.sh demo Counter</pre>
 */
public final class CounterDemo {
    private static final Shortcut UP = Shortcut.of(Key.UP), DOWN = Shortcut.of(Key.DOWN);

    int n;
    final DoubleRef step = new DoubleRef(1);

    void view(Context c) {
        int by = (int) step.get();
        if (c.shortcut(UP)) {
            n += by;
        }
        if (c.shortcut(DOWN)) {
            n -= by;
        }
        Theme t = c.theme();
        column(c).fill().center().gap(16).children(() -> {
            text(c, Integer.toString(n)).fontSize(56).bold();
            row(c).gap(8).children(() -> {
                if (button(c.key("dec"), "−").label("减少").width(44).clicked()) {
                    n -= by;
                }
                if (button(c.key("reset"), "重置").disabled(n == 0).clicked()) {
                    n = 0;
                }
                if (primaryButton(c.key("inc"), "+").label("增加").width(44).clicked()) {
                    n += by;
                }
            });
            row(c).gap(10).children(() -> {
                text(c, "步长 " + (int) step.get());
                slider(c, step, 1, 10, 1).width(160).label("步长");
            });
            text(c, "↑ 和 ↓ 也能计数").fontSize(12).textColor(t.textMuted());
        });
    }

    public static void main(String[] args) {
        CounterDemo s = new CounterDemo();
        App.run(() -> {
            Window w = Window.open(WindowOptions.of("计数器").size(360, 300).content(s::view));
            if (Arrays.asList(args).contains("--print-window")) {
                System.out.println("window=" + w.windowNumber());
            }
        });
    }
}
