package jui.examples;

import java.time.Duration;

import jui.testing.Check;
import jui.ui.Tester;

public final class ScrollDemoTest {
    public void testAppendFollowsTheEnd() {
        ScrollDemo s = new ScrollDemo();
        Tester tt = Tester.of(s::view, 560, 640);
        tt.click("追加 5 行");
        Check.equal(s.entries.size() - 1, s.log.last());    // the last row followed to the last entry
        Check.isTrue(s.log.atEnd(), "at the end");
    }

    public void testTheRightButtonGlidesTheCardsAcross() {
        ScrollDemo s = new ScrollDemo();
        Tester tt = Tester.of(s::view, 560, 640);
        tt.click("往右（动画）");
        tt.advance(Duration.ofMillis(400));
        Check.near(400, s.cards.x());
    }
}
