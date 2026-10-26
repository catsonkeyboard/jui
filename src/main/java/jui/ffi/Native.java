package jui.ffi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/**
 * One downcall handle per signature in {@link Sigs}, bound to no function:
 * the function's address is the handle's first argument.
 *
 * <p>Holding no native address, this class is initialized at build time
 * (build.sh passes --initialize-at-build-time), so native-image sees the
 * handles as constants and compiles calls through them to direct calls:
 * about 6 ns a call. A handle in a class initialized at run time goes
 * through the generic method handle path instead, some 1.7 µs a call. The
 * JVM's JIT inlines both.
 */
public final class Native {
    private Native() {}

    private static MethodHandle handle(FunctionDescriptor fd) {
        return Linker.nativeLinker().downcallHandle(fd);
    }

    static final MethodHandle W0 = handle(Sigs.W0);
    static final MethodHandle W1 = handle(Sigs.W1);
    static final MethodHandle W2 = handle(Sigs.W2);
    static final MethodHandle W3 = handle(Sigs.W3);
    static final MethodHandle W4 = handle(Sigs.W4);
    static final MethodHandle W5 = handle(Sigs.W5);
    static final MethodHandle W6 = handle(Sigs.W6);
    static final MethodHandle W7 = handle(Sigs.W7);

    public static final MethodHandle INT0 = handle(Sigs.INT0);
    public static final MethodHandle SYSCTL = handle(Sigs.SYSCTL);
    public static final MethodHandle SUGGEST_LINE_BREAK = handle(Sigs.SUGGEST_LINE_BREAK);
    public static final MethodHandle CREATE_LINE = handle(Sigs.CREATE_LINE);
    public static final MethodHandle RUN_GET = handle(Sigs.RUN_GET);
    public static final MethodHandle LINE_BOUNDS = handle(Sigs.LINE_BOUNDS);
    public static final MethodHandle GLYPH_RECTS = handle(Sigs.GLYPH_RECTS);
    public static final MethodHandle SET_FILL_RGBA = handle(Sigs.SET_FILL_RGBA);
    public static final MethodHandle SCALE_CTM = handle(Sigs.SCALE_CTM);

    // objc_msgSend's typed shapes.
    static final MethodHandle RECT_OF = handle(Sigs.RECT_OF);
    static final MethodHandle DOUBLE_OF = handle(Sigs.DOUBLE_OF);
    static final MethodHandle SET_DOUBLE = handle(Sigs.SET_DOUBLE);
    static final MethodHandle DOUBLE2_ID = handle(Sigs.DOUBLE2_ID);
    static final MethodHandle SET_SIZE = handle(Sigs.SET_SIZE);
    static final MethodHandle POINT_OF = handle(Sigs.POINT_OF);
    static final MethodHandle CONVERT_POINT = handle(Sigs.CONVERT_POINT);
    static final MethodHandle INIT_TRACKING_AREA = handle(Sigs.INIT_TRACKING_AREA);
    static final MethodHandle SET_RECT = handle(Sigs.SET_RECT);
    static final MethodHandle INIT_RECT = handle(Sigs.INIT_RECT);
    static final MethodHandle INIT_WINDOW = handle(Sigs.INIT_WINDOW);
    static final MethodHandle SET_CLEAR_COLOR = handle(Sigs.SET_CLEAR_COLOR);
    static final MethodHandle PERFORM_AFTER = handle(Sigs.PERFORM_AFTER);
    static final MethodHandle REPLACE_REGION = handle(Sigs.REPLACE_REGION);
    static final MethodHandle SET_SCISSOR = handle(Sigs.SET_SCISSOR);
    static final MethodHandle RECT_RECT_W = handle(Sigs.RECT_RECT_W);
    static final MethodHandle RECT_RECT = handle(Sigs.RECT_RECT);
    static final MethodHandle MSG_RANGE = handle(Sigs.MSG_RANGE);
    static final MethodHandle MSG_V_W_RANGE = handle(Sigs.MSG_V_W_RANGE);
    static final MethodHandle MSG_V_W_RANGE2 = handle(Sigs.MSG_V_W_RANGE2);
    static final MethodHandle MSG_W_RANGE_W = handle(Sigs.MSG_W_RANGE_W);
    static final MethodHandle MSG_RECT_RANGE_W = handle(Sigs.MSG_RECT_RANGE_W);
    static final MethodHandle MSG_W_POINT = handle(Sigs.MSG_W_POINT);

    // Calls of functions whose arguments and result are words.

    public static long call(MemorySegment fn) {
        try {
            return (long) W0.invokeExact(fn);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long call(MemorySegment fn, long a) {
        try {
            return (long) W1.invokeExact(fn, a);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long call(MemorySegment fn, long a, long b) {
        try {
            return (long) W2.invokeExact(fn, a, b);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long call(MemorySegment fn, long a, long b, long c) {
        try {
            return (long) W3.invokeExact(fn, a, b, c);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long call(MemorySegment fn, long a, long b, long c, long d) {
        try {
            return (long) W4.invokeExact(fn, a, b, c, d);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long call(MemorySegment fn, long a, long b, long c, long d, long e) {
        try {
            return (long) W5.invokeExact(fn, a, b, c, d, e);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long call(MemorySegment fn, long a, long b, long c, long d, long e, long f) {
        try {
            return (long) W6.invokeExact(fn, a, b, c, d, e, f);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long call(MemorySegment fn, long a, long b, long c, long d, long e, long f, long g) {
        try {
            return (long) W7.invokeExact(fn, a, b, c, d, e, f, g);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException r) {
            return r;
        }
        if (t instanceof Error e) {
            throw e;
        }
        return new RuntimeException(t);
    }
}
