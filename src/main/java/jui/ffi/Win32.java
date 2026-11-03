package jui.ffi;

import java.lang.foreign.SymbolLookup;

/**
 * Finds symbols in Windows's DLLs, as ObjC.framework does in macOS's
 * frameworks. The lookups stay alive as long as the class does.
 */
public final class Win32 {
    private Win32() {}

    /** The named symbol in {@code dll} (without its .dll), as a lookup into its exports. */
    public static java.lang.foreign.MemorySegment symbol(String dll, String name) {
        try {
            return SymbolLookup.libraryLookup(dll + ".dll", java.lang.foreign.Arena.global()).find(name)
                    .orElseThrow(() -> new UnsatisfiedLinkError(dll + ": " + name + " not found"));
        } catch (UnsatisfiedLinkError e) {
            throw e;
        }
    }
}
