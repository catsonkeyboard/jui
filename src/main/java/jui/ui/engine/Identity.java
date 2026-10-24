package jui.ui.engine;

/**
 * Element identities: a parent's identity mixed with the child's position
 * among its siblings, or with its key. Positions and keys mix with different
 * salts, so the child at position 3 and the child keyed 3 differ.
 */
public final class Identity {
    private Identity() {}

    /** The window's root. */
    public static final long ROOT = 0x6A75692D726F6F74L; // "jui-root"

    private static final long POSITION_SALT = 0x9E3779B97F4A7C15L;
    private static final long KEY_SALT = 0xC2B2AE3D27D4EB4FL;

    public static long child(long parentId, int position) {
        return mix(mix(parentId ^ POSITION_SALT) + position);
    }

    public static long keyed(long parentId, long keyHash) {
        return mix(mix(parentId ^ KEY_SALT) + keyHash);
    }

    /** 64-bit FNV-1a over a string's UTF-16 code units. */
    public static long hash(String s) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001B3L;
        }
        return h;
    }

    /** splitmix64's finalizer. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
