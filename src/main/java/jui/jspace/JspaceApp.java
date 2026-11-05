package jui.jspace;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.Key;
import jui.ui.Menu;
import jui.ui.Shortcut;

/** QSpace, a file manager built on j-ui. Run: ./build.sh demo QSpace */
public final class JspaceApp {
    private static Window main;
    private static Workspace workspace;

    public static void main(String[] args) {
        App.menu(JspaceApp::menus);
        App.run(() -> {
            workspace = new Workspace(new Model());
            main = Window.open(WindowOptions.of("QSpace").size(1280, 800).content(workspace::build));
            main.setThemePair(JspaceTheme.LIGHT, JspaceTheme.DARK);
        });
    }

    private static void menus(Menu bar) {
        bar.submenu("文件", file -> {
            if (file.item("新建标签页").shortcut(Shortcut.cmd(Key.N)).chosen()) workspace.newTab();
            if (file.item("关闭标签页").shortcut(Shortcut.cmd(Key.W)).chosen()) workspace.closeTab();
        });
        bar.submenu("视图", view -> {
            if (view.item("显示边栏").shortcut(Shortcut.cmd(Key.B)).chosen()) workspace.toggleSidebar();
        });
        bar.submenu("QSpace", app -> {
            if (app.item("设置…").shortcut(Shortcut.cmd(Key.COMMA)).chosen()) workspace.openSettings();
            app.separator();
            if (app.item("退出").shortcut(Shortcut.cmd(Key.Q)).chosen()) System.exit(0);
        });
    }

    private JspaceApp() {}
}
