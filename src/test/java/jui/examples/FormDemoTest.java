package jui.examples;

import java.util.List;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

public final class FormDemoTest {
    public void testEnterSendsTheMessageAndClearsTheBox() {
        FormDemo s = new FormDemo();
        Tester tt = Tester.of(s::view, 480, 600);
        tt.click("消息框");
        tt.type("你好");
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(List.of("你好"), s.messages);
        Check.equal("", s.draft.get());
        Check.isTrue(tt.hasText("你好"), "listed under the box");
    }

    public void testShowPasswordShowsItsText() {
        FormDemo s = new FormDemo();
        s.password.set("secret");
        Tester tt = Tester.of(s::view, 480, 600);
        Check.equal("••••••", tt.shownText("密码框"));
        tt.click("显示密码");
        Check.equal("secret", tt.shownText("密码框"));
    }
}
