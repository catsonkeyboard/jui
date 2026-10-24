package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class CallbackTest {
    public void testOnClickRunsAfterTheBuildAndMayChangeTheList() {
        java.util.List<String> items = new java.util.ArrayList<>(java.util.List.of("a", "b", "c"));
        Tester tt = Tester.of(c -> column(c).children(() -> {
            for (String s : items) {                        // removing inside the loop would throw
                box(c.key(s)).padding(4).onClick(() -> items.remove(s)).children(() -> text(c, s));
            }
        }), 300, 200);
        tt.click("b");
        Check.equal(java.util.List.of("a", "c"), tt.texts());
    }

    public void testQueryAndCallbackBothFireOnce() {
        int[] q = {0}, cb = {0};
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(80, 30).label("x").onClick(() -> cb[0]++);
            if (e.clicked()) q[0]++;
        }, 300, 200);
        tt.click("x");
        tt.frame();
        Check.equal(1, q[0]); Check.equal(1, cb[0]);
    }

    public void testDisabledIsNotClickedHoveredOrPressed() {
        boolean[] seen = new boolean[3];
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(80, 30).label("x").disabled(true);
            seen[0] |= e.clicked(); seen[1] |= e.hovered(); seen[2] |= e.pressed();
        }, 300, 200);
        tt.click("x");
        Check.isTrue(!seen[0] && !seen[1] && !seen[2], "nothing reported");
    }

    public void testDisabledIsInherited() {
        int[] n = {0};
        Tester tt = Tester.of(c -> column(c).disabled(true).children(() -> {
            if (box(c).size(80, 30).label("x").clicked()) n[0]++;
        }), 300, 200);
        tt.click("x");
        Check.equal(0, n[0]);
    }

    public void testDisablingDuringAPressCancelsTheClick() { // Review Focus 5
        int[] n = {0};
        boolean[] off = {false};
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(80, 30).label("x").disabled(off[0]);
            if (e.clicked()) n[0]++;
        }, 300, 200);
        Rect r = tt.find("x");
        tt.press(r.x() + 5, r.y() + 5);
        off[0] = true; tt.frame();
        tt.release(r.x() + 5, r.y() + 5);
        off[0] = false; tt.frame();
        Check.equal(0, n[0]);
    }
}
