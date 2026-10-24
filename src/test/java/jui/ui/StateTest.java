package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class StateTest {
    public void testLocalStateSurvivesFrames() {
        int[] created = {0};
        Object[] seen = new Object[3];
        int[] frame = {0};
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(10, 10);
            seen[frame[0]++] = local(e, "n", () -> { created[0]++; return new int[1]; }).get();
        }, 100, 100);
        tt.frame(); tt.frame(); tt.frame();
        Check.equal(1, created[0]);
        Check.isTrue(seen[0] == seen[2], "the same value, frame after frame");
    }

    public void testLocalStateGoesWithItsElement() {
        int[] created = {0};
        boolean[] show = {true};
        Tester tt = Tester.of(c -> {
            if (show[0]) local(box(c).size(10, 10), "n", () -> created[0]++);
        }, 100, 100);
        tt.frame();
        show[0] = false; tt.frame();      // absent: its state is pruned
        show[0] = true; tt.frame();
        Check.equal(2, created[0]);
    }

    public void testNamesKeepLocalsApart() {
        boolean[] got = new boolean[2];
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(10, 10);
            got[0] = localBool(e, "a", true).get();
            got[1] = localBool(e, "b", false).get();
        }, 100, 100);
        tt.frame();
        Check.isTrue(got[0] && !got[1], "a and b are separate");
    }

    public void testLocalFollowsTheKey() {
        java.util.List<String> order = new java.util.ArrayList<>(java.util.List.of("x", "y"));
        java.util.Map<String, Float> got = new java.util.HashMap<>();
        Tester tt = Tester.of(c -> {
            for (String k : order) {
                FloatRef f = localFloat(box(c.key(k)).size(10, 10), "v", k.equals("x") ? 1 : 2);
                got.put(k, f.get());
            }
        }, 100, 100);
        tt.frame();
        java.util.Collections.reverse(order); tt.frame();
        Check.near(1, got.get("x")); Check.near(2, got.get("y"));
    }

    public void testBoundsAreTheLastFramesBox() {
        Rect[] r = new Rect[1];
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> r[0] = box(c).size(100, 40).bounds()), 300, 200);
        tt.frame();
        Check.equal(new Rect(0, 0, 0, 0), r[0]);
        tt.frame();
        Check.equal(new Rect(10, 10, 100, 40), r[0]);
    }
}
