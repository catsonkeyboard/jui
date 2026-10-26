package jui.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The Objective-C runtime through FFM, as MyGO's internal/darwin/objc.go does
 * it through purego: classes and selectors are looked up by name and cached,
 * and messages go through {@code objc_msgSend}, called with the exact
 * signature of each method (see {@link Native}).
 *
 * <p>Ids are {@code long}s. Calls that pass structs use preallocated argument
 * memory, so they belong on the main thread, as all AppKit calls do.
 */
public final class ObjC {
    private ObjC() {}

    public static final Linker LINKER = Linker.nativeLinker();
    private static final Arena LIBS = Arena.global();
    private static final SymbolLookup LIBOBJC = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", LIBS);
    public static final SymbolLookup LIBC = LINKER.defaultLookup();

    /** Loads a system framework, which also registers its classes. */
    public static SymbolLookup framework(String name) {
        return SymbolLookup.libraryLookup("/System/Library/Frameworks/" + name + ".framework/" + name, LIBS);
    }

    /** A function's address, to call through {@link Native}. */
    public static MemorySegment symbol(SymbolLookup lib, String name) {
        return lib.find(name).orElseThrow(() -> new UnsatisfiedLinkError("no symbol " + name));
    }

    private static final MemorySegment GET_CLASS = symbol(LIBOBJC, "objc_getClass");
    private static final MemorySegment REGISTER_NAME = symbol(LIBOBJC, "sel_registerName");
    private static final MemorySegment ALLOCATE_CLASS_PAIR = symbol(LIBOBJC, "objc_allocateClassPair");
    private static final MemorySegment REGISTER_CLASS_PAIR = symbol(LIBOBJC, "objc_registerClassPair");
    private static final MemorySegment ADD_METHOD = symbol(LIBOBJC, "class_addMethod");
    private static final MemorySegment GET_SUPERCLASS = symbol(LIBOBJC, "class_getSuperclass");
    private static final MemorySegment POOL_PUSH = symbol(LIBOBJC, "objc_autoreleasePoolPush");
    private static final MemorySegment POOL_POP = symbol(LIBOBJC, "objc_autoreleasePoolPop");
    // objc_msgSend must be called with the exact signature of the method: on
    // arm64 a variadic call would put the arguments on the stack.
    private static final MemorySegment MSG_SEND = symbol(LIBOBJC, "objc_msgSend");
    private static final MemorySegment MSG_SEND_SUPER = symbol(LIBOBJC, "objc_msgSendSuper");

    // Argument and return memory for struct-passing calls (main thread only).
    private static final MemorySegment SIZE_ARG = Arena.global().allocate(Sigs.CG_SIZE);
    private static final MemorySegment RECT_ARG = Arena.global().allocate(Sigs.CG_RECT);
    private static final MemorySegment COLOR_ARG = Arena.global().allocate(Sigs.MTL_CLEAR_COLOR);
    private static final MemorySegment SUPER_ARG = Arena.global().allocate(16, 8); // struct objc_super
    private static final MemorySegment REGION_ARG = Arena.global().allocate(Sigs.MTL_REGION);
    private static final MemorySegment SCISSOR_ARG = Arena.global().allocate(Sigs.MTL_SCISSOR);
    private static final SegmentAllocator RECT_RET = SegmentAllocator.prefixAllocator(
            Arena.global().allocate(Sigs.CG_RECT));
    private static final SegmentAllocator POINT_RET = SegmentAllocator.prefixAllocator(
            Arena.global().allocate(Sigs.CG_POINT));
    private static final MemorySegment POINT_ARG = Arena.global().allocate(Sigs.CG_POINT);
    private static final MemorySegment IDS_ARG = Arena.global().allocate(JAVA_LONG, 8);

    private static final ConcurrentHashMap<String, Long> CLASSES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> SELECTORS = new ConcurrentHashMap<>();

    /** The class named {@code name}; it fails for a class that does not exist. */
    public static long cls(String name) {
        Long c = CLASSES.get(name);
        if (c != null) {
            return c;
        }
        long found = withCString(name, p -> Native.call(GET_CLASS, p));
        if (found == 0) {
            throw new IllegalStateException("Objective-C class " + name + " not found");
        }
        CLASSES.put(name, found);
        return found;
    }

    public static long sel(String name) {
        Long s = SELECTORS.get(name);
        if (s != null) {
            return s;
        }
        long found = withCString(name, p -> Native.call(REGISTER_NAME, p));
        SELECTORS.put(name, found);
        return found;
    }

    // Messages whose arguments and result are words.

    public static long send(long obj, String sel) {
        return Native.call(MSG_SEND, obj, sel(sel));
    }

    public static long send(long obj, String sel, long a) {
        return Native.call(MSG_SEND, obj, sel(sel), a);
    }

    public static long send(long obj, String sel, long a, long b) {
        return Native.call(MSG_SEND, obj, sel(sel), a, b);
    }

    public static long send(long obj, String sel, long a, long b, long c) {
        return Native.call(MSG_SEND, obj, sel(sel), a, b, c);
    }

    public static long send(long obj, String sel, long a, long b, long c, long d) {
        return Native.call(MSG_SEND, obj, sel(sel), a, b, c, d);
    }

    public static long send(long obj, String sel, long a, long b, long c, long d, long e) {
        return Native.call(MSG_SEND, obj, sel(sel), a, b, c, d, e);
    }

    /** {@code [encoder setScissorRect:(MTLScissorRect){x, y, width, height}]}, in pixels. */
    public static void setScissor(long encoder, long x, long y, long width, long height) {
        try {
            SCISSOR_ARG.set(JAVA_LONG, 0, x);
            SCISSOR_ARG.set(JAVA_LONG, 8, y);
            SCISSOR_ARG.set(JAVA_LONG, 16, width);
            SCISSOR_ARG.set(JAVA_LONG, 24, height);
            Native.SET_SCISSOR.invokeExact(MSG_SEND, encoder, sel("setScissorRect:"), SCISSOR_ARG);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** A message returning BOOL, which sets only the low byte of the register. */
    public static boolean sendBool(long obj, String sel) {
        return (send(obj, sel) & 0xFF) != 0;
    }

    public static boolean sendBool(long obj, String sel, long a) {
        return (send(obj, sel, a) & 0xFF) != 0;
    }

    public static boolean respondsTo(long obj, String selector) {
        return obj != 0 && sendBool(obj, "respondsToSelector:", sel(selector));
    }

    // Messages with floats or structs.

    public static double sendDouble(long obj, String sel) {
        try {
            return (double) Native.DOUBLE_OF.invokeExact(MSG_SEND, obj, sel(sel));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void sendDouble(long obj, String sel, double v) {
        try {
            Native.SET_DOUBLE.invokeExact(MSG_SEND, obj, sel(sel), v);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** A message taking two doubles and returning an id, as {@code [NSFont systemFontOfSize:weight:]}. */
    public static long sendDoubles(long obj, String sel, double a, double b) {
        try {
            return (long) Native.DOUBLE2_ID.invokeExact(MSG_SEND, obj, sel(sel), a, b);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code [texture replaceRegion:mipmapLevel:0 withBytes:bytesPerRow:]} for a 2D region. */
    public static void replaceRegion(long texture, long x, long y, long width, long height,
                                     long bytes, long bytesPerRow) {
        try {
            REGION_ARG.set(JAVA_LONG, 0, x);
            REGION_ARG.set(JAVA_LONG, 8, y);
            REGION_ARG.set(JAVA_LONG, 16, 0);
            REGION_ARG.set(JAVA_LONG, 24, width);
            REGION_ARG.set(JAVA_LONG, 32, height);
            REGION_ARG.set(JAVA_LONG, 40, 1);
            Native.REPLACE_REGION.invokeExact(MSG_SEND, texture,
                    sel("replaceRegion:mipmapLevel:withBytes:bytesPerRow:"), REGION_ARG, 0L, bytes, bytesPerRow);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** A message returning a CGRect, as {x, y, width, height}. */
    public static double[] sendRect(long obj, String sel) {
        try {
            MemorySegment r = (MemorySegment) Native.RECT_OF.invokeExact(MSG_SEND, RECT_RET, obj, sel(sel));
            return new double[] {
                r.get(JAVA_DOUBLE, 0), r.get(JAVA_DOUBLE, 8), r.get(JAVA_DOUBLE, 16), r.get(JAVA_DOUBLE, 24)
            };
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** Where an event happened in a view, {x, y} in points: {@code [view convertPoint:[event locationInWindow] fromView:nil]}. */
    public static double[] locationInView(long view, long event) {
        try {
            MemorySegment p = (MemorySegment) Native.POINT_OF.invokeExact(MSG_SEND, POINT_RET, event, sel("locationInWindow"));
            POINT_ARG.set(JAVA_DOUBLE, 0, p.get(JAVA_DOUBLE, 0));
            POINT_ARG.set(JAVA_DOUBLE, 8, p.get(JAVA_DOUBLE, 8));
            MemorySegment q = (MemorySegment) Native.CONVERT_POINT.invokeExact(MSG_SEND, POINT_RET, view,
                    sel("convertPoint:fromView:"), POINT_ARG, 0L);
            return new double[] {q.get(JAVA_DOUBLE, 0), q.get(JAVA_DOUBLE, 8)};
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code [area initWithRect:NSZeroRect options:options owner:owner userInfo:nil]}. */
    public static long initTrackingArea(long area, long options, long owner) {
        try {
            return (long) Native.INIT_TRACKING_AREA.invokeExact(MSG_SEND, area,
                    sel("initWithRect:options:owner:userInfo:"), rect(0, 0, 0, 0), options, owner, 0L);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** An autoreleased NSArray of the objects. */
    public static long array(long... objects) {
        for (int i = 0; i < objects.length; i++) {
            IDS_ARG.setAtIndex(JAVA_LONG, i, objects[i]);
        }
        return send(cls("NSArray"), "arrayWithObjects:count:", IDS_ARG.address(), objects.length);
    }

    public static void sendSize(long obj, String sel, double width, double height) {
        try {
            SIZE_ARG.set(JAVA_DOUBLE, 0, width);
            SIZE_ARG.set(JAVA_DOUBLE, 8, height);
            Native.SET_SIZE.invokeExact(MSG_SEND, obj, sel(sel), SIZE_ARG);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void sendRect(long obj, String sel, double x, double y, double width, double height) {
        try {
            Native.SET_RECT.invokeExact(MSG_SEND, obj, sel(sel), rect(x, y, width, height));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code [obj initWithFrame:rect]}. */
    public static long initWithFrame(long obj, double x, double y, double width, double height) {
        try {
            return (long) Native.INIT_RECT.invokeExact(MSG_SEND, obj, sel("initWithFrame:"),
                    rect(x, y, width, height));
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code [window initWithContentRect:styleMask:backing:defer:]}. */
    public static long initWindow(long obj, double x, double y, double width, double height,
                                  long styleMask, long backing, boolean defer) {
        try {
            return (long) Native.INIT_WINDOW.invokeExact(MSG_SEND, obj,
                    sel("initWithContentRect:styleMask:backing:defer:"),
                    rect(x, y, width, height), styleMask, backing, defer ? 1L : 0L);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    public static void setClearColor(long attachment, double r, double g, double b, double a) {
        try {
            COLOR_ARG.set(JAVA_DOUBLE, 0, r);
            COLOR_ARG.set(JAVA_DOUBLE, 8, g);
            COLOR_ARG.set(JAVA_DOUBLE, 16, b);
            COLOR_ARG.set(JAVA_DOUBLE, 24, a);
            Native.SET_CLEAR_COLOR.invokeExact(MSG_SEND, attachment, sel("setClearColor:"), COLOR_ARG);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** {@code [obj performSelector:sel withObject:arg afterDelay:seconds]}. */
    public static void performAfter(long obj, String selector, long arg, double seconds) {
        try {
            Native.PERFORM_AFTER.invokeExact(MSG_SEND, obj, sel("performSelector:withObject:afterDelay:"),
                    sel(selector), arg, seconds);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /**
     * Calls the superclass's {@code setFrameSize:} from an override. The
     * superclass comes from the class that defines the override, never from
     * the object's class, which key-value observing may replace (see MyGO's
     * sendSuper).
     */
    public static void superSetSize(long self, long superclass, long sel, double width, double height) {
        try {
            SUPER_ARG.set(JAVA_LONG, 0, self);
            SUPER_ARG.set(JAVA_LONG, 8, superclass);
            SIZE_ARG.set(JAVA_DOUBLE, 0, width);
            SIZE_ARG.set(JAVA_DOUBLE, 8, height);
            Native.SET_SIZE.invokeExact(MSG_SEND_SUPER, SUPER_ARG.address(), sel, SIZE_ARG);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    private static MemorySegment rect(double x, double y, double width, double height) {
        RECT_ARG.set(JAVA_DOUBLE, 0, x);
        RECT_ARG.set(JAVA_DOUBLE, 8, y);
        RECT_ARG.set(JAVA_DOUBLE, 16, width);
        RECT_ARG.set(JAVA_DOUBLE, 24, height);
        return RECT_ARG;
    }

    // Runtime classes.

    static long allocateClassPair(long superclass, String name) {
        long c = withCString(name, p -> Native.call(ALLOCATE_CLASS_PAIR, superclass, p, 0L));
        if (c == 0) {
            throw new IllegalStateException("cannot define Objective-C class " + name);
        }
        return c;
    }

    static void addMethod(long cls, long sel, MemorySegment imp, String types) {
        withCString(types, p -> Native.call(ADD_METHOD, cls, sel, imp.address(), p));
    }

    static void registerClassPair(long cls) {
        Native.call(REGISTER_CLASS_PAIR, cls);
    }

    public static long superclass(long cls) {
        return Native.call(GET_SUPERCLASS, cls);
    }

    // Memory management.

    public static long poolPush() {
        return Native.call(POOL_PUSH);
    }

    public static void poolPop(long pool) {
        Native.call(POOL_POP, pool);
    }

    public static long retain(long obj) {
        return obj == 0 ? 0 : send(obj, "retain");
    }

    public static void release(long obj) {
        if (obj != 0) {
            send(obj, "release");
        }
    }

    /** {@code [[cls alloc] init]}, owned by the caller. */
    public static long create(String className) {
        return send(send(cls(className), "alloc"), "init");
    }

    // Strings.

    private static final long NS_UTF8_STRING_ENCODING = 4;

    /** An autoreleased NSString. */
    public static long nsString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(Math.max(1, bytes.length));
            MemorySegment.copy(bytes, 0, buf, JAVA_BYTE, 0, bytes.length);
            long str = send(send(cls("NSString"), "alloc"), "initWithBytes:length:encoding:",
                    buf.address(), bytes.length, NS_UTF8_STRING_ENCODING);
            return send(str, "autorelease");
        }
    }

    /** Copies an NSString into a Java string. */
    public static String string(long nsString) {
        if (nsString == 0) {
            return "";
        }
        return cString(send(nsString, "UTF8String"));
    }

    public static String cString(long address) {
        if (address == 0) {
            return "";
        }
        return MemorySegment.ofAddress(address).reinterpret(Long.MAX_VALUE).getString(0);
    }

    /** What an NSError says. */
    public static String describe(long nsError) {
        return nsError == 0 ? "unknown error" : string(send(nsError, "localizedDescription"));
    }

    private interface CStringCall {
        long call(long cString);
    }

    private static long withCString(String s, CStringCall call) {
        try (Arena arena = Arena.ofConfined()) {
            return call.call(arena.allocateFrom(s).address());
        }
    }

    static RuntimeException rethrow(Throwable t) {
        return Native.rethrow(t);
    }
}
