package jui.app;

import jui.platform.Platform;
import jui.platform.PlatformWindow;
import jui.platform.mac.MacApp;
import jui.platform.mac.MacWindow;
import jui.platform.win.WinApp;
import jui.platform.win.WinWindow;
import jui.ui.Key;
import jui.ui.Theme;
import jui.ui.View;
import jui.ui.engine.Engine;

/**
 * A window showing a view, drawn by the UI engine on the GPU. Frames are
 * drawn only when asked for: when the platform displays the window (as it
 * opens or resizes), or when the view or the app invalidates it.
 */
public final class Window implements PlatformWindow.Input, jui.ui.engine.Host {
    private final Engine engine;
    private final PlatformWindow platform;
    /** The macOS window, for the presentation observers Windows does without; null on Windows. */
    private final MacWindow mac;
    private final jui.platform.win.WinWindow win;
    private final jui.platform.Clipboard clipboard;
    private boolean closed;
    private long menuGeneration;

    private Window(WindowOptions options) {
        View view = options.content() != null ? options.content() : c -> {};
        Theme theme = Theme.light();
        engine = new Engine(App.text(), theme, this::invalidate);
        engine.setHost(this);
        PlatformWindow.Painter painter = (scene, w, h) -> {
            engine.buildAndLayout(view, (float) w, (float) h);
            engine.paint(scene);
            scheduleTimer();
        };
        if (Platform.windows()) {
            win = WinWindow.open(options.title(), options.width(), options.height(),
                    theme.background().rgba() >>> 8, App.text(), painter, this);
            mac = null;
            platform = win;
            clipboard = jui.platform.win.WinClipboard.general();
        } else {
            mac = MacWindow.open(options.title(), options.width(), options.height(),
                    theme.background().rgba() >>> 8, App.text(), painter, this);
            win = null;
            platform = mac;
            clipboard = jui.platform.mac.MacPasteboard.general();
        }
        appearanceChanged(platform.dark());
    }

    /** Opens a window; call it on the main thread once the app is ready. */
    public static Window open(WindowOptions options) {
        return new Window(options);
    }

    /** Asks for a frame; any thread may call it. */
    public void invalidate() {
        if (closed) return;
        if (!onMainThread()) {
            post(this::invalidate);
            return;
        }
        if (platform != null) {
            platform.requestFrame();
        }
    }

    private static boolean onMainThread() {
        return Platform.windows() ? WinApp.onMainThread() : MacApp.onMainThread();
    }

    private static void post(Runnable task) {
        if (Platform.windows()) {
            WinApp.post(task);
        } else {
            MacApp.post(task);
        }
    }

    /** The window's engine, for the app's menu bar to report a choice in (PlatformWindow.Input). */
    @Override
    public jui.ui.engine.Engine engine() {
        return engine;
    }

    /**
     * Runs change on the main thread, then draws a frame; any thread may
     * call it, as threads that scan or index do when they are done.
     */
    public void update(Runnable change) {
        if (!onMainThread()) {
            post(() -> update(change));
            return;
        }
        change.run();
        invalidate();
    }

    /** Arms the window's timer for the wake-up the frame asked for (c.after), or cancels it. */
    private void scheduleTimer() {
        if (platform == null) {
            return;
        }
        long wake = engine.nextWake();
        if (wake == Long.MAX_VALUE) {
            platform.cancelTimer();
        } else {
            platform.armTimer(wake - engine.now());
        }
    }

    /** Closes the window. */
    public void close() {
        windowClosed();
        platform.close();
    }

    @Override
    public void windowClosed() {
        if (closed) return;
        closed = true;
        engine.close();
    }

    @Override
    public void cancelMenu() {
        menuGeneration++;
        if (platform != null) platform.cancelMenu();
    }

    @Override
    public void popupMenu(jui.platform.MenuModel model, float x, float y, java.util.function.IntConsumer result) {
        cancelMenu();
        long generation = menuGeneration;
        post(() -> {
            if (closed || generation != menuGeneration) { result.accept(0); return; }
            try {
                platform.popupMenu(model, x, y, choice -> result.accept(closed || generation != menuGeneration ? 0 : choice));
            } catch (RuntimeException | Error failure) {
                result.accept(0);
                throw failure;
            }
        });
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

    @Override public boolean dispatchKeyDown(Key key, int mods, boolean repeat) { return engine.dispatchKeyDown(key, mods, repeat); }
    @Override public void keyUp(Key key, int mods) { engine.keyUp(key, mods); }

    @Override
    public void modifiersChanged(int mods) {
        engine.modifiersChanged(mods);
    }

    @Override
    public void windowFocusChanged(boolean focused) {
        engine.windowFocusChanged(focused);
    }

    @Override
    public void scroll(double x, double y, double dx, double dy, boolean precise, int mods) {
        engine.scroll((float) x, (float) y, (float) dx, (float) dy, precise, mods);
    }

    // Text input, from the platform's text input method.

    @Override
    public boolean textActive() {
        return engine.textActive();
    }

    @Override
    public boolean textInputMethod() {
        return engine.textInputMethod();
    }

    @Override
    public boolean textComposing() {
        return engine.textComposing();
    }

    @Override
    public void textInsert(String s, int replStart, int replLength) {
        engine.textInsert(s, replStart, replLength);
    }

    @Override
    public void textMark(String s, int selStart, int selLength, int replStart, int replLength) {
        engine.textMark(s, selStart, selLength, replStart, replLength);
    }

    @Override
    public void textUnmark() {
        engine.textUnmark();
    }

    @Override
    public boolean textMarkedRange(int[] out) {
        return engine.textMarkedRange(out);
    }

    @Override
    public boolean textSelectedRange(int[] out) {
        return engine.textSelectedRange(out);
    }

    @Override
    public int textLength() {
        return engine.textLength();
    }

    @Override
    public String textIn(int start, int end) {
        return engine.textIn(start, end);
    }

    @Override public String textIn(int start, int end, int[] actual) { return engine.textIn(start, end, actual); }

    @Override
    public boolean textRect(int start, int end, float[] out) {
        return engine.textRect(start, end, out);
    }

    @Override
    public int textIndexAt(double x, double y) {
        return engine.textIndexAt((float) x, (float) y);
    }

    @Override
    public void editCommand(jui.ui.EditCommand c) {
        engine.editCommand(c);
    }

    // The engine's host: the clipboard, the pointer's shape, the input method.

    @Override
    public String readClipboard() {
        return clipboard.read();
    }

    @Override
    public void writeClipboard(String s) {
        clipboard.write(s);
    }

    @Override
    public void setCursor(jui.ui.Cursor c) {
        if (platform != null) {
            platform.setCursor(c);
        }
    }

    @Override
    public void discardMarkedText() {
        if (platform != null) {
            platform.discardMarkedText();
        }
    }

    @Override
    public void textInputChanged() {
        if (platform != null) {
            platform.invalidateCharacterCoordinates();
        }
    }

    /** The system's appearance changed: the theme follows, and the window clears to its background. */
    @Override
    public void appearanceChanged(boolean dark) {
        engine.setDark(dark);
        if (platform != null) {
            platform.setBackground(engine.theme().background().rgba() >>> 8);
        }
    }

    // Observability of the platform window, for tools and demos.

    /** The platform's number for the window, as its own tools capture it by. */
    public long windowNumber() {
        return platform == null ? 0 : platform.windowNumber();
    }

    /** The display's refresh period in nanoseconds, as well as it is known. */
    public long refreshNanos() {
        return platform == null ? 16_666_667L : platform.refreshNanos();
    }

    /** Wall-clock microseconds of the first CPU submission, for startup diagnostics. */
    public long firstCommitMicros() {
        return platform == null ? 0 : platform.firstCommitMicros();
    }

    /** Called on the UI thread when the first GPU frame completes (0 on failure). */
    public void onFirstFrame(java.util.function.LongConsumer done) {
        if (platform != null) {
            platform.onFirstFrame(done);
        }
    }

    /** Optional actual drawable presentation timestamps, in uptime seconds, on the UI thread; macOS only. */
    public void onPresented(MacWindow.FrameObserver done) {
        if (mac != null) mac.onPresented(done);
    }
}
