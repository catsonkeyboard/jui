package jui.perf;

import java.util.ArrayList;
import java.util.List;
import jui.testing.Check;

/** Deterministic clocks keep threshold boundaries and repeated sections honest. */
public final class FrameStatsTest {
    public void testThresholdModesAndInvalidValues() {
        for (String value : new String[] {null, "", "  ", "off", "garbage", "-2", "NaN", "Infinity", "1e99"}) {
            Check.equal(-1L, FrameStats.threshold(value));
        }
        Check.equal(8_000_000L, FrameStats.threshold("1"));
        Check.equal(0L, FrameStats.threshold("all"));
        Check.equal(0L, FrameStats.threshold("0"));
        Check.equal(2_500_000L, FrameStats.threshold(" 2.5 "));
    }

    public void testRepeatedSectionsAndFrameCountersAreReset() {
        long[] time = {0}, bytes = {100}, gc = {4};
        List<String> lines = new ArrayList<>();
        FrameStats stats = new FrameStats(0, () -> time[0], () -> bytes[0], () -> gc[0], lines::add);
        stats.begin();
        stats.round();
        long start = stats.start(); time[0] += 1_000_000; stats.built(start);
        start = stats.start(); time[0] += 2_000_000; stats.built(start);
        start = stats.start(); time[0] += 3_000_000; stats.laidOut(start);
        stats.round();
        start = stats.start(); time[0] += 1_000_000; stats.built(start);
        start = stats.start(); time[0] += 2_000_000; stats.laidOut(start);
        start = stats.start(); time[0] += 4_000_000; stats.painted(start);
        start = stats.start(); time[0] += 2_000_000;
        bytes[0] = 612; gc[0] = 5;
        stats.finish(start);
        Check.equal(1, lines.size());
        String line = lines.getFirst();
        for (String field : new String[] {"total=15.000ms", "build=4.000ms", "passes=3", "rounds=2", "layout=5.000ms", "layouts=2", "paint=4.000ms", "present=2.000ms", "alloc=512B", "gc=1"}) {
            Check.isTrue(line.contains(field), "missing " + field + " in " + line);
        }
        stats.begin(); stats.round();
        start = stats.start(); time[0] += 1_000_000; stats.painted(start);
        stats.finish(stats.start());
        Check.isTrue(lines.get(1).contains("passes=0 rounds=1"), "counters reset");
        Check.isTrue(lines.get(1).contains("alloc=0B gc=0"), "per-frame deltas reset");
    }

    public void testThresholdUsesWholeFrameAndExcludesLoggingAllocation() {
        long[] time = {0}, bytes = {0};
        List<String> lines = new ArrayList<>();
        FrameStats stats = new FrameStats(8_000_000, () -> time[0], () -> bytes[0], () -> 0,
                line -> { lines.add(line); bytes[0] += 1000; });
        stats.begin(); time[0] = 8_000_000; stats.finish(stats.start());
        Check.equal(0, lines.size());
        stats.begin(); time[0] += 9_000_000; bytes[0] += 32; stats.finish(stats.start());
        Check.equal(1, lines.size());
        Check.isTrue(lines.getFirst().contains("alloc=32B"), "logging excluded from allocation");
        stats.begin(); time[0] += 9_000_000; stats.finish(stats.start());
        Check.isTrue(lines.get(1).contains("alloc=0B"), "previous logging excluded");
    }

    public void testUnavailableCountersAreNotReportedAsZero() {
        long[] time = {0};
        List<String> lines = new ArrayList<>();
        FrameStats stats = new FrameStats(0, () -> time[0], () -> -1, () -> -1, lines::add);
        stats.begin(); time[0]++;
        stats.finish(stats.start());
        Check.isTrue(lines.getFirst().contains("alloc=n/a gc=n/a"), "unsupported metrics explicit");
    }
}
