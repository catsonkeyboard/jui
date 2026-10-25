package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ShortcutTest {
    static final Shortcut CMD_P = Shortcut.cmd(Key.P), CMD_D = Shortcut.cmd(Key.D), UP = Shortcut.of(Key.UP);

    public void testWindowShortcutMatchesExactModifiers() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(CMD_P)) n[0]++; text(c, "x"); }, 100, 100);
        tt.key(CMD_P);
        tt.key(Shortcut.of(Key.P));
        tt.key(Shortcut.of(Mod.CMD | Mod.SHIFT, Key.P));
        Check.equal(1, n[0]);
    }

    public void testElementShortcutNeedsFocusWithin() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (box(c).size(80, 30).label("a").focusable().shortcut(CMD_D)) n[0]++; }, 300, 200);
        tt.key(CMD_D);
        Check.equal(0, n[0]);
        tt.click("a"); tt.key(CMD_D);
        Check.equal(1, n[0]);
    }

    public void testTheFocusedElementWinsOverTheWindow() {
        int[] el = {0}, win = {0};
        Tester tt = Tester.of(c -> {
            if (c.shortcut(CMD_D)) win[0]++;
            if (box(c).size(80, 30).label("a").focusable().shortcut(CMD_D)) el[0]++;
        }, 300, 200);
        tt.key(CMD_D);
        Check.equal(1, win[0]); Check.equal(0, el[0]);
        tt.click("a"); tt.key(CMD_D);
        Check.equal(1, win[0]); Check.equal(1, el[0]);
    }

    public void testAShortcutIsReportedOnce() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(UP)) n[0]++; text(c, "x"); }, 100, 100);
        tt.key(UP); tt.frame(); tt.frame();
        Check.equal(1, n[0]);
    }

    public void testRepeatedKeysAreNotLost() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(UP)) n[0]++; text(c, "x"); }, 100, 100);
        tt.frame();
        tt.engine().keyDown(Key.UP, 0, false);
        tt.engine().keyDown(Key.UP, 0, true);
        tt.settle();                                   // frames until none is pending
        Check.equal(2, n[0]);
    }

    public void testAShortcutSeesAKeyInTheFrameItFirstAppears() {
        int[] n = {0};
        boolean[] on = {false};
        Tester tt = Tester.of(c -> { if (on[0] && c.shortcut(CMD_P)) n[0]++; text(c, "x"); }, 100, 100);
        tt.frame();
        on[0] = true;                                   // registered only from the next frame
        tt.key(CMD_P);
        Check.equal(1, n[0]);
    }

    public void testDisabledElementsTakeNoShortcut() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (box(c).size(80, 30).label("a").focusable().disabled(true).shortcut(CMD_D)) n[0]++; }, 300, 200);
        tt.click("a"); tt.key(CMD_D);
        Check.equal(0, n[0]);
    }

    public void testModifiers() {
        int[] mods = {0};
        Tester tt = Tester.of(c -> { mods[0] = c.modifiers(); text(c, "x"); }, 100, 100);
        tt.engine().modifiersChanged(Mod.SHIFT); tt.frame();
        Check.equal(Mod.SHIFT, mods[0]);
    }

    public void testAWindowShortcutForTabTakesTab() { // final review I4
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(Shortcut.of(Key.TAB))) n[0]++; box(c).size(80, 30).label("a").focusable(); }, 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        Check.equal(1, n[0]);
        Check.isTrue(!tt.focused("a"), "claimed: Tab does not move focus");
    }

    public void testAnEnterShortcutAroundAFocusedButtonWinsOverTheButton() { // final review I4
        int[] row = {0}, btn = {0};
        Tester tt = Tester.of(c -> {
            Element r = row(c).padding(10);
            r.children(() -> { if (button(c, "B").clicked()) btn[0]++; });
            if (r.shortcut(Shortcut.of(Key.ENTER))) row[0]++;
        }, 300, 200);
        tt.click("B");
        btn[0] = 0;
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(1, row[0]);
        Check.equal(0, btn[0]);
    }

    public void testAFocusedSliderTakesArrowsBeforeItsRow() { // final review I4
        DoubleRef v = new DoubleRef(50);
        int[] row = {0};
        Tester tt = Tester.of(c -> {
            Element r = row(c).padding(10);
            r.children(() -> slider(c, v, 0, 100, 1).width(160).label("s"));
            if (r.shortcut(Shortcut.of(Key.RIGHT))) row[0]++;
        }, 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.RIGHT));
        Check.near(51, v.get());
        Check.equal(0, row[0]);
    }

    boolean off;

    public void testADisabledElementsShortcutFallsThroughToTheWindow() { // final review I5
        int[] win = {0}, el = {0};
        Shortcut esc = Shortcut.of(Key.ESCAPE);
        Tester tt = Tester.of(c -> {
            if (c.shortcut(esc)) win[0]++;
            if (box(c).size(80, 30).label("a").focusable().disabled(off).shortcut(esc)) el[0]++;
        }, 300, 200);
        tt.click("a");
        off = true; tt.frame(); tt.frame();
        tt.key(esc);
        Check.equal(1, win[0]);
        Check.equal(0, el[0]);
    }
}
