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
}
