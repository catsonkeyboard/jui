package jui.spike;

import java.util.Arrays;

import jui.examples.FilesDemo;
import jui.render.Scene;
import jui.text.GlyphAtlas;
import jui.text.TextEngine;
import jui.ui.ListState;
import jui.ui.Theme;
import jui.ui.View;
import jui.ui.engine.Engine;

/**
 * The scroll frame gate of M3: FilesDemo's 10000 notes, scrolled 24 DIPs a
 * frame for 3000 frames, built, laid out and painted without a window.
 * Prints each frame's time at p50, p95 and max, and the rows built; in a
 * native image, fails above 4 ms at p95, 8 ms at most, or when a frame
 * builds more than the rows in view and five.
 */
public final class ListBench {
    private ListBench() {}

    private static final int WARMUP = 300, FRAMES = 3000;
    private static final float WIDTH = 360, HEIGHT = 640;

    public static void main(String[] args) {
        Object[] demo = FilesDemo.forTools();
        View view = (View) demo[0];
        ListState files = (ListState) demo[1];
        TextEngine text = new TextEngine("zh-Hans");
        Engine e = new Engine(text, Theme.light(), () -> {});
        Scene scene = new Scene();
        GlyphAtlas atlas = new GlyphAtlas(1024, 1024);
        for (int i = 0; i < WARMUP; i++) {
            e.scroll(WIDTH / 2, HEIGHT / 2, 0, i < WARMUP / 2 ? 24 : -24, true, 0);
            frame(e, view, scene, text, atlas);
        }
        long[] times = new long[FRAMES];
        int rowsMax = 0, visibleMax = 0;
        for (int i = 0; i < FRAMES; i++) {
            e.scroll(WIDTH / 2, HEIGHT / 2, 0, 24, true, 0);
            long t0 = System.nanoTime();
            frame(e, view, scene, text, atlas);
            times[i] = System.nanoTime() - t0;
            rowsMax = Math.max(rowsMax, files.rowsBuilt());
            visibleMax = Math.max(visibleMax, files.last() - files.first() + 1);
        }
        Arrays.sort(times);
        double p50 = times[FRAMES / 2] / 1e6, p95 = times[(int) (FRAMES * 0.95)] / 1e6, max = times[FRAMES - 1] / 1e6;
        boolean nativeImage = "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
        System.out.printf("LISTBENCH mode=%s p50=%.3fms p95=%.3fms max=%.3fms rows_max=%d visible_max=%d last_row=%d%n",
                nativeImage ? "native" : "jvm", p50, p95, max, rowsMax, visibleMax, files.last());
        if (nativeImage && (p95 > 4 || max > 8 || rowsMax > visibleMax + 5)) {
            System.out.println("FAIL: a scroll frame is over budget (p95 4 ms, max 8 ms) or builds rows out of view");
            System.exit(1);
        }
    }

    private static void frame(Engine e, View view, Scene scene, TextEngine text, GlyphAtlas atlas) {
        e.buildAndLayout(view, WIDTH, HEIGHT);
        scene.begin(2, text, atlas);
        e.paint(scene);
    }
}
