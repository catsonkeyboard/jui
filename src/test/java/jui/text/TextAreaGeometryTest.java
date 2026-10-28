package jui.text;

import jui.testing.Check;

public final class TextAreaGeometryTest {
    private final TextEngine engine = new TextEngine("zh-Hans");

    public void testSoftBoundaryHasTwoVisualPositions() {
        TextLayout layout = engine.layout("abcdef ghijkl mnopqr", 13, 400, 45);
        int boundary = layout.lineEnd(0);
        Check.equal(0, layout.lineAt(boundary, true));
        Check.equal(1, layout.lineAt(boundary, false));
        Check.isTrue(layout.caretX(boundary, true) > 0, "upstream is at the old line end");
        Check.near(0, layout.caretX(boundary, false));
        int[] hit = new int[2];
        layout.hitTest(1000, layout.lineTop(0) + 1, hit);
        Check.equal(boundary, hit[0]);
        Check.equal(1, hit[1]);
        layout.hitTest(-10, layout.lineTop(1) + 1, hit);
        Check.equal(boundary, hit[0]);
        Check.equal(0, hit[1]);
    }

    public void testSelectionsIncludeHardBreakAndEmptyLineStubs() {
        TextLayout layout = engine.layout("a\r\n\nb\n", 13, 400, 100);
        float[] rectangle = new float[4];
        Check.isTrue(layout.selectionRect(1, 4, 0, rectangle), "CRLF selected");
        Check.near(13f / 3, rectangle[2]);
        Check.isTrue(layout.selectionRect(1, 4, 1, rectangle), "empty line selected");
        Check.isTrue(!layout.selectionRect(1, 4, 2, rectangle), "selection stops at paragraph start");
        Check.isTrue(!layout.selectionRect(2, 2, 0, rectangle), "collapsed selection");
    }

    public void testHitRoundTripsAndClampsOutsideTheText() {
        String source = "a😀é中\n";
        TextLayout layout = engine.layout(source, 13, 400, 100);
        int[] hit = new int[2];
        for (int stop : TextBounds.graphemes(source)) {
            int line = layout.lineAt(stop, false);
            layout.hitTest(layout.caretX(stop, false), layout.lineTop(line) + 1, hit);
            Check.equal(stop, hit[0]);
        }
        layout.hitTest(-100, -100, hit);
        Check.equal(0, hit[0]);
        layout.hitTest(1000, 1000, hit);
        Check.equal(source.length(), hit[0]);
    }
}
