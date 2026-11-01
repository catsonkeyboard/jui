package jui.spike;

import java.util.Arrays;
import java.util.Locale;
import jui.app.*;
import jui.examples.NotesDemo;
import jui.ffi.ObjC;
import jui.ui.*;

/** Native AppKit key dispatch → drawable presentation, and continuously presented scroll cadence. */
public final class PresentBench {
    private static final int WARMUP = 120, SAMPLES = 1000;
    private Window window;
    private long nativeView, processInfo;
    private final Object[] workload = NotesDemo.forTools(true);
    private final jui.util.LongMap<String> paintedTexts = new jui.util.LongMap<>();
    private final jui.util.LongMap<Double> paintedTimes = new jui.util.LongMap<>();
    private final Painter.Draw recordPaint = (p, bounds) -> {
        if (window == null) return;
        paintedTexts.put(window.engine().frame(), window.engine().shownText("search"));
        paintedTimes.put(window.engine().frame(), ObjC.sendDouble(processInfo, "systemUptime"));
    };
    private final View view = c -> {
        ((View) workload[0]).build(c);
        Ui.canvas(c.key("native-present-probe")).absolute().left(0).top(0).size(1, 1).draw(recordPaint);
    };
    private final TextRef query = (TextRef) workload[2];
    private final ListState files = (ListState) workload[1];
    private final double[] keys = new double[SAMPLES], gaps = new double[SAMPLES], cpu = new double[SAMPLES], display = new double[SAMPLES];
    private int phase, index, missed, skipped;
    private double eventTime, previousPresent;
    private long expectedFrame;
    private volatile boolean done;

    static boolean withinBudget(double keyMax, double gap95, double gap50, int missed, int count, double hz) {
        double frame = 1000 / hz;
        return hz > 0 && keyMax >= 0 && keyMax <= frame && gap50 > 0 && gap50 <= frame * 1.1
                && gap95 > 0 && gap95 <= frame * 1.5 && count > 0 && missed >= 0 && missed <= count / 100;
    }

    public static void main(String[] args) { new PresentBench().run(); }

    private void run() {
        Thread.startVirtualThread(() -> {
            try { Thread.sleep(90_000); } catch (InterruptedException ignored) {}
            if (!done) { System.err.println("FAIL: no complete native presentation samples (window must be visible)"); System.exit(1); }
        });
        App.run(() -> {
            window = Window.open(WindowOptions.of("Notes presentation benchmark").size(980, 640).content(view));
            long nsWindow = ObjC.send(ObjC.send(ObjC.cls("NSApplication"), "sharedApplication"), "windowWithWindowNumber:", window.windowNumber());
            nativeView = ObjC.send(nsWindow, "contentView");
            processInfo = ObjC.send(ObjC.cls("NSProcessInfo"), "processInfo");
            window.onPresented((frame, at) -> {
                try { presented(frame, at); }
                catch (Throwable error) { done = true; error.printStackTrace(); System.exit(1); }
            });
            window.invalidate();
        });
    }

    private void presented(long frame, double at) {
        String painted = paintedTexts.remove(frame);
        Double paintedAt = paintedTimes.remove(frame);
        if (frame < expectedFrame) return;
        if (!(at > 0)) {
            if (phase == 3 && index >= WARMUP) skipped++;
            window.invalidate(); return;
        }
        if (phase == 0) {
            Rect search = window.engine().bounds("search");
            expectedFrame = window.engine().frame() + 1;
            window.pointerDown(search.x() + 10, search.y() + 10, 0, 1, 0);
            window.pointerUp(search.x() + 10, search.y() + 10, 0, 0);
            phase = 1;
            return;
        }
        if (phase == 1) {
            phase = 2; sendKey(); return;
        }
        if (phase == 2) {
            if (!((index & 1) == 0 ? "a" : "").equals(painted)) throw new AssertionError("presented frame has not consumed key index=" + index + " painted=" + painted + " query=" + query.get() + " shown=" + window.engine().shownText("search") + " frame=" + frame + " active=" + window.engine().textActive() + " focused=" + window.engine().focusIn("search"));
            double duration = (at - eventTime) * 1000;
            if (!(duration > 0)) throw new AssertionError("input/present timestamp domains differ");
            if (paintedAt == null) throw new AssertionError("no paint timestamp for presented frame");
            if (index < 5) System.out.printf(Locale.ROOT, "PRESENTPROBE key_cpu=%.3fms paint_present=%.3fms callback_delay=%.3fms%n",
                    (paintedAt - eventTime) * 1000, (at - paintedAt) * 1000, (ObjC.sendDouble(processInfo, "systemUptime") - at) * 1000);
            if (index >= WARMUP) {
                keys[index - WARMUP] = duration;
                cpu[index - WARMUP] = (paintedAt - eventTime) * 1000;
                display[index - WARMUP] = (at - paintedAt) * 1000;
            }
            if (++index < WARMUP + SAMPLES) { sendKey(); return; }
            phase = 3; index = 0; previousPresent = at;
        } else {
            double gap = (at - previousPresent) * 1000;
            previousPresent = at;
            if (index >= WARMUP) {
                gaps[index - WARMUP] = gap;
                if (gap > window.refreshNanos() / 1e6 * 1.5) missed++;
            }
            if (++index == WARMUP + SAMPLES) { report(); return; }
        }
        // Delivered at the native Input boundary, exactly as MacWindow.wheel sends it.
        expectedFrame = window.engine().frame() + 1;
        window.scroll(120, 320, 0, 24, true, 0);
    }

    private void sendKey() {
        long pool = ObjC.poolPush();
        try {
            expectedFrame = window.engine().frame() + 1;
            eventTime = ObjC.sendDouble(processInfo, "systemUptime");
            long key = ObjC.keyEvent(window.windowNumber(), (index & 1) == 0 ? "a" : "\u007f",
                    (short) ((index & 1) == 0 ? 0 : 0x33), eventTime);
            ObjC.send(nativeView, "keyDown:", key);
        } finally { ObjC.poolPop(pool); }
    }

    private void report() {
        done = true;
        Arrays.sort(keys); Arrays.sort(gaps); Arrays.sort(cpu); Arrays.sort(display);
        double hz = 1e9 / window.refreshNanos();
        double keyMax = keys[SAMPLES - 1], gap50 = gaps[SAMPLES / 2], gap95 = gaps[950];
        System.out.printf(Locale.ROOT, "PRESENTBENCH hz=%.1f key_p95=%.3fms key_max=%.3fms scroll_p50=%.3fms scroll_p95=%.3fms scroll_max=%.3fms missed_intervals=%d/%d skipped_callbacks=%d last_row=%d%n",
                hz, keys[950], keyMax, gap50, gap95, gaps[SAMPLES - 1], missed, SAMPLES, skipped, files.last());
        System.out.printf(Locale.ROOT, "PRESENTBENCH key_cpu_p95=%.3fms paint_present_p95=%.3fms%n", cpu[950], display[950]);
        boolean pass = withinBudget(keyMax, gap95, gap50, missed, SAMPLES, hz) && files.last() > 100;
        if (!pass) System.err.println("FAIL: native key→present one-refresh budget or sustained scroll presentation cadence failed");
        window.close();
        System.exit(pass ? 0 : 1);
    }
}
