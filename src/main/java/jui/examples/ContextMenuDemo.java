package jui.examples;

import java.time.Duration;
import java.util.Arrays;
import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Key;
import jui.ui.Menu;
import jui.ui.Shortcut;
import jui.ui.TextRef;
import static jui.ui.Ui.*;

public final class ContextMenuDemo {
    private static final Shortcut DELETE = Shortcut.cmd(Key.BACKSPACE);
    final BoolRef pinned = new BoolRef(false), locked = new BoolRef(false);
    final TextRef value = new TextRef("选择这段文字，再在选区内或选区外右键。");
    final TextRef readOnly = new TextRef("只读输入框：只有 Copy 和 Select All。");
    final TextRef password = new TextRef("secret密码");
    final TextRef notes = new TextRef("多行输入框有自定义菜单。\n清空之外，还能用默认编辑项。\n试试跨行选择和中文组字。");
    private String status = "右键或 Ctrl+单击；聚焦卡片后可按 Shift+F10。";

    private void cardMenu(Menu menu) {
        if (menu.item("重命名").chosen()) status = "已选择：重命名";
        menu.submenu("设置", child -> {
            if (child.item("置顶").checked(pinned.get()).chosen()) pinned.set(!pinned.get());
        });
        menu.separator();
        if (menu.item("删除").disabled(locked.get()).shortcut(DELETE).chosen()) status = "已选择：删除（仅演示，不操作文件）";
    }

    private void noteMenu(Menu menu) {
        if (menu.item("清空").chosen()) notes.set("");
        menu.separator();
        menu.editItems();
    }

    void view(Context context) {
        context.after(Duration.ofSeconds(1));
        column(context).fill().padding(24).gap(12).children(() -> {
            text(context, "原生右键菜单").fontSize(22).bold();
            text(context, status).textColor(context.theme().textMuted());
            Element outer = column(context.key("cards")).padding(12).gap(8)
                    .background(context.theme().surface()).radius(8).children(() -> {
                        Element card = box(context.key("card")).height(38).focusable().label("菜单卡片")
                                .children(() -> text(context, "菜单卡片 · 重命名 / 设置 / 删除"));
                        contextMenu(context, card, this::cardMenu);
                        Element inner = box(context.key("inner")).height(32).focusable().label("内层卡片")
                                .children(() -> text(context, "内层卡片 · 最内层菜单优先"));
                        contextMenu(context, inner, menu -> {
                            if (menu.item("内层动作").chosen()) status = "已选择：内层动作";
                        });
                    });
            contextMenu(context, outer, menu -> {
                if (menu.item("外层动作").chosen()) status = "已选择：外层动作";
            });
            checkbox(context, locked, "禁用卡片的删除项");
            textInput(context.key("plain"), value).label("普通文本");
            textInput(context.key("readonly"), readOnly).readOnly(true).label("只读文本");
            textInput(context.key("password"), password).password().label("密码文本");
            Element area = textArea(context.key("notes"), notes).height(130).label("多行备注");
            contextMenu(context, area, this::noteMenu);
            text(context, "定时帧：" + context.now() / 1_000_000_000L).fontSize(12);
            text(context, "菜单打开时定时帧仍应继续；取消后普通单击应正常。").fontSize(12)
                    .textColor(context.theme().textMuted());
        });
    }

    public static void main(String[] arguments) {
        ContextMenuDemo demo = new ContextMenuDemo();
        App.run(() -> {
            Window window = Window.open(WindowOptions.of("原生右键菜单").size(600, 760).content(demo::view));
            if (Arrays.asList(arguments).contains("--print-window")) System.out.println("window=" + window.windowNumber());
        });
    }
}
