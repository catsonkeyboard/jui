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
        pending = false;
        engine.buildAndLayout(view, width, height);
        scene.begin(2, text, atlas);
        engine.paint(scene);
    }

    /** The texts of the frame's text elements, in build order. */
    public List<String> texts() {
        return engine.texts();
    }

    public boolean hasText(String s) {
        return engine.texts().contains(s);
    }

    /** The box, in DIPs, of the element showing or labeled s. */
    public Rect find(String s) {
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

    /** Whether the view asked for another frame since the last one began. */
    public boolean framePending() {
        return pending;
    }
}
