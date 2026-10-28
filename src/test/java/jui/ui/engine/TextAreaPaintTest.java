package jui.ui.engine;

import jui.testing.Check;
import jui.ui.*;
import static jui.ui.Ui.*;

public final class TextAreaPaintTest {
    public void testOnlyVisibleRowsEmitGlyphs() {
        TextRef value = new TextRef("visible row\n".repeat(1000));
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 100).label("area"), 300, 200);
        tester.frame();
        Check.isTrue(tester.instances() < 200, "offscreen glyphs are not emitted: " + tester.instances());
    }

    public void testASelectionPaintsEveryCoveredLine() {
        TextRef value = new TextRef("a\n\nb");
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 120).label("area")
                .setTextSelection(0, value.get().length()), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        int boxes = 0;
        int color = Theme.light().selection().rgba();
        for (int index = 0; index < tester.instances(); index++) {
            float[] instance = tester.scene().instance(index);
            if (instance[18] == 0 && Math.round(instance[8] * 255) == (color >>> 24)
                    && Math.round(instance[9] * 255) == ((color >>> 16) & 255)
                    && Math.round(instance[10] * 255) == ((color >>> 8) & 255)
                    && Math.round(instance[11] * 255) == (color & 255)) boxes++;
        }
        Check.equal(3, boxes);
    }
}
