package jui.platform.mac;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import jui.ffi.ObjC;
import jui.testing.Check;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** What a window lets go of when it closes. */
public final class MacWindowCloseTest {
    private static final SymbolLookup OBJC = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global());
    private static final MethodHandle INIT_WEAK = Linker.nativeLinker().downcallHandle(
            OBJC.find("objc_initWeak").orElseThrow(), FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG));
    private static final MethodHandle LOAD_WEAK_RETAINED = Linker.nativeLinker().downcallHandle(
            OBJC.find("objc_loadWeakRetained").orElseThrow(), FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    private static final MethodHandle DESTROY_WEAK = Linker.nativeLinker().downcallHandle(
            OBJC.find("objc_destroyWeak").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));

    public void testClosingReleasesTheWindowDelegate() throws Throwable { // M2 final review minor
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment weak = arena.allocate(JAVA_LONG);
            long pool = ObjC.poolPush();
            try {
                MacWindow w = MacWindow.open("close", 200, 150, 0xFFFFFF, null, (s, width, height) -> {});
                long ignored = (long) INIT_WEAK.invokeExact(weak, w.delegate());
                w.close();
            } finally {
                ObjC.poolPop(pool);
            }
            long alive = (long) LOAD_WEAK_RETAINED.invokeExact(weak);
            ObjC.release(alive);
            DESTROY_WEAK.invokeExact(weak);
            Check.equal(0L, alive);
        }
    }
}
