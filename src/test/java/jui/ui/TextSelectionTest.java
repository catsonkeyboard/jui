package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class TextSelectionTest {
    final TextRef q = new TextRef("hello world");
    final int[] ask = {-1, -1};                              // a selection to set in the next build, -1 for none
    final int[] seen = {-1, -1};

    Tester tester() {
        return Tester.of(c -> {
            Element in = textInput(c, q).label("in");
            if (ask[0] >= 0) {
                in.setTextSelection(ask[0], ask[1]);
                ask[0] = ask[1] = -1;
            }
            seen[0] = in.textSelectionStart();
            seen[1] = in.textSelectionEnd();
        }, 300, 200);
    }

    public void testASelectionSetInTheBuildShowsInThatFrame() {
        Tester tt = tester();
        tt.click("in");
        ask[0] = 1;
        ask[1] = 3;
        tt.frame();
        Check.equal("[1, 3]", java.util.Arrays.toString(tt.textSelection()));
        int fill = Theme.light().selection().rgba(), painted = 0;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] == 0 && Math.round(in[11] * 255) == (fill & 0xFF) && Math.round(in[8] * 255) == (fill >>> 24)) painted++;
        }
        Check.equal(1, painted);                            // its box, in the frame that set it
        tt.frame();
        Check.equal(1, seen[0]);
        Check.equal(3, seen[1]);
    }

    public void testEndsAreHeldInTheTextAndOnGraphemes() {
        q.set("a😀b");
        Tester tt = tester();
        tt.click("in");
        ask[0] = 0;
        ask[1] = 99;
        tt.frame();
        Check.equal("[0, 4]", java.util.Arrays.toString(tt.textSelection()));
        ask[0] = ask[1] = 2;                                 // inside the emoji's surrogate pair
        tt.frame();
        Check.equal("[1, 1]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testABackwardSelectionKeepsItsAnchor() {
        Tester tt = tester();
        tt.click("in");
        ask[0] = 5;
        ask[1] = 2;
        tt.frame();
        tt.key(Shortcut.of(Mod.SHIFT, Key.LEFT));           // the caret, at 2, moves; the anchor stays at 5
        Check.equal("[1, 5]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testItCommitsAComposition() {
        q.set("");
        Tester tt = tester();
        tt.click("in");
        tt.compose("ni", 2);
        ask[0] = ask[1] = 0;
        tt.frame();
        Check.equal("ni", q.get());
        Check.equal("[0, 0]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testACompositionTheSelectionCommitsIsReportedChangedOnce() { // final review
        q.set("");
        int[] changes = {0};
        boolean[] select = {false};
        Tester tt = Tester.of(c -> {
            Element in = textInput(c, q).label("in");
            if (in.changed()) changes[0]++;                  // read before the selection is set
            if (select[0]) {
                in.setTextSelection(0, 0);
                select[0] = false;
            }
        }, 300, 200);
        tt.click("in");
        tt.compose("ni", 2);
        select[0] = true;
        tt.frame();
        Check.equal("ni", q.get());
        Check.equal(1, changes[0]);
        tt.frame();
        Check.equal(1, changes[0]);
    }
}
