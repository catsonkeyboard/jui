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
    private final View view;
    private final float width, height;
    private final TextEngine text = new TextEngine("zh-Hans");
    private final GlyphAtlas atlas = new GlyphAtlas(1024, 1024);
    private final Scene scene = new Scene();
    private final Engine engine;
    private boolean pending;
    private boolean started;

    private Tester(View view, float width, float height) {
        this.view = view;
        this.width = width;
        this.height = height;
        this.engine = new Engine(text, Theme.light(), () -> pending = true);
    }

    /** A tester of the view in a window of width by height DIPs. */
    public static Tester of(View view, float width, float height) {
        return new Tester(view, width, height);
    }

    /** Builds, lays out and paints a frame. */
    public void frame() {
        started = true;
        pending = false;
        engine.buildAndLayout(view, width, height);
        scene.begin(2, text, atlas);
        engine.paint(scene);
    }

    /** The texts of the frame's text elements, in build order. */
    public List<String> texts() {
        started();
        return engine.texts();
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

    /** Moves the pointer onto the element's center. */
    public void hover(String s) {
        float[] p = center(s);
        move(p[0], p[1]);
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

    /** Runs frames while the view asks for them, at most 20. */
    void settle() {
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
