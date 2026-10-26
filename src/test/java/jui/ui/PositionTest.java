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
}
