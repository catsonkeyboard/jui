package jui.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Opt-in diagnostics. Frame ownership and section calls stay on the UI thread. */
public final class FrameStats {
    private static final long THRESHOLD = threshold(System.getenv("JUI_FRAME_STATS"));
    public static final boolean ENABLED = THRESHOLD >= 0;
    private static FrameStats pending;

    private final long threshold;
    private final LongSupplier clock, allocated, collections;
    private final Consumer<String> output;
    private long began, bytesBefore, gcBefore, build, layout, paint;
    private int passes, rounds, layouts;
    private boolean painted;

    /** Invalid and non-finite thresholds disable diagnostics; 1 means the default 8 ms. */
    static long threshold(String value) {
        if (value == null || value.isBlank()) return -1;
        value = value.trim();
        if (value.equals("all")) return 0;
        if (value.equals("1")) return 8_000_000;
        try {
            double nanos = Double.parseDouble(value) * 1_000_000;
            return Double.isFinite(nanos) && nanos >= 0 && nanos < Long.MAX_VALUE ? (long) nanos : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** Management beans are initialized only when diagnostics are enabled. */
    public static FrameStats create() {
        return ENABLED ? new FrameStats(THRESHOLD, System::nanoTime, Metrics::allocated,
                Metrics::collections, System.err::println) : null;
    }

    FrameStats(long threshold, LongSupplier clock, LongSupplier allocated, LongSupplier collections, Consumer<String> output) {
        this.threshold = threshold;
        this.clock = clock;
        this.allocated = allocated;
        this.collections = collections;
        this.output = output;
    }

    /** A platform draw cannot inherit a previous headless or failed frame. */
    public static void beginDraw() { pending = null; }

    /** Finish the UI frame after the renderer returns from encoding/submission. */
    public static void present(long started) {
        FrameStats stats = pending;
        pending = null;
        if (stats != null && stats.painted) stats.finish(started);
    }

    public void begin() {
        bytesBefore = allocated.getAsLong();
        gcBefore = collections.getAsLong();
        began = clock.getAsLong();
        build = layout = paint = 0;
        passes = rounds = layouts = 0;
        painted = false;
        pending = this;
    }

    public long start() { return clock.getAsLong(); }
    public void round() { rounds++; }
    public void built(long start) { build += clock.getAsLong() - start; passes++; }
    public void laidOut(long start) { layout += clock.getAsLong() - start; layouts++; }
    public void painted(long start) { paint += clock.getAsLong() - start; painted = true; }

    void finish(long start) {
        long end = clock.getAsLong();
        long bytesAfter = allocated.getAsLong(), gcAfter = collections.getAsLong();
        if (threshold < 0 || end - began <= threshold) return;
        // Snapshot before formatting: diagnostics never count their own log allocations.
        String bytes = delta(bytesBefore, bytesAfter, "B");
        String gc = delta(gcBefore, gcAfter, "");
        output.accept(String.format(Locale.ROOT,
                "JUI_FRAME_STATS total=%.3fms build=%.3fms passes=%d rounds=%d layout=%.3fms layouts=%d paint=%.3fms present=%.3fms alloc=%s gc=%s",
                (end - began) / 1e6, build / 1e6, passes, rounds, layout / 1e6, layouts, paint / 1e6,
                (end - start) / 1e6, bytes, gc));
    }

    private static String delta(long before, long after, String unit) {
        return before < 0 || after < before ? "n/a" : (after - before) + unit;
    }

    private static final class Metrics {
        private static final com.sun.management.ThreadMXBean THREAD =
                ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean ? bean : null;
        private static final GarbageCollectorMXBean[] GC =
                ManagementFactory.getGarbageCollectorMXBeans().toArray(GarbageCollectorMXBean[]::new);

        static long allocated() {
            return THREAD != null && THREAD.isThreadAllocatedMemorySupported() && THREAD.isThreadAllocatedMemoryEnabled()
                    ? THREAD.getCurrentThreadAllocatedBytes() : -1;
        }
        static long collections() {
            long count = 0;
            boolean supported = false;
            for (GarbageCollectorMXBean bean : GC) {
                long n = bean.getCollectionCount();
                if (n >= 0) { count += n; supported = true; }
            }
            return supported ? count : -1;
        }
    }
}
