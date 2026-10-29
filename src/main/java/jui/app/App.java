package jui.app;

import jui.platform.mac.MacApp;
import jui.text.TextEngine;

/** The application: run it from main, on the main thread. */
public final class App {
    private App() {}

    private static TextEngine text;
    private static java.util.function.Consumer<jui.ui.Menu> menuBuild;

    /** Runs the app until it quits; whenReady runs once it has launched, to open windows. */
    public static void run(Runnable whenReady) {
        MacApp.run(whenReady, menuBuild);
    }


    /**
     * The app's own menus in the menu bar, built once at launch, each submenu
     * rebuilt as it opens: item labels, separators, submenus, disabled,
     * checked and a shortcut label, with chosen() reporting a pick or the
     * shortcut in the focused window's next frame. The standard Edit menu
     * needs no declaring; call it before run. A shortcut declared here is
     * the menu's: the app should not also register it on the window.
     */
    public static void menu(java.util.function.Consumer<jui.ui.Menu> build) {
        menuBuild = build;
    }


    /** The app's text engine, shared by its windows; made on first use, on the main thread. */
    static TextEngine text() {
        if (text == null) {
            text = new TextEngine("zh-Hans");
        }
        return text;
    }
}
