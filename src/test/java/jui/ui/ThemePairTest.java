package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ThemePairTest {
    static Theme tinted(boolean dark) {
        return new Theme(dark,
                Color.rgb(0x232326), Color.rgb(0x2E2E32), Color.rgb(0x38383D), Color.rgb(0x414146),
                Color.rgb(0x3F3F46), Color.rgb(0xE5E5E7), Color.rgb(0x98989D),
                Color.rgb(0x0A84FF), Color.rgb(0x409CFF), Color.rgb(0x0875D1), Color.rgb(0xFFFFFF),
                Color.rgba(0x0A84FF, 0.35f), Color.rgba(0x0A84FF, 0.6f), 13, 6,
                Color.rgba(0xFFFFFF, 0.3f), 6);
    }

    public void testSetDarkPicksFromThePair() {
        Theme light = tinted(false), dark = tinted(true);
        Tester tt = Tester.of(c -> text(c, "x"), 50, 50);
        tt.engine().setThemePair(light, dark);
        Check.equal(light, tt.engine().theme());               // light to begin with
        tt.setDark(true);
        Check.equal(dark, tt.engine().theme());
        tt.setDark(false);
        Check.equal(light, tt.engine().theme());
    }

    public void testDefaultsAreTheBuiltInThemes() {
        Tester tt = Tester.of(c -> text(c, "x"), 50, 50);
        tt.setDark(true);
        Check.equal(Theme.dark(), tt.engine().theme());
    }
}
