package jui.platform.mac;

import jui.ui.Key;
import jui.ui.Mod;

/**
 * Keys and modifiers of AppKit's events, as MyGO's internal/darwin maps them
 * (surface.go): a table of virtual key codes for the keys that type nothing,
 * then the character typed without modifiers.
 */
public final class MacKeys {
    private MacKeys() {}

    private static final Key[] F_KEYS = {
        Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12
    };
    private static final int[] F_CODES = {0x7A, 0x78, 0x63, 0x76, 0x60, 0x61, 0x62, 0x64, 0x65, 0x6D, 0x67, 0x6F};

    /** The key of an event's {@code keyCode} and {@code charactersIgnoringModifiers}. */
    public static Key key(int keyCode, String chars) {
        switch (keyCode) {
            case 0x24, 0x4C: return Key.ENTER;
            case 0x30: return Key.TAB;
            case 0x31: return Key.SPACE;
            case 0x33: return Key.BACKSPACE;
            case 0x35: return Key.ESCAPE;
            case 0x75: return Key.DELETE;
            case 0x72: return Key.INSERT;
            case 0x73: return Key.HOME;
            case 0x77: return Key.END;
            case 0x74: return Key.PAGE_UP;
            case 0x79: return Key.PAGE_DOWN;
            case 0x7B: return Key.LEFT;
            case 0x7C: return Key.RIGHT;
            case 0x7D: return Key.DOWN;
            case 0x7E: return Key.UP;
            case 0x6E: return Key.CONTEXT_MENU;
            default: break;
        }
        for (int i = 0; i < F_CODES.length; i++) {
            if (F_CODES[i] == keyCode) {
                return F_KEYS[i];
            }
        }
        if (chars == null || chars.isEmpty()) {
            return Key.UNKNOWN;
        }
        char ch = chars.charAt(0);
        if (ch >= 'a' && ch <= 'z') {
            return Key.values()[Key.A.ordinal() + ch - 'a'];
        }
        if (ch >= 'A' && ch <= 'Z') {
            return Key.values()[Key.A.ordinal() + ch - 'A'];
        }
        if (ch >= '0' && ch <= '9') {
            return Key.values()[Key.DIGIT0.ordinal() + ch - '0'];
        }
        return switch (ch) {
            case '-' -> Key.MINUS;
            case '=' -> Key.EQUAL;
            case ',' -> Key.COMMA;
            case '.' -> Key.PERIOD;
            case '/' -> Key.SLASH;
            case ';' -> Key.SEMICOLON;
            case '\'' -> Key.QUOTE;
            case '[' -> Key.LEFT_BRACKET;
            case ']' -> Key.RIGHT_BRACKET;
            case '\\' -> Key.BACKSLASH;
            case '`' -> Key.BACKQUOTE;
            case ' ' -> Key.SPACE;
            default -> Key.UNKNOWN;
        };
    }

    /** The {@link Mod} bits of an event's {@code modifierFlags}; Caps Lock and Fn are ignored. */
    public static int mods(long modifierFlags) {
        int mods = 0;
        if ((modifierFlags & (1L << 17)) != 0) {
            mods |= Mod.SHIFT;
        }
        if ((modifierFlags & (1L << 18)) != 0) {
            mods |= Mod.CTRL;
        }
        if ((modifierFlags & (1L << 19)) != 0) {
            mods |= Mod.ALT;
        }
        if ((modifierFlags & (1L << 20)) != 0) {
            mods |= Mod.CMD;
        }
        return mods;
    }
}
