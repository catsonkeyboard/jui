package jui.platform.mac;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.function.BooleanSupplier;

import jui.ffi.ObjC;
import jui.testing.Check;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** Frames on demand through the run loop: setNeedsDisplay, the display link and timers. */
public final class MacWindowFrameTest {
    private static final SymbolLookup CF = SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());
    private static final MethodHandle RUN_IN_MODE = Linker.nativeLinker().downcallHandle(
            CF.find("CFRunLoopRunInMode").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_BOOLEAN));
    private static final MemorySegment DEFAULT_MODE =
            CF.find("kCFRunLoopDefaultMode").orElseThrow().reinterpret(8).get(ADDRESS, 0);

    /** Runs the main run loop for up to seconds, until done says so. */
    static void pump(double seconds, BooleanSupplier done) {
        long end = System.nanoTime() + (long) (seconds * 1e9);
        while (System.nanoTime() < end && !done.getAsBoolean()) {
            try {
                int ignored = (int) RUN_IN_MODE.invokeExact(DEFAULT_MODE, 0.01, true);
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }
    }

    public void testFramesComeOnRequestAndTheLinkPausesWhenIdle() {
        long app = ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        ObjC.send(app, "finishLaunching");
        int[] painted = {0};
        MacWindow w = MacWindow.open("frames", 200, 150, 0xFFFFFF, null, (scene, width, height) -> painted[0]++);
        try {
            pump(2, () -> w.frames > 0);                       // the first frame, from opening
            Check.isTrue(w.frames > 0, "the window drew its first frame");
            pump(0.1, () -> false);
            int before = w.frames;
            for (int i = 0; i < 3; i++) {
                w.requestFrame();                              // asked for together: one frame
            }
            pump(0.5, () -> w.frames > before);
            Check.isTrue(w.frames > before, "a requested frame was drawn");
            Check.isTrue(w.frames <= before + 2, "requests are paced, not one frame each: " + (w.frames - before));
            pump(0.3, () -> false);                            // idle
            Check.isTrue(!w.displayLinkRunning(), "the link pauses when no frame is asked for");
            int idle = w.frames;
            w.armTimer(50_000_000L);
            pump(1, () -> w.frames > idle);
            Check.isTrue(w.frames > idle, "the timer drew a frame");
        } finally {
            w.close();
        }
    }
}
