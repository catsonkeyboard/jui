package jui.text;

import jui.testing.Check;

public final class FontFamilyTest {
    public void testNamedFamilyShapesAndRasterizesWithItsOwnMetrics() {
        TextEngine serif = new TextEngine("en", "Times New Roman");
        TextEngine system = new TextEngine("en");
        TextLayout a = serif.layout("Typography", 32, 400, 0);
        TextLayout b = system.layout("Typography", 32, 400, 0);
        Check.isTrue(Math.abs(a.width - b.width) > 2, "family changes the actual glyph advances: " + a.width + " / " + b.width);
        Check.isTrue(serif.layout("Typography", 32, 400, 0) == a, "named layouts are cached");
        TextLayout bold = serif.layout("Typography", 32, 700, 0);
        Check.isTrue(a.font(0) != bold.font(0), "bold is preserved during family conversion");
        Check.isTrue(serif.metrics(a.font(0)).ascent() > 0, "named font metrics are registered");
        GlyphAtlas atlas = new GlyphAtlas(1024, 1024);
        jui.render.Scene scene = new jui.render.Scene();
        scene.begin(2, system, atlas);
        scene.text(a, 0, 0, 0xFFFFFFFF);
        Check.isTrue(atlas.glyphs() > 0, "a layout can be drawn by the window's text engine");
    }
}
