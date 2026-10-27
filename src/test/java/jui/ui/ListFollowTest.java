package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ListFollowTest { // Review Focus 5
    final ListState state = new ListState().followEnd(true);
    final int[] count = {100};
    final View view = c -> list(c, state, count[0], i -> box(c).height(30).label("row" + i)).height(300).label("list");

    public void testItStartsAtTheEndAndFollowsAppendedRows() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        Check.equal(99, state.last());
        count[0] = 120;
        tt.frame();
        Check.equal(119, state.last());
        Check.isTrue(state.atEnd(), "at the end");
    }

    public void testScrollingUpStopsFollowingAndBackDownResumes() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        tt.scroll("list", 0, -300);
        int last = state.last();
        count[0] = 130;
        tt.frame();
        Check.equal(last, state.last());                    // not pulled down
        tt.scroll("list", 0, 100_000);                      // back to the end
        count[0] = 140;
        tt.frame();
        Check.equal(139, state.last());
    }

    public void testScrollToEndResumes() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        tt.scroll("list", 0, -300);
        state.scrollToEnd();
        tt.frame();
        count[0] = 110;
        tt.frame();
        Check.equal(109, state.last());
    }
}
