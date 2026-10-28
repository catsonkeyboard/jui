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

    public void testCachedCaretOffsetsMatchCoreTextForLigaturesAndKerning() {
        String source = "office AV é 中文";
        TextLayout layout = engine.layout(source, 13, 400, 0);
        long string = CoreText.string(source);
        long attributes = CoreText.dictionary(new long[] {CoreText.FONT_ATTRIBUTE_NAME}, new long[] {layout.font(0)});
        long attributed = CoreText.attributedString(string, attributes);
        long typesetter = CoreText.typesetter(attributed);
        long line = CoreText.createLine(typesetter, 0, source.length());
        try {
            for (int stop : TextBounds.graphemes(source)) {
                Check.near(CoreText.lineOffset(line, stop), layout.caretX(stop), 0.01);
            }
        } finally {
            CoreText.release(line);
            CoreText.release(typesetter);
            CoreText.release(attributed);
            CoreText.release(attributes);
            CoreText.release(string);
        }
    }

    public void testAppendingDoesNotReshapeUnchangedHardParagraphs() {
        String source = "same paragraph with 中文 text\n".repeat(100);
        engine.layout(source, 13, 400, 120);
        long before = engine.shapedLines;
        engine.layout(source + "tail", 13, 400, 120);
        Check.isTrue(engine.shapedLines - before <= 2, "unchanged paragraphs are reused: " + (engine.shapedLines - before));
    }

    public void testParagraphReusePreservesSourceGeometry() {
        String paragraph = "office AV a😀é中文 abcdefghijkl";
        String prefix = "\r\n\n";
        TextLayout whole = engine.layout(prefix + paragraph + "\r\ntail", 13, 400, 65);
        TextLayout part = engine.layout(paragraph, 13, 400, 65);
        float top = whole.lineTop(2);
        for (int line = 0; line < part.lines; line++) {
            Check.equal(prefix.length() + part.lineStart(line), whole.lineStart(2 + line));
            Check.equal(prefix.length() + part.lineEnd(line), whole.lineEnd(2 + line));
            Check.near(top + part.lineTop(line), whole.lineTop(2 + line));
            Check.near(part.lineHeight(line), whole.lineHeight(2 + line));
        }
        for (int stop : TextBounds.graphemes(paragraph)) {
            Check.near(part.caretX(stop, false), whole.caretX(prefix.length() + stop, false));
            Check.near(part.caretX(stop, true), whole.caretX(prefix.length() + stop, true));
        }
        int firstGlyph = whole.lineGlyphStart(2);
        for (int glyph = 0; glyph < part.glyphCount(); glyph++) {
            Check.equal(part.glyph(glyph), whole.glyph(firstGlyph + glyph));
            Check.equal(prefix.length() + part.index(glyph), whole.index(firstGlyph + glyph));
            Check.near(top + part.baseline(glyph), whole.baseline(firstGlyph + glyph));
        }
    }
}
