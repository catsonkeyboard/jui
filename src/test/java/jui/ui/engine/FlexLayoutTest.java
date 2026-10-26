package jui.ui.engine;

import jui.testing.Check;
import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Element;
import jui.ui.Justify;
import jui.ui.Rect;
import jui.ui.Theme;
import jui.ui.Ui;

import static jui.ui.Ui.box;
import static jui.ui.Ui.column;
import static jui.ui.Ui.row;

public final class FlexLayoutTest {
    final Engine e = new Engine(null, Theme.light(), () -> {});

    Rect at(String label) {
        return e.bounds(label);
    }

    public void testRowPlacesFixedChildrenWithGapAndCentersThem() {
        e.buildAndLayout(c -> row(c).size(300, 100).gap(10).children(() -> {
            box(c).size(50, 20).label("a"); box(c).size(60, 20).label("b"); }), 300, 100);
        Check.near(0, at("a").x()); Check.near(60, at("b").x());
        Check.near(40, at("a").y()); // a row centers across by default
    }

    public void testColumnStretchesByDefault() {
        e.buildAndLayout(c -> column(c).size(200, 300).children(() -> box(c).height(40).label("a")), 200, 300);
        Check.near(200, at("a").width()); Check.near(40, at("a").height());
    }

    public void testGrowSharesFreeSpace() {
        e.buildAndLayout(c -> row(c).size(300, 50).children(() -> {
            box(c).width(100); box(c).grow(1).label("g1"); box(c).grow(2).label("g2"); }), 300, 50);
        Check.near(66.67, at("g1").width()); Check.near(133.33, at("g2").width());
        Check.near(166.67, at("g2").x());
    }

    public void testShrinkWhenOverflowing() {
        e.buildAndLayout(c -> row(c).size(200, 50).children(() -> {
            box(c).basis(150).label("a"); box(c).basis(150).label("b"); }), 200, 50);
        Check.near(100, at("a").width()); Check.near(100, at("b").width());
    }

    public void testJustify() {
        float[][] expected = {{100, 150}, {0, 250}, {50, 200}, {66.67f, 183.33f}};
        Justify[] modes = {Justify.CENTER, Justify.SPACE_BETWEEN, Justify.SPACE_AROUND, Justify.SPACE_EVENLY};
        for (int i = 0; i < modes.length; i++) {
            Justify j = modes[i];
            e.buildAndLayout(c -> row(c).size(300, 50).justify(j).children(() -> {
                box(c).size(50, 10).label("a"); box(c).size(50, 10).label("b"); }), 300, 50);
            Check.near(expected[i][0], at("a").x()); Check.near(expected[i][1], at("b").x());
        }
    }

    public void testSpaceAroundAndEvenlyCenterWhenTheyOverflow() { // deferred minor: CSS Flexbox §8.2
        for (Justify j : new Justify[] {Justify.SPACE_AROUND, Justify.SPACE_EVENLY}) {
            e.buildAndLayout(c -> row(c).size(300, 50).justify(j).children(() -> {
                box(c).size(180, 10).shrink(0).label("a"); box(c).size(180, 10).shrink(0).label("b"); }), 300, 50);
            Check.near(-30, at("a").x()); // 60 too wide: half overflows each side
            Check.near(150, at("b").x());
        }
    }

    public void testPaddingInsetsContent() {
        e.buildAndLayout(c -> column(c).size(200, 200).padding(10).children(() -> box(c).height(20).label("a")), 200, 200);
        Check.near(10, at("a").x()); Check.near(10, at("a").y()); Check.near(180, at("a").width());
    }

    public void testAutoMarginPushesToTheEnd() {
        e.buildAndLayout(c -> row(c).size(300, 50).children(() -> {
            box(c).size(50, 10); box(c).size(50, 10).margin(0, 0, 0, Ui.AUTO).label("end"); }), 300, 50);
        Check.near(250, at("end").x());
    }

    public void testPercentAndFill() {
        e.buildAndLayout(c -> row(c).size(400, 200).children(() -> box(c).widthPercent(25).height(10).label("p")), 400, 200);
        Check.near(100, at("p").width());
        e.buildAndLayout(c -> box(c).fill().label("f"), 400, 200);
        Check.near(400, at("f").width()); Check.near(200, at("f").height());
    }

    public void testMaxClampRedistributes() {
        e.buildAndLayout(c -> row(c).size(300, 50).children(() -> {
            box(c).grow(1).maxWidth(120).label("a"); box(c).grow(1).label("b"); }), 300, 50);
        Check.near(120, at("a").width()); Check.near(180, at("b").width());
    }

    public void testReverseStartsFromTheEnd() {
        e.buildAndLayout(c -> row(c).size(300, 50).reverse().children(() -> {
            box(c).size(50, 10).label("a"); box(c).size(60, 10).label("b"); }), 300, 50);
        Check.near(250, at("a").x()); Check.near(190, at("b").x());
    }

    public void testReverseKeepsMarginsOnTheirSide() { // deferred minor: as CSS's row-reverse
        e.buildAndLayout(c -> row(c).size(300, 50).reverse().children(() ->
            box(c).size(50, 10).margin(0, 0, 0, 20).label("a")), 300, 50);
        Check.near(250, at("a").x()); // the left margin stays on its left, away from the right edge
    }

    public void testAlignItemsEndAndNestedContentSize() {
        e.buildAndLayout(c -> column(c).size(300, 200).children(() ->
            row(c).gap(5).alignItems(Align.END).label("r").children(() -> {
                box(c).size(40, 30); box(c).size(50, 20).label("short"); })), 300, 200);
        Check.near(30, at("r").height());      // as high as its tallest child
        Check.near(300, at("r").width());      // stretched across the column
        Check.near(10, at("short").y());       // bottom-aligned in the 30-high row
    }

    public void testTinySpacesNeverGoNegative() { // Review Focus 2
        e.buildAndLayout(c -> row(c).size(20, 10).padding(30).children(() -> {
            box(c).size(50, 50).shrink(0).label("a"); box(c).grow(1).label("b"); }), 20, 10);
        for (String s : new String[] {"a", "b"}) {
            Rect r = at(s);
            Check.isTrue(r.width() >= 0 && r.height() >= 0 && !Float.isNaN(r.x()) && !Float.isNaN(r.y()), s + " " + r);
        }
    }

    public void testARowWrapsWhenTheNextItemDoesNotFit() {
        e.buildAndLayout(c -> row(c).width(100).wrap().gap(10).alignItems(Align.START).children(() -> {
            for (int i = 0; i < 3; i++) box(c).size(40, 20).label("a" + i);
        }), 300, 300);
        Check.equal(new Rect(0, 0, 40, 20), e.bounds("a0"));
        Check.equal(new Rect(50, 0, 40, 20), e.bounds("a1"));
        Check.equal(new Rect(0, 30, 40, 20), e.bounds("a2"));     // 90 + 10 + 40 > 100
    }

    public void testALineIsAsTallAsItsTallestItem() {
        e.buildAndLayout(c -> row(c).width(100).wrap().gapX(10).gapY(4).alignItems(Align.START).children(() -> {
            box(c).size(40, 20).label("a"); box(c).size(40, 30).label("b"); box(c).size(40, 10).label("c");
        }), 300, 300);
        Check.near(34, e.bounds("c").y());                        // line 1 is 30 high, then gapY 4
    }

    public void testAlignContentDefaultsToStartAndCanCenterOrStretch() {
        for (Align a : new Align[] {null, Align.CENTER, Align.STRETCH}) {
            e.buildAndLayout(c -> {
                Element r = row(c).size(100, 200).wrap().gap(10).alignItems(Align.START);
                if (a != null) r.alignContent(a);
                r.children(() -> { for (int i = 0; i < 3; i++) box(c).size(40, 20).label("a" + i); });
            }, 300, 300);
            float y0 = e.bounds("a0").y(), y2 = e.bounds("a2").y();
            if (a == null) { Check.near(0, y0); Check.near(30, y2); }
            if (a == Align.CENTER) { Check.near(75, y0); Check.near(105, y2); }      // free 200 - 50 = 150
            if (a == Align.STRETCH) { Check.near(0, y0); Check.near(105, y2); }      // each line + 75
        }
    }

    public void testAColumnWrapsIntoColumns() {
        e.buildAndLayout(c -> column(c).height(100).wrap().gap(10).alignItems(Align.START).children(() -> {
            for (int i = 0; i < 3; i++) box(c).size(30, 40).label("a" + i);
        }), 300, 300);
        Check.equal(new Rect(0, 50, 30, 40), e.bounds("a1"));
        Check.equal(new Rect(40, 0, 30, 40), e.bounds("a2"));     // a new column after gapX
    }

    public void testAWrappingContainerWithOneLineKeepsItsNaturalHeight() {
        e.buildAndLayout(c -> row(c).size(300, 200).wrap().alignItems(Align.STRETCH).children(() ->
                box(c).width(40).label("s")), 300, 300);
        Check.near(0, e.bounds("s").height());   // stretched to its line, whose natural height is 0, not to the 200-high container
    }

    public void testEachLineFlexesOnItsOwn() {
        e.buildAndLayout(c -> row(c).width(100).wrap().children(() -> {
            box(c).width(60).label("a"); box(c).basis(60).grow(1).label("b");   // grow alone means basis 0, which never wraps
        }), 300, 300);
        Check.near(100, e.bounds("b").width());                   // alone on line 2, it grows to the whole line
    }

    public void testAbsoluteIsPlacedInThePaddingBox() {
        e.buildAndLayout(c -> box(c).size(200, 100).padding(10).border(2, Color.rgb(0)).children(() -> {
            box(c).absolute().top(5).left(7).size(20, 20).label("tl");
            box(c).absolute().right(5).bottom(5).size(20, 20).label("br");
            box(c).absolute().left(10).right(10).height(8).label("wide");
            box(c).absolute().size(4, 4).label("none");
        }), 300, 300);
        Check.equal(new Rect(9, 7, 20, 20), e.bounds("tl"));     // 2 (border) + 7, 2 + 5
        Check.equal(new Rect(173, 73, 20, 20), e.bounds("br"));  // 2 + 196 - 5 - 20, 2 + 96 - 5 - 20
        Check.equal(new Rect(12, 2, 176, 8), e.bounds("wide"));  // 196 - 10 - 10
        Check.equal(new Rect(2, 2, 4, 4), e.bounds("none"));
    }

    public void testAbsoluteChildrenDoNotSizeTheirParent() {
        e.buildAndLayout(c -> column(c).alignItems(Align.START).children(() ->
            column(c).padding(5).label("p").children(() -> box(c).absolute().size(300, 300))), 400, 400);
        Check.near(10, e.bounds("p").height());
        Check.near(10, e.bounds("p").width());
    }
}
