package jui.ui;

import jui.testing.Check;

public final class ValuesTest {
    public void testColorPacksRgba() {
        Check.equal(0x2563EBFF, Color.rgb(0x2563EB).rgba());
        Check.equal(0x2563EB80, Color.rgba(0x2563EB, 0.5f).rgba()); // round(127.5) = 128
        Check.isTrue(Color.TRANSPARENT.transparent(), "transparent");
    }

    public void testLightTheme() {
        Theme t = Theme.light();
        Check.equal(0xFFFFFFFF, t.background().rgba());
        Check.equal(0xF3F4F6FF, t.surface().rgba());
        Check.equal(0xE5E7EBFF, t.surfaceHover().rgba());
        Check.equal(0xE5E7EBFF, t.border().rgba());
        Check.equal(0x111827FF, t.text().rgba());
        Check.equal(0x6B7280FF, t.textMuted().rgba());
        Check.equal(0x2563EBFF, t.accent().rgba());
        Check.equal(0xFFFFFFFF, t.accentText().rgba());
        Check.equal(0xBFDBFEFF, t.selection().rgba());
        Check.equal(0x3B82F6FF, t.focusRing().rgba());
        Check.near(13, t.fontSize());
        Check.near(6, t.radius());
    }
}
