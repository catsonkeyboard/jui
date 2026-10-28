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
public final class Window implements MacWindow.Input, jui.ui.engine.Host {
    private final Engine engine;
    private final MacWindow mac;
    private boolean closed;
    private long menuGeneration;
    private final jui.platform.mac.MacPasteboard pasteboard = jui.platform.mac.MacPasteboard.general();

    private Window(WindowOptions options) {
        View view = options.content() != null ? options.content() : c -> {};
        Theme theme = Theme.light();
        engine = new Engine(App.text(), theme, this::invalidate);
        engine.setHost(this);
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
        if (closed) return;
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
        windowClosed();
        mac.close();
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
        if (mac != null) mac.cancelMenu();
    }

    @Override
    public void popupMenu(jui.platform.MenuModel model, float x, float y, java.util.function.IntConsumer result) {
        cancelMenu();
        long generation = menuGeneration;
        MacApp.post(() -> {
            if (closed || generation != menuGeneration) { result.accept(0); return; }
            try {
                mac.popupMenu(model, x, y, choice -> result.accept(closed || generation != menuGeneration ? 0 : choice));
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

    // Text input, from the view's NSTextInputClient.

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
        return pasteboard.read();
    }

    @Override
    public void writeClipboard(String s) {
        pasteboard.write(s);
    }

    @Override
    public void setCursor(jui.ui.Cursor c) {
        if (mac != null) {
            mac.setCursor(c);
        }
    }

    @Override
    public void discardMarkedText() {
        if (mac != null) {
            mac.discardMarkedText();
        }
    }

    @Override
    public void textInputChanged() {
        if (mac != null) {
            mac.invalidateCharacterCoordinates();
        }
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
