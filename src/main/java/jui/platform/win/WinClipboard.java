package jui.platform.win;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import jui.ffi.Win32FFI;
import jui.platform.Clipboard;

/**
 * The Win32 clipboard's plain text (design §7.7: plain text only in this
 * phase), opened for the window's thread while each operation runs.
 */
public final class WinClipboard implements Clipboard {
    private static final long USER32 = 0;
    private static final MemorySegment OPEN_CLIPBOARD = Win32FFI.symbol("user32", "OpenClipboard");
    private static final MemorySegment CLOSE_CLIPBOARD = Win32FFI.symbol("user32", "CloseClipboard");
    private static final MemorySegment EMPTY_CLIPBOARD = Win32FFI.symbol("user32", "EmptyClipboard");
    private static final MemorySegment SET_CLIPBOARD_DATA = Win32FFI.symbol("user32", "SetClipboardData");
    private static final MemorySegment GET_CLIPBOARD_DATA = Win32FFI.symbol("user32", "GetClipboardData");
    private static final MemorySegment GLOBAL_ALLOC = Win32FFI.symbol("kernel32", "GlobalAlloc");
    private static final MemorySegment GLOBAL_LOCK = Win32FFI.symbol("kernel32", "GlobalLock");
    private static final MemorySegment GLOBAL_UNLOCK = Win32FFI.symbol("kernel32", "GlobalUnlock");
    private static final long CF_UNICODETEXT = 13;
    private static final long GMEM_MOVEABLE = 0x0002;

    private static final java.lang.invoke.MethodHandle INT1 = handle(
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle INT2 = handle(
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle LONG_LONG1 = handle(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle LONG_LONG2 = handle(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

    private static java.lang.invoke.MethodHandle handle(FunctionDescriptor d) {
        try {
            return java.lang.foreign.Linker.nativeLinker().downcallHandle(d);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    WinClipboard() {}

    /** The system clipboard. */
    public static WinClipboard general() {
        return new WinClipboard();
    }

    @Override
    public String read() {
        if (!open()) {
            return null;
        }
        try {
            long handle = invokeL2(GET_CLIPBOARD_DATA, CF_UNICODETEXT, 0);
            if (handle == 0) {
                return null;
            }
            long locked = invokeL1(GLOBAL_LOCK, handle);
            if (locked == 0) {
                return null;
            }
            try {
                MemorySegment text = MemorySegment.ofAddress(locked).reinterpret(Long.MAX_VALUE);
                long units = 0;
                while (text.get(ValueLayout.JAVA_CHAR, units * 2) != 0) {
                    units++;
                }
                byte[] bytes = new byte[(int) (units * 2)];
                for (long i = 0; i < units; i++) {
                    char c = text.get(ValueLayout.JAVA_CHAR, i * 2);
                    bytes[(int) (i * 2)] = (byte) c;
                    bytes[(int) (i * 2 + 1)] = (byte) (c >> 8);
                }
                return new String(bytes, StandardCharsets.UTF_16LE);
            } finally {
                invokeL1(GLOBAL_UNLOCK, handle);
            }
        } finally {
            close();
        }
    }

    @Override
    public void write(String s) {
        if (!open()) {
            return;
        }
        try {
            invokeI1(EMPTY_CLIPBOARD, 0);
            byte[] bytes = (s + "\0").getBytes(StandardCharsets.UTF_16LE);
            long handle = invokeL2(GLOBAL_ALLOC, GMEM_MOVEABLE, bytes.length);
            if (handle == 0) {
                return;
            }
            long locked = invokeL1(GLOBAL_LOCK, handle);
            if (locked != 0) {
                try {
                    MemorySegment.ofAddress(locked).reinterpret(bytes.length)
                            .copyFrom(MemorySegment.ofArray(bytes));
                } finally {
                    invokeL1(GLOBAL_UNLOCK, handle);
                }
                // The clipboard owns the memory once handed over.
                invokeL2(SET_CLIPBOARD_DATA, CF_UNICODETEXT, handle);
            }
        } finally {
            close();
        }
    }

    private boolean open() {
        // Retry as the clipboard may briefly belong to another window.
        for (int attempt = 0; attempt < 10; attempt++) {
            if (invokeI2(OPEN_CLIPBOARD, 0, 0) != 0) {
                return true;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void close() {
        invokeI1(CLOSE_CLIPBOARD, 0);
    }

    private static int invokeI1(MemorySegment fn, long a) {
        try {
            return (int) INT1.invokeExact(fn, a);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static int invokeI2(MemorySegment fn, long a, long b) {
        try {
            return (int) INT2.invokeExact(fn, a, b);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static long invokeL1(MemorySegment fn, long a) {
        try {
            return (long) LONG_LONG1.invokeExact(fn, a);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static long invokeL2(MemorySegment fn, long a, long b) {
        try {
            return (long) LONG_LONG2.invokeExact(fn, a, b);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
