package jui.examples;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

public final class TodoDemoTest {
    public void testCheckDeleteAddAndHide() {
        TodoDemo s = new TodoDemo();
        Tester tt = Tester.of(s::view, 420, 480);
        String first = s.todos.get(0).title;
        tt.click(first);
        Check.isTrue(s.todos.get(0).done.get(), "checked");
        Check.isTrue(tt.hasText("剩余 2 项"), "two left");
        tt.click("删除 " + first);
        Check.equal(2, s.todos.size());
        Check.isTrue(!tt.hasText(first), "gone");
        tt.key(Shortcut.cmd(Key.N));
        Check.equal(3, s.todos.size());
        tt.click(s.todos.get(1).title);                    // check the second
        tt.click("显示已完成");
        Check.isTrue(!tt.hasText(s.todos.get(1).title), "done todos hidden");
        tt.click("清除已完成");
        Check.equal(2, s.todos.size());
    }

    public void testTabAndSpaceCheckATodo() {
        TodoDemo s = new TodoDemo();
        Tester tt = Tester.of(s::view, 420, 480);
        tt.key(Shortcut.of(Key.TAB));                       // the switch
        tt.key(Shortcut.of(Key.TAB));                       // the first todo's check box
        tt.key(Shortcut.of(Key.SPACE));
        Check.isTrue(s.todos.get(0).done.get(), "space checked the first todo");
    }

    public void testTypingATitleAndEnterAddsATodo() {
        TodoDemo s = new TodoDemo();
        Tester tt = Tester.of(s::view, 420, 480);
        tt.click("新待办");
        tt.type("买菜");
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(4, s.todos.size());
        Check.isTrue(tt.hasText("买菜"), "the typed todo");
        Check.equal(java.util.Arrays.toString(new int[] {0, 0}), java.util.Arrays.toString(tt.textSelection())); // cleared
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(4, s.todos.size());                    // an empty title adds nothing
    }
}
