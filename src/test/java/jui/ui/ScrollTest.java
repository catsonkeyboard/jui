package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ScrollTest {
    final ScrollState st = new ScrollState();
    int rows = 10;
    final int[] clicks = new int[20];

    /** A 200×100 scroll container (labeled "s") of rows 40 high, labeled r0, r1, …; each counts its clicks. */
    final View view = c -> column(c).padding(10).children(() ->
        scroll(c, st).size(200, 100).label("s").children(() -> {
            for (int i = 0; i < rows; i++) {
                if (box(c.key(i)).height(40).label("r" + i).clicked()) clicks[i]++;
            }
        }));

    public void testContentScrollsAndTheOffsetClamps() {
        Tester tt = Tester.of(view, 400, 300);
        tt.frame();
        Check.near(300, st.maxY());                               // 400 - 100
        tt.scroll("s", 0, 50);
        Check.near(50, st.y());
        Check.near(10 - 50, tt.find("r0").y());
        tt.scroll("s", 0, 1000);
        Check.near(300, st.y());
        Check.near(110, tt.find("r9").y() + 40);                  // the last row ends at the bottom: 10 + 100
        tt.scroll("s", 0, -5000);
        Check.near(0, st.y());
    }

    public void testScrollStateRequests() {
        Tester tt = Tester.of(view, 400, 300);
        st.scrollTo(120);                                         // unbound yet: kept until bound
        tt.frame();
        Check.near(120, st.y());
        Check.near(10, tt.find("r3").y());                        // 3 * 40 - 120 from the top
        st.scrollToEnd(); tt.frame(); Check.near(300, st.y());
        st.scrollToTop(); tt.frame(); Check.near(0, st.y());
    }

    public void testContentShrinkingClampsTheOffset() { // Review Focus 1
        Tester tt = Tester.of(view, 400, 300);
        tt.scroll("s", 0, 300);
        rows = 5;
        tt.frame();
        Check.near(100, st.y());                                  // 200 - 100
        Check.near(110, tt.find("r4").y() + 40);
    }

    public void testScrollingOverAButtonScrollsTheContainer() { // Review Focus 3
        Tester tt = Tester.of(c -> scroll(c, st).size(200, 100).children(() -> {
            for (int i = 0; i < 10; i++) button(c.key(i), "按钮 " + i).height(40);
        }), 400, 300);
        tt.scroll("按钮 1", 0, 30);
        Check.near(30, st.y());
    }

    public void testTheInnerContainerTakesTheWheelUntilItsEnd() {
        ScrollState inner = new ScrollState(), outer = new ScrollState();
        Tester tt = Tester.of(c -> scroll(c, outer).size(200, 100).children(() -> {
            scroll(c, inner).size(200, 80).label("in").children(() -> box(c).height(130));
            box(c).height(200);
        }), 400, 300);
        tt.scroll("in", 0, 40); tt.scroll("in", 0, 40);           // inner max is 50: 40, then 50 (10 is dropped)
        Check.near(50, inner.y()); Check.near(0, outer.y());
        tt.scroll("in", 0, 40);
        Check.near(40, outer.y());
    }

    public void testAScrolledRowIsHitWhereItIsAndNotOutsideTheViewport() { // Review Focus 5
        Tester tt = Tester.of(view, 400, 300);
        tt.scroll("s", 0, 50);
        Rect r2 = tt.find("r2");                                  // 10 + 80 - 50 = 40 .. 80
        tt.press(50, r2.y() + 5); tt.release(50, r2.y() + 5);
        Check.equal(1, clicks[2]);
        tt.press(50, 130); tt.release(50, 130);                   // below the viewport: r4 is laid out there, clipped
        Check.equal(0, clicks[4]);
    }

    public void testGrowingChildrenKeepTheirContentHeightInAScroll() {
        Tester tt = Tester.of(c -> scroll(c, st).size(200, 100).children(() ->
            column(c).grow(1).label("g").children(() -> box(c).height(300))), 400, 300);
        tt.frame();
        Check.near(300, tt.find("g").height());
        Check.near(200, st.maxY());
    }

    public void testTheWheelGlidesOver150Milliseconds() {
        Tester tt = Tester.of(view, 400, 300);
        tt.wheel("s", 0, 40);
        Check.near(0, st.y());                                    // settle runs frames at one time: no glide yet
        tt.advance(java.time.Duration.ofMillis(50));              // frames at 16.7, 33.3, 50.0 ms
        Check.near(28.15, st.y(), 0.1);                           // 40 * (1 - (1 - 1/3)^3)
        tt.advance(java.time.Duration.ofMillis(150));
        Check.near(40, st.y());
        Check.isTrue(!tt.framePending(), "the glide ended");
    }

    public void testWheelNotchesAddUp() {
        Tester tt = Tester.of(view, 400, 300);
        tt.wheel("s", 0, 40);
        tt.advance(java.time.Duration.ofMillis(30));
        tt.wheel("s", 0, 40);
        tt.advance(java.time.Duration.ofMillis(300));
        Check.near(80, st.y());
    }

    public void testKeysScrollTheFocusedContainer() {
        Tester tt = Tester.of(c -> scroll(c, st).size(200, 100).focusable().label("s").children(() -> box(c).height(400)), 400, 300);
        tt.click("s");
        tt.key(Shortcut.of(Key.DOWN));      Check.near(40, st.y());
        tt.key(Shortcut.of(Key.PAGE_DOWN)); Check.near(100, st.y());   // + max(100 - 40, 50)
        tt.key(Shortcut.of(Key.END));       Check.near(300, st.y());
        tt.key(Shortcut.of(Key.HOME));      Check.near(0, st.y());
        tt.key(Shortcut.of(Key.SPACE));     Check.near(60, st.y());
        tt.key(Shortcut.of(Mod.SHIFT, Key.SPACE)); Check.near(0, st.y());
    }

    public void testKeysScrollTheContainerUnderThePointerWithoutFocus() {
        Tester tt = Tester.of(view, 400, 300);
        tt.hover("s");
        tt.key(Shortcut.of(Key.DOWN));
        Check.near(40, st.y());
    }

    public void testAShortcutClaimsItsKeyFromScrolling() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(Shortcut.of(Key.DOWN))) n[0]++; view.build(c); }, 400, 300);
        tt.hover("s");
        tt.key(Shortcut.of(Key.DOWN));
        Check.equal(1, n[0]);
        Check.near(0, st.y());
    }

    public void testTabScrollsTheFocusIntoView() {
        Tester tt = Tester.of(c -> scroll(c, st).size(200, 100).children(() -> {
            for (int i = 0; i < 10; i++) box(c.key(i)).height(40).label("f" + i).focusable();
        }), 400, 300);
        for (int i = 0; i < 4; i++) tt.key(Shortcut.of(Key.TAB));      // f3: 120..160, below the 100-high view
        Check.isTrue(tt.focused("f3"), "f3 focused");
        Check.near(60, st.y());                                   // as little as shows it: 160 - 100
        Check.near(60, tt.find("f3").y());                        // its bottom at the view's bottom, in the same frame
    }
}
