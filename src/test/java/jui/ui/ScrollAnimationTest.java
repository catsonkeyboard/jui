package jui.ui;

import java.time.Duration;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ScrollAnimationTest {
    final ScrollState st = new ScrollState();
    final int[] rows = {50};
    final View view = c -> scroll(c, st).size(200, 200).label("s").children(() -> {
        for (int i = 0; i < rows[0]; i++) box(c).height(20);
    });

    public void testItEasesOutToItsTarget() {
        Tester tt = Tester.of(view, 300, 300);
        tt.frame();
        st.scrollTo(400, Duration.ofMillis(200));
        tt.frame();
        Check.isTrue(st.scrolling(), "scrolling");
        tt.advance(Duration.ofMillis(100));
        Check.near(350, st.y(), 0.5);                       // 400 × (1 − 0.5³)
        tt.advance(Duration.ofMillis(120));
        Check.near(400, st.y());
        Check.isTrue(!st.scrolling() && !tt.framePending(), "at rest");
    }

    public void testAWheelStopsIt() {
        Tester tt = Tester.of(view, 300, 300);
        tt.frame();
        st.scrollTo(600, Duration.ofMillis(300));
        tt.advance(Duration.ofMillis(50));
        tt.scroll("s", 0, -10);                             // the user takes over
        float at = st.y();
        tt.advance(Duration.ofMillis(400));
        Check.isTrue(!st.scrolling(), "stopped");
        Check.near(at, st.y());
    }

    public void testFewerRowsMidWayEndAtTheNewEnd() { // Review Focus 2
        Tester tt = Tester.of(view, 300, 300);
        tt.frame();
        st.scrollTo(800, Duration.ofMillis(200));          // max 800
        tt.advance(Duration.ofMillis(50));
        rows[0] = 20;                                       // max 200 now
        tt.advance(Duration.ofMillis(300));
        Check.near(200, st.y());
        Check.isTrue(!st.scrolling(), "done");
    }

    public void testZeroJumps() {
        Tester tt = Tester.of(view, 300, 300);
        tt.frame();
        st.scrollTo(300, Duration.ZERO);
        tt.frame();
        Check.near(300, st.y());
        Check.isTrue(!st.scrolling(), "no animation");
    }
}
