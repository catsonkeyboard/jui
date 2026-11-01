package jui.spike;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

import jui.examples.NotesDemo;
import jui.render.Scene;
import jui.text.GlyphAtlas;
import jui.text.TextEngine;
import jui.ui.Key;
import jui.ui.ListState;
import jui.ui.Rect;
import jui.ui.TextRef;
import jui.ui.Theme;
import jui.ui.View;
import jui.ui.engine.Engine;

/** Full NotesDemo build/layout/paint, including application allocations, without a GPU/window. */
public final class ShellBench {
    private static final int WARMUP = 1000, FRAMES = 1000;
    private static final float WIDTH = 980, HEIGHT = 640;
    private static final double REFRESH_MS = 1000.0 / 120;
    private ShellBench() {}

    static boolean withinBudget(double steady, double bytes, double typing, double scroll, boolean full) {
        return steady >= 0 && steady < 4 && bytes >= 0 && bytes <= 32768
                && typing >= 0 && typing <= REFRESH_MS && scroll >= 0 && scroll < 4 && !full;
    }

    public static void main(String[] args) {
        Object[] workload = NotesDemo.forTools();
        View view = (View) workload[0];
        ListState files = (ListState) workload[1];
        TextRef query = (TextRef) workload[2];
        TextEngine text = new TextEngine("zh-Hans");
        Engine engine = new Engine(text, Theme.light(), () -> {});
        Scene scene = new Scene();
        GlyphAtlas atlas = new GlyphAtlas(1024, 1024, 4096);
        var mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        try {
            for (int i = 0; i < WARMUP; i++) frame(engine, view, scene, text, atlas);
            long[] times = new long[FRAMES];
            long before = mx.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < FRAMES; i++) {
                long started = System.nanoTime();
                frame(engine, view, scene, text, atlas);
                times[i] = System.nanoTime() - started;
            }
            double bytes = (mx.getCurrentThreadAllocatedBytes() - before) / (double) FRAMES;
            double steady = report("steady", times);
            System.out.printf(Locale.ROOT, "SHELLBENCH cache_hits=%d eligible=%d hit_rate=%.1f%%%n",
                    engine.cacheHits(), engine.cacheEligible(), engine.cacheEligible() == 0 ? 0 : 100.0 * engine.cacheHits() / engine.cacheEligible());
            System.out.printf(Locale.ROOT, "SHELLBENCH alloc=%.1fB/frame notes=10000%n", bytes);

            Rect search = engine.bounds("search");
            float x = search.x() + search.width() / 2, y = search.y() + search.height() / 2;
            engine.pointerDown(x, y, 0, 1, 0); frame(engine, view, scene, text, atlas);
            engine.pointerUp(x, y, 0, 0); frame(engine, view, scene, text, atlas);
            for (int i = 0; i < WARMUP + FRAMES; i++) {
                long started = System.nanoTime();
                if ((i & 1) == 0) engine.textInsert("笔", -1, 0);
                else engine.keyDown(Key.BACKSPACE, 0, false);
                frame(engine, view, scene, text, atlas);
                long elapsed = System.nanoTime() - started;
                if (i >= WARMUP) times[i - WARMUP] = elapsed;
                if (!query.get().equals((i & 1) == 0 ? "笔" : ""))
                    throw new AssertionError("search did not consume the key in its next frame");
            }
            report("key_to_paint", times);
            double typingMax = times[FRAMES - 1] / 1e6;
            Rect noteList = engine.bounds("notes-list");
            float scrollX = noteList.x() + noteList.width() / 2, scrollY = noteList.y() + noteList.height() / 2;
            int rowsMax = 0, visibleMax = 0;
            for (int i = 0; i < WARMUP + FRAMES; i++) {
                long started = System.nanoTime();
                engine.scroll(scrollX, scrollY, 0, 64, true, 0);
                frame(engine, view, scene, text, atlas);
                if (i >= WARMUP) {
                    times[i - WARMUP] = System.nanoTime() - started;
                    rowsMax = Math.max(rowsMax, files.rowsBuilt());
                    visibleMax = Math.max(visibleMax, files.last() - files.first() + 1);
                }
            }
            double scroll = report("scroll", times);
            boolean nativeImage = "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
            System.out.printf("SHELLBENCH mode=%s rows_max=%d visible_max=%d last=%d atlas_full=%s%n",
                    nativeImage ? "native" : "jvm", rowsMax, visibleMax, files.last(), atlas.isFull());
            if (atlas.isFull() || rowsMax > visibleMax + 5 || files.last() < 1000
                    || nativeImage && !withinBudget(steady, bytes, typingMax, scroll, atlas.isFull())) {
                throw new AssertionError("shell budget failed: steady/scroll p95 <4ms, alloc <=32KB, typing max <=8.333ms, visible rows only");
            }
        } finally {
            engine.close();
        }
    }

    private static double report(String name, long[] times) {
        Arrays.sort(times);
        double p95 = times[(int) (times.length * .95)] / 1e6;
        System.out.printf(Locale.ROOT, "SHELLBENCH %s p50=%.3fms p95=%.3fms max=%.3fms%n",
                name, times[times.length / 2] / 1e6, p95, times[times.length - 1] / 1e6);
        return p95;
    }

    private static void frame(Engine engine, View view, Scene scene, TextEngine text, GlyphAtlas atlas) {
        engine.buildAndLayout(view, WIDTH, HEIGHT);
        scene.begin(2, text, atlas);
        engine.paint(scene);
    }
}
