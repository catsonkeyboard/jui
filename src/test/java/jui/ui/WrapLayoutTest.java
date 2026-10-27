package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class WrapLayoutTest {
    /** Four 50 by 20 boxes in a 100 by 100 wrapping row: two lines of two, 60 of free space across. */
    static Tester lines(java.util.function.Consumer<Element> setup, float height) {
        return Tester.of(c -> {
            Element r = row(c).size(100, height).wrap().alignItems(Align.START).label("r");
            setup.accept(r);
            r.children(() -> {
                for (int i = 0; i < 4; i++) box(c).size(50, 20).label("b" + i);
            });
        }, 300, 300);
    }

    static float top(Tester tt, String s) {
        return tt.find(s).y() - tt.find("r").y();
    }

    public void testSpaceBetweenAroundAndEvenly() {
        Tester tt = lines(r -> r.alignContent(Justify.SPACE_BETWEEN), 100);
        Check.near(0, top(tt, "b0"));
        Check.near(80, top(tt, "b2"));                      // 20 + 60
        tt = lines(r -> r.alignContent(Justify.SPACE_AROUND), 100);
        Check.near(15, top(tt, "b0"));                      // 60 / 2 / 2
        Check.near(65, top(tt, "b2"));                      // 15 + 20 + 30
        tt = lines(r -> r.alignContent(Justify.SPACE_EVENLY), 100);
        Check.near(20, top(tt, "b0"));
        Check.near(60, top(tt, "b2"));
    }

    public void testOverflowFallsBackToTheStart() {
        Tester tt = lines(r -> r.alignContent(Justify.SPACE_EVENLY), 30);   // 40 of lines in 30
        Check.near(0, top(tt, "b0"));
        Check.near(20, top(tt, "b2"));
    }

    public void testOneLineAroundAndEvenlyCenterBetweenStarts() {
        for (Justify j : new Justify[] {Justify.SPACE_AROUND, Justify.SPACE_EVENLY, Justify.SPACE_BETWEEN}) {
            Tester tt = Tester.of(c -> row(c).size(100, 100).wrap().alignItems(Align.START).alignContent(j).label("r")
                    .children(() -> box(c).size(50, 20).label("b0")), 300, 300);
            Check.near(j == Justify.SPACE_BETWEEN ? 0 : 40, top(tt, "b0"));
        }
    }

    public void testWrapReverseStacksLinesFromTheCrossEnd() { // MyGO's style_test.go:174
        Tester tt = Tester.of(c -> row(c).size(50, 40).wrapReverse().alignItems(Align.START).label("r").children(() -> {
            box(c).size(30, 10).label("b0");
            box(c).size(30, 10).label("b1");
        }), 300, 300);
        Check.near(30, top(tt, "b0"));                      // the first line at the bottom
        Check.near(20, top(tt, "b1"));                      // the second above it
    }
}
