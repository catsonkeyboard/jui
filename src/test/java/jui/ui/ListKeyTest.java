package jui.ui;

import java.util.ArrayList;
import java.util.List;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ListKeyTest {
    final ListState state = new ListState();
    final List<Long> items = new ArrayList<>();
    {
        for (long i = 0; i < 200; i++) items.add(i);
        state.keys(i -> items.get(i));
    }
    final View view = c -> list(c, state, items.size(), i -> box(c).height(30).label("item" + items.get(i))).height(300).label("list");

    public void testRowsInsertedAboveKeepTheViewOnTheSameItem() { // Review Focus 4
        Tester tt = Tester.of(view, 300, 400);
        state.scrollTo(50);
        tt.frame();
        Rect before = tt.find("item50");
        for (long k = 1000; k < 1003; k++) items.add(0, k);  // three new items at the front
        tt.frame();
        Check.near(before.y(), tt.find("item50").y());      // the same item where it was
        Check.equal(53, state.first());
    }

    public void testTheAnchoredItemRemovedFallsBackToItsIndex() { // Review Focus 4
        Tester tt = Tester.of(view, 300, 400);
        state.scrollTo(50);
        tt.frame();
        items.remove(Long.valueOf(50));
        tt.frame();
        Check.equal(50, state.first());                     // by index now: no jump to the start
    }

    public void testTheFocusedRowStaysBuiltOutOfView() {
        int[] presses = {0};
        Tester tt = Tester.of(c -> list(c, state, items.size(), i -> {
            Element r = box(c).height(30).focusable().label("item" + items.get(i));
            if (r.shortcut(Shortcut.of(Key.D))) presses[0]++;
        }).height(300).label("list"), 300, 400);
        tt.click("item0");
        tt.scroll("list", 0, 3000);                         // item 0 far above the view
        Check.isTrue(tt.focused("item0"), "still built, still focused");
        Check.isTrue(tt.find("item0").y() + 30 <= tt.find("list").y() + 0.01f, "above the view");
        tt.key(Shortcut.of(Key.D));                          // its keys still reach it
        Check.equal(1, presses[0]);
    }
}
