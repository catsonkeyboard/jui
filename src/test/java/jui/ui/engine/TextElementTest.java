package jui.ui.engine;

import jui.testing.Check;
import jui.text.TextEngine;
import jui.ui.Align;
import jui.ui.Theme;

import static jui.ui.Ui.box;
import static jui.ui.Ui.column;
import static jui.ui.Ui.row;
import static jui.ui.Ui.text;

public final class TextElementTest {
    final Engine e = new Engine(new TextEngine("zh-Hans"), Theme.light(), () -> {});
    static final String LONG = "Java 通过 FFM 直接调用 Core Text：由它找字体，为缺字的字体回退，再整形和断行。";

    float oneLine() {
        e.buildAndLayout(c -> column(c).children(() -> text(c, "一行").label("t")), 2000, 200);
        return e.bounds("t").height();
    }

    public void testTextWrapsToTheContainer() {
        float line = oneLine();
        e.buildAndLayout(c -> column(c).width(120).children(() -> text(c, LONG).label("t")), 2000, 2000);
        Check.isTrue(e.bounds("t").height() > 2 * line * 0.9f, "several lines");
        Check.isTrue(e.bounds("t").width() <= 120.01f, "within the column");
    }

    public void testSingleLineDoesNotWrap() {
        float line = oneLine();
        e.buildAndLayout(c -> column(c).width(120).children(() -> text(c, LONG).singleLine().label("t")), 2000, 200);
        Check.near(line, e.bounds("t").height());
    }

    public void testFontSizeIsInherited() {
        e.buildAndLayout(c -> column(c).children(() -> text(c, "继承").fontSize(26).label("own")), 2000, 200);
        float own = e.bounds("own").height();
        e.buildAndLayout(c -> column(c).fontSize(26).children(() -> text(c, "继承").label("inh")), 2000, 200);
        Check.near(own, e.bounds("inh").height());
    }

    public void testBoldIsWider() {
        e.buildAndLayout(c -> row(c).children(() -> {
            text(c, "Bold 粗体").label("r"); text(c, "Bold 粗体").bold().label("b"); }), 2000, 200);
        Check.isTrue(e.bounds("b").width() > e.bounds("r").width(), "700 is wider than 400");
    }

    public void testTextBesideAFixedBoxWrapsInTheRest() { // Review Focus 1
        float line = oneLine();
        e.buildAndLayout(c -> row(c).width(300).alignItems(Align.START).children(() -> {
            box(c).size(100, 20); text(c, LONG).grow(1).label("t"); }), 300, 2000);
        Check.isTrue(e.bounds("t").width() <= 200.01f, "fits beside the box");
        Check.isTrue(e.bounds("t").height() > line * 1.5f, "wraps");
    }

    public void testAutoHeightColumnMeasuresTextAtItsMaxWidth() { // final review #1
        float line = oneLine();
        e.buildAndLayout(c -> column(c).width(300).children(() -> {
            column(c).children(() -> text(c, LONG).maxWidth(100).label("t"));
            box(c).height(10).label("below"); }), 2000, 2000);
        Check.isTrue(e.bounds("t").height() > line * 3, "wraps at its max width of 100");
        Check.isTrue(e.bounds("below").y() >= e.bounds("t").y() + e.bounds("t").height() - 0.01f,
                "the box below starts after the text: " + e.bounds("t") + " " + e.bounds("below"));
    }

    public void testTextSqueezedToNoWidthStillWraps() { // deferred minor: 0 meant "no limit"
        float line = oneLine();
        e.buildAndLayout(c -> row(c).width(100).alignItems(Align.START).children(() -> {
            box(c).size(100, 20).shrink(0); text(c, LONG).grow(1).label("t"); }), 100, 4000);
        Check.near(0, e.bounds("t").width());
        Check.isTrue(e.bounds("t").height() > line * 3, "breaks at every chance rather than running on one line");
    }

    public void testEmptyAndUnbrokenText() { // Review Focus 3
        float line = oneLine();
        e.buildAndLayout(c -> column(c).width(100).children(() -> {
            text(c, "").label("empty");
            text(c, "https://example.com/" + "a".repeat(200)).label("url"); }), 2000, 2000);
        Check.near(0, e.bounds("empty").height());                       // no line, and no crash
        Check.isTrue(e.bounds("url").height() > line * 1.5f, "breaks inside the word instead of overflowing");
    }
}
