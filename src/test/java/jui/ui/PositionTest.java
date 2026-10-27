package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class PositionTest {
    public void testAbsoluteChildrenPaintAndHitOverInFlowOnes() {
        int[] abs = {0}, flow = {0};
        Tester tt = Tester.of(c -> box(c).size(200, 100).children(() -> {
            if (box(c).absolute().size(50, 50).label("abs").clicked()) abs[0]++;   // built first
            if (box(c).size(100, 100).label("flow").clicked()) flow[0]++;          // under it
        }), 300, 200);
        tt.press(10, 10); tt.release(10, 10);
        Check.equal(1, abs[0]); Check.equal(0, flow[0]);
    }

    public void testInvisibleKeepsItsRoomButIsNotPaintedHitOrFocused() {
        int[] n = {0};
        Tester tt = Tester.of(c -> column(c).children(() -> {
            Element a = box(c).size(80, 40).label("a").background(Color.rgb(0xFF0000)).focusable().invisible();
            if (a.clicked()) n[0]++;
            box(c).size(80, 40).label("b").focusable();
        }), 300, 200);
        tt.frame();
        Check.near(40, tt.find("b").y());
        Check.equal(0, tt.instances());                          // nothing painted: a is invisible, b has no look
        tt.press(10, 10); tt.release(10, 10);
        Check.equal(0, n[0]);
        tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("b"), "tab skips the invisible element");
    }

    public void testTopAndLeftMoveAnElementInTheFlowButNotItsSiblings() { // MyGO's style_test.go:207
        Tester tt = Tester.of(c -> row(c).height(10).alignItems(Align.START).label("r").children(() -> {
            box(c).size(20, 10).label("a").top(5).left(3);
            box(c).size(20, 10).label("b");
            box(c).size(20, 10).label("c").right(4).bottom(2);
            box(c).size(20, 10).label("d").left(1).right(9).top(2).bottom(9);   // left and top win
        }), 300, 200);
        Rect r = tt.find("r");
        Check.near(r.x() + 3, tt.find("a").x());
        Check.near(r.y() + 5, tt.find("a").y());
        Check.near(r.x() + 20, tt.find("b").x());             // where the flow put it
        Check.near(r.x() + 40 - 4, tt.find("c").x());
        Check.near(r.y() - 2, tt.find("c").y());
        Check.near(r.x() + 60 + 1, tt.find("d").x());
        Check.near(r.y() + 2, tt.find("d").y());
    }

    public void testAMovedElementIsHitWhereItShows() {
        int[] clicks = {0};
        Tester tt = Tester.of(c -> column(c).padding(10).alignItems(Align.START).children(() -> {
            if (box(c).size(40, 20).label("a").top(30).left(50).clicked()) clicks[0]++;
        }), 300, 200);
        tt.frame();                                             // in the flow at (10, 10); moved to (60, 40)
        tt.press(30, 20);                                       // where the flow put it: nothing there now
        tt.release(30, 20);
        Check.equal(0, clicks[0]);
        tt.press(80, 50);                                       // where it shows
        tt.release(80, 50);
        Check.equal(1, clicks[0]);
    }

    public void testAMovedElementDoesNotGrowAScrollContainersContent() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scroll(c, st).size(100, 100).children(() -> {
            box(c).height(200);
            box(c).height(10).top(500);
        }), 300, 300);
        tt.frame();
        Check.near(110, st.maxY());                             // 210 − 100, the offset aside
    }
}
