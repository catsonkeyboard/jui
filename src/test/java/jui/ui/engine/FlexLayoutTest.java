package jui.ui.engine;

import jui.testing.Check;
import jui.ui.Align;
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
}
