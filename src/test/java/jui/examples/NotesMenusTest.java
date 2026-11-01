package jui.examples;

import java.util.List;
import jui.platform.MenuModel;
import jui.testing.Check;
import jui.ui.*;

/** Native menu choices must act on the same model as workspace buttons. */
public final class NotesMenusTest {
    private static MenuModel.Item item(NotesDemo demo, String submenu, String label) {
        var model = Menu.build(demo::menus, List.of(), null).model();
        for (var top : model.items()) if (top.label().equals(submenu))
            for (var choice : top.children()) if (choice.label().equals(label)) return choice;
        throw new AssertionError("missing menu action " + submenu + "/" + label);
    }

    public void testNewMenuNoteAppearsInSelectedGroupAndSharedTabs() {
        NotesDemo d = new NotesDemo(); Tester t = Tester.of(d::view, 1180, 690);
        try {
            d.addGroup(0); d.selectGroup(1); t.frame();
            t.engine().appMenuChose(d::menus, item(d, "文件", "新建笔记")); t.frame();
            Check.equal(10001, d.notes.size());
            Check.isTrue(d.notes.contains(d.current()) && d.open.contains(d.current()), "menu shares catalog and tabs");
            Check.isTrue(t.hasText("1 note") && t.hasText("空白笔记"), "new note belongs to selected group");
        } finally { t.close(); }
    }

    public void testCloseMenuClosesOnlyCurrentTabAndDisablesWhenEmpty() {
        NotesDemo d = new NotesDemo(); Tester t = Tester.of(d::view, 1180, 690);
        try {
            d.open(d.notes.get(0)); d.open(d.notes.get(1)); t.frame();
            t.engine().appMenuChose(d::menus, item(d, "文件", "关闭标签页")); t.frame();
            Check.equal(1, d.open.size()); Check.isTrue(d.current() == d.notes.get(0), "previous tab remains selected");
            t.engine().appMenuChose(d::menus, item(d, "文件", "关闭标签页")); t.frame();
            Check.equal(0, d.open.size());
            Check.isTrue(!item(d, "文件", "关闭标签页").enabled(), "no phantom close action");
        } finally { t.close(); }
    }
}
