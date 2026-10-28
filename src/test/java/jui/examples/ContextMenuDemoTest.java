package jui.examples;

import jui.testing.Check;
import jui.ui.Tester;

public final class ContextMenuDemoTest {
    public void testNestedChoiceAndCheckedState() {
        ContextMenuDemo demo = new ContextMenuDemo();
        Tester tester = Tester.of(demo::view, 600, 760);
        tester.rightClick("菜单卡片");
        tester.chooseMenuItem("设置", "置顶");
        Check.isTrue(demo.pinned.get(), "chosen toggles application state");
        tester.rightClick("菜单卡片");
        Check.isTrue(tester.menu().items().get(1).children().get(0).checked(), "next snapshot shows check");
        tester.closeMenu();
        tester.rightClick("内层卡片");
        Check.equal("内层动作", tester.menu().items().get(0).label());
    }

    public void testDefaultAndCustomTextMenus() {
        ContextMenuDemo demo = new ContextMenuDemo();
        Tester tester = Tester.of(demo::view, 600, 760);
        tester.rightClick("多行备注");
        tester.chooseMenuItem("清空");
        Check.equal("", demo.notes.get());
        tester.rightClick("只读文本");
        Check.equal(3, tester.menu().items().size());
    }
}
