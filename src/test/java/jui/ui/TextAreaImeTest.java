package jui.ui;

import jui.testing.Check;
import static jui.ui.Ui.*;

public final class TextAreaImeTest {
    public void testRangeRectsUseTheirOwnLineAndOriginalUtf16Range() {
        TextRef value = new TextRef("a😀\r\n中");
        Tester tester = Tester.of(context -> textArea(context, value).size(240, 140).label("area"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        float[] first = new float[6], second = new float[6];
        tester.engine().textRect(2, 3, first);
        tester.engine().textRect(5, 6, second);
        Check.near(1, first[5]);
        Check.near(3, first[4]);
        Check.isTrue(second[1] > first[1], "requested range chooses its own line");
        Check.near(5, second[5]);
        Check.near(6, second[4]);
    }

    public void testCaretQuerySeesInsertionBeforeTheNextFrame() {
        TextRef value = new TextRef("a");
        Tester tester = Tester.of(context -> textArea(context, value).size(240, 140).label("area"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.CTRL, Key.END));
        float[] before = new float[4], after = new float[4];
        tester.engine().textCaretRect(before);
        tester.engine().textInsert("\na", -1, 0);
        tester.engine().textCaretRect(after);
        Check.isTrue(after[1] > before[1], "native query uses fresh layout");
    }

    public void testCompositionWrapsWithoutWritingTheRefAndUndoIsOneStep() {
        TextRef value = new TextRef("prefix\n");
        Tester tester = Tester.of(context -> textArea(context, value).size(110, 140).label("area"), 200, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.CTRL, Key.END));
        tester.compose("ni ".repeat(15), 45);
        Check.equal("prefix\n", value.get());
        Check.isTrue(tester.textCaret().y() > tester.find("area").y() + 25, "composing caret wraps");
        tester.type("你好");
        Check.equal("prefix\n你好", value.get());
        tester.command(EditCommand.UNDO);
        Check.equal("prefix\n", value.get());
    }
}
