package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class SplitVerticalTest {
    /** A vertical split of two labeled panes filling the window. */
    static Tester shell(FloatRef first, float w, float h) {
        return Tester.of(c -> splitVertical(c, first,
                () -> box(c).fillHeight().label("first"),
                () -> box(c).fillHeight().label("second")).fill().label("split"), w, h);
    }

    public void testThePanesShareTheHeightAtTheDivider() {
        FloatRef first = new FloatRef(100);
        Tester tt = shell(first, 200, 300);
        Check.near(100, tt.find("first").height());
        Check.near(199, tt.find("second").height());          // the 1 DIP line between them
        Check.near(96.5f, tt.find("divider").y());
        Check.near(8, tt.find("divider").height());
    }

    public void testDraggingMovesTheFirstHeight() {
        FloatRef first = new FloatRef(100);
        Tester tt = shell(first, 200, 300);
        tt.frame();
        tt.press(100, 100);
        tt.move(100, 133);
        tt.frame();
        Check.near(133, first.get());
        tt.release(100, 133);
        tt.frame();
        Check.near(133, first.get());                          // a release does not move it
    }

    public void testADragPastTheRoomIsClampedToLeave40OnEitherSide() {
        FloatRef first = new FloatRef(250);                    // the most the room takes is 259
        Tester tt = shell(first, 200, 300);
        tt.press(100, 250);
        tt.move(100, 360);
        tt.frame();
        Check.near(259, first.get());
    }

    public void testArrowsMoveTheDividerWithTheFocus() {
        FloatRef first = new FloatRef(100);
        Tester tt = shell(first, 200, 300);
        tt.frame();
        tt.click("divider");
        tt.key(Shortcut.of(Key.DOWN));
        tt.frame();
        Check.near(108, first.get());
    }
}
