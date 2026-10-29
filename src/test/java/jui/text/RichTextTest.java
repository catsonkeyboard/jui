package jui.text;

import jui.testing.Check;
import jui.ui.Color;
import jui.ui.Span;
import jui.ui.Spans;

public final class RichTextTest {
    public void testFontBucketsAndShapeHashCollisionsCompareRealValues() throws Exception {
        TextEngine engine = new TextEngine("en");
        long normal = engine.layout("a", 14, 400, 0).font(0), bold = engine.layout("a", 14, 700, 0).font(0);
        Check.isTrue(!CoreText.equal(normal, bold), "font traits differ");
        var field = TextEngine.class.getDeclaredField("canonicalFonts"); field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var buckets = (java.util.Map<Long, java.util.ArrayList<Long>>) field.get(engine);
        buckets.get(CoreText.hash(bold)).addFirst(normal);
        var canonical = TextEngine.class.getDeclaredMethod("canonical", long.class); canonical.setAccessible(true);
        Check.equal(bold, ((Long) canonical.invoke(engine, bold)).longValue());
        Spans first = Spans.of(Span.text("Aa")), second = Spans.of(Span.text("BB"));
        Check.equal(first.shapeHash(), second.shapeHash());
        Check.isTrue(!first.sameShape(second), "hash is not identity");
        Check.isTrue(engine.layout(first, 14, 400, 0) != engine.layout(second, 14, 400, 0), "collision cannot reuse wrong text");
    }

    public void testDecorationsOnPaintOnlyPartOfSharedCluster() {
        TextEngine engine = new TextEngine("en");
        TextLayout base = engine.layout("f", 14, 400, 0);
        TextLayout.LineTable table = new TextLayout.LineTable();
        table.add(0, 2, 2, 0, 1, 0, base.height, base.width);
        table.baselines[0] = base.lineBaseline(0);
        float[] carets = {0, base.width / 2, base.width};
        TextLayout shared = new TextLayout("fi", 14, 400, 0, base.width, base.height, 1,
                base.fonts, base.glyphs, base.xs, base.ys, new int[] {0}, table, new int[] {0, 1, 2}, carets, carets);
        jui.render.Scene scene = new jui.render.Scene();
        scene.begin(2, engine, new GlyphAtlas(128, 128));
        scene.richText(shared, Spans.of(Span.text("f"), Span.text("i").strikethrough()), 0, 0, 0x000000FF, 1);
        float actual = scene.instance(scene.count() - 1)[1];
        Check.near(base.lineBaseline(0) - engine.metrics(base.font(0)).xHeight() / 2, actual);
    }

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
