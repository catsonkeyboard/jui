package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ThemeTest {
    public void testTheThemeFollowsTheAppearance() {
        boolean[] dark = {false};
        Tester tt = Tester.of(c -> { dark[0] = c.theme().isDark(); text(c, "字"); }, 200, 100);
        tt.frame();
        Check.isTrue(!dark[0], "light by default");
        tt.setDark(true);
        Check.isTrue(dark[0], "dark after the switch");
        float[] glyph = tt.scene().instance(0);
        Check.near(0xF4 / 255.0, glyph[8], 0.01);     // the dark theme's text, #f4f4f5
    }

    public void testSettingTheSameAppearanceAsksForNoFrame() {
        Tester tt = Tester.of(c -> text(c, "x"), 100, 100);
        tt.frame();
        tt.engine().setDark(false);
        Check.isTrue(!tt.framePending(), "no change, no frame");
    }
}
