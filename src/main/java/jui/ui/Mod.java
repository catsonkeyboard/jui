package jui.ui;

/** Modifier keys, as bits to combine with |: {@code Mod.CMD | Mod.SHIFT}. */
public final class Mod {
    private Mod() {}

    public static final int SHIFT = 1;
    public static final int CTRL = 2;
    /** Option on macOS. */
    public static final int ALT = 4;
    /** Command on macOS. */
    public static final int CMD = 8;
}
