package jui.ui;

import java.util.List;

import jui.render.Scene;
import jui.text.GlyphAtlas;
import jui.text.TextEngine;
import jui.ui.engine.Engine;

/**
 * Runs a view without a window, as fast as a unit test: frames are built,
 * laid out and painted in memory at scale 2, with the system's text engine
 * but no GPU. Elements are found by their text or label.
 */
public final class Tester {
    public void close() { engine.close(); }
    private final View view;
    private float width, height;
    private final TextEngine text = new TextEngine("zh-Hans");
    private final GlyphAtlas atlas = new GlyphAtlas(1024, 1024);
    private final Scene scene = new Scene();
    private final Engine engine;
    /** The tester's platform: a clipboard in memory, the cursor last set. */
    private final jui.ui.engine.Host.Memory host = new jui.ui.engine.Host.Memory();
    private boolean pending;
    private boolean started;
    /** The tester's clock, in nanoseconds: it moves only in advance(). */
    private long now;
    private long lastFrameAt;
    /** A 60 Hz refresh, in whole nanoseconds. */
    private static final long REFRESH = 1_000_000_000L / 60;

    private Tester(View view, float width, float height) {
        this.view = view;
        this.width = width;
        this.height = height;
        this.engine = new Engine(text, Theme.light(), () -> pending = true, () -> now);
        engine.setHost(host);
    }

    /** A tester of the view in a window of width by height DIPs. */
    public static Tester of(View view, float width, float height) {
        return new Tester(view, width, height);
    }

    /** Builds, lays out and paints a frame. */
    public void frame() {
        started = true;
        pending = false;
        lastFrameAt = now;
        engine.buildAndLayout(view, width, height);
        scene.begin(2, text, atlas);
        engine.paint(scene);
    }

    /** The texts of the frame's text elements, in build order. */
    public List<String> texts() {
        started();
        return engine.texts();
    }

    /** The text the element showing or labeled s shows: a text input's, with its composition, or its mask. */
    public String shownText(String s) {
        started();
        find(s);
        return engine.shownText(s);
    }

    public boolean hasText(String s) {
        started();
        return engine.texts().contains(s);
    }

    /** The box, in DIPs, of the element showing or labeled s. */
    public Rect find(String s) {
        started();
        Rect r = engine.bounds(s);
        if (r == null) {
            throw new AssertionError("no element shows or is labeled \"" + s + "\"; texts: " + texts());
        }
        return r;
    }

    /** What the last frame painted. */
    public Scene scene() {
        return scene;
    }

    /** What the frame painted, as GPU instances. */
    public int instances() {
        return scene.count();
    }

    // Input, as a user gives it: each runs frames until none is asked for (at most 20).

    /** Moves the pointer onto the element showing or labeled s, then clicks it. */
    public void click(String s) {
        float[] p = center(s);
        engine.pointerMove(p[0], p[1], 0);
        settle();
        clickAt(p[0], p[1], 0, 1);
    }

    /** Clicks the element twice, as a double click. */
    public void doubleClick(String s) {
        float[] p = center(s);
        engine.pointerMove(p[0], p[1], 0);
        settle();
        clickAt(p[0], p[1], 0, 1);
        clickAt(p[0], p[1], 0, 2);
    }

    /** Clicks the element with the secondary button. */
    public void rightClick(String s) {
        float[] p = center(s);
        engine.pointerMove(p[0], p[1], 0);
        settle();
        clickAt(p[0], p[1], 1, 1);
    }

    public void rightClickAt(float x, float y) { clickAt(x, y, 1, 1); }

    public jui.platform.MenuModel menu() { started(); return host.menuModel; }

    public void closeMenu() { host.cancelMenu(); settle(); }

    public void chooseMenuItem(String... labels) {
        if (host.menuModel == null || labels.length == 0) throw new AssertionError("no menu or empty path");
        java.util.List<jui.platform.MenuModel.Item> items = host.menuModel.items();
        jui.platform.MenuModel.Item found = null;
        for (String label : labels) {
            found = null;
            for (jui.platform.MenuModel.Item item : items) {
                if (item.label().equals(label)) {
                    if (found != null) throw new AssertionError("ambiguous menu label: " + label);
                    found = item;
                }
            }
            if (found == null) throw new AssertionError("no menu item: " + label);
            items = found.children();
        }
        host.chooseMenu(found.id());
        settle();
    }

    /** Moves the pointer onto the element's center. */
    public void hover(String s) {
        float[] p = center(s);
        move(p[0], p[1]);
    }

    /** Switches the system's appearance to dark or light, as a user would. */
    public void setDark(boolean dark) {
        started();
        engine.setDark(dark);
        settle();
    }

    /** Presses and releases the shortcut's key with its modifiers held. */
    public void key(Shortcut s) {
        started();
        engine.keyDown(s.key(), s.mods(), false);
        settle();
    }

    // Text input, as the platform's input method gives it.

    /** Types s into the text input with focus, as the input method commits it. */
    public void type(String s) {
        started();
        engine.textInsert(s, -1, 0);
        settle();
    }

    /** Composes text in the text input with focus, its caret at caret (UTF-16); empty text ends the composition. */
    public void compose(String text, int caret) {
        started();
        if (!engine.textInputMethod()) {
            return; // without the input method nothing composes (as MyGO's headless Compose)
        }
        engine.textMark(text, caret, 0, -1, 0);
        settle();
    }

    /** Whether the text input with focus takes text input, as the input method sees it: not read-only, nor disabled. */
    public boolean textInputActive() {
        started();
        return engine.textActive();
    }

    /** Runs a command of the Edit menu. */
    public void command(EditCommand c) {
        started();
        engine.editCommand(c);
        settle();
    }

    /** The clipboard's text, or null. */
    public String clipboard() {
        return host.clipboard;
    }

    public void setClipboard(String s) {
        host.clipboard = s;
    }

    /** The selection of the text input with focus, as {start, end} in its shown text; null without one. */
    public int[] textSelection() {
        started();
        int[] r = new int[2];
        if (!engine.textSelectedRange(r)) {
            return null;
        }
        return new int[] {r[0], r[0] + r[1]};
    }

    /** The caret of the text input with focus, in window DIPs, as the input method sees it; null without one. */
    public Rect textCaret() {
        started();
        float[] r = new float[4];
        return engine.textCaretRect(r) ? new Rect(r[0], r[1], r[2], r[3]) : null;
    }

    /** How far the text input showing or labeled s has scrolled its text. */
    public float textScroll(String s) {
        started();
        find(s);
        return engine.textScroll(s);
    }

    public float textScrollY(String label) {
        started();
        find(label);
        return engine.textScrollY(label);
    }

    /** The pointer's shape, as last set. */
    public Cursor cursor() {
        return host.cursor;
    }

    /** Whether the element showing or labeled s, or one around it, has focus. */
    public boolean focused(String s) {
        started();
        find(s);
        return engine.focusIn(s);
    }

    /** Scrolls by (dx, dy) at (x, y), precisely, as a trackpad does: dy above 0 moves the content up. */
    public void scroll(float x, float y, float dx, float dy) {
        started();
        engine.scroll(x, y, dx, dy, true, 0);
        settle();
    }

    /** Scrolls at the center of the element showing or labeled s. */
    public void scroll(String s, float dx, float dy) {
        float[] p = center(s);
        scroll(p[0], p[1], dx, dy);
    }

    /** Turns a wheel by (dx, dy) at (x, y): a scroll that glides, in DIPs. */
    public void wheel(float x, float y, float dx, float dy) {
        started();
        engine.scroll(x, y, dx, dy, false, 0);
        settle();
    }

    /** Turns a wheel at the center of the element showing or labeled s. */
    public void wheel(String s, float dx, float dy) {
        float[] p = center(s);
        wheel(p[0], p[1], dx, dy);
    }

    /** Makes the window w by h DIPs, for the frames that follow. */
    public void resize(float w, float h) {
        width = w;
        height = h;
        pending = true;
    }

    /** Presses the primary button at (x, y), in DIPs. */
    public void press(float x, float y) {
        started();
        engine.pointerDown(x, y, 0, 1, 0);
        settle();
    }

    public void move(float x, float y) {
        started();
        engine.pointerMove(x, y, 0);
        settle();
    }

    public void release(float x, float y) {
        started();
        engine.pointerUp(x, y, 0, 0);
        settle();
    }

    private void clickAt(float x, float y, int button, int clickCount) {
        engine.pointerDown(x, y, button, clickCount, 0);
        settle();
        engine.pointerUp(x, y, button, 0);
        settle();
    }

    private float[] center(String s) {
        started();
        Rect r = find(s);
        return new float[] {r.x() + r.width() / 2, r.y() + r.height() / 2};
    }

    /** Runs a first frame if none has run: input needs what a frame commits. */
    private void started() {
        if (!started) {
            frame();
        }
    }

    /** The tester's clock, in nanoseconds. */
    public long now() {
        return now;
    }

    /**
     * Moves the clock d on, running the frames that come due on the way, as
     * MyGO's Advance: one a 60 Hz refresh after the last while frames are
     * asked for, and one when a wake-up the view asked for (after) is due.
     */
    public void advance(java.time.Duration d) {
        started();
        long end = now + d.toNanos();
        while (true) {
            long due = pending ? lastFrameAt + REFRESH : Long.MAX_VALUE;
            long wake = engine.nextWake();
            if (wake != Long.MAX_VALUE) {
                due = Math.min(due, Math.max(wake, lastFrameAt + 1_000_000));
            }
            if (due > end) {
                break;
            }
            now = Math.max(now, due);
            frame();
        }
        now = end;
    }

    /** Runs frames while the view asks for them, at most 20. */
    public void settle() {
        for (int i = 0; i < 20 && pending; i++) {
            frame();
        }
    }

    /** The engine under the tester: for tests that drive it directly. */
    public Engine engine() {
        return engine;
    }

    /** Whether the view asked for another frame since the last one began. */
    public boolean framePending() {
        return pending;
    }
}
