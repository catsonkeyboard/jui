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
}
