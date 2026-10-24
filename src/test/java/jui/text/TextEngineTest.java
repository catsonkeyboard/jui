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
}
