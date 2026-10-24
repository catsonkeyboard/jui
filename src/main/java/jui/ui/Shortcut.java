package jui.ui;

/**
 * A key with the modifiers held, which must match exactly: Cmd+Shift+P is
 * not Cmd+P. Make shortcuts once, as constants, rather than in every frame.
 */
public record Shortcut(int mods, Key key) {

    /** The key alone. */
    public static Shortcut of(Key key) {
        return new Shortcut(0, key);
    }

    /** The key with these {@link Mod} bits. */
    public static Shortcut of(int mods, Key key) {
        return new Shortcut(mods, key);
    }

    /** Command and the key. */
    public static Shortcut cmd(Key key) {
        return new Shortcut(Mod.CMD, key);
    }

    public boolean matches(int mods, Key key) {
        return this.mods == mods && this.key == key;
    }
}
