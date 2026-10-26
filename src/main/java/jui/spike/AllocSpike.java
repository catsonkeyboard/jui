package jui.spike;

import java.lang.management.ManagementFactory;

import jui.examples.CardDemo;
import jui.render.Scene;
import jui.text.GlyphAtlas;
import jui.text.TextEngine;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.DoubleRef;
import jui.ui.Element;
import jui.ui.ListState;
import jui.ui.Rect;
import jui.ui.Theme;
import jui.ui.View;
import jui.ui.engine.Engine;

import static jui.ui.Ui.box;
import static jui.ui.Ui.button;
import static jui.ui.Ui.checkbox;
import static jui.ui.Ui.list;
import static jui.ui.Ui.slider;
import static jui.ui.Ui.row;
import static jui.ui.Ui.text;

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
        // Frames with text, built, laid out and painted: the framework's own
        // share (a view of texts and no lambdas), and the card example whole.
        TextEngine text = new TextEngine("zh-Hans");
        Engine te = new Engine(text, Theme.light(), () -> {});
        Scene scene = new Scene();
        GlyphAtlas atlas = new GlyphAtlas(1024, 1024);
        View texts = c -> {
            for (int i = 0; i < TEXTS.length; i++) {
                text(c, TEXTS[i]).fontSize(11 + 3 * i);
            }
        };
        double textFrame = perFrame(() -> {
            te.buildAndLayout(texts, 760, 560);
            scene.begin(2, text, atlas);
            te.paint(scene);
        });
        int textInstances = scene.count();
        View card = CardDemo::view;
        double cardFrame = perFrame(() -> {
            te.buildAndLayout(card, 760, 560);
            scene.begin(2, text, atlas);
            te.paint(scene);
        });

        // Frames of controls, built, laid out and painted: per control, net of
        // an empty painted frame; then with the pointer resting on a button.
        double emptyPainted = perFrame(() -> {
            te.buildAndLayout(c -> {}, 800, 600);
            scene.begin(2, text, atlas);
            te.paint(scene);
        });
        Runnable controlsFrame = () -> {
            te.buildAndLayout(AllocSpike::controls, 800, 600);
            scene.begin(2, text, atlas);
            te.paint(scene);
        };
        double controls = (perFrame(controlsFrame) - emptyPainted) / CONTROLS;
        Rect first = te.bounds(LABELS[0]);
        te.pointerMove(first.x() + first.width() / 2, first.y() + first.height() / 2, 0);
        double controlsHover = (perFrame(controlsFrame) - emptyPainted) / CONTROLS;
        te.pointerLeave();

        // A list of 10000 rows in a frame that does not change: per row in view, net of an empty frame.
        ListState rowsState = new ListState();
        Rows rows = new Rows();
        View listView = c -> {
            rows.c = c;
            list(c, rowsState, 10_000, rows).height(560);
        };
        double listRaw = perFrame(() -> {
            te.buildAndLayout(listView, 800, 600);
            scene.begin(2, text, atlas);
            te.paint(scene);
        });
        int inView = rowsState.last() - rowsState.first() + 1;
        double listFrame = (listRaw - emptyPainted) / Math.max(1, inView);

        boolean nativeImage = "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
        System.out.printf("ALLOC mode=%s empty_frame=%.0f handles=%.1f lambdas=%.1f temprefs=%.1f"
                        + " key_long=%.1f key_object=%.1f shell_frame=%.0f text_frame=%.0f text_instances=%d"
                        + " card_frame=%.0f controls_frame=%.1f controls_hover_frame=%.1f list_frame=%.1f list_rows=%d%n",
                nativeImage ? "native" : "jvm", empty, handles, lambdas, temprefs, keyLong, keyObject, shell,
                textFrame, textInstances, cardFrame, controls, controlsHover, listFrame, inView);
        // Design §8, checked where it ships: the framework allocates nothing
        // per element, and a whole frame stays within 32 KB.
        if (nativeImage && textFrame > 0.5 * textInstances) {
            System.out.printf("FAIL: the framework allocates %.1f bytes per instance of a text frame%n",
                    textFrame / textInstances);
            System.exit(1);
        }
        if (nativeImage && (controls > 0.5 || controlsHover > 0.5)) {
            System.out.printf("FAIL: the framework allocates %.1f bytes per control (%.1f hovered)%n",
                    controls, controlsHover);
            System.exit(1);
        }
        if (nativeImage && listFrame > 0.5) {
            System.out.printf("FAIL: the framework allocates %.1f bytes per list row in view%n", listFrame);
            System.exit(1);
        }
        if (nativeImage && cardFrame > 32768) {
            System.out.printf("FAIL: a card frame allocates %.0f bytes, over 32 KB%n", cardFrame);
            System.exit(1);
        }
    }

    /** Controls a controls frame builds: 40 buttons, 40 check boxes, 20 sliders. */
    private static final int CONTROLS = 100;
    private static final String[] LABELS = new String[40];
    private static final BoolRef[] CHECKS = new BoolRef[40];
    private static final DoubleRef[] VALUES = new DoubleRef[20];

    static {
        for (int i = 0; i < LABELS.length; i++) {
            LABELS[i] = "控件 " + i;
            CHECKS[i] = new BoolRef(i % 2 == 0);
        }
        for (int i = 0; i < VALUES.length; i++) {
            VALUES[i] = new DoubleRef(i * 5);
        }
    }

    private static void controls(Context c) {
        for (int i = 0; i < 40; i++) {
            button(c.key(i), LABELS[i]);
        }
        for (int i = 0; i < 40; i++) {
            checkbox(c.key(100 + i), CHECKS[i], LABELS[i]);
        }
        for (int i = 0; i < 20; i++) {
            slider(c.key(200 + i), VALUES[i], 0, 100);
        }
    }

    /** A list's rows, made once, so a frame builds them without a lambda of its own. */
    private static final class Rows implements java.util.function.IntConsumer {
        Context c;

        @Override
        public void accept(int i) {
            text(c.key(i), LABELS[i % LABELS.length]);
        }
    }

    private static final String[] TEXTS = {
        "j-ui 文字渲染", "Core Text 排版 · Core Graphics 光栅化 · Metal 绘制",
        "Java 通过 FFM 直接调用 Core Text：由它找字体，为缺字的字体回退。The quick brown fox jumps over the lazy dog.",
        "Regular 常规", "11pt 小字号：永和九年，岁在癸丑，暮春之初。0123456789",
    };

    /** Bytes this thread allocates in a frame of the view, on average. */
    private static double perFrame(Engine e, View view) {
        return perFrame(() -> e.buildAndLayout(view, 800, 600));
    }

    private static double perFrame(Runnable frame) {
        for (int i = 0; i < WARMUP; i++) {
            frame.run();
        }
        long before = MX.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < FRAMES; i++) {
            frame.run();
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
