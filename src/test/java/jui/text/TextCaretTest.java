package jui.text;

import jui.testing.Check;

public final class TextCaretTest {
    static final TextEngine T = new TextEngine("zh-Hans");

    public void testCaretPositionsGrowAcrossTheText() {
        TextLayout l = T.layout("ab中文 x", 13, 400, 0);
        Check.near(0, l.caretX(0));
        for (int i = 1; i <= l.length(); i++) {
            Check.isTrue(l.caretX(i) > l.caretX(i - 1), "caret " + i + " is right of caret " + (i - 1));
        }
        Check.near(l.width, l.caretX(l.length()));
    }

    public void testIndexAtFindsTheNearestCaretStop() {
        TextLayout l = T.layout("hello 世界", 13, 400, 0);
        for (int i = 0; i <= l.length(); i++) {
            Check.equal(i, l.indexAt(l.caretX(i)));
        }
        Check.equal(0, l.indexAt(-20));
        Check.equal(l.length(), l.indexAt(10_000));
        float mid = (l.caretX(1) + l.caretX(2)) / 2;
        Check.equal(1, l.indexAt(mid - 0.1f));
        Check.equal(2, l.indexAt(mid + 0.1f));
    }

    public void testACaretNeverLandsInsideAnEmoji() {
        TextLayout l = T.layout("a😀b", 13, 400, 0);
        for (float x = -1; x < l.width + 2; x += 0.25f) {
            Check.isTrue(l.indexAt(x) != 2, "no stop between the halves of a surrogate pair, at " + x);
        }
    }

    public void testAnEmptyLayoutHasOneCaretStop() {
        TextLayout l = T.layout("", 13, 400, 0);
        Check.equal(0, l.length());
        Check.near(0, l.caretX(0));
        Check.equal(0, l.indexAt(30));
    }

    public void testTheLineHeightFitsLatinAndHan() {
        float h = T.lineHeight(13, 400);
        Check.isTrue(h >= T.layout("Ag", 13, 400, 0).height - 0.01f, "fits Latin");
        Check.isTrue(h >= T.layout("中", 13, 400, 0).height - 0.01f, "fits Han");
    }
}
