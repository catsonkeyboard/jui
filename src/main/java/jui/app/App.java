package jui.app;

import jui.platform.mac.MacApp;
import jui.text.TextEngine;

/** The application: run it from main, on the main thread. */
public final class App {
    private App() {}

    private static TextEngine text;

    /** Runs the app until it quits; whenReady runs once it has launched, to open windows. */
    public static void run(Runnable whenReady) {
        MacApp.run(whenReady);
    }

    /** The app's text engine, shared by its windows; made on first use, on the main thread. */
    static TextEngine text() {
        if (text == null) {
            text = new TextEngine("zh-Hans");
        }
        return text;
    }
}
