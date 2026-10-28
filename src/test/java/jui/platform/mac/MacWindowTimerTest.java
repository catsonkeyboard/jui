package jui.platform.mac;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.function.BooleanSupplier;
import jui.ffi.Native;
import jui.ffi.ObjC;
import jui.testing.Check;
import static java.lang.foreign.ValueLayout.*;

public final class MacWindowTimerTest {
    private static final SymbolLookup CF = ObjC.framework("CoreFoundation");
    private static final MethodHandle RUN = Linker.nativeLinker().downcallHandle(
            CF.find("CFRunLoopRunInMode").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_BOOLEAN));

    private static void tracking(double seconds, BooleanSupplier done) throws Throwable {
        long mode = ObjC.nsString("NSEventTrackingRunLoopMode");
        Native.call(CF.find("CFRunLoopAddCommonMode").orElseThrow(), Native.call(CF.find("CFRunLoopGetMain").orElseThrow()), mode);
        MemorySegment address = MemorySegment.ofAddress(mode);
        long end = System.nanoTime() + (long) (seconds * 1e9);
        while (System.nanoTime() < end && !done.getAsBoolean()) {
            int ignored = (int) RUN.invokeExact(address, 0.01, true);
        }
    }

    public void testTimerWakesFramesInTrackingModeAndCanBeCancelled() throws Throwable {
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        MacWindow window = MacWindow.open("tracking timer", 200, 150, 0xFFFFFF, null, (scene, width, height) -> {});
        long pool = ObjC.poolPush();
        try {
            MacWindowFrameTest.pump(1, () -> window.frames > 0);
            MacWindowFrameTest.pump(0.3, () -> false);
            int before = window.frames;
            window.armTimer(500_000_000L);
            window.armTimer(30_000_000L);
            tracking(0.3, () -> window.frames > before);
            Check.isTrue(window.frames > before, "replacement timer draws while menus track");
            MacWindowFrameTest.pump(0.3, () -> false);
            int idle = window.frames;
            window.armTimer(30_000_000L);
            window.cancelTimer();
            tracking(0.1, () -> false);
            Check.equal(idle, window.frames);
        } finally {
            window.close();
            ObjC.poolPop(pool);
        }
    }
}
