package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ScrollXTest {
    public void testAHorizontalScrollContainerScrollsItsRowAcross() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).label("h").children(() -> {
            for (int i = 0; i < 3; i++) box(c).size(200, 40).label("b" + i);
        }), 400, 200);
        tt.frame();
        Check.near(300, st.maxX());                         // 600 − 300
        Rect h = tt.find("h");
        Check.near(h.y() + 30, tt.find("b0").y());          // centered down, as a row
        st.scrollTo(150, 0);
        tt.frame();
        Check.near(h.x() - 150, tt.find("b0").x());
        st.scrollTo(1000, 0);
        tt.frame();
        Check.near(300, st.x());                            // held within its content
    }

    public void testGrowDoesNothingAlongTheScrollingAxis() {
        Tester tt = Tester.of(c -> scrollHorizontal(c).size(300, 100).children(() -> box(c).size(50, 20).grow(1).label("g")),
                400, 200);
        Check.near(50, tt.find("g").width());
    }

    public void testScrollBothStretchesChildrenToTheWidest() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scrollBoth(c, st).size(300, 200).children(() -> {
            box(c).size(500, 300).label("wide");
            box(c).height(100).label("stretched");
        }), 400, 400);
        tt.frame();
        Check.near(200, st.maxX());
        Check.near(200, st.maxY());
        Check.near(500, tt.find("stretched").width());
    }

    public void testAVerticalContainerKeepsXAtZero() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scroll(c, st).size(100, 100).children(() -> box(c).size(500, 500)), 300, 300);
        tt.frame();
        st.scrollTo(50, 0);
        tt.frame();
        Check.near(0, st.x());
        Check.near(0, st.maxX());
    }

    public void testAWheelAcrossAndShiftWheelScrollAcross() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).label("h").children(() -> {
            for (int i = 0; i < 3; i++) box(c).size(200, 40);
        }), 400, 200);
        tt.scroll("h", 30, 0);                                  // a trackpad across: at once
        Check.near(30, st.x());
        Rect h = tt.find("h");
        tt.engine().scroll(h.x() + 10, h.y() + 10, 0, 40, false, Mod.SHIFT);   // Shift turns a wheel's notch across
        tt.advance(java.time.Duration.ofMillis(200));
        Check.near(70, st.x());
    }

    public void testAWheelDownOverAHorizontalContainerGoesToTheOneAroundIt() { // Review Focus 1
        ScrollState outer = new ScrollState(), inner = new ScrollState();
        Tester tt = Tester.of(c -> scroll(c, outer).size(300, 200).children(() -> {
            scrollHorizontal(c, inner).size(300, 100).label("h").children(() -> box(c).size(900, 40));
            box(c).height(500);
        }), 400, 400);
        tt.scroll("h", 0, 50);
        Check.near(50, outer.y());
        Check.near(0, inner.x());
    }

    public void testArrowKeysScrollAcrossWhenNothingElseTakesThem() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).children(() -> {
            button(c, "b");
            box(c).size(900, 40);
        }), 400, 200);
        tt.key(Shortcut.of(Key.TAB));                          // focus inside it
        tt.key(Shortcut.of(Key.RIGHT));
        Check.near(40, st.x());
    }

    public void testTheBarAlongTheBottomDragsAndPages() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).label("h").children(() -> box(c).size(1200, 40)),
                400, 200);
        Rect h = tt.find("h");
        tt.hover("h");
        float y = h.y() + h.height() - 3 - 3;                   // the bar's middle: 6 thick, 3 from the bottom
        tt.press(h.x() + h.width() - 20, y);                   // on the track, right of the thumb: a page
        tt.release(h.x() + h.width() - 20, y);
        Check.near(270, st.x());                                // 0.9 × 300
    }

    public void testTabRevealsAnElementOffTheRightEdge() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).children(() -> {
            for (int i = 0; i < 6; i++) box(c).size(100, 40).focusable().label("f" + i);
        }), 400, 200);
        for (int i = 0; i < 5; i++) tt.key(Shortcut.of(Key.TAB));       // to f4, at 400–500
        Check.near(200, st.x());                                // its right edge at the view's right
    }
}
