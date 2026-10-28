package jui.examples;

import jui.testing.Check;
import jui.ui.*;

public final class TextAreaDemoTest {
    public void testEnterSendsButShiftEnterKeepsTheLineBreak() {
        TextAreaDemo demo = new TextAreaDemo();
        Tester tester = Tester.of(demo::view, 540, 720);
        tester.click("消息框");
        tester.type("第一行");
        tester.key(Shortcut.of(Mod.SHIFT, Key.ENTER));
        tester.type("第二行");
        tester.key(Shortcut.of(Key.ENTER));
        Check.equal("第一行\n第二行", demo.messages.get(0));
        Check.equal("", demo.draft.get());
    }

    public void testSendingDoesNotResetTheNotesScroll() {
        TextAreaDemo demo = new TextAreaDemo();
        Tester tester = Tester.of(demo::view, 540, 720);
        tester.scroll("备注框", 0, -100);
        float before = tester.textScrollY("备注框");
        tester.click("消息框");
        tester.type("hi");
        tester.key(Shortcut.of(Key.ENTER));
        Check.near(before, tester.textScrollY("备注框"));
    }
}
