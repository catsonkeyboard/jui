package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class TextInputPointerTest {
    static final String LONG = "0123456789".repeat(8);

    /** The caret's x before character k of the focused input (ASCII text: one arrow a character). */
    static float xOf(Tester tt, int k) {
        tt.key(Shortcut.of(Key.HOME));
        for (int i = 0; i < k; i++) {
            tt.key(Shortcut.of(Key.RIGHT));
        }
        return tt.textCaret().x();
    }

    /** The middle of the focused input's line. */
    static float lineY(Tester tt) {
        Rect r = tt.textCaret();
        return r.y() + r.height() / 2;
    }

    static void clickAt(Tester tt, float x, float y, int count, int mods) {
        tt.engine().pointerDown(x, y, 0, count, mods);
        tt.settle();
        tt.engine().pointerUp(x, y, 0, mods);
        tt.settle();
    }

    public void testAClickPlacesTheCaretAtTheCharacter() {
        TextRef q = new TextRef("hello world");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in")), 400, 200);
        tt.click("in");                                   // focus first
        float x = xOf(tt, 6) + 0.3f, y = lineY(tt);       // just right of the caret before "w"
        clickAt(tt, x, y, 1, 0);
        Check.equal("[6, 6]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testDoubleAndTripleClicksAndShiftClick() {
        TextRef q = new TextRef("hello world");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in")), 400, 200);
        tt.click("in");
        float inWorld = (xOf(tt, 8) + xOf(tt, 9)) / 2, inE = xOf(tt, 1) + 0.3f, onR = xOf(tt, 8) + 0.3f, y = lineY(tt);
        clickAt(tt, inWorld, y, 1, 0);
        clickAt(tt, inWorld, y, 2, 0);
        Check.equal("[6, 11]", java.util.Arrays.toString(tt.textSelection()));
        clickAt(tt, inWorld, y, 3, 0);
        Check.equal("[0, 11]", java.util.Arrays.toString(tt.textSelection()));
        clickAt(tt, inE, y, 1, 0);
        clickAt(tt, onR, y, 1, Mod.SHIFT);
        Check.equal("[1, 8]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testDraggingSelects() {
        TextRef q = new TextRef("hello world");
        Tester tt = Tester.of(c -> textInput(c, q).label("in"), 400, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.HOME));
        Rect a = tt.textCaret();
        tt.key(Shortcut.of(Key.END));
        Rect b = tt.textCaret();
        float y = a.y() + a.height() / 2;
        tt.press(a.x() + 0.5f, y);
        tt.move(b.x() + 30, y);                          // past the end
        tt.release(b.x() + 30, y);
        Check.equal("[0, 11]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testTheCaretStaysInViewAndUnfocusedShowsTheStart() { // Review Focus 5
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> {
            textInput(c, q).label("in").width(120);
            button(c, "别处");
        }), 400, 200);
        tt.click("in");
        tt.type(LONG);
        Rect box = tt.find("in");
        Rect caret = tt.textCaret();
        // The content box's right is box right − border 1 − padding 10; the caret keeps 1 inside it.
        Check.isTrue(caret.x() <= box.x() + box.width() - 11 + 0.01f, "the caret is in the content box: " + caret);
        Check.isTrue(tt.textScroll("in") > 0, "scrolled");
        tt.key(Shortcut.of(Key.HOME));
        Check.near(0, tt.textScroll("in"));
        tt.key(Shortcut.of(Key.END));
        tt.click("别处");
        Check.near(0, tt.textScroll("in"));
    }

    public void testAClickAfterScrollingLandsOnTheCharacterUnderIt() { // Review Focus 5
        TextRef q = new TextRef(LONG);
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in").width(120)), 400, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        Rect end = tt.textCaret();
        tt.press(end.x() - 0.2f, end.y() + 2);
        tt.release(end.x() - 0.2f, end.y() + 2);
        Check.equal("[80, 80]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testTheCaretRectIsForTheCandidateWindow() {
        TextRef q = new TextRef("ab");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in")), 400, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        Rect caret = tt.textCaret();
        Rect box = tt.find("in");
        Check.near(1, caret.width());
        Check.isTrue(caret.y() >= box.y() && caret.y() + caret.height() <= box.y() + box.height(), "inside the box");
        float[] r = new float[6];
        Check.isTrue(tt.engine().textRect(0, 2, r), "a range");
        Check.near(box.x() + 1 + 10, r[0], 0.5);          // its first grapheme: "a", at the content's left
        Check.equal(1, (int) r[4]);                       // the actual range ends after "a"
    }

    public void testCursorShapes() {
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).padding(10).gap(10).children(() -> {
            textInput(c, q).label("in");
            box(c).size(120, 60).cursor(Cursor.POINTER).label("link").children(() -> button(c, "b").width(40));
            box(c).size(50, 30).label("plain");
        }), 400, 300);
        tt.hover("in");
        Check.equal(Cursor.TEXT, tt.cursor());
        Rect link = tt.find("link");
        tt.move(link.x() + 100, link.y() + 50);           // in the box, clear of its button
        Check.equal(Cursor.POINTER, tt.cursor());
        tt.hover("b");                                    // a clickable element without a cursor stops the search
        Check.equal(Cursor.DEFAULT, tt.cursor());
        tt.hover("plain");
        Check.equal(Cursor.DEFAULT, tt.cursor());
        Rect in = tt.find("in");
        tt.press(in.x() + 20, in.y() + in.height() / 2);
        tt.move(in.x() + 20, in.y() + 200);               // held: the I-beam stays
        Check.equal(Cursor.TEXT, tt.cursor());
        tt.release(in.x() + 20, in.y() + 200);
    }
}
