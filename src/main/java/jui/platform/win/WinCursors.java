package jui.platform.win;

import jui.ffi.Win32FFI;
import jui.ui.Cursor;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** The system cursors for the pointer shapes, by their LoadCursor resources. */
public final class WinCursors {
    private WinCursors() {}

    private static final MemorySegment LOAD_CURSOR_W = Win32FFI.symbol("user32", "LoadCursorW");
    private static final MemorySegment SET_CURSOR = Win32FFI.symbol("user32", "SetCursor");

    private static final java.lang.invoke.MethodHandle LOAD = handle(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle SET = handle(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

    private static java.lang.invoke.MethodHandle handle(FunctionDescriptor d) {
        try {
            return java.lang.foreign.Linker.nativeLinker().downcallHandle(d);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    /** The standard cursor of a shape, by its resource id (0x7F00 offset). */
    public static long of(Cursor c) {
        int id = switch (c) {
            case DEFAULT -> 32512; // IDC_ARROW
            case POINTER -> 32649; // IDC_HAND
            case TEXT -> 32513; // IDC_IBEAM
            case RESIZE_EW -> 32644; // IDC_SIZEWE
            case RESIZE_NS -> 32645; // IDC_SIZENS
            case NOT_ALLOWED -> 32648; // IDC_NO
            case CROSSHAIR -> 32515; // IDC_CROSS
            case GRAB, GRABBING -> 32649; // IDC_HAND: Windows has no open/closed pair
        };
        try {
            return (long) LOAD.invokeExact(LOAD_CURSOR_W, 0L, (long) id);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** Sets the pointer's shape, as WM_SETCURSOR's default would. */
    public static void set(long cursor) {
        try {
            long previous = (long) SET.invokeExact(SET_CURSOR, cursor);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
