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
}
