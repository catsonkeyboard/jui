package jui.text;

import jui.testing.Check;

public final class TextLineTest {
    private final TextEngine engine = new TextEngine("zh-Hans");

    public void testEmptyAndTrailingLinesHaveHeight() {
        TextLayout empty = engine.layout("", 13, 400, 100);
        Check.equal(1, empty.lines);
        Check.isTrue(empty.lineHeight(0) > 0, "empty line has height");
        Check.equal(2, engine.layout("a\n", 13, 400, 100).lines);
        Check.equal(3, engine.layout("\n\n", 13, 400, 100).lines);
    }

    public void testSourceIndicesIncludeHardBreaks() {
        TextLayout layout = engine.layout("a\r\nb\rc", 13, 400, 100);
        Check.equal(3, layout.lines);
        Check.equal(1, layout.lineEnd(0));
        Check.equal(3, layout.lineBreakEnd(0));
        Check.equal(3, layout.lineStart(1));
        Check.equal(5, layout.lineStart(2));
    }

    public void testWrapMakesProgressOnGraphemes() {
        String source = "a😀é中文 abc ";
        int[] stops = TextBounds.graphemes(source);
        TextLayout layout = engine.layout(source, 13, 400, 0.01f);
        Check.isTrue(layout.lines > 3, "wrapped");
        for (int line = 0; line < layout.lines; line++) {
            Check.isTrue(layout.lineEnd(line) > layout.lineStart(line), "each line advances");
            Check.equal(layout.lineEnd(line), TextBounds.floor(stops, layout.lineEnd(line)));
        }
    }

    public void testBlankLayoutsAgeBySourceUnits() {
        for (int length = 2000; length < 4000; length += 20) {
            engine.layout("\n".repeat(length), 13, 400, 100);
        }
        Check.isTrue(engine.cachedSourceUnits() <= 408000, "blank lines count toward the cache budget");
        TextLayout oversized = engine.layout("\n".repeat(200001), 13, 400, 100);
        Check.isTrue(oversized == engine.layout("\n".repeat(200001), 13, 400, 100), "oversized layout still cached");
    }
}
