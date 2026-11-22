package jui.ui;

import jui.testing.Check;

import java.time.Duration;

import static jui.ui.Ui.*;

public final class SpringMotionTest {
    static final double STIFF = 170, DAMP = 20;
    // An obviously underdamped pair for the overshoot proof: ζ≈0.46, ~20% over.
    static final double OSTIFF = 170, ODAMP = 12;
    static final Duration TICK = Duration.ofMillis(16);

    /** A host box whose spring channel the test reads each frame. */
    static Tester host(float[] value, float[] target, float[] max) {
        return host(value, target, max, STIFF, DAMP);
    }

    static Tester host(float[] value, float[] target, float[] max, double stiff, double damp) {
        return Tester.of(c -> {
            Element e = box(c).label("spring-host");
            value[0] = e.spring("x", target[0], stiff, damp);
            max[0] = Math.max(max[0], value[0]);
        }, 60, 40);
    }

    public void testSpringsOvershootAndSettlesExactly() {
        float[] v = new float[1], target = new float[1], max = new float[1];
        Tester tt = host(v, target, max, OSTIFF, ODAMP);
        tt.frame();                                          // the channel comes to rest at 0
        target[0] = 100;
        tt.frame();                                          // the pass that retargets: asks for frames
        tt.advance(Duration.ofMillis(500));                  // the overshoot window
        Check.isTrue(max[0] > 115, "an underdamped spring overshoots the target, max=" + max[0]);
        tt.advance(Duration.ofMillis(1500));                 // the rest of the settle
        Check.equal(100f, v[0]);                             // exact: the settle writes the target
    }

    public void testRetargetingKeepsTheVelocity() {
        float[] v = new float[1], target = new float[1], max = new float[1];
        Tester tt = host(v, target, max);
        tt.frame();
        target[0] = 100;
        tt.frame();
        tt.advance(Duration.ofMillis(50));
        float before = v[0];
        target[0] = 0;                                       // reverse while moving up
        tt.frame();                                          // the retarget pass: velocity carried
        tt.advance(TICK);
        Check.isTrue(v[0] > before, "the spring keeps its velocity across a retarget: "
                + v[0] + " > " + before);
    }

    public void testStayingAtTheTargetStopsAskingForFrames() {
        float[] v = new float[1], target = new float[1], max = new float[1];
        Tester tt = host(v, target, max);
        tt.frame();
        target[0] = 50;
        tt.frame();
        tt.advance(Duration.ofMillis(2500));
        Check.equal(50f, v[0]);
        Check.isTrue(!tt.framePending(), "a settled spring does not keep the frame loop alive");
    }

    public void testASpringChannelIsDistinctPerKey() {
        float[] a = new float[1], b = new float[1];
        Tester tt = Tester.of(c -> {
            Element e = box(c).label("m");
            a[0] = e.spring("a", 10, STIFF, DAMP);
            b[0] = e.spring("b", 90, STIFF, DAMP);
        }, 60, 40);
        tt.frame();
        tt.advance(Duration.ofMillis(33));
        Check.isTrue(a[0] <= 10.5f && b[0] >= 80, "two channels run independently: "
                + a[0] + " / " + b[0]);
    }
}
