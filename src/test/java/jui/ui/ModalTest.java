package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ModalTest {
    /** A window with two buttons and a modal holding two more, opened by the app. */
    static Tester shell(BoolRef open, int[] builds, int[] dismissed, int[] esc) {
        return Tester.of(c -> {
            if (esc != null && c.shortcut(Shortcut.of(Key.ESCAPE))) esc[0]++;
            column(c).children(() -> {
                button(c, "below-a");
                button(c, "below-b");
            });
            Element panel = modal(c, open, () -> {
                builds[0]++;
                column(c).children(() -> {
                    button(c, "inside-x");
                    button(c, "inside-y");
                });
            }).width(120).label("panel");
            if (dismissed != null && panel.dismissed()) dismissed[0]++;
        }, 300, 200);
    }

    public void testItBuildsOnlyWhileOpenAndCentersThePanel() {
        BoolRef open = new BoolRef(false);
        int[] builds = {0};
        Tester tt = shell(open, builds, null, null);
        tt.frame();
        Check.equal(0, builds[0]);                           // closed: no content, nothing hit
        tt.press(20, 20);                                    // the click reaches what is below
        tt.release(20, 20);
        tt.frame();
        Check.equal(0, builds[0]);
        open.set(true);
        tt.frame();
        Check.equal(1, builds[0]);
        Rect panel = tt.find("panel");
        Check.near(90, panel.x());                           // centered in 300
        Check.near((200 - panel.height()) / 2, panel.y());   // centered, as tall as its content
    }

    public void testAClickOnTheBackdropOrEscapeClosesAndReportsOnce() {
        BoolRef open = new BoolRef(false);
        int[] builds = {0}, dismissed = {0};
        Tester tt = shell(open, builds, dismissed, null);
        tt.frame();
        open.set(true);
        tt.frame();
        tt.press(5, 5);                                      // on the backdrop, outside the panel
        tt.release(5, 5);
        tt.frame();
        Check.isTrue(!open.get(), "the click closed it");
        Check.equal(1, dismissed[0]);
        tt.frame();
        Check.equal(1, dismissed[0]);                        // reported once
        open.set(true);
        tt.frame();
        tt.press(100, 100);                                  // on the panel: stays open
        tt.release(100, 100);
        tt.frame();
        Check.isTrue(open.get(), "a press on the panel does not close it");
        tt.key(Shortcut.of(Key.ESCAPE));
        tt.frame();
        Check.isTrue(!open.get(), "Escape closed it");
        Check.equal(2, dismissed[0]);
    }

    public void testEscapeIsTheModalSNotTheAppSWhileItShows() {
        BoolRef open = new BoolRef(false);
        int[] builds = {0}, esc = {0};
        Tester tt = shell(open, builds, null, esc);
        tt.frame();
        tt.key(Shortcut.of(Key.ESCAPE));
        tt.frame();
        Check.equal(1, esc[0]);                              // with no modal, the app's Escape fires
        open.set(true);
        tt.frame();
        tt.key(Shortcut.of(Key.ESCAPE));
        tt.frame();
        Check.isTrue(!open.get(), "the modal took it");
        Check.equal(1, esc[0]);                              // and the app's did not fire
    }

    public void testTheFocusStaysInsideAndComesBack() {
        BoolRef open = new BoolRef(false);
        int[] builds = {0};
        Tester tt = shell(open, builds, null, null);
        tt.frame();
        tt.click("below-a");                                 // the focus is on a button below
        tt.frame();
        Check.isTrue(tt.focused("below-a"), "focused below");
        open.set(true);
        tt.frame();
        tt.key(Shortcut.of(Key.TAB));                        // into the modal
        tt.frame();
        Check.isTrue(tt.focused("inside-x"), "Tab entered the modal");
        tt.key(Shortcut.of(Key.TAB));
        tt.frame();
        Check.isTrue(tt.focused("inside-y"), "Tab stays in it");
        tt.key(Shortcut.of(Key.TAB));
        tt.frame();
        Check.isTrue(tt.focused("inside-x"), "around the end, still in it");
        open.set(false);                                     // the app closes it
        tt.frame();
        tt.frame();
        Check.isTrue(tt.focused("below-a"), "the focus came back");
    }
}
