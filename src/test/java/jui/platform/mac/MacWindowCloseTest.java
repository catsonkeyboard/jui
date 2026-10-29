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
    public void testSystemAndExplicitCloseNotifyOnlyOnce() {
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        MacWindowInputTest.Recorder recorder = new MacWindowInputTest.Recorder();
        MacWindow window = MacWindow.open("close notification", 200, 150, 0xFFFFFF, null, (scene, width, height) -> {}, recorder);
        ObjC.send(window.delegate(), "windowWillClose:", 0);
        window.armTimer(0);
        window.close();
        window.close();
        Check.equal(1, recorder.closed);
    }

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

    public void testAScrollStillArrivesAfterAnotherWindowClosed() throws Throwable { // the close() minor
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        MacWindow first = MacWindow.open("gone", 200, 150, 0xFFFFFF, null, (s, width, height) -> {});
        first.close();
        MacWindowInputTest.Recorder r = new MacWindowInputTest.Recorder();
        MacWindow w = MacWindow.open("after close", 200, 150, 0xFFFFFF, null, (s, width, height) -> {}, r);
        try {
            long pixels = (long) MacWindowInputTest.SCROLL_EVENT.invokeExact(0L, 0, 1, 10, 0, 0);
            ObjC.send(w.view(), "scrollWheel:", ObjC.send(ObjC.cls("NSEvent"), "eventWithCGEvent:", pixels));
            Check.equal(1, r.scrolls);
        } finally {
            w.close();
        }
    }

    public void testTheRefreshRateIsReadAgainWhenTheWindowChangesScreen() { // M3 final review minor
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        MacWindow w = MacWindow.open("screen", 200, 150, 0xFFFFFF, null, (s, width, height) -> {});
        try {
            Check.isTrue(ObjC.respondsTo(w.delegate(), "windowDidChangeScreen:"), "the delegate hears of a new screen");
            long screen = ObjC.send(w.window(), "screen");
            Check.isTrue(screen != 0, "the window is on a screen");
            w.refresh = 1;                                 // as read on another screen
            ObjC.send(w.delegate(), "windowDidChangeScreen:", 0);
            Check.equal(1_000_000_000L / Math.max(ObjC.send(screen, "maximumFramesPerSecond"), 30), w.refresh);
        } finally {
            w.close();
        }
    }
}
