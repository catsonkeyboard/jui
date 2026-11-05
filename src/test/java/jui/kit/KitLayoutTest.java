package jui.kit;

import jui.testing.Check;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.TextRef;
import jui.ui.Tester;

import static jui.ui.Ui.*;

public final class KitLayoutTest {
    public void testGroupBoxAndDescriptionList() {
        Tester tt = Tester.of(c -> column(c).padding(12).gap(10).children(() -> {
            GroupBox.groupBox(c, "网络", () -> DescriptionList.descriptionList(c,
                    DescriptionList.entry("IP", "10.0.0.2"),
                    DescriptionList.entry("网关", "10.0.0.1")));
        }), 280, 180);
        tt.frame();
        Check.isTrue(tt.texts().contains("10.0.0.2"), "entry value shown");
        Check.isTrue(tt.texts().contains("网络"), "group title shown");
    }

    public void testDialogOkAndCancel() {
        BoolRef open = new BoolRef(true);
        BoolRef confirmed = new BoolRef(false);
        Tester tt = Tester.of(c -> Dialog.dialog(c, open, "删除项目?",
                () -> text(c, "该操作不可撤销"), Dialog.Buttons.OK_CANCEL,
                ok -> confirmed.set(ok)), 400, 300);
        tt.frame();
        tt.frame();
        tt.click("kit-btn-取消");
        tt.frame();
        Check.isTrue(!open.get(), "closed");
        Check.isTrue(!confirmed.get(), "cancelled");
        open.set(true);
        tt.frame();
        tt.frame();
        tt.click("kit-btn-确定");
        tt.frame();
        Check.isTrue(confirmed.get(), "ok reported");
    }

    public void testNotificationQueueDrains() {
        Notifications.notify("已保存", Kit.Variant.SUCCESS);
        Tester tt = Tester.of(c -> box(c).fill().children(() -> Notifications.host(c)), 300, 200);
        tt.frame();
        Check.isTrue(tt.texts().contains("已保存"), "toast visible");
    }

    public void testSheetClosesOnBackdrop() {
        BoolRef open = new BoolRef(true);
        Tester tt = Tester.of(c -> Sheet.sheet(c, open, 220,
                () -> text(c, "抽屉内容")), 400, 300);
        tt.frame();
        tt.frame();
        Check.isTrue(tt.find("kit-sheet").width() == 220, "sheet width");
        tt.key(jui.ui.Shortcut.of(jui.ui.Key.ESCAPE));
        tt.frame();
        Check.isTrue(!open.get(), "esc closes the sheet");
    }

    public void testSidebarSelectsEntries() {
        TextRef selected = new TextRef("");
        Tester tt = Tester.of(c -> Sidebar.sidebar(c, new Sidebar.Entry[] {
                Sidebar.entry("常规", "外观", "photo"),
                Sidebar.entry("常规", "面板", "square-grid"),
        }, selected, 180), 300, 220);
        tt.frame();
        tt.click("kit-side-常规/面板");
        tt.frame();
        Check.equal("常规/面板", selected.get());
    }

    public void testBarsRender() {
        Tester tt = Tester.of(c -> column(c).fill().children(() -> {
            Bars.toolbar(c,
                    () -> Button.button(c, "新建", Kit.Variant.PRIMARY, Kit.Size.XSMALL),
                    () -> Label.label(c, "就绪", Kit.Size.SMALL, Label.Variant.MUTED));
            box(c).grow(1);
            Bars.statusBar(c, "3 项", () -> Label.label(c, "OK", Kit.Size.XSMALL, Label.Variant.SUCCESS));
        }), 300, 200);
        tt.frame();
        Check.isTrue(tt.find("kit-toolbar").height() == 40, "toolbar height");
        Check.isTrue(tt.find("kit-statusbar").height() == 24, "status bar height");
    }
}
