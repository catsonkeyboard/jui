package jui.spike;

import java.util.Arrays;

import jui.ffi.Libc;
import jui.platform.mac.MacApp;
import jui.platform.mac.MacWindow;
import jui.render.Scene;
import jui.text.TextEngine;
import jui.text.TextLayout;

/**
 * The macOS spike: a window drawn with Metal, called from Java through FFM.
 *
 * <p>With {@code --bench} it measures startup, from the process's start to
 * the GPU's end of its first frame (which shows at the display's next
 * refresh), prints it and quits: as MyGO's ContentWindowOpen benchmark,
 * which times a window until its native UI has built a frame.
 */
public final class Main {
    private Main() {}

    private static final int BACKGROUND = 0xF3F4F6;

    public static void main(String[] args) {
        long start = Libc.processStartMicros();
        long mainAt = Libc.nowMicros();
        boolean bench = Arrays.asList(args).contains("--bench");
        boolean mainThread = MacApp.onMainThread();
        if (!mainThread) {
            System.err.println("jui: main() is not on the main thread; on the JVM, pass -XstartOnFirstThread");
            System.exit(2);
        }
        long[] marks = new long[2];
        MacApp.run(() -> {
            marks[0] = Libc.nowMicros();
            TextEngine text = new TextEngine("zh-Hans");
            MacWindow window = MacWindow.open("j-ui 验证原型", 760, 560, BACKGROUND, text, Main::paint);
            marks[1] = Libc.nowMicros();
            if (Arrays.asList(args).contains("--print-window")) {
                System.out.println("window=" + window.windowNumber());
            }
            if (bench) {
                window.onFirstFrame(done -> {
                    report(start, mainAt, marks[0], marks[1], window.firstCommitMicros, done,
                            window.renderer().libraryKind);
                    reportText(window, text);
                    System.exit(done > 0 ? 0 : 1);
                });
            }
        });
    }

    private static final String PARAGRAPH = "Java 通过 FFM 直接调用 Core Text：由它找字体，为缺字的字体回退"
            + "（中文落到苹方），再整形和断行。The quick brown fox jumps over the lazy dog. "
            + "中英文混排、标点挤压和自动换行，都交给系统处理，和 AppKit 画出来的一样。";

    /** The demo: a card of text, boxes, borders and shadows, which follows the window's size. */
    static void paint(Scene s, double width, double height) {
        float w = (float) width, h = (float) height;
        float cx = 32, cy = 32, cw = Math.max(360, w - 64), ch = Math.max(360, h - 64);

        s.shadow(cx, cy + 8, cw, ch, 16, 0x0F172A26, 24);
        s.box(cx, cy, cw, ch, 16, 0xFFFFFFFF, 1, 0xE5E7EBFF);

        float x = cx + 32, inner = cw - 64, y = cy + 26;
        y += s.text("j-ui 文字渲染", x, y, 26, 700, 0x111827FF, 0).height + 2;
        y += s.text("Core Text 排版 · Core Graphics 光栅化 · Metal 绘制", x, y, 13, 400, 0x6B7280FF, 0).height + 16;
        y += s.text(PARAGRAPH, x, y, 16, 400, 0x1F2937FF, inner).height + 16;

        // The weights of the system font, and the fallback font's.
        String[] names = {"Regular 常规", "Medium 中等", "Semibold 中粗", "Bold 粗体"};
        int[] weights = {400, 500, 600, 700};
        float wx = x, row = 0;
        for (int i = 0; i < names.length; i++) {
            TextLayout l = s.text(names[i], wx, y, 15, weights[i], 0x111827FF, 0);
            wx += l.width + 24;
            row = Math.max(row, l.height);
        }
        y += row + 12;
        y += s.text("11pt 小字号：永和九年，岁在癸丑，暮春之初，会于会稽山阴之兰亭。0123456789",
                x, y, 11, 400, 0x6B7280FF, inner).height + 16;

        // Tags.
        float tx = x;
        tx += tag(s, tx, y, "已完成", 0xDCFCE7FF, 0x86EFACFF, 0x166534FF) + 10;
        tx += tag(s, tx, y, "Metal", 0xDBEAFEFF, 0x93C5FDFF, 0x1E40AFFF) + 10;
        tag(s, tx, y, "GraalVM 25", 0xFEF3C7FF, 0xFCD34DFF, 0x92400EFF);

        // Buttons, light text on dark and dark on light, and a circle.
        float by = cy + ch - 32 - 44;
        s.shadow(x, by + 4, 152, 44, 10, 0x2563EB55, 12);
        button(s, x, by, 152, 44, "确定 OK", 0x2563EBFF, 0, 0xFFFFFFFF);
        button(s, x + 168, by, 152, 44, "取消 Cancel", 0xFFFFFFFF, 0xD1D5DBFF, 0x111827FF);
        s.box(cx + cw - 32 - 44, by, 44, 44, 22, 0xF43F5EFF);
    }

    /** A rounded tag with a label, and returns its width. */
    private static float tag(Scene s, float x, float y, String label, int fill, int border, int color) {
        TextLayout l = s.layout(label, 13, 500, 0);
        float w = l.width + 24, h = 28;
        s.box(x, y, w, h, h / 2, fill, 1, border);
        s.text(l, x + 12, y + (h - l.height) / 2, color);
        return w;
    }

    private static void button(Scene s, float x, float y, float w, float h, String label, int fill, int border,
                               int color) {
        s.box(x, y, w, h, 10, fill, border == 0 ? 0 : 1, border);
        TextLayout l = s.layout(label, 15, 500, 0);
        s.text(l, x + (w - l.width) / 2, y + (h - l.height) / 2, color);
    }

    private static void report(long start, long main, long launched, long ready, long committed, long done,
                               String library) {
        String mode = "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")) ? "native" : "jvm";
        System.out.printf("jui spike (%s, shader from %s)%n", mode, library);
        System.out.printf("  process start -> main()            %7.1f ms%n", ms(main - start));
        System.out.printf("  main() -> app launched             %7.1f ms%n", ms(launched - main));
        System.out.printf("  app launched -> window + Metal     %7.1f ms%n", ms(ready - launched));
        System.out.printf("  window -> first frame committed    %7.1f ms%n", ms(committed - ready));
        System.out.printf("  committed -> drawn by the GPU      %7.1f ms%n", ms(done - committed));
        System.out.printf("  total: process start -> first frame %6.1f ms%n", ms(done - start));
        System.out.printf("BENCH mode=%s main=%.1f launched=%.1f ready=%.1f committed=%.1f frame=%.1f%n",
                mode, ms(main - start), ms(launched - start), ms(ready - start), ms(committed - start),
                ms(done - start));
    }

    /** What the first frame's text cost, and what painting the same frame costs once its text is cached. */
    private static void reportText(MacWindow window, TextEngine text) {
        long cold = window.lastPaintNanos;
        long warm = Long.MAX_VALUE;
        for (int i = 0; i < 20; i++) {
            warm = Math.min(warm, window.paint());
        }
        System.out.printf("  text: %d instances, %d glyphs rasterized into the atlas%n",
                window.instances(), text.rasterized);
        System.out.printf("  first paint %.2f ms (layout %.2f ms, rasterizing %.2f ms); cached paint %.3f ms%n",
                cold / 1e6, text.layoutNanos / 1e6, text.rasterNanos / 1e6, warm / 1e6);
        // What a downcall costs: pthread_main_np does almost nothing itself.
        int sink = 0;
        for (int i = 0; i < 200_000; i++) {
            sink += Libc.pthreadMainNp();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            sink += Libc.pthreadMainNp();
        }
        double perCall = (System.nanoTime() - t0) / 1e6;
        System.out.printf("  an FFM downcall costs %.1f ns (%d)%n", perCall, sink > 0 ? 1 : 0);
        System.out.printf("TEXT paint=%.2f layout=%.2f raster=%.2f cached=%.3f glyphs=%d call_ns=%.1f%n",
                cold / 1e6, text.layoutNanos / 1e6, text.rasterNanos / 1e6, warm / 1e6, text.rasterized, perCall);
    }

    private static double ms(long micros) {
        return micros / 1000.0;
    }
}
