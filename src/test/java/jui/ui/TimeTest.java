package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class TimeTest {
    static final long MS = 1_000_000;

    public void testAdvanceRunsFramesAt60HzWhileFramesAreAskedFor() {
        java.util.List<Long> times = new java.util.ArrayList<>();
        Tester tt = Tester.of(c -> { times.add(c.now()); c.invalidate(); text(c, "x"); }, 100, 100);
        tt.frame();
        times.clear();
        tt.advance(java.time.Duration.ofMillis(50));
        Check.equal(3, times.size());
        Check.near(16.667, times.get(0) / 1e6, 0.01);
        Check.near(33.333, times.get(1) / 1e6, 0.01);
        Check.near(50.0, times.get(2) / 1e6, 0.01);
        Check.equal(50 * MS, tt.now());
    }

    public void testAfterWakesAFrameAtItsTime() {
        int[] frames = {0};
        boolean[] fired = {false};
        Tester tt = Tester.of(c -> {
            frames[0]++;
            if (c.now() >= 100 * MS) fired[0] = true; else c.after(java.time.Duration.ofMillis(100));
            text(c, "x");
        }, 100, 100);
        tt.frame();
        tt.advance(java.time.Duration.ofMillis(99));
        Check.isTrue(!fired[0], "not yet");
        tt.advance(java.time.Duration.ofMillis(1));
        Check.isTrue(fired[0], "fired at 100 ms");
        Check.equal(2, frames[0]);                 // no frames in between: nothing asked for them
    }

    public void testAFrameThatDoesNotCallAfterStopsTheTimer() {
        int[] frames = {0};
        Tester tt = Tester.of(c -> { if (frames[0]++ == 0) c.after(java.time.Duration.ofMillis(100)); text(c, "x"); }, 100, 100);
        tt.frame();
        tt.frame();                                // does not call after
        tt.advance(java.time.Duration.ofMillis(200));
        Check.equal(2, frames[0]);
    }

    public void testAnimateEasesOutToItsTarget() {
        float[] target = {0}, value = {0};
        Tester tt = Tester.of(c -> value[0] = box(c).size(10, 10).animate("x", target[0], java.time.Duration.ofMillis(100)), 100, 100);
        tt.frame();
        Check.near(0, value[0]);                   // first call: at its target
        target[0] = 1;
        tt.frame();
        Check.near(0, value[0]);                   // the frame the target changes returns the old value
        Check.isTrue(tt.framePending(), "moving: asks for frames");
        tt.advance(java.time.Duration.ofMillis(50));
        Check.near(0.875, value[0], 1e-3);         // 1 - (1 - 0.5)^3
        tt.advance(java.time.Duration.ofMillis(80));     // frames at 99.999998 (just short of the end) and 116.7 ms
        Check.near(1, value[0]);
        Check.isTrue(!tt.framePending(), "at rest: no more frames");
    }
}
