package jui.ui;

import jui.testing.Check;
import jui.text.TextLayout;
import static jui.ui.Ui.*;

public final class TextAreaPointerTest {
    public void testClickAndDragUseBothCoordinates() {
        TextRef value = new TextRef("ab\ncd\nef");
        Tester tester = Tester.of(context -> textArea(context, value).fontSize(13).size(220, 120).label("area"), 300, 240);
        Rect area = tester.find("area");
        TextLayout layout = tester.engine().text().layout(value.get(), 13, 400, 198);
        float x = area.x() + 11;
        float secondY = area.y() + 7 + layout.lineTop(1) + layout.lineHeight(1) / 2;
        tester.press(x, secondY);
        tester.release(x, secondY);
        Check.equal("[3, 3]", java.util.Arrays.toString(tester.textSelection()));
        tester.press(x, area.y() + 8);
        tester.move(x + 100, secondY);
        tester.release(x + 100, secondY);
        Check.equal("[0, 5]", java.util.Arrays.toString(tester.textSelection()));
    }

    public void testTripleClickSelectsTheVisualLineOnly() {
        TextRef value = new TextRef("ab\ncd\nef");
        Tester tester = Tester.of(context -> textArea(context, value).fontSize(13).size(220, 120).label("area"), 300, 240);
        Rect area = tester.find("area");
        TextLayout layout = tester.engine().text().layout(value.get(), 13, 400, 198);
        float x = area.x() + 12, y = area.y() + 8 + layout.lineTop(1);
        for (int clicks = 1; clicks <= 3; clicks++) {
            tester.engine().pointerDown(x, y, 0, clicks, 0);
            tester.engine().pointerUp(x, y, 0, 0);
        }
        tester.frame();
        Check.equal("[3, 5]", java.util.Arrays.toString(tester.textSelection()));
    }
}
