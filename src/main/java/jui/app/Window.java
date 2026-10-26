package jui.app;

import jui.platform.mac.MacApp;
import jui.platform.mac.MacWindow;
import jui.ui.Key;
import jui.ui.Theme;
import jui.ui.View;
import jui.ui.engine.Engine;

/**
 * A window showing a view, drawn by the UI engine on the GPU. Frames are
 * drawn only when asked for: when AppKit displays the window (as it opens
 * or resizes), or when the view or the app invalidates it.
 */
public final class Window implements MacWindow.Input {
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
                    armTimer();
                }, this);
        appearanceChanged(mac.dark());
    }

    /** Opens a window; call it on the main thread once the app is ready. */
    public static Window open(WindowOptions options) {
        return new Window(options);
    }

    /** Asks for a frame; any thread may call it. */
    public void invalidate() {
        if (!MacApp.onMainThread()) {
            MacApp.post(this::invalidate);
            return;
        }
        if (mac != null) {
            mac.requestFrame();
        }
    }

    /**
     * Runs change on the main thread, then draws a frame; any thread may
     * call it, as threads that scan or index do when they are done.
     */
    public void update(Runnable change) {
        if (!MacApp.onMainThread()) {
            MacApp.post(() -> update(change));
            return;
        }
        change.run();
        invalidate();
    }

    /** Arms the window's timer for the wake-up the frame asked for (c.after), or cancels it. */
    private void armTimer() {
        if (mac == null) {
            return;
        }
        long wake = engine.nextWake();
        if (wake == Long.MAX_VALUE) {
            mac.cancelTimer();
        } else {
            mac.armTimer(wake - engine.now());
        }
    }

    /** Closes the window. */
    public void close() {
        mac.close();
    }

    // Input from the window's view, for the engine.

    @Override
    public void pointerMove(double x, double y, int mods) {
        engine.pointerMove((float) x, (float) y, mods);
    }

    @Override
    public void pointerDown(double x, double y, int button, int clicks, int mods) {
        engine.pointerDown((float) x, (float) y, button, clicks, mods);
    }

    @Override
    public void pointerUp(double x, double y, int button, int mods) {
        engine.pointerUp((float) x, (float) y, button, mods);
    }

    @Override
    public void pointerLeave() {
        engine.pointerLeave();
    }

    @Override
    public void keyDown(Key key, int mods, boolean repeat) {
        engine.keyDown(key, mods, repeat);
    }

    @Override
    public void modifiersChanged(int mods) {
        engine.modifiersChanged(mods);
    }

    @Override
    public void windowFocusChanged(boolean focused) {
        engine.windowFocusChanged(focused);
    }

    /** The system's appearance changed: the theme follows, and the window clears to its background. */
    @Override
    public void appearanceChanged(boolean dark) {
        engine.setDark(dark);
        if (mac != null) {
            mac.setBackground(engine.theme().background().rgba() >>> 8);
        }
    }

    /** The window server's number for the window, as screencapture -l takes: for tools. */
    public long windowNumber() {
        return mac.windowNumber();
    }
}
