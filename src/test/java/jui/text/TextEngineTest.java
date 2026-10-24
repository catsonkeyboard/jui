package jui.text;

import jui.testing.Check;

public final class TextEngineTest {
    public void testPositionsFollowCoreGraphicsQuantization() {
        Check.equal(2, TextEngine.positions(16, 2));  // 32 px: ceil(100/96) = 2
        Check.equal(1, TextEngine.positions(17, 2));  // 34 px: one position from 34 px an em
        Check.equal(5, TextEngine.positions(6, 1));   // 6 px: ceil(100/18) = 6, at most 5
    }

    public void testShadeIsLuminanceInQuarters() {
        Check.equal(0, TextEngine.shadeOf(0x111827FF));
        Check.equal(3, TextEngine.shadeOf(0xFFFFFFFF)); // at most 3/4
    }
}
