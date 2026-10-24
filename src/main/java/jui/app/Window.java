package jui.app;

import jui.platform.mac.MacWindow;
import jui.ui.Theme;
import jui.ui.View;
import jui.ui.engine.Engine;

/**
 * A window showing a view, drawn by the UI engine on the GPU. Frames are
 * drawn only when asked for: when AppKit displays the window (as it opens
 * or resizes), or when the view or the app invalidates it.
 */
public final class Window {
    private final Engine engine;
    private final MacWindow mac;

    private Window(WindowOptions options) {
        View view = options.content() != null ? options.content() : c -> {};
        Theme theme = Theme.light();
        engine = new Engine(App.text(), theme, this::invalidate);
        mac = MacWindow.open(options.title(), options.width(), options.height(), theme.background().rgba() >>> 8,
                App.text(), (scene, w, h) -> {
                    engine.buildAndLayout(view, (float) w, (float) h);
                    engine.paint(scene);
                });
    }

    /** Opens a window; call it on the main thread once the app is ready. */
    public static Window open(WindowOptions options) {
        return new Window(options);
    }

    /** Asks for a frame (main thread). */
    public void invalidate() {
        if (mac != null) {
            mac.requestFrame();
        }
    }

    /** The window server's number for the window, as screencapture -l takes: for tools. */
    public long windowNumber() {
        return mac.windowNumber();
    }
}
