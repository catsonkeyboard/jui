package jui.text;

import jui.testing.Check;
import jui.ui.Color;
import jui.ui.Span;
import jui.ui.Spans;

public final class RichTextTest {
    public void testImmutableSpansAndGlobalGraphemeBoundaries() {
        Span[] values = {Span.text("Hello "), Span.text("中😀").bold()};
        Spans spans = Spans.of(values);
        values[0] = Span.text("changed");
        Check.equal("Hello 中😀", spans.text());
        Check.fails(IllegalArgumentException.class, () -> Spans.of(Span.text("e"), Span.text("́")));
        Check.fails(IllegalArgumentException.class, () -> Spans.of(Span.text("\uD83D"), Span.text("\uDE00")));
        Check.equal(spans, Spans.of(Span.text("Hello "), Span.text("中😀").bold()));
    }

    public void testSharedBaselineAndPaintIndependentCache() {
        TextEngine engine = new TextEngine("zh-Hans");
        Spans first = Spans.of(Span.text("small "), Span.text("BIG").fontSize(30).bold(), Span.text(" code").monospace().italic());
        TextLayout layout = engine.layout(first, 14, 400, 0);
        Check.isTrue(layout.height >= 30, "mixed line contains largest font");
        Check.near(layout.baseline(0), layout.baseline(7));
        Check.near(30, layout.glyphSize(6));
        long shaped = engine.shapedLines;
        Spans colored = Spans.of(Span.text("small ").color(Color.rgb(0xFF0000)), Span.text("BIG").fontSize(30).bold(),
                Span.text(" code").monospace().italic().underline());
        Check.isTrue(layout == engine.layout(colored, 14, 400, 0), "paint does not shape");
        Check.equal(shaped, engine.shapedLines);
        float[] caret = new float[4]; layout.caretRect(7, false, caret);
        Check.near(layout.lineHeight(0), caret[3]);
        Check.near(layout.lineBaseline(0), layout.baseline(0));
    }

    public void testHardParagraphReuseAndTrailingLine() {
        TextEngine engine = new TextEngine("zh-Hans");
        TextLayout first = engine.layout(Spans.of(Span.text("first\n").bold(), Span.text("second\r\n").monospace()), 14, 400, 70);
        long shaped = engine.shapedLines;
        TextLayout next = engine.layout(Spans.of(Span.text("first\n").bold(), Span.text("new\r\n").monospace()), 14, 400, 70);
        Check.isTrue(engine.shapedLines - shaped <= 1, "unchanged paragraphs reused");
        Check.equal(0, next.lineStart(0));
        Check.equal(next.length(), next.lineStart(next.lines - 1));
        Check.isTrue(first.lines >= 3, "empty trailing paragraph");
    }
}
