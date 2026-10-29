package jui.text;

import jui.testing.Check;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public final class AtlasGrowthTest {
    public void testGrowthPreservesEntriesPixelsAndDirtyBounds() {
        TextEngine engine = new TextEngine("zh-Hans");
        GlyphAtlas atlas = new GlyphAtlas(32, 32, 256);
        TextLayout layout = engine.layout("Wabcdefghijklmnopqrstuvwxyz0123456789中文测试", 18, 400, 0);
        GlyphAtlas.Entry first = atlas.get(engine, layout.font(0), layout.glyph(0), 2, 0, 1, 0);
        byte[] pixels = new byte[first.width() * first.height()];
        for (int row = 0; row < first.height(); row++) for (int col = 0; col < first.width(); col++)
            pixels[row * first.width() + col] = atlas.pixels().get(JAVA_BYTE, (long) (first.y() + row) * atlas.width + first.x() + col);
        for (int index = 1; index < layout.glyphCount(); index++) atlas.get(engine, layout.font(index), layout.glyph(index), 2, 0, 1, 0);
        Check.isTrue(atlas.generation() >= 2, "at least two growths");
        Check.isTrue(first == atlas.get(engine, layout.font(0), layout.glyph(0), 2, 0, 1, 0), "entry remains stable");
        for (int row = 0; row < first.height(); row++) for (int col = 0; col < first.width(); col++)
            Check.equal(pixels[row * first.width() + col], atlas.pixels().get(JAVA_BYTE, (long) (first.y() + row) * atlas.width + first.x() + col));
        int[] dirty = new int[4];
        Check.isTrue(atlas.takeDirty(dirty), "growth dirties whole image");
        Check.equal(atlas.width, dirty[2]); Check.equal(atlas.height, dirty[3]);
        Check.isTrue(!atlas.isFull(), "sample fits at cap");
    }

    public void testFixedAndCappedAtlasesFailWithoutOverwriting() {
        TextEngine engine = new TextEngine("en");
        TextLayout layout = engine.layout("W", 100, 400, 0);
        GlyphAtlas fixed = new GlyphAtlas(8, 8);
        Check.equal(null, fixed.get(engine, layout.font(0), layout.glyph(0), 2, 0, 1, 0));
        Check.isTrue(fixed.isFull(), "fixed capacity unchanged");
        GlyphAtlas capped = new GlyphAtlas(8, 8, 32);
        Check.equal(null, capped.get(engine, layout.font(0), layout.glyph(0), 2, 0, 1, 0));
        Check.equal(32, capped.width); Check.isTrue(capped.isFull(), "growth has a hard limit");
    }
}
