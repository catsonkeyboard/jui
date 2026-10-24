package jui.ui.engine;

import jui.render.Scene;
import jui.testing.Check;
import jui.text.GlyphAtlas;
import jui.text.TextEngine;
import jui.ui.Color;
import jui.ui.Theme;

import static jui.ui.Ui.box;
import static jui.ui.Ui.column;
import static jui.ui.Ui.text;

public final class PaintTest {
    final TextEngine text = new TextEngine("zh-Hans");
    final Engine e = new Engine(text, Theme.light(), () -> {});

    Scene paint(float scale) {
        Scene s = new Scene();
        s.begin(scale, text, new GlyphAtlas(512, 512));
        e.paint(s);
        return s;
    }

    public void testShadowThenBoxAtLayoutBounds() {
        e.buildAndLayout(c -> column(c).padding(10, 20).children(() ->
            box(c).height(50).background(Color.rgb(0xFFFFFF)).radius(8).border(1, Color.rgb(0xE5E7EB))
                  .shadow(0, 4, 12, 2, Color.rgba(0x000000, 0.2f))), 200, 100);
        Scene s = paint(2);
        float[] shadow = s.instance(0), box = s.instance(1);
        Check.near(1, shadow[18]); Check.near(0, box[18]);
        Check.near(20, box[0]); Check.near(10, box[1]); Check.near(160, box[2]); Check.near(50, box[3]);
        Check.near(8, box[4]);
        Check.near(18, shadow[0]); Check.near(12, shadow[1]); // offset by (0, 4), grown by the spread 2
        Check.near(164, shadow[2]); Check.near(54, shadow[3]);
    }

    public void testRadiiStopAtHalfTheShorterSide() { // final review #5: radius(999) makes a pill
        e.buildAndLayout(c -> column(c).alignItems(jui.ui.Align.START).children(() ->
            box(c).size(100, 28).radius(999).background(Color.rgb(0x2563EB))
                  .shadow(0, 2, 6, 0, Color.rgba(0x000000, 0.2f))), 200, 100);
        Scene s = paint(2);
        float[] shadow = s.instance(0), box = s.instance(1);
        for (int k = 4; k < 8; k++) {
            Check.near(14, box[k]);
        }
        Check.near(14, shadow[4]);
    }

    public void testTransparentTextColorIsKept() { // deferred minor: 0 was the inherit sentinel
        e.buildAndLayout(c -> column(c).textColor(Color.rgb(0xFF0000)).children(() ->
            text(c, "x").textColor(Color.TRANSPARENT)), 200, 100);
        Scene s = paint(2);
        Check.near(2, s.instance(0)[18]);  // the glyph
        Check.near(0, s.instance(0)[11]);  // its alpha: transparent, not the column's red
    }

    public void testBorderInsetsChildren() {
        e.buildAndLayout(c -> column(c).border(2, Color.rgb(0)).padding(10).children(() -> box(c).height(5).label("in")), 100, 100);
        Check.near(12, e.bounds("in").x());
    }

    public void testParentsPaintBeforeChildrenAndTextBecomesGlyphs() {
        e.buildAndLayout(c -> column(c).background(Color.rgb(0xF3F4F6)).children(() -> text(c, "Hi")), 200, 100);
        Scene s = paint(2);
        Check.near(0, s.instance(0)[18]);                     // the column's box comes first
        Check.isTrue(s.count() >= 3, "two glyphs after it");
        Check.near(2, s.instance(1)[18]);
    }

    public void testScaleChangesPixelsNotPoints() { // Review Focus 5
        e.buildAndLayout(c -> column(c).children(() -> {
            box(c).size(40, 10).background(Color.rgb(0)); text(c, "W"); }), 200, 100);
        Scene one = paint(1), two = paint(2);
        Check.near(one.instance(0)[2], two.instance(0)[2]);   // the box: same width in points
        // Glyph bitmaps keep a fixed pixel margin each side for smoothing (17 px at 1x, 28 at 2x for
        // this W, ink 13 and 24), so the whole width grows by less than twice.
        Check.isTrue(two.instance(1)[2] > one.instance(1)[2] * 1.5f, "the glyph rasterized at twice the pixels");
    }

    public void testDisabledPaintsAtHalfOpacity() {
        e.buildAndLayout(c -> box(c).size(50, 50).background(Color.rgb(0xFF0000)).disabled(true), 200, 100);
        Check.near(0.5, paint(1).instance(0)[11]);
    }

    public void testOpacityMultipliesDownTheTree() {
        e.buildAndLayout(c -> box(c).opacity(0.5f).children(() ->
                box(c).size(50, 50).opacity(0.8f).background(Color.rgb(0xFF0000))), 200, 100);
        Check.near(0.4, paint(1).instance(0)[11], 1e-3);
    }
}
