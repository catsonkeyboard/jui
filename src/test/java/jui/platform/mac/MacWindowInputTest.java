package jui.platform.mac;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import jui.ffi.ObjC;
import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Mod;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/** AppKit events, made as AppKit makes them, through the view's overrides to the window's input. */
public final class MacWindowInputTest {
    private static final MemorySegment MSG_SEND = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global())
            .find("objc_msgSend").orElseThrow();
    private static final StructLayout POINT = MemoryLayout.structLayout(JAVA_DOUBLE.withName("x"), JAVA_DOUBLE.withName("y"));
    private static final MethodHandle MOUSE_EVENT = Linker.nativeLinker().downcallHandle(MSG_SEND,
            FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, POINT, JAVA_LONG, JAVA_DOUBLE, JAVA_LONG,
                    JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_FLOAT));
    private static final MethodHandle KEY_EVENT = Linker.nativeLinker().downcallHandle(MSG_SEND,
            FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, POINT, JAVA_LONG, JAVA_DOUBLE, JAVA_LONG,
                    JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_BOOLEAN, JAVA_SHORT));

    private static final MethodHandle SCROLL_EVENT = Linker.nativeLinker().downcallHandle(
            SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global())
                    .find("CGEventCreateScrollWheelEvent2").orElseThrow(),
            FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

    /** What the window told its input, as text. */
    static final class Recorder implements MacWindow.Input {
        final List<String> events = new ArrayList<>();

        public void pointerMove(double x, double y, int mods) { events.add("move " + x + " " + y); }
        public void pointerDown(double x, double y, int button, int clicks, int mods) {
            events.add("down " + x + " " + y + " " + button + " " + clicks + " " + mods);
        }
        public void pointerUp(double x, double y, int button, int mods) { events.add("up " + x + " " + y + " " + button); }
        public void pointerLeave() { events.add("leave"); }
        public void keyDown(Key key, int mods, boolean repeat) { events.add("key " + key + " " + mods + " " + repeat); }
        public void modifiersChanged(int mods) { events.add("mods " + mods); }
        public void windowFocusChanged(boolean focused) { events.add("focus " + focused); }
        public void appearanceChanged(boolean dark) { events.add("dark " + dark); }
        double scrollDy;
        boolean scrollPrecise;
        int scrolls;
        int closed;
        @Override public void windowClosed() { closed++; }
        @Override
        public void scroll(double x, double y, double dx, double dy, boolean precise, int mods) {
            scrollDy = dy;
            scrollPrecise = precise;
            scrolls++;
        }
    }

    public void testMouseAndKeyEventsReachTheInput() throws Throwable {
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        Recorder r = new Recorder();
        MacWindow w = MacWindow.open("input test", 200, 150, 0xFFFFFF, null, (scene, width, height) -> {}, r);
        long pool = ObjC.poolPush();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment at = arena.allocate(POINT);
            at.set(JAVA_DOUBLE, 0, 30);
            at.set(JAVA_DOUBLE, 8, 40);                                   // window coordinates: from the bottom left
            long down = (long) MOUSE_EVENT.invokeExact(ObjC.cls("NSEvent"),
                    ObjC.sel("mouseEventWithType:location:modifierFlags:timestamp:windowNumber:context:eventNumber:clickCount:pressure:"),
                    1L, at, 1L << 20, 0.0, w.windowNumber(), 0L, 0L, 2L, 1.0f);
            ObjC.send(w.view(), "mouseDown:", down);
            Check.isTrue(r.events.contains("down 30.0 110.0 0 2 " + Mod.CMD), r.events.toString());

            long tab = (long) KEY_EVENT.invokeExact(ObjC.cls("NSEvent"),
                    ObjC.sel("keyEventWithType:location:modifierFlags:timestamp:windowNumber:context:characters:charactersIgnoringModifiers:isARepeat:keyCode:"),
                    10L, at, 0L, 0.0, w.windowNumber(), 0L, ObjC.nsString("\t"), ObjC.nsString("\t"), false, (short) 0x30);
            ObjC.send(w.view(), "keyDown:", tab);
            Check.isTrue(r.events.contains("key TAB 0 false"), r.events.toString());
        } finally {
            ObjC.poolPop(pool);
            w.close();
        }
    }

    public void testControlClickWithoutReleaseDoesNotPoisonTheNextClick() throws Throwable {
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        Recorder recorder = new Recorder();
        MacWindow window = MacWindow.open("ctrl click", 200, 150, 0xFFFFFF, null, (scene, width, height) -> {}, recorder);
        long pool = ObjC.poolPush();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment point = arena.allocate(POINT);
            for (long flags : new long[] {1L << 18, 0}) {
                long event = (long) MOUSE_EVENT.invokeExact(ObjC.cls("NSEvent"),
                        ObjC.sel("mouseEventWithType:location:modifierFlags:timestamp:windowNumber:context:eventNumber:clickCount:pressure:"),
                        1L, point, flags, 0.0, window.windowNumber(), 0L, 0L, 1L, 1.0f);
                ObjC.send(window.view(), "mouseDown:", event);
            }
            Check.isTrue(recorder.events.contains("down 0.0 150.0 1 1 " + Mod.CTRL), recorder.events.toString());
            Check.isTrue(recorder.events.contains("down 0.0 150.0 0 1 0"), recorder.events.toString());
        } finally {
            ObjC.poolPop(pool);
            window.close();
        }
    }

    public void testScrollWheelEventsReachTheInput() throws Throwable {
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        Recorder r = new Recorder();
        MacWindow w = MacWindow.open("scroll test", 200, 150, 0xFFFFFF, null, (scene, width, height) -> {}, r);
        long pool = ObjC.poolPush();
        try {
            long pixels = (long) SCROLL_EVENT.invokeExact(0L, 0, 1, 10, 0, 0);      // pixel units: 10 up
            ObjC.send(w.view(), "scrollWheel:", ObjC.send(ObjC.cls("NSEvent"), "eventWithCGEvent:", pixels));
            Check.equal(1, r.scrolls);
            Check.isTrue(r.scrollPrecise, "pixel deltas are precise");
            Check.near(-10, r.scrollDy);                                          // up moves the content down
            long lines = (long) SCROLL_EVENT.invokeExact(0L, 1, 1, 1, 0, 0);       // line units: one notch up
            long event = ObjC.send(ObjC.cls("NSEvent"), "eventWithCGEvent:", lines);
            ObjC.send(w.view(), "scrollWheel:", event);
            Check.isTrue(!r.scrollPrecise, "line deltas are not precise");
            Check.isTrue(r.scrollDy < 0, "up moves the content down: " + r.scrollDy);
            Check.near(-40 * ObjC.sendDouble(event, "scrollingDeltaY"), r.scrollDy);
        } finally {
            ObjC.poolPop(pool);
            w.close();
        }
    }
}
