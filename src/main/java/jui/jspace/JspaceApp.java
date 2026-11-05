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
    private static final AtomicReference<Window> settingsWin = new AtomicReference<>();
    private static Workspace workspace;
    private static Settings settings;
    private static Model model;

    public static void main(String[] args) {
        settings = Settings.load();
        model = new Model(settings);
        if (settings.restoreWorkspace) {
            WorkspaceState.apply(model, WorkspaceState.defaultFile());
        }
        App.menu(JspaceApp::menus);
        App.run(() -> {
            workspace = new Workspace(model, settings, r -> {
                Window w = main.get();               // set just below, before any scan runs
                if (w != null) w.update(r);
            });
            workspace.onAppearanceChange = JspaceApp::applyAppearance;
            workspace.onSettings = JspaceApp::openSettings;
            main.set(Window.open(WindowOptions.of("QSpace").size(1280, 800).content(workspace::build)));
            applyAppearance();
        });
    }

    /** Installs the pair the appearance choice picks on every open window. */
    static void applyAppearance() {
        Theme[] pair = JspaceTheme.pair(settings.appearance);
        Window w = main.get();
        if (w != null) w.setThemePair(pair[0], pair[1]);
        Window s = settingsWin.get();
        if (s != null) s.setThemePair(pair[0], pair[1]);
    }

    /** ⌘,: opens the preferences window, a fresh one whenever the last was closed. */
    static void openSettings() {
        SettingsWindow view = new SettingsWindow(workspace, () -> settings.save());
        Window w = Window.open(WindowOptions.of("设置").size(560, 480).content(view.view()));
        settingsWin.set(w);
        applyAppearance();
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
            if (app.item("设置…").shortcut(Shortcut.cmd(Key.COMMA)).chosen()) openSettings();
            app.separator();
            if (app.item("退出").shortcut(Shortcut.cmd(Key.Q)).chosen()) quit();
        });
    }

    private static void quit() {
        if (settings.restoreWorkspace) {
            WorkspaceState.save(model, WorkspaceState.defaultFile());
        }
        settings.save();
        System.exit(0);
    }

    private JspaceApp() {}
}
