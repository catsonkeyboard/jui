package jui.ui;

import jui.testing.Check;
import jui.ui.engine.Engine;

import static jui.ui.Ui.*;

public final class PointerTest {
    int aClicks, aDoubles, aRights, bClicks;
    boolean aHovered, aPressed, bHovered;
    float dragX, dragY;

    final View view = c -> column(c).padding(10).gap(10).children(() -> {
        Element a = box(c).size(100, 40).label("a");
        if (a.clicked()) aClicks++;
        if (a.doubleClicked()) aDoubles++;
        if (a.rightClicked()) aRights++;
        aHovered = a.hovered();
        aPressed = a.pressed();
        dragX += a.dragX(); dragY += a.dragY();   // summed: a drag is consumed, so the next pass reads 0
        Element b = box(c).size(100, 40).label("b");
        if (b.clicked()) bClicks++;
        bHovered = b.hovered();
    });

    public void testClickIsReportedOnce() {
        Tester tt = Tester.of(view, 300, 200);
        tt.click("a");
        tt.frame();
        Check.equal(1, aClicks);
        Check.equal(0, bClicks);
    }

    public void testAClickRebuildsOnceMore() {
        Engine e = new Engine(null, Theme.light(), () -> {});
        e.buildAndLayout(view, 300, 200);
        Check.equal(1, e.passes());
        e.pointerDown(60, 30, 0, 1, 0);
        e.pointerUp(60, 30, 0, 0);
        e.buildAndLayout(view, 300, 200);
        Check.equal(2, e.passes());   // clicked() was true in pass 1, so pass 2 shows its result
        Check.equal(1, aClicks);
    }

    public void testDoubleClickAlsoClicks() {
        Tester tt = Tester.of(view, 300, 200);
        tt.doubleClick("a");
        Check.equal(1, aDoubles);
        Check.equal(2, aClicks);
    }

    public void testRightClickIsNotAClick() {
        Tester tt = Tester.of(view, 300, 200);
        tt.rightClick("a");
        Check.equal(1, aRights);
        Check.equal(0, aClicks);
    }

    public void testHoverFollowsThePointer() {
        Tester tt = Tester.of(view, 300, 200);
        tt.hover("a");
        Check.isTrue(aHovered && !bHovered, "a hovered");
        tt.move(250, 150);
        Check.isTrue(!aHovered, "off a");
        tt.hover("a");
        tt.engine().pointerLeave(); tt.frame();
        Check.isTrue(!aHovered, "left the window");
    }

    public void testPressThenDragOffCancelsTheClick() { // Review Focus 2
        Tester tt = Tester.of(view, 300, 200);
        tt.press(60, 30);
        Check.isTrue(aPressed, "pressed while inside");
        tt.move(60, 80);                       // over b
        Check.isTrue(!aPressed && aHovered && !bHovered, "a keeps the hover; b does not light up");
        tt.release(60, 80);
        Check.equal(0, aClicks); Check.equal(0, bClicks);
    }

    public void testDragReportsTheMovement() {
        Tester tt = Tester.of(view, 300, 200);
        tt.press(60, 30);
        tt.move(75, 35);
        Check.near(15, dragX); Check.near(5, dragY);
        tt.frame();
        Check.near(15, dragX);                 // reported once: nothing more in a later frame
    }

    public void testTheInnermostReceiverTakesThePress() {
        int[] outer = {0}, inner = {0};
        Tester tt = Tester.of(c -> {
            Element o = box(c).size(200, 100).padding(20).label("outer");
            o.children(() -> { if (box(c).size(50, 30).label("inner").clicked()) inner[0]++; });
            if (o.clicked()) outer[0]++;
        }, 300, 200);
        tt.click("inner");
        Check.equal(1, inner[0]); Check.equal(0, outer[0]);
        tt.press(150, 80); tt.release(150, 80);
        Check.equal(1, outer[0]);
    }

    public void testTextInsideAClickableBoxClicksTheBox() {
        int[] n = {0};
        Tester tt = Tester.of(c -> {
            Element b = box(c).padding(8);
            b.children(() -> text(c, "保存"));
            if (b.clicked()) n[0]++;
        }, 300, 200);
        tt.click("保存");
        Check.equal(1, n[0]);
    }

    public void testTheRowSlidingUnderThePointerIsNotClicked() { // Review Focus 1
        java.util.List<String> rows = new java.util.ArrayList<>(java.util.List.of("r1", "r2", "r3"));
        java.util.List<String> clicked = new java.util.ArrayList<>();
        boolean[] r2Hovered = {false};
        Tester tt = Tester.of(c -> column(c).children(() -> {
            for (String r : java.util.List.copyOf(rows)) {
                Element e = box(c.key(r)).size(100, 30).label(r);
                if (e.clicked()) { clicked.add(r); rows.remove(r); }
                if (r.equals("r2")) r2Hovered[0] = e.hovered();
            }
        }), 300, 200);
        tt.click("r1");
        Check.equal(java.util.List.of("r1"), clicked);
        Check.isTrue(r2Hovered[0], "r2 now under the pointer is hovered, not clicked");
    }

    public void testTwoClicksBeforeAFrameCountTwo() { // Review Focus 4
        int[] counts = new int[2];
        Engine e = new Engine(null, Theme.light(), () -> {});
        View v = c -> { Element a = box(c).size(100, 40); counts[0] += a.clicked() ? 1 : 0; counts[1] += a.clicks(); };
        e.buildAndLayout(v, 300, 200);
        e.pointerDown(50, 20, 0, 1, 0); e.pointerUp(50, 20, 0, 0);
        e.pointerDown(50, 20, 0, 1, 0); e.pointerUp(50, 20, 0, 0);
        e.buildAndLayout(v, 300, 200);
        Check.equal(1, counts[0]);   // clicked() once in pass 1, nothing left for pass 2
        Check.equal(2, counts[1]);
    }

    public void testARowStaysHoveredWhileItsButtonIsHeld() { // final review I2
        boolean[] rowHot = {false};
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> {
            Element r = row(c).padding(10).label("row");
            r.children(() -> button(c, "B"));
            rowHot[0] = r.hovered();
        }), 300, 200);
        tt.hover("B");
        Check.isTrue(rowHot[0], "hovered before the press");
        Rect b = tt.find("B");
        tt.press(b.x() + b.width() / 2, b.y() + b.height() / 2);
        Check.isTrue(rowHot[0], "still hovered while its button is held");
    }

    public void testADoubleClickAfterAQuickPressElsewhereCounts() { // final review I3
        int[] dbl = {0};
        Engine e = new Engine(null, Theme.light(), () -> {});
        View v = c -> row(c).children(() -> {
            box(c).size(50, 40).doubleClicked();
            if (box(c).size(50, 40).doubleClicked()) dbl[0]++;
        });
        e.buildAndLayout(v, 300, 200);
        e.pointerDown(25, 20, 0, 1, 0); e.pointerUp(25, 20, 0, 0); e.buildAndLayout(v, 300, 200);   // A
        e.pointerDown(75, 20, 0, 2, 0); e.pointerUp(75, 20, 0, 0); e.buildAndLayout(v, 300, 200);   // B, at once: the platform counts 2
        e.pointerDown(75, 20, 0, 1, 0); e.pointerUp(75, 20, 0, 0); e.buildAndLayout(v, 300, 200);   // later, B again
        e.pointerDown(75, 20, 0, 2, 0); e.pointerUp(75, 20, 0, 0); e.buildAndLayout(v, 300, 200);   // and its double click
        Check.equal(1, dbl[0]);
    }

    public void testAClippedChildIsNotHitOutsideItsClip() { // Review Focus 5
        int[] n = {0};
        Tester tt = Tester.of(c -> box(c).size(100, 50).clip().children(() -> {
            if (box(c).size(300, 300).label("big").clicked()) n[0]++;
        }), 400, 400);
        tt.press(150, 150); tt.release(150, 150);  // inside big's box, outside the clip
        Check.equal(0, n[0]);
        tt.press(50, 25); tt.release(50, 25);
        Check.equal(1, n[0]);
    }
}
