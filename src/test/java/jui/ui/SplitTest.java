package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class SplitTest {
    /** A split of two labeled panes filling the window. */
    static Tester shell(FloatRef first, float w, float h) {
        return Tester.of(c -> split(c, first,
                () -> box(c).label("first"),
                () -> box(c).label("second")).fill().label("split"), w, h);
    }

    public void testThePanesShareTheRoomAtTheDivider() {
        FloatRef first = new FloatRef(100);
        Tester tt = shell(first, 300, 200);
        Check.near(100, tt.find("first").width());
        Check.near(199, tt.find("second").width());          // the 1 DIP line between them
        Check.near(96.5f, tt.find("divider").x());           // the handle over the line
        Check.near(8, tt.find("divider").width());
    }

    public void testDraggingMovesTheFirstWidth() {
        FloatRef first = new FloatRef(100);
        int[] changed = {0};
        Tester tt = Tester.of(c -> {
            if (split(c, first, () -> box(c).label("first"), () -> box(c).label("second")).fill().changed()) {
                changed[0]++;
            }
        }, 300, 200);
        tt.frame();
        tt.press(100, 100);
        tt.move(133, 100);
        tt.frame();
        Check.near(133, first.get());
        Check.equal(1, changed[0]);
        tt.release(133, 100);
        tt.frame();
        Check.near(133, first.get());                        // a release does not move it
        Check.equal(1, changed[0]);
    }

    public void testADragPastTheRoomIsClampedToLeave40OnEitherSide() {
        FloatRef first = new FloatRef(250);                  // the most the room takes is 259
        Tester tt = shell(first, 300, 200);
        tt.press(250, 100);
        tt.move(360, 100);
        tt.frame();
        Check.near(259, first.get());
    }

    public void testTheFirstGivesWayAndComesBackWithItsWidth() {
        FloatRef first = new FloatRef(100);
        Tester tt = shell(first, 300, 200);
        Check.near(100, tt.find("first").width());
        tt.resize(80, 200);
        tt.frame();
        Check.near(40, tt.find("first").width());            // its own 40 at the least; the room is a DIP short
        Check.near(100, first.get());                        // the width is kept for when there is room
        tt.resize(300, 200);
        tt.frame();
        Check.near(100, tt.find("first").width());
    }

    public void testTheArrowsMoveTheDividerOnceItHasTheFocus() {
        FloatRef first = new FloatRef(100);
        Tester tt = shell(first, 300, 200);
        tt.press(100, 100);                                  // the press gives the divider the focus
        tt.release(100, 100);
        tt.frame();
        Check.isTrue(tt.focused("divider"), "the divider took the focus");
        tt.key(Shortcut.of(Key.RIGHT));
        tt.frame();
        Check.near(108, first.get());
        tt.key(Shortcut.of(Key.LEFT));
        tt.frame();
        Check.near(100, first.get());
    }

    public void testTheLineAccentsWhileTheDividerIsHeld() {
        FloatRef first = new FloatRef(100);
        int[] colors = new int[2];                            // the border and the accent, as the theme has them
        Tester tt = Tester.of(c -> {
            Theme t = c.theme();
            colors[0] = t.border().rgba();
            colors[1] = t.accent().rgba();
            split(c, first, () -> box(c).label("first"), () -> box(c).label("second")).fill();
        }, 300, 200);
        tt.frame();
        Check.isTrue(painted(tt, colors[0]), "the line is the border color at rest");
        tt.press(100, 100);
        tt.move(104, 100);
        tt.frame();
        Check.isTrue(painted(tt, colors[1]), "the line accents while held");
        tt.release(104, 100);
        tt.move(300, 100);                                    // off the divider: no hover either
        tt.frame();
        Check.isTrue(painted(tt, colors[0]), "back to the border color");
    }

    /** Whether a 1 DIP box of the color is painted (the divider's line). */
    static boolean painted(Tester tt, int rgba) {
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[2] == 1 && (Math.round(in[8] * 255) << 24 | Math.round(in[9] * 255) << 16
                    | Math.round(in[10] * 255) << 8 | Math.round(in[11] * 255)) == rgba) {
                return true;
            }
        }
        return false;
    }
}
