package jui.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

/**
 * The Win32 API through FFM: DLL symbol lookup, as ObjC wraps macOS's
 * frameworks. COM vtables are called by index (see the platform layer),
 * with the object as the first argument.
 */
public final class Win32FFI {
    private Win32FFI() {}

    /** The named export of {@code dll} (its name with or without .dll). */
    public static MemorySegment symbol(String dll, String name) {
        String library = dll.endsWith(".dll") ? dll : dll + ".dll";
        return SymbolLookup.libraryLookup(library, Arena.global())
                .find(name).orElseThrow(() -> new UnsatisfiedLinkError(library + "!" + name));
    }
}
