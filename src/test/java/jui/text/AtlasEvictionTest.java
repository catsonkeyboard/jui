package jui.text;

import java.lang.foreign.Arena;
import java.util.Arrays;
import jui.render.Scene;
import jui.testing.Check;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

public final class AtlasEvictionTest {
    private static byte[] bitmap(GlyphAtlas atlas, GlyphAtlas.Entry entry) {
        byte[] bytes = new byte[entry.width() * entry.height()];
        for (int y = 0; y < entry.height(); y++) for (int x = 0; x < entry.width(); x++)
            bytes[y * entry.width() + x] = atlas.pixels().get(JAVA_BYTE, (long) (entry.y() + y) * atlas.width + entry.x() + x);
        return bytes;
    }

    public void testColdGlyphIsEvictedAndRerasterizedIdentically() {
        TextEngine text = new TextEngine("zh-Hans");
        GlyphAtlas atlas = new GlyphAtlas(96, 96, 96);
        TextLayout cold = text.layout("W", 18, 400, 0);
        GlyphAtlas.Entry first = atlas.get(text, cold.font(0), cold.glyph(0), 2, 0, 1, 0);
        byte[] pixels = bitmap(atlas, first);
        for (int frame = 0; frame < 100; frame++) {
            Scene scene = new Scene(); scene.begin(2, text, atlas);
            scene.text(Character.toString(0x4e00 + frame), 0, 0, 18, 400, 0xFFFFFFFF, 0);
        }
        Check.isTrue(atlas.generation() > 2, "small capped atlas must repack repeatedly");
        Check.isTrue(!atlas.isFull(), "cold glyphs must not cause missing ink");
        int rasterized = text.rasterized;
        GlyphAtlas.Entry again = atlas.get(text, cold.font(0), cold.glyph(0), 2, 0, 1, 0);
        Check.isTrue(again != first && text.rasterized > rasterized, "evicted glyph must rasterize again");
        Check.isTrue(Arrays.equals(pixels, bitmap(atlas, again)), "coverage identical after eviction");
        int[] dirty = new int[4];
        Check.isTrue(atlas.takeDirty(dirty), "replacement uploads");
        Check.equal(96, dirty[2]); Check.equal(96, dirty[3]);
    }

    public void testGlyphsAlreadyPaintedThisFrameSurviveMidFrameRepack() {
        TextEngine text = new TextEngine("en");
        GlyphAtlas atlas = new GlyphAtlas(128, 128, 128);
        Scene scene = new Scene();
        scene.begin(2, text, atlas);
        scene.text("abcdefghijklmno", 0, 0, 12, 400, 0xFFFFFFFF, 0);
        scene.begin(2, text, atlas); // previous glyphs are now cold candidates
        TextLayout w = text.layout("W", 13, 400, 0);
        scene.text(w, 8, 8, 0xFFFFFFFF);
        GlyphAtlas.Entry active = atlas.get(text, w.font(0), w.glyph(0), 2, 0, 1, 3);
        byte[] before = bitmap(atlas, active);
        int generation = atlas.generation();
        TextLayout m = text.layout("M", 48, 400, 0);
        GlyphAtlas.Entry large = atlas.get(text, m.font(0), m.glyph(0), 2, 0, 1, 0);
        Check.isTrue(large != null && atlas.generation() > generation, "pressure repacks at same dimensions");
        Check.isTrue(Arrays.equals(before, bitmap(atlas, active)), "active glyph remains resident");
        try (Arena arena = Arena.ofConfined()) {
            var instances = arena.allocate((long) scene.count() * Scene.BYTES);
            scene.writeTo(instances, scene.count());
            Check.near(active.x(), instances.getAtIndex(JAVA_FLOAT, 4));
            Check.near(active.y(), instances.getAtIndex(JAVA_FLOAT, 5));
        }
        Check.isTrue(!atlas.isFull(), "no glyph dropped");
    }
}
