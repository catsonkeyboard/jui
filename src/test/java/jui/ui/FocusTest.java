package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class FocusTest {
    static final Shortcut TAB = Shortcut.of(Key.TAB), BACK = Shortcut.of(Mod.SHIFT, Key.TAB);
    boolean bDisabled;
    final View three = c -> column(c).padding(10).gap(10).children(() -> {
        box(c).size(80, 30).label("a").focusable();
        box(c).size(80, 30).label("b").focusable().disabled(bDisabled);
        box(c).size(80, 30).label("c").focusable();
    });

    public void testClickFocusesAndClickingNothingClears() {
        Tester tt = Tester.of(three, 300, 200);
        tt.click("b");
        Check.isTrue(tt.focused("b"), "b focused");
        tt.press(250, 190); tt.release(250, 190);
        Check.isTrue(!tt.focused("b"), "a click on nothing clears focus");
    }

    public void testTabFollowsBuildOrderAndWraps() {
        Tester tt = Tester.of(three, 300, 200);
        tt.key(TAB); Check.isTrue(tt.focused("a"), "a");
        tt.key(TAB); Check.isTrue(tt.focused("b"), "b");
        tt.key(TAB); Check.isTrue(tt.focused("c"), "c");
        tt.key(TAB); Check.isTrue(tt.focused("a"), "wraps to a");
        tt.key(BACK); Check.isTrue(tt.focused("c"), "shift-tab wraps back to c");
    }

    public void testTabSkipsDisabled() {
        bDisabled = true;
        Tester tt = Tester.of(three, 300, 200);
        tt.key(TAB); tt.key(TAB);
        Check.isTrue(tt.focused("c"), "b skipped");
    }

    public void testTabWithNothingFocusableDoesNothing() {
        Tester tt = Tester.of(c -> text(c, "x"), 100, 100);
        tt.key(TAB);
        Check.isTrue(!tt.focused("x"), "no focus");
    }

    public void testFocusRingShowsOnlyAfterTheKeyboard() {
        boolean[] visible = new boolean[2];
        Tester tt = Tester.of(c -> column(c).padding(10).gap(10).children(() -> {
            visible[0] = box(c).size(80, 30).label("a").focusable().focusVisible();
            visible[1] = box(c).size(80, 30).label("b").focusable().focusVisible();
        }), 300, 200);
        tt.click("a");  Check.isTrue(!visible[0], "not after a click");
        tt.key(TAB);    Check.isTrue(visible[1], "after tab, on b");
        tt.click("b");  Check.isTrue(visible[1], "a click on the focused element changes no focus: the ring stays");
        tt.click("a");  Check.isTrue(!visible[0] && !visible[1], "a click that moves focus hides it");
    }

    public void testEnterAndSpaceClickTheFocusedElement() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (box(c).size(80, 30).label("a").focusable().clicked()) n[0]++; }, 300, 200);
        tt.key(TAB);
        tt.key(Shortcut.of(Key.ENTER)); tt.key(Shortcut.of(Key.SPACE));
        tt.key(Shortcut.of(Mod.SHIFT, Key.SPACE));
        Check.equal(2, n[0]);
    }

    public void testAutoFocusOnlyWhenItAppears() {
        boolean[] show = {false};
        Tester tt = Tester.of(c -> column(c).children(() -> {
            box(c).size(80, 30).label("a").focusable();
            if (show[0]) box(c).size(80, 30).label("dialog").focusable().autoFocus();
        }), 300, 200);
        tt.frame();
        show[0] = true; tt.frame();
        Check.isTrue(tt.focused("dialog"), "focused as it appears");
        tt.click("a"); tt.frame();
        Check.isTrue(tt.focused("a"), "autoFocus does not take focus back later");
    }

    public void testHandleFocusesItsElementOnceItIsBuilt() {
        Handle h = new Handle();
        boolean[] show = {false};
        Tester tt = Tester.of(c -> column(c).children(() -> {
            box(c).size(80, 30).label("go").onClick(h::focus);
            if (show[0]) box(c).size(80, 30).label("target").focusable().bind(h);
        }), 300, 200);
        tt.click("go");                       // the target is absent: the request waits
        show[0] = true; tt.frame();
        Check.isTrue(tt.focused("target") && h.focused(), "focused once it appears");
    }

    public void testPassesStopAtThree() {
        Handle h1 = new Handle(), h2 = new Handle();
        boolean[] flip = {false};
        Tester tt = Tester.of(c -> {
            box(c).size(10, 10).focusable().bind(h1);
            box(c).size(10, 10).focusable().bind(h2);
            if (flip[0] = !flip[0]) h1.focus(); else h2.focus();   // focus changes every pass
        }, 100, 100);
        tt.frame();
        Check.equal(3, tt.engine().passes());
        Check.isTrue(tt.framePending(), "still consuming after 3 passes: another frame");
    }

    public void testFocusWithin() {
        boolean[] within = {false};
        Tester tt = Tester.of(c -> {
            Element r = row(c).padding(10);
            r.children(() -> box(c).size(80, 30).label("b").focusable());
            within[0] = r.focusWithin();
        }, 300, 200);
        tt.click("b"); tt.frame();
        Check.isTrue(within[0], "the row has focus within");
    }

    public void testRemovedFocusThenTabGoesToTheFirst() { // Review Focus 3
        java.util.List<String> ids = new java.util.ArrayList<>(java.util.List.of("a", "b"));
        Tester tt = Tester.of(c -> column(c).children(() -> {
            for (String s : ids) box(c.key(s)).size(80, 30).label(s).focusable();
        }), 300, 200);
        tt.click("b");
        ids.remove("b"); tt.frame();
        ids.add("b2"); tt.frame();
        Check.isTrue(!tt.focused("b2"), "a new element does not inherit the focus");
        tt.key(TAB);
        Check.isTrue(tt.focused("a"), "tab starts over at the first");
    }

    public void testFocusNeedsTheWindowFocused() {
        Tester tt = Tester.of(three, 300, 200);
        tt.click("a");
        tt.engine().windowFocusChanged(false); tt.frame();
        Check.isTrue(!tt.focused("a"), "window inactive");
        tt.engine().windowFocusChanged(true); tt.frame();
        Check.isTrue(tt.focused("a"), "back");
    }
}
