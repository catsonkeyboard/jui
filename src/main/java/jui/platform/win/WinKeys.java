package jui.platform.win;

import jui.ui.Key;
import jui.ui.Mod;

/**
 * Keys and modifiers of Win32's messages, as MacKeys maps AppKit's: a
 * table of virtual key codes for the keys that type nothing, then the
 * character typed without modifiers. Windows reports Ctrl where macOS
 * reports Command and vice versa, as MyGO's surface.go relabels them.
 */
public final class WinKeys {
    private WinKeys() {}

    /** The key of a WM_KEYDOWN {@code wParam} (a virtual key code). */
    public static Key key(int vk, char ch) {
        switch (vk) {
            case 0x0D: return Key.ENTER;
            case 0x09: return Key.TAB;
            case 0x20: return Key.SPACE;
            case 0x08: return Key.BACKSPACE;
            case 0x1B: return Key.ESCAPE;
            case 0x2E: return Key.DELETE;
            case 0x2D: return Key.INSERT;
            case 0x24: return Key.HOME;
            case 0x23: return Key.END;
            case 0x21: return Key.PAGE_UP;
            case 0x22: return Key.PAGE_DOWN;
            case 0x25: return Key.LEFT;
            case 0x27: return Key.RIGHT;
            case 0x28: return Key.DOWN;
            case 0x26: return Key.UP;
            case 0x5D: return Key.CONTEXT_MENU;
            default: break;
        }
        if (vk >= 0x70 && vk <= 0x7B) {
            return Key.values()[Key.F1.ordinal() + vk - 0x70];
        }
        if (vk >= 'A' && vk <= 'Z') {
            return Key.values()[Key.A.ordinal() + vk - 'A'];
        }
        if (vk >= '0' && vk <= '9') {
            return Key.values()[Key.DIGIT0.ordinal() + vk - '0'];
        }
        // The OEM keys: by their unmodified character, as MacKeys reads AppKit's.
        switch (ch) {
            case '-': return Key.MINUS;
            case '=': return Key.EQUAL;
            case ',': return Key.COMMA;
            case '.': return Key.PERIOD;
            case '/': return Key.SLASH;
            case ';': return Key.SEMICOLON;
            case '\'': return Key.QUOTE;
            case '[': return Key.LEFT_BRACKET;
            case ']': return Key.RIGHT_BRACKET;
            case '\\': return Key.BACKSLASH;
            case '`': return Key.BACKQUOTE;
            default: return Key.UNKNOWN;
        }
    }

    /** The {@link Mod} bits of GetAsyncKeyState's present modifiers; Caps Lock is ignored. */
    public static int mods() {
        int mods = 0;
        if (pressed(0x10)) mods |= Mod.SHIFT;
        // Windows' Ctrl is the engine's Ctrl; its left Windows key maps to CMD,
        // so app shortcuts written as CMD (⌘ on macOS) still have a key.
        if (pressed(0x11)) mods |= Mod.CTRL;
        if (pressed(0x12)) mods |= Mod.ALT;
        if (pressed(0x5B) || pressed(0x5C)) mods |= Mod.CMD;
        return mods;
    }

    private static boolean pressed(int vk) {
        return (GetAsyncKeyState(vk) & 0x8000) != 0;
    }

    private static final java.lang.foreign.MemorySegment GET_ASYNC_KEY_STATE = jui.ffi.Win32FFI.symbol("user32", "GetAsyncKeyState");
    private static final java.lang.invoke.MethodHandle ASYNC = handle();

    private static java.lang.invoke.MethodHandle handle() {
        try {
            return java.lang.foreign.Linker.nativeLinker().downcallHandle(
                    java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_SHORT,
                            java.lang.foreign.ValueLayout.JAVA_INT));
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    private static short GetAsyncKeyState(int vk) {
        try {
            return (short) ASYNC.invokeExact(GET_ASYNC_KEY_STATE, vk);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
