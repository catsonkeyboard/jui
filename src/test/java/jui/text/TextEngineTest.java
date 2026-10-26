package jui.text;

import jui.testing.Check;

public final class TextEngineTest {
    public void testPositionsFollowCoreGraphicsQuantization() {
        Check.equal(2, TextEngine.positions(16, 2));  // 32 px: ceil(100/96) = 2
        Check.equal(1, TextEngine.positions(17, 2));  // 34 px: one position from 34 px an em
        Check.equal(5, TextEngine.positions(6, 1));   // 6 px: ceil(100/18) = 6, at most 5
    }

    public void testLayoutCacheIsBoundedByEntries() { // final review #4
        TextEngine t = new TextEngine("zh-Hans");
        for (int w = 0; w < 10_000; w++) {
            t.layout("缓存", 13, 400, 100 + w);
        }
        Check.isTrue(t.cachedLayouts() <= 2 * 2048, "at most two generations of 2048: " + t.cachedLayouts());
    }

    public void testLayoutCacheIsBoundedByGlyphs() { // final review #4: long notes resized
        TextEngine t = new TextEngine("zh-Hans");
        String note = "中文和 English 混排的一段长笔记。".repeat(100); // about 2000 characters
        for (int w = 0; w < 300; w++) {
            t.layout(note, 13, 400, 300 + w);
        }
        Check.isTrue(t.cachedGlyphs() <= 2 * 100_000 + note.length(), "glyphs kept: " + t.cachedGlyphs());
    }

    public void testCachedLayoutsComeBack() {
        TextEngine t = new TextEngine("zh-Hans");
        TextLayout a = t.layout("同一段", 13, 400, 100);
        Check.isTrue(a == t.layout("同一段", 13, 400, 100), "the same layout object for the same parameters");
        Check.isTrue(a != t.layout("同一段", 13, 700, 100), "another weight is another layout");
    }

    public void testShadeIsLuminanceInQuarters() {
        Check.equal(0, TextEngine.shadeOf(0x111827FF));
        Check.equal(3, TextEngine.shadeOf(0xFFFFFFFF)); // at most 3/4
    }

    public void testAFallbackFontIsOneFontAcrossLayouts() { // M3 hand check: glyphs missing deep in the files demo
        TextEngine t = new TextEngine("zh-Hans");
        TextLayout a = t.layout("笔记 0001 · 周报", 13, 400, 0), b = t.layout("笔记 0002 · 日记", 13, 400, 0);
        Check.isTrue(a.font(0) == b.font(0), "笔 has one font, not one per layout: " + a.font(0) + " vs " + b.font(0));
    }

    public void testManyTitlesFromFewWordsDoNotKeepAddingGlyphs() {
        String[] words = {"会议纪要", "产品规划", "读书笔记", "周报", "日记", "灵感", "Java", "Metal"};
        TextEngine t = new TextEngine("zh-Hans");
        GlyphAtlas atlas = new GlyphAtlas(1024, 1024);
        jui.render.Scene scene = new jui.render.Scene();
        for (int i = 0; i < 1000; i++) {
            scene.begin(2, t, atlas);
            scene.text("笔记 " + (i % 10) + " · " + words[i % words.length] + " " + words[(i / 8) % words.length], 16, 0, 13, 400, 0x18181BFF, 0);
        }
        // About 40 glyphs, in at most 2 positions within a pixel: a few hundred at most, however many titles.
        Check.isTrue(atlas.glyphs() < 300, "the atlas keeps adding glyphs it has: " + atlas.glyphs());
    }
}
