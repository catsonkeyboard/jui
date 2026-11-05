package jui.jspace;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.jspace.store.Settings;
import jui.ui.Key;
import jui.ui.Menu;
import jui.ui.Shortcut;
import jui.ui.Theme;

import java.util.concurrent.atomic.AtomicReference;

/** QSpace, a file manager built on j-ui. Run: ./build.sh demo QSpace */
public final class JspaceApp {
    private static final AtomicReference<Window> main = new AtomicReference<>();
    private static Workspace workspace;
    private static Settings settings;

    public static void main(String[] args) {
        settings = Settings.load();
        Model model = new Model(settings);
        App.menu(JspaceApp::menus);
        App.run(() -> {
            workspace = new Workspace(model, settings, r -> {
                Window w = main.get();               // set just below, before any scan runs
                if (w != null) w.update(r);
            });
            workspace.onAppearanceChange = JspaceApp::applyAppearance;
            main.set(Window.open(WindowOptions.of("QSpace").size(1280, 800).content(workspace::build)));
            applyAppearance();
        });
    }

    /** Installs the pair the appearance choice picks on every open window. */
    static void applyAppearance() {
        Window w = main.get();
        if (w == null) return;
        Theme[] pair = JspaceTheme.pair(settings.appearance);
        w.setThemePair(pair[0], pair[1]);
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
