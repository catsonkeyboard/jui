package jui.platform.mac;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Mod;

public final class MacKeysTest {
    public void testFixedKeysByKeyCode() {
        Check.equal(Key.ENTER, MacKeys.key(0x24, "\r"));
        Check.equal(Key.ENTER, MacKeys.key(0x4C, "\u0003"));
        Check.equal(Key.TAB, MacKeys.key(0x30, "\t"));
        Check.equal(Key.LEFT, MacKeys.key(0x7B, ""));
        Check.equal(Key.F1, MacKeys.key(0x7A, ""));
        Check.equal(Key.F12, MacKeys.key(0x6F, ""));
    }

    public void testOtherKeysByTheirCharacter() {
        Check.equal(Key.P, MacKeys.key(0x23, "p"));
        Check.equal(Key.P, MacKeys.key(0x23, "P"));
        Check.equal(Key.DIGIT1, MacKeys.key(0x12, "1"));
        Check.equal(Key.SLASH, MacKeys.key(0x2C, "/"));
        Check.equal(Key.UNKNOWN, MacKeys.key(0x12, "!"));
        Check.equal(Key.UNKNOWN, MacKeys.key(0x00, ""));
    }

    public void testModifierFlags() {
        Check.equal(Mod.CMD | Mod.SHIFT, MacKeys.mods((1L << 20) | (1L << 17) | (1L << 16)));   // caps lock ignored
        Check.equal(Mod.CTRL | Mod.ALT, MacKeys.mods((1L << 18) | (1L << 19)));
    }
}
