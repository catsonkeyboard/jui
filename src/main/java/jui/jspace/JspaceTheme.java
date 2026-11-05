package jui.jspace;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Theme;

/** QSpace's own light and dark palettes, plus the app colors the Theme record has no role for. */
final class JspaceTheme {
    static final Theme LIGHT = new Theme(false,
            Color.rgb(0xFFFFFF), Color.rgb(0xF7F7F9), Color.rgb(0xEDEDF0), Color.rgb(0xE3E3E7),
            Color.rgb(0xD9D9DE), Color.rgb(0x1D1D1F), Color.rgb(0x6E6E73),
            Color.rgb(0x0A60FF), Color.rgb(0x3B7DFF), Color.rgb(0x0849CC), Color.rgb(0xFFFFFF),
            Color.rgba(0x0A60FF, 0.15f), Color.rgba(0x0A60FF, 0.55f), 13, 6,
            Color.rgba(0x000000, 0.25f), 6);

    static final Theme DARK = new Theme(true,
            Color.rgb(0x232326), Color.rgb(0x2E2E32), Color.rgb(0x38383D), Color.rgb(0x414146),
            Color.rgb(0x3F3F46), Color.rgb(0xE5E5E7), Color.rgb(0x98989D),
            Color.rgb(0x0A84FF), Color.rgb(0x409CFF), Color.rgb(0x0875D1), Color.rgb(0xFFFFFF),
            Color.rgba(0x0A84FF, 0.35f), Color.rgba(0x0A84FF, 0.6f), 13, 6,
            Color.rgba(0xFFFFFF, 0.30f), 6);

    /** Colors beyond the Theme record's roles, per appearance. */
    record Palette(Color sidebar, Color sidebarHover, Color pill, Color pillSel,
                   Color bar, Color panel, Color warn, Color zebra) {}

    private static final Palette LIGHT_X = new Palette(Color.rgb(0xF2F2F4), Color.rgb(0xE6E6EA),
            Color.rgb(0xEDEDF0), Color.rgb(0xD9E4FF), Color.rgb(0xF7F7F9), Color.rgb(0xFFFFFF),
            Color.rgb(0xC66100), Color.rgb(0xF7F7F9));

    private static final Palette DARK_X = new Palette(Color.rgb(0x1A1A1C), Color.rgb(0x2C2C30),
            Color.rgb(0x38383D), Color.rgb(0x2E4A66), Color.rgb(0x2E2E32), Color.rgb(0x232326),
            Color.rgb(0xFFB340), Color.rgb(0x27272B));

    static Palette palette(Context c) {
        return c.theme().isDark() ? DARK_X : LIGHT_X;
    }

    /** The pair a window installs for an appearance choice: FOLLOW keeps both of ours, a forced
     *  choice pins the one look. */
    static Theme[] pair(jui.jspace.store.Settings.Appearance a) {
        return switch (a) {
            case FOLLOW -> new Theme[] { LIGHT, DARK };
            case LIGHT -> new Theme[] { LIGHT, LIGHT };
            case DARK -> new Theme[] { DARK, DARK };
        };
    }

    private JspaceTheme() {}
}
