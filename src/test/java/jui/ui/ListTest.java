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

    public void testReadingTheVisibleRowsAsksForAFrameWhenTheyChange() { // as MyGO's list.go: Visible() read, range changed
        String[] header = {""};
        Tester tt = Tester.of(c -> {
            header[0] = "rows " + state.first() + "-" + state.last();      // read before the list is built
            list(c, state, 1000, i -> box(c).height(30).label("row" + i)).height(200).label("list");
        }, 300, 400);
        tt.frame();
        Check.isTrue(tt.framePending(), "the rows it read changed: another frame");
        tt.settle();
        Check.equal("rows 0-6", header[0]);
        tt.scroll("list", 0, 300);
        Check.equal("rows 10-16", header[0]);
    }

    // Final review C1: a wheel glide deep in a list of varied rows moves one way, by what was turned.
    static float tall(int i) { return i % 7 == 0 ? 120 : 24; }

    /** The list's true offset: the real heights of the rows above the first in view, less how far it sticks out. */
    static float trueOffset(Tester tt, ListState s) {
        int f = s.first();
        float top = 24f * f + 96f * ((f + 6) / 7);              // rows 0, 7, 14, … are 120, the rest 24
        return top - tt.find("row" + f).y();
    }

    public void testAWheelGlideDeepInAVariedListMovesOneWayByItsDistance() {
        Tester tt = Tester.of(c -> list(c, state, count, i -> box(c).height(tall(i)).label("row" + i)).height(300).label("list"), 300, 400);
        state.scrollTo(5000); tt.frame(); tt.frame();
        float start = trueOffset(tt, state), prev = start;
        tt.wheel("list", 0, 400);
        for (int k = 0; k < 25; k++) {
            tt.advance(java.time.Duration.ofMillis(16));
            float now = trueOffset(tt, state);
            Check.isTrue(now >= prev - 0.01f, "moved backwards: " + prev + " -> " + now);
            Check.isTrue(now <= start + 400.5f, "went past the turn: " + (now - start));
            prev = now;
        }
        Check.near(400, prev - start, 0.5);
    }

    // Final review C2 (Review Focus 1): scrolled far, then few rows that fit: they start at the top.
    public void testFewRowsThatFitStartAtTheTopAfterScrolling() {
        Tester tt = Tester.of(c -> list(c, state, count, i -> box(c).height(30).label("row" + i)).height(200).label("list"), 300, 400);
        tt.scroll("list", 0, 5000);
        count = 3;
        tt.frame(); tt.frame();
        Check.near(0, tt.find("row0").y());
        Check.equal(0, state.first()); Check.equal(2, state.last());
    }

    public void testFewRowsThatFitStartAtTheTopFromTheEnd() {
        Tester tt = Tester.of(c -> list(c, state, count, i -> box(c).height(30).label("row" + i)).height(200).label("list"), 300, 400);
        tt.scroll("list", 0, 1e7f);
        count = 3;
        tt.frame(); tt.frame();
        Check.near(0, tt.find("row0").y());
    }

    // Final review C2 (Review Focus 2): the window grows until all the rows fit.
    public void testAWindowThatGrowsToHoldAllRowsShowsThemFromTheTop() {
        for (float scrolled : new float[] {1000, 150}) {
            ListState s = new ListState();
            Tester tt = Tester.of(c -> list(c, s, 20, i -> box(c).height(30).label("row" + i)).fill().label("list"), 300, 200);
            tt.scroll("list", 0, scrolled);
            tt.resize(300, 700);
            tt.frame();
            Check.near(0, tt.find("row0").y());
            Rect l = tt.find("row19");
            Check.near(600, l.y() + l.height());
        }
    }

    // Final review I1: rows with vertical margins; at the end the frames stop and the last row meets the bottom.
    public void testRowsWithMarginsSettleAtTheEnd() {
        Tester tt = Tester.of(c -> list(c, state, 100, i -> box(c).height(30).margin(4, 0).label("row" + i)).height(200).label("list"), 300, 400);
        tt.scroll("list", 0, 1e6f);
        tt.settle();
        Check.isTrue(!tt.framePending(), "no frames once at the end");
        Rect l = tt.find("row99");
        Check.near(196, l.y() + l.height());                     // its bottom margin, 4, at the view's bottom
        Check.equal(99, state.last());
    }

    // Final review I2a: Shift+Tab to the row just above the view, at the list's end, reveals it.
    public void testShiftTabRevealsTheRowAboveAtTheEnd() {
        Tester tt = Tester.of(c -> list(c, state, 100, i -> box(c).height(30).label("row" + i).focusable()).height(200).label("list"), 300, 400);
        tt.scroll("list", 0, 1e6f);
        tt.click("row94");
        tt.key(Shortcut.of(Mod.SHIFT, Key.TAB));
        Check.isTrue(tt.focused("row93"), "row93 has focus");
        Check.near(0, tt.find("row93").y());
    }

    // Final review I2b: a list inside a scroll container; Tab reveals rows in both views.
    public void testTabRevealsRowsOfAListInsideAScrollContainer() {
        ScrollState outer = new ScrollState();
        Tester tt = Tester.of(c -> scroll(c, outer).size(300, 300).label("outer").children(() -> {
            box(c).height(250);
            list(c, state, 100, i -> box(c).height(30).label("row" + i).focusable()).height(200).label("list");
            box(c).height(400);
        }), 400, 400);
        for (int i = 0; i < 8; i++) tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("row7"), "row7 has focus");
        Rect r = tt.find("row7"), l = tt.find("list");
        Check.isTrue(r.y() >= l.y() - 0.01f && r.y() + r.height() <= l.y() + l.height() + 0.01f, "row7 inside the list's view");
        Check.isTrue(r.y() >= -0.01f && r.y() + r.height() <= 300.01f, "row7 inside the outer view");
    }

    // Final review I4: a list hidden for a frame keeps its place: its ListState lives in the app.
    boolean shown = true;

    public void testAListHiddenForAFrameKeepsItsPlace() {
        Tester tt = Tester.of(c -> {
            if (shown) list(c, state, 1000, i -> box(c).height(30).label("row" + i)).height(200).label("list");
            else text(c.key(77), "other");
        }, 300, 400);
        tt.scroll("list", 0, 300);
        Check.equal(10, state.first());
        shown = false; tt.frame();
        shown = true; tt.frame(); tt.frame();
        Check.equal(10, state.first());
        Check.near(0, tt.find("row10").y());
    }

    public void testTabFromARowScrolledOutOfViewGoesToTheFirstRowInView() { // M3 final review minor
        Tester tt = Tester.of(c -> list(c, state, 1000, i -> box(c).height(30).label("row" + i).focusable()).height(200).label("list"), 300, 400);
        tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("row0"), "row 0 has focus");
        tt.scroll("list", 0, 600);                          // row 0 out of view, no longer built; row 20 at the top
        Check.equal(20, state.first());
        tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("row20"), "the first row in view, not a row built above it");
        Check.equal(20, state.first());                     // and the view stays where it was
        tt.key(Shortcut.of(Mod.SHIFT, Key.TAB));
        Check.isTrue(tt.focused("row19"), "Shift+Tab from there walks up as usual");
    }

    public void testAListWithoutAHeightBoundIsWarnedAboutInDevelopment() { // M3 final review minor
        jui.ui.engine.Dev.clearWarnings();
        ListState a = new ListState(), b = new ListState();   // made once: a list's state lives outside the view
        Tester bounded = Tester.of(c -> list(c, a, 500, i -> box(c).height(30).label("r" + i)).height(200), 300, 200);
        bounded.frame();
        Check.isTrue(!jui.ui.engine.Dev.warned(jui.ui.engine.Engine.UNBOUNDED_LIST), "a list with a height builds the rows in view");
        // In a scroll container nothing bounds its height (in a column, flex shrink does).
        Tester unbounded = Tester.of(c -> scroll(c).size(300, 200).children(() ->
                list(c, b, 500, i -> box(c).height(30).label("r" + i))), 300, 200);
        unbounded.frame();
        Check.equal(500, unbounded.engine().nodeCount() - 3); // root, scroll, list: every row built
        Check.isTrue(jui.ui.engine.Dev.warned(jui.ui.engine.Engine.UNBOUNDED_LIST), "warned: it builds all 500 rows");
    }
}
