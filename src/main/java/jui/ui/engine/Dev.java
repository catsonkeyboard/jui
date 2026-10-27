package jui.ui.engine;

/**
 * Development mode: on the JVM, or with -Djui.dev=true, misuse of the API
 * throws; in a native image it is ignored, as in MyGO's production builds.
 */
public final class Dev {
    private Dev() {}

    public static final boolean ENABLED = Boolean.getBoolean("jui.dev")
            || System.getProperty("org.graalvm.nativeimage.imagecode") == null;

    public static void fail(String message) {
        if (ENABLED) {
            throw new IllegalStateException(message);
        }
    }

    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Use that works but costs: in development mode, says so once, on standard error. */
    public static void warn(String message) {
        if (ENABLED && WARNED.add(message)) {
            System.err.println("jui: warning: " + message);
        }
    }

    /** Whether this warning was given: for tests. */
    public static boolean warned(String message) {
        return WARNED.contains(message);
    }

    /** Forgets the warnings given: for tests. */
    public static void clearWarnings() {
        WARNED.clear();
    }
}
