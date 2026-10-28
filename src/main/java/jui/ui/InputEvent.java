package jui.ui;

import jui.ui.engine.Dev;
import jui.ui.engine.Engine;

public final class InputEvent {
    public enum Kind { KEY_DOWN, KEY_UP, MODIFIERS_CHANGED, POINTER_DOWN, POINTER_MOVE, POINTER_UP, POINTER_CANCEL, SCROLL, COMMAND }

    private Engine engine;
    private final Kind kind;
    private final Key key;
    private final int mods, button, clicks;
    private final boolean repeat, precise;
    private final float x, y, dx, dy, width, height;
    private final long timeNanos;
    private final EditCommand command;

    private InputEvent(Engine engine, Kind kind, Key key, int mods, boolean repeat, int button, int clicks,
                       float x, float y, float dx, float dy, boolean precise, EditCommand command, long time, float width, float height) {
        this.engine = engine; this.kind = kind; this.key = key; this.mods = mods; this.repeat = repeat;
        this.button = button; this.clicks = clicks; this.x = x; this.y = y; this.dx = dx; this.dy = dy;
        this.precise = precise; this.command = command; this.timeNanos = time; this.width = width; this.height = height;
    }

    private boolean valid() {
        if (engine != null && jui.ffi.Libc.pthreadMainNp() != 0) return true;
        Dev.fail("InputEvent is valid only during its handler on the UI thread");
        return false;
    }

    public Kind kind() { valid(); return kind; }
    public Key key() { valid(); return key; }
    public int mods() { valid(); return mods; }
    public boolean repeat() { valid(); return repeat; }
    public int button() { valid(); return button; }
    public int clicks() { valid(); return clicks; }
    public float x() { valid(); return x; }
    public float y() { valid(); return y; }
    public float dx() { valid(); return dx; }
    public float dy() { valid(); return dy; }
    public float width() { valid(); return width; }
    public float height() { valid(); return height; }
    public boolean precise() { valid(); return precise; }
    public EditCommand command() { valid(); return command; }
    public long timeNanos() { valid(); return timeNanos; }
    public String clipboardText() { return valid() ? engine.host().readClipboard() : null; }
    public void copyText(String text) { if (valid()) engine.host().writeClipboard(text); }
    public void invalidate() { if (valid()) engine.invalidate(); }

    public static boolean dispatch(Engine engine, InputHandler handler, Kind kind, Key key, int mods, boolean repeat,
                                   int button, int clicks, float x, float y, float dx, float dy, boolean precise,
                                   EditCommand command, long time, float width, float height) {
        InputEvent event = new InputEvent(engine, kind, key, mods, repeat, button, clicks, x, y, dx, dy, precise, command, time, width, height);
        engine.beginCallback();
        try { return handler.handle(event); }
        finally { event.engine = null; engine.endCallback(); engine.invalidate(); }
    }
}
