package jui.ui;

import jui.testing.Check;
import jui.render.Scene;
import static jui.ui.Ui.*;

public final class RichPaintTest {
    public void testBackgroundGlyphAndDecorationOrder() {
        Spans spans = Spans.of(Span.text("red").color(Color.rgb(0xFF0000)).background(Color.rgb(0xEEEEEE)).underline(),
                Span.text(" BIG").fontSize(28).monospace().strikethrough());
        Tester tester = Tester.of(context -> richText(context, spans), 300, 200);
        tester.frame();
        Scene scene = tester.scene();
        Check.isTrue(scene.count() > 8, "rich glyphs and decoration emitted");
        Check.near(0, scene.instance(0)[18]);
        Check.near(2, scene.instance(1)[18]);
        Check.near(1, scene.instance(1)[8]); Check.near(0, scene.instance(1)[9]);
        Check.near(0, scene.instance(scene.count() - 1)[18]);
        Check.isTrue(tester.find("red BIG").height() >= 28, "rich sizes affect layout");
    }

    public void testDecorationUsesFontMetricsAcrossPaintBoundaryInsideLigature() {
        Spans spans = Spans.of(Span.text("f"), Span.text("i").strikethrough());
        jui.text.TextLayout[] measured = new jui.text.TextLayout[1];
        Tester tester = Tester.of(context -> {
            measured[0] = context.layoutText(spans, 14, 400, 300);
            richText(context, spans).fontSize(14);
        }, 300, 100);
        tester.frame();
        float[] strike = tester.scene().instance(tester.scene().count() - 1);
        Check.near(0, strike[18]);
        Check.isTrue(strike[1] < measured[0].lineBaseline(0) - 1, "strike stays above baseline even inside ligature");
    }
}
