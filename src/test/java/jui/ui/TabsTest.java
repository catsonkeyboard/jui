package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class TabsTest {
    static final String[] TITLES = {"General", "Appearance", "Advanced"};

    static Tester shell(IntRef selected, int[] changed) {
        return Tester.of(c -> {
            if (tabs(c, selected, TITLES).changed()) changed[0]++;
        }, 300, 100);
    }

    public void testAClickChoosesAndReportsIt() {
        IntRef selected = new IntRef(0);
        int[] changed = {0};
        Tester tt = shell(selected, changed);
        tt.frame();
        Check.near(0, selected.get());
        tt.click("Appearance");
        tt.frame();
        Check.equal(1, selected.get());
        Check.equal(1, changed[0]);
        tt.click("Appearance");                              // the same one again: no report
        tt.frame();
        Check.equal(1, changed[0]);
    }

    public void testTheArrowsWrapAndHomeAndEndChooseWithTheFocusFollowing() {
        IntRef selected = new IntRef(0);
        int[] changed = {0};
        Tester tt = shell(selected, changed);
        tt.frame();
        tt.click("General");                                 // the focus is on the first tab
        tt.frame();
        Check.isTrue(tt.focused("General"), "the chosen tab has the focus");
        tt.key(Shortcut.of(Key.RIGHT));
        tt.frame();
        Check.equal(1, selected.get());
        Check.isTrue(tt.focused("Appearance"), "the focus follows the choice");
        tt.key(Shortcut.of(Key.RIGHT));
        tt.key(Shortcut.of(Key.RIGHT));                      // past the end wraps to the start
        tt.frame();
        Check.equal(0, selected.get());
        Check.isTrue(tt.focused("General"), "wrapped, and the focus followed");
        tt.key(Shortcut.of(Key.HOME));
        tt.frame();
        Check.equal(0, selected.get());
        tt.key(Shortcut.of(Key.END));
        tt.frame();
        Check.equal(2, selected.get());
        Check.isTrue(tt.focused("Advanced"), "End goes to the last one");
    }

    public void testTheChoiceIsClampedToTheTabs() {
        IntRef selected = new IntRef(5);
        int[] changed = {0};
        Tester tt = shell(selected, changed);
        tt.frame();
        Check.equal(2, selected.get());
        Check.equal(0, changed[0]);                          // clamping is not a choice
    }

    public void testTheChosenTabIsUnderlinedInAccent() {
        IntRef selected = new IntRef(1);
        int[] changed = {0};
        int[] colors = new int[2];
        Tester tt = Tester.of(c -> {
            Theme t = c.theme();
            colors[0] = t.accent().rgba();
            tabs(c, selected, TITLES).fillWidth();
        }, 300, 100);
        tt.frame();
        Rect tab = tt.find("Appearance");
        boolean underlined = false;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[3] == 2 && (Math.round(in[8] * 255) << 24 | Math.round(in[9] * 255) << 16
                    | Math.round(in[10] * 255) << 8 | Math.round(in[11] * 255)) == colors[0]
                    && Math.abs(in[0] - tab.x() - 6) < 0.01 && Math.abs(in[1] + in[3] - tab.y() - tab.height()) < 0.01) {
                underlined = true;
            }
        }
        Check.isTrue(underlined, "the chosen tab has a 2 DIP accent underline");
    }
}
