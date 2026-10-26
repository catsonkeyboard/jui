package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ListTest {
    final ListState state = new ListState();
    int count = 10_000;

    static float varied(int i) { return 20 + i * 7 % 45; }

    /** A list 200 high, at y = 0, of rows varied(i) high labeled row0, row1, … */
    final View view = c -> list(c, state, count, i -> box(c).height(varied(i)).label("row" + i)).height(200).label("list");

    /** The index of the node labeled s. */
    static int nodeOf(Tester tt, String s) {
        for (int i = 0; i < tt.engine().nodeCount(); i++) {
            if (s.equals(tt.engine().node(i).label)) return i;
        }
        throw new AssertionError("no node labeled " + s);
    }

    /** Visible rows meet end to end and cover the 200-high view. */
    static void checkCovered(Tester tt, ListState s) {
        float y = tt.find("row" + s.first()).y();
        Check.isTrue(y <= 0.01, "the first visible row starts at or above the top: " + y);
        for (int i = s.first(); i < s.last(); i++) {
            Rect a = tt.find("row" + i), b = tt.find("row" + (i + 1));
            Check.near(a.y() + a.height(), b.y());
        }
        Rect l = tt.find("row" + s.last());
        Check.isTrue(l.y() + l.height() >= 200 - 0.01 || s.last() == 9_999, "the view is covered");
    }

    public void testOnlyTheRowsInViewAreBuiltAndTheFirstFrameIsFull() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        checkCovered(tt, state);                  // the first frame is full
        tt.frame();                               // planned for the 200-high view it measured
        int visible = state.last() - state.first() + 1;
        Check.isTrue(tt.engine().nodeCount() <= visible + 2 + 2 + 3, "built " + tt.engine().nodeCount() + " nodes");   // rows + root + list
        Check.isTrue(tt.engine().identity("row40") == 0, "row 40 is not built");
    }

    public void testScrollingMovesRowsByExactlyTheDelta() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        float y = tt.find("row3").y();
        tt.scroll("list", 0, 13);
        Check.near(y - 13, tt.find("row3").y());
    }

    public void testVariedRowsStayEndToEndWhileScrolling() {
        Tester tt = Tester.of(view, 300, 400);
        for (int k = 0; k < 60; k++) {
            tt.scroll("list", 0, 37);
            checkCovered(tt, state);
        }
        for (int k = 0; k < 60; k++) {
            tt.scroll("list", 0, -37);
            checkCovered(tt, state);
        }
        Check.equal(0, state.first());
        Check.near(0, tt.find("row0").y());
    }

    public void testRowsKeyedInTheCallbackKeepTheirStateWhenItemsMove() {
        java.util.List<Integer> items = new java.util.ArrayList<>(java.util.List.of(10, 11, 12, 13));
        Tester tt = Tester.of(c -> list(c, state, items.size(), i -> {
            int id = items.get(i);
            Element r = box(c.key(id)).height(30).label("item" + id);
            if (r.clicked()) localBool(r, "picked", false).set(true);
            if (localBool(r, "picked", false).get()) r.background(Color.rgb(0xFF0000));
        }).height(200), 300, 400);
        tt.click("item12");
        items.remove(0);                          // item12 moves from row 2 to row 1
        tt.frame();
        Check.isTrue(tt.engine().node(nodeOf(tt, "item12")).background != 0, "its local state followed the key");
    }

    public void testANullStateKeepsTheOffsetInTheList() {
        Tester tt = Tester.of(c -> list(c, null, 1000, i -> box(c).height(30).label("row" + i)).height(200).label("list"), 300, 400);
        tt.scroll("list", 0, 300);
        tt.frame(); tt.frame();
        Check.near(0, tt.find("row10").y());      // 10 * 30 - 300
    }

    public void testARowMustBuildOneElement() {
        Tester tt = Tester.of(c -> list(c, state, 3, i -> { box(c).height(10); box(c).height(10); }).height(200), 300, 400);
        Check.fails(IllegalStateException.class, tt::frame);
    }

    public void testAShortListStartsAtTheTop() {
        count = 3;
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        Check.near(0, tt.find("row0").y());
        Check.equal(0, state.first()); Check.equal(2, state.last());
    }

    public void testScrollToTopAndToARow() {
        Tester tt = Tester.of(view, 300, 400);
        state.scrollTo(500); tt.frame();
        Check.equal(500, state.first());
        Check.near(0, tt.find("row500").y());
        state.scrollToTop(); tt.frame();
        Check.equal(0, state.first());
        Check.near(0, tt.find("row0").y());
    }

    public void testDraggingTheThumbToTheEndAndBackReachesBothEnds() { // Review Focus 4
        count = 1000;
        Tester tt = Tester.of(view, 300, 400);
        tt.hover("list");
        float[] t = ScrollTest.thumb(tt);
        float x = t[0] + 3, y = t[1] + 12;
        tt.press(x, y);
        tt.move(x, y + 400);                         // past the end
        Check.equal(999, state.last());
        Rect last = tt.find("row999");
        Check.near(200, last.y() + last.height());
        checkCovered(tt, state);
        tt.move(x, y - 400);                         // past the top
        Check.equal(0, state.first());
        Check.near(0, tt.find("row0").y());
        checkCovered(tt, state);
        tt.release(x, y - 400);
    }

    public void testFewerRowsClampTheList() { // Review Focus 1
        count = 1000;
        Tester tt = Tester.of(view, 300, 400);
        state.scrollTo(900); tt.frame();
        count = 100;
        tt.frame();
        Check.equal(99, state.last());
        Rect last = tt.find("row99");
        Check.near(200, last.y() + last.height());
    }

    public void testATallerWindowIsFilledInTheSameFrame() { // Review Focus 2
        Tester tt = Tester.of(c -> list(c, state, 10_000, i -> box(c).height(varied(i)).label("row" + i)).fill().label("list"), 300, 200);
        tt.scroll("list", 0, 5000);
        tt.resize(300, 600);
        tt.frame();
        Rect l = tt.find("row" + state.last());
        Check.isTrue(l.y() + l.height() >= 600 - 0.01, "the 600-high view is covered in one frame");
    }

    public void testTabWalksDownTheRowsAndRevealsThem() {
        Tester tt = Tester.of(c -> list(c, state, 1000, i -> box(c).height(30).label("row" + i).focusable()).height(200).label("list"), 300, 400);
        for (int i = 0; i < 12; i++) tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("row11"), "row 11 has focus");
        Rect r = tt.find("row11");
        Check.near(200, r.y() + r.height());      // revealed: its bottom at the view's bottom
    }

    public void testTheWheelGlidesAList() {
        count = 1000;
        Tester tt = Tester.of(c -> list(c, state, count, i -> box(c).height(30).label("row" + i)).height(200).label("list"), 300, 400);
        tt.wheel("list", 0, 40);
        tt.advance(java.time.Duration.ofMillis(300));
        Check.near(-10, tt.find("row1").y());     // 30 - 40
    }
}
