package jui.spike;

import java.lang.management.ManagementFactory;

import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Theme;
import jui.ui.View;
import jui.ui.engine.Engine;

import static jui.ui.Ui.box;
import static jui.ui.Ui.row;

/**
 * Measures what frames of the UI API allocate on this thread (design §8),
 * by pattern: Element handles, capturing lambdas passed to children,
 * temporary refs passed to a control, and keys. Each scenario warms up
 * for 20000 frames, then is measured over 1000; bytes per unit are net of
 * an empty frame.
 */
public final class AllocSpike {
    private AllocSpike() {}

    private static final int WARMUP = 20_000, FRAMES = 1_000;
    private static final com.sun.management.ThreadMXBean MX =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) {
        Engine e = new Engine(null, Theme.light(), () -> {});
        double empty = perFrame(e, c -> {});
        double handles = (perFrame(e, c -> handles(c, 300)) - empty) / 300;
        double lambdas = (perFrame(e, c -> lambdas(c, 100)) - empty) / 100;
        double temprefs = (perFrame(e, c -> temprefs(c, 100)) - empty) / 100;
        double keyLong = (perFrame(e, c -> keyLong(c, 30)) - empty) / 30;
        double keyObject = (perFrame(e, c -> keyObject(c, 30)) - empty) / 30;
        double shell = perFrame(e, c -> {
            handles(c, 200);
            lambdas(c, 60);
            temprefs(c, 10);
            keyLong(c, 30);
        });
        String mode = "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")) ? "native" : "jvm";
        System.out.printf("ALLOC mode=%s empty_frame=%.0f handles=%.1f lambdas=%.1f temprefs=%.1f"
                        + " key_long=%.1f key_object=%.1f shell_frame=%.0f%n",
                mode, empty, handles, lambdas, temprefs, keyLong, keyObject, shell);
    }

    /** Bytes this thread allocates in a frame of the view, on average. */
    private static double perFrame(Engine e, View view) {
        for (int i = 0; i < WARMUP; i++) {
            e.buildAndLayout(view, 800, 600);
        }
        long before = MX.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < FRAMES; i++) {
            e.buildAndLayout(view, 800, 600);
        }
        return (MX.getCurrentThreadAllocatedBytes() - before) / (double) FRAMES;
    }

    private static void handles(Context c, int n) {
        for (int i = 0; i < n; i++) {
            box(c).width(10).grow(1);
        }
    }

    private static void lambdas(Context c, int n) {
        for (int i = 0; i < n; i++) {
            float w = i; // captured: each lambda is a new one
            box(c).padding(4).children(() -> box(c).width(w));
        }
    }

    private static void temprefs(Context c, int n) {
        for (int i = 0; i < n; i++) {
            fakeCheckbox(c, new BoolRef(i % 2 == 0), "x");
        }
    }

    private static void keyLong(Context c, int n) {
        for (int i = 0; i < n; i++) {
            box(c.key(1_000_000L + i));
        }
    }

    private static void keyObject(Context c, int n) {
        for (int i = 0; i < n; i++) {
            box(c.key((Object) Long.valueOf(1_000_000L + i)));
        }
    }

    /** About the size of a real control: reads and writes its ref, and builds a box and its mark. */
    private static Element fakeCheckbox(Context c, BoolRef value, String label) {
        boolean on = value.get();
        float size = 16;
        float radius = 4;
        float gap = 8;
        float padY = 2;
        float padX = 0;
        if (label.isEmpty()) {
            gap = 0;
        }
        if (size < 12) {
            size = 12;
        }
        Element r = row(c).gap(gap).padding(padY, padX);
        float markSize = size - 6;
        float markOffset = (size - markSize) / 2;
        if (markOffset < 0) {
            markOffset = 0;
        }
        Element mark = box(c).size(size, size);
        if (on) {
            mark.padding(markOffset);
        } else {
            mark.padding(0);
        }
        float total = size + gap + label.length() * 7;
        if (total > 400) {
            total = 400;
        }
        r.minWidth(total);
        if (radius > size / 2) {
            radius = size / 2;
        }
        boolean toggled = (label.hashCode() & 1) == 1 && markSize > 100; // never true: no input in a spike
        if (toggled) {
            on = !on;
            value.set(on);
        }
        if (on != value.get()) {
            value.set(on);
        }
        return r;
    }
}
