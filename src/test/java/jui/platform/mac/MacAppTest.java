package jui.platform.mac;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import jui.ffi.Libc;
import jui.testing.Check;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public final class MacAppTest {
    // The main run loop, run by hand: the tests run on the main thread, without NSApp running.
    private static final SymbolLookup CF = SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());
    private static final MethodHandle RUN_IN_MODE = Linker.nativeLinker().downcallHandle(
            CF.find("CFRunLoopRunInMode").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_BOOLEAN));
    private static final MemorySegment DEFAULT_MODE =
            CF.find("kCFRunLoopDefaultMode").orElseThrow().reinterpret(8).get(ADDRESS, 0);

    private static void runLoopOnce() {
        try {
            int ignored = (int) RUN_IN_MODE.invokeExact(DEFAULT_MODE, 0.01, true);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    public void testPostRunsOnTheMainThread() throws InterruptedException { // deferred minor: invalidate from any thread
        int[] ranOnMain = {-1};
        Thread t = new Thread(() -> MacApp.post(() -> ranOnMain[0] = Libc.pthreadMainNp()));
        t.start();
        t.join();
        for (int i = 0; i < 200 && ranOnMain[0] == -1; i++) {
            runLoopOnce();
        }
        Check.equal(1, ranOnMain[0]);
    }
}
