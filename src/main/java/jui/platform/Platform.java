package jui.platform;

/** Which desktop the process runs on, picking the platform layer to build on. */
public final class Platform {
    private Platform() {}

    /** Whether this is Windows, with its Win32, Direct3D and DirectWrite stack. */
    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
