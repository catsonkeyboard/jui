package jui.ui;

import jui.testing.Check;

public final class ValuesTest {
    public void testColorPacksRgba() {
        Check.equal(0x2563EBFF, Color.rgb(0x2563EB).rgba());
        Check.equal(0x2563EB80, Color.rgba(0x2563EB, 0.5f).rgba()); // round(127.5) = 128
        Check.isTrue(Color.TRANSPARENT.transparent(), "transparent");
    }

    public void testLightTheme() { // MyGo's LightTheme (ui/theme.go)
        Theme t = Theme.light();
        Check.equal(0xFFFFFFFF, t.background().rgba());
        Check.equal(0xF4F4F5FF, t.surface().rgba());
        Check.equal(0xE9E9ECFF, t.surfaceHover().rgba());
        Check.equal(0xDDDDE1FF, t.surfacePressed().rgba());
        Check.equal(0xD9D9DEFF, t.border().rgba());
        Check.equal(0x18181BFF, t.text().rgba());
        Check.equal(0x71717AFF, t.textMuted().rgba());
        Check.equal(0x2563EBFF, t.accent().rgba());
        Check.equal(0x1D4ED8FF, t.accentHover().rgba());
        Check.equal(0x1E40AFFF, t.accentPressed().rgba());
        Check.equal(0xFFFFFFFF, t.accentText().rgba());
        Check.equal(Color.rgba(0x2563EB, 0.25f), t.selection());
        Check.equal(Color.rgba(0x2563EB, 0.55f), t.focusRing());
        Check.near(13, t.fontSize());
        Check.near(6, t.radius());
    }

    public void testShortcutMatchesExactModifiers() {
        Shortcut s = Shortcut.cmd(Key.P);
        Check.isTrue(s.matches(Mod.CMD, Key.P), "cmd-P");
        Check.isTrue(!s.matches(Mod.CMD | Mod.SHIFT, Key.P), "extra shift does not match");
        Check.isTrue(!s.matches(0, Key.P), "no cmd does not match");
        Check.equal(new Shortcut(0, Key.UP), Shortcut.of(Key.UP));
    }

    public void testColorMix() {
        Check.equal(Color.rgb(0x808080).rgba(), Color.rgb(0x000000).mix(Color.rgb(0xFFFFFF), 0.5f).rgba() & 0xFEFEFEFF);
        Check.equal(Color.rgb(0x123456), Color.rgb(0x123456).mix(Color.rgb(0xFFFFFF), 0));
        Check.equal(Color.rgb(0xFFFFFF), Color.rgb(0x123456).mix(Color.rgb(0xFFFFFF), 7));   // t is clamped
    }

    public void testThemesFollowMyGo() {
        Check.isTrue(!Theme.light().isDark() && Theme.dark().isDark(), "dark flag");
        Check.equal(Color.rgb(0x2563EB), Theme.light().accent());
        Check.equal(Color.rgb(0x18181B), Theme.dark().background());
        Check.equal(Color.rgb(0x60A5FA), Theme.dark().accentHover());
        Check.equal(Theme.dark(), Theme.of(true));
        Check.equal(Theme.light(), Theme.of(false));
    }

    public void testRefsHoldTheirValue() {
        DoubleRef d = new DoubleRef(1.5); d.set(2.5); Check.near(2.5, d.get());
        IntRef i = new IntRef(1); i.set(2); Check.equal(2, i.get());
        FloatRef f = new FloatRef(1); f.set(3); Check.near(3, f.get());
        Ref<String> r = new Ref<>("a"); r.set("b"); Check.equal("b", r.get());
    }

    public void testThemesHaveMyGosScrollbar() {
        Check.equal(Color.rgba(0x000000, 0.32f), Theme.light().scrollbar());
        Check.equal(Color.rgba(0xFFFFFF, 0.35f), Theme.dark().scrollbar());
        Check.near(6, Theme.light().scrollbarWidth());
    }
}
