package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class PopoverTest {
    /** A popover below an anchor button placed at (x, y) in a 300 by 200 window. */
    static Tester shell(BoolRef open, float x, float y, float panelHeight, int[] dismissed) {
        return Tester.of(c -> {
            Element anchor = button(c, "anchor").absolute().width(60).height(20).left(x).top(y);
            if (anchor.clicked()) {
                open.set(!open.get());                      // the app toggles it
            }
            Element panel = popover(c, anchor, open, () -> box(c).width(80).height(panelHeight).label("pop"));
            if (dismissed != null && panel.dismissed()) dismissed[0]++;
        }, 300, 200);
    }

    public void testItOpensBelowTheAnchorStartAlignedAndAsWideAsIt() {
        BoolRef open = new BoolRef(false);
        Tester tt = shell(open, 10, 30, 40, null);
        tt.frame();
        open.set(true);
        tt.frame();
        Rect pop = tt.find("pop");
        Check.near(15, pop.x());                              // panel padding and 1 DIP border around it
        Check.near(30 + 20 + 4 + 1 + 4, pop.y());             // 4 below the anchor, then border and padding
        Check.isTrue(tt.find("popover").width() >= 60, "as wide as the anchor");
    }

    public void testATallPanelOverflowsAboveTheAnchor() {
        BoolRef open = new BoolRef(false);
        Tester tt = shell(open, 10, 170, 100, null);           // the anchor at the bottom of the window
        tt.frame();
        open.set(true);
        tt.frame();
        Rect pop = tt.find("pop");
        Check.isTrue(pop.y() + pop.height() <= 170 + 0.01f, "the panel ends above the anchor");
    }

    public void testItSlidesInFromTheRightEdge() {
        BoolRef open = new BoolRef(false);
        Tester tt = shell(open, 250, 30, 40, null);            // the anchor near the right edge
        tt.frame();
        open.set(true);
        tt.frame();
        Rect pop = tt.find("pop");
        Check.isTrue(pop.x() + pop.width() <= 300 + 0.01f, "the panel stays in the window");
    }

    public void testAPressOutsideClosesButOnTheAnchorOrPanelItDoesNot() {
        BoolRef open = new BoolRef(false);
        int[] dismissed = {0};
        Tester tt = shell(open, 10, 30, 40, dismissed);
        tt.frame();
        open.set(true);
        tt.frame();
        tt.press(200, 20);                                     // outside, on nothing
        tt.release(200, 20);
        tt.frame();
        Check.isTrue(!open.get(), "the press outside closed it");
        Check.equal(1, dismissed[0]);
        open.set(true);
        tt.frame();
        tt.press(30, 60);                                      // on the panel
        tt.release(30, 60);
        tt.frame();
        Check.isTrue(open.get(), "a press on the panel does not close it");
        tt.press(30, 40);                                      // on the anchor: the app toggles, twice
        tt.release(30, 40);
        tt.frame();
        Check.isTrue(!open.get(), "the anchor's own click toggled it");
        Check.equal(1, dismissed[0]);                          // not a dismissal
    }

    public void testEscapeClosesAndTheAnchorGoingAwayClosesIt() {
        BoolRef open = new BoolRef(false);
        int[] dismissed = {0};
        boolean[] anchorThere = {true};
        Tester tt = Tester.of(c -> {
            if (anchorThere[0]) {
                Element anchor = button(c, "anchor").absolute().width(60).height(20).left(10).top(30);
                Element panel = popover(c, anchor, open, () -> text(c, "hi").label("pop"));
                if (panel.dismissed()) dismissed[0]++;
            }
        }, 300, 200);
        tt.frame();
        open.set(true);
        tt.frame();
        Check.isTrue(tt.find("pop") != null, "open");
        tt.key(Shortcut.of(Key.ESCAPE));
        tt.frame();
        Check.isTrue(!open.get(), "Escape closed it");
        Check.equal(1, dismissed[0]);
        open.set(true);
        tt.frame();
        anchorThere[0] = false;                                // the anchor is gone: the app builds no popover
        tt.frame();
        anchorThere[0] = true;                                 // it comes back
        tt.frame();
        Check.isTrue(tt.find("pop") != null, "it opens again beside the anchor");
    }
}
