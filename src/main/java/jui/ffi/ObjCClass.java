package jui.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Defines Objective-C classes at run time whose methods are Java handlers.
 *
 * <p>As MyGO does with purego callbacks, there is one upcall stub per
 * signature, made once; the selector routes each call to its handler. Every
 * entry point catches what the handler throws: a Java exception unwinding
 * into Objective-C would end the process.
 */
public final class ObjCClass {

    public interface Void0 {
        void call(long self, long cmd);
    }

    public interface Bool0 {
        boolean call(long self, long cmd);
    }

    public interface Void1 {
        void call(long self, long cmd, long arg);
    }

    public interface Bool1 {
        boolean call(long self, long cmd, long arg);
    }

    public interface VoidSize {
        void call(long self, long cmd, double width, double height);
    }

    // NSTextInputClient's shapes. Ranges come as location and length (NSNotFound is ObjC.NOT_FOUND);
    // a range or rect returned is written to out.

    /** NSRange (id, SEL): writes location and length to out[0], out[1]. */
    public interface RangeOf {
        void call(long self, long cmd, long[] out);
    }

    /** id (id, SEL). */
    public interface Word0 {
        long call(long self, long cmd);
    }

    /** void (id, SEL, id, NSRange). */
    public interface TextRange {
        void call(long self, long cmd, long text, long location, long length);
    }

    /** void (id, SEL, id, NSRange, NSRange). */
    public interface TextRanges {
        void call(long self, long cmd, long text, long selLocation, long selLength, long replLocation, long replLength);
    }

    /** id (id, SEL, NSRange, NSRange *): actualRange may be 0. */
    public interface WordRange {
        long call(long self, long cmd, long location, long length, long actualRange);
    }

    /** NSRect (id, SEL, NSRange, NSRange *): writes x, y, width, height to out; actualRange may be 0. */
    public interface RectRange {
        void call(long self, long cmd, long location, long length, long actualRange, double[] out);
    }

    /** NSUInteger (id, SEL, NSPoint). */
    public interface WordPoint {
        long call(long self, long cmd, double x, double y);
    }

    public interface BoolWordPointWord {
        boolean call(long self, long cmd, long item, double x, double y, long view);
    }

    /** The upcall targets: the static methods below, with their signatures. */
    static final String[] TARGET_NAMES = {
        "void0", "bool0", "void1", "bool1", "voidSize",
        "range0", "word0", "textRange", "textRanges", "wordRange", "rectRange", "wordPoint", "boolWordPointWord"
    };
    static final FunctionDescriptor[] TARGET_SIGS = {
        Sigs.IMP_V, Sigs.IMP_B, Sigs.IMP_V_W, Sigs.IMP_B_W, Sigs.IMP_V_SIZE,
        Sigs.IMP_RANGE, Sigs.IMP_W, Sigs.IMP_V_W_RANGE, Sigs.IMP_V_W_RANGE2, Sigs.IMP_W_RANGE_W,
        Sigs.IMP_RECT_RANGE_W, Sigs.IMP_W_POINT, Sigs.POPUP_MENU
    };

    static MethodHandle target(int i) throws ReflectiveOperationException {
        return MethodHandles.lookup().findStatic(ObjCClass.class, TARGET_NAMES[i], TARGET_SIGS[i].toMethodType());
    }

    // Handlers by selector: classes may be defined on any thread, their methods called on the main one.
    private static final Map<Long, Void0> VOID0 = new ConcurrentHashMap<>();
    private static final Map<Long, Bool0> BOOL0 = new ConcurrentHashMap<>();
    private static final Map<Long, Void1> VOID1 = new ConcurrentHashMap<>();
    private static final Map<Long, Bool1> BOOL1 = new ConcurrentHashMap<>();
    private static final Map<Long, VoidSize> VOID_SIZE = new ConcurrentHashMap<>();
    private static final Map<Long, RangeOf> RANGE0 = new ConcurrentHashMap<>();
    private static final Map<Long, Word0> WORD0 = new ConcurrentHashMap<>();
    private static final Map<Long, TextRange> TEXT_RANGE = new ConcurrentHashMap<>();
    private static final Map<Long, TextRanges> TEXT_RANGES = new ConcurrentHashMap<>();
    private static final Map<Long, WordRange> WORD_RANGE = new ConcurrentHashMap<>();
    private static final Map<Long, RectRange> RECT_RANGE = new ConcurrentHashMap<>();
    private static final Map<Long, WordPoint> WORD_POINT = new ConcurrentHashMap<>();
    private static final Map<Long, BoolWordPointWord> BOOL_WORD_POINT_WORD = new ConcurrentHashMap<>();

    // What a method returning a struct gives back: its handler writes the arrays, which are copied
    // into this memory (methods are called on the main thread, one at a time).
    private static final long[] RANGE_OUT = new long[2];
    private static final double[] RECT_OUT = new double[4];
    private static final MemorySegment RANGE_RET = Arena.global().allocate(Sigs.NS_RANGE);
    private static final MemorySegment RECT_RET = Arena.global().allocate(Sigs.CG_RECT);

    private static final MemorySegment[] STUBS = new MemorySegment[TARGET_NAMES.length];

    private static synchronized MemorySegment stub(int i) {
        if (STUBS[i] == null) {
            try {
                STUBS[i] = ObjC.LINKER.upcallStub(target(i), TARGET_SIGS[i], Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return STUBS[i];
    }

    private final long cls;

    private ObjCClass(long cls) {
        this.cls = cls;
    }

    /** Starts a class named {@code name} that inherits from {@code superName}. */
    public static ObjCClass define(String name, String superName) {
        return new ObjCClass(ObjC.allocateClassPair(ObjC.cls(superName), name));
    }

    /** A method {@code void (id, SEL)}. */
    public ObjCClass method(String selector, Void0 handler) {
        long sel = ObjC.sel(selector);
        VOID0.put(sel, handler);
        ObjC.addMethod(cls, sel, stub(0), "v@:");
        return this;
    }

    /** A method {@code BOOL (id, SEL)}. */
    public ObjCClass boolMethod(String selector, Bool0 handler) {
        long sel = ObjC.sel(selector);
        BOOL0.put(sel, handler);
        ObjC.addMethod(cls, sel, stub(1), "B@:");
        return this;
    }

    /** A method {@code void (id, SEL, id)}. */
    public ObjCClass method1(String selector, Void1 handler) {
        long sel = ObjC.sel(selector);
        VOID1.put(sel, handler);
        ObjC.addMethod(cls, sel, stub(2), "v@:@");
        return this;
    }

    /** A method {@code BOOL (id, SEL, id)}. */
    public ObjCClass boolMethod1(String selector, Bool1 handler) {
        long sel = ObjC.sel(selector);
        BOOL1.put(sel, handler);
        ObjC.addMethod(cls, sel, stub(3), "B@:@");
        return this;
    }

    /** A method {@code void (id, SEL, NSSize)}. */
    public ObjCClass sizeMethod(String selector, VoidSize handler) {
        long sel = ObjC.sel(selector);
        VOID_SIZE.put(sel, handler);
        ObjC.addMethod(cls, sel, stub(4), "v@:{CGSize=dd}");
        return this;
    }

    /** A method {@code NSRange (id, SEL)}. */
    public ObjCClass rangeMethod(String selector, RangeOf handler) {
        return add(selector, RANGE0, handler, 5, "{_NSRange=QQ}@:");
    }

    /** A method {@code id (id, SEL)}. */
    public ObjCClass wordMethod(String selector, Word0 handler) {
        return add(selector, WORD0, handler, 6, "@@:");
    }

    /** A method {@code void (id, SEL, id, NSRange)}. */
    public ObjCClass textRangeMethod(String selector, TextRange handler) {
        return add(selector, TEXT_RANGE, handler, 7, "v@:@{_NSRange=QQ}");
    }

    /** A method {@code void (id, SEL, id, NSRange, NSRange)}. */
    public ObjCClass textRangesMethod(String selector, TextRanges handler) {
        return add(selector, TEXT_RANGES, handler, 8, "v@:@{_NSRange=QQ}{_NSRange=QQ}");
    }

    /** A method {@code id (id, SEL, NSRange, NSRange *)}. */
    public ObjCClass wordRangeMethod(String selector, WordRange handler) {
        return add(selector, WORD_RANGE, handler, 9, "@@:{_NSRange=QQ}^{_NSRange=QQ}");
    }

    /** A method {@code NSRect (id, SEL, NSRange, NSRange *)}. */
    public ObjCClass rectRangeMethod(String selector, RectRange handler) {
        return add(selector, RECT_RANGE, handler, 10, "{CGRect={CGPoint=dd}{CGSize=dd}}@:{_NSRange=QQ}^{_NSRange=QQ}");
    }

    /** A method {@code NSUInteger (id, SEL, NSPoint)}. */
    public ObjCClass wordPointMethod(String selector, WordPoint handler) {
        return add(selector, WORD_POINT, handler, 11, "Q@:{CGPoint=dd}");
    }

    public ObjCClass boolWordPointWordMethod(String selector, BoolWordPointWord handler) {
        return add(selector, BOOL_WORD_POINT_WORD, handler, 12, "B@:@{CGPoint=dd}@");
    }

    /** Declares that the class adopts a protocol, before it is registered; fails for an unknown protocol. */
    public ObjCClass protocol(String name) {
        ObjC.addProtocol(cls, ObjC.protocol(name));
        return this;
    }

    private <H> ObjCClass add(String selector, Map<Long, H> handlers, H handler, int target, String types) {
        long sel = ObjC.sel(selector);
        handlers.put(sel, handler);
        ObjC.addMethod(cls, sel, stub(target), types);
        return this;
    }

    /** Registers the class with the runtime and returns it. */
    public long register() {
        ObjC.registerClassPair(cls);
        return cls;
    }

    // Upcall entry points.

    static void void0(long self, long cmd) {
        try {
            VOID0.get(cmd).call(self, cmd);
        } catch (Throwable t) {
            report(t);
        }
    }

    static boolean bool0(long self, long cmd) {
        try {
            return BOOL0.get(cmd).call(self, cmd);
        } catch (Throwable t) {
            report(t);
            return false;
        }
    }

    static void void1(long self, long cmd, long arg) {
        try {
            VOID1.get(cmd).call(self, cmd, arg);
        } catch (Throwable t) {
            report(t);
        }
    }

    static boolean bool1(long self, long cmd, long arg) {
        try {
            return BOOL1.get(cmd).call(self, cmd, arg);
        } catch (Throwable t) {
            report(t);
            return false;
        }
    }

    static void voidSize(long self, long cmd, MemorySegment size) {
        try {
            VOID_SIZE.get(cmd).call(self, cmd, size.get(JAVA_DOUBLE, 0), size.get(JAVA_DOUBLE, 8));
        } catch (Throwable t) {
            report(t);
        }
    }

    static MemorySegment range0(long self, long cmd) {
        RANGE_OUT[0] = ObjC.NOT_FOUND;
        RANGE_OUT[1] = 0;
        try {
            RANGE0.get(cmd).call(self, cmd, RANGE_OUT);
        } catch (Throwable t) {
            report(t);
            RANGE_OUT[0] = ObjC.NOT_FOUND;
            RANGE_OUT[1] = 0;
        }
        RANGE_RET.set(JAVA_LONG, 0, RANGE_OUT[0]);
        RANGE_RET.set(JAVA_LONG, 8, RANGE_OUT[1]);
        return RANGE_RET;
    }

    static long word0(long self, long cmd) {
        try {
            return WORD0.get(cmd).call(self, cmd);
        } catch (Throwable t) {
            report(t);
            return 0;
        }
    }

    static void textRange(long self, long cmd, long text, MemorySegment range) {
        try {
            TEXT_RANGE.get(cmd).call(self, cmd, text, range.get(JAVA_LONG, 0), range.get(JAVA_LONG, 8));
        } catch (Throwable t) {
            report(t);
        }
    }

    static void textRanges(long self, long cmd, long text, MemorySegment selected, MemorySegment replacement) {
        try {
            TEXT_RANGES.get(cmd).call(self, cmd, text, selected.get(JAVA_LONG, 0), selected.get(JAVA_LONG, 8),
                    replacement.get(JAVA_LONG, 0), replacement.get(JAVA_LONG, 8));
        } catch (Throwable t) {
            report(t);
        }
    }

    static long wordRange(long self, long cmd, MemorySegment range, long actualRange) {
        try {
            return WORD_RANGE.get(cmd).call(self, cmd, range.get(JAVA_LONG, 0), range.get(JAVA_LONG, 8), actualRange);
        } catch (Throwable t) {
            report(t);
            return 0;
        }
    }

    static MemorySegment rectRange(long self, long cmd, MemorySegment range, long actualRange) {
        java.util.Arrays.fill(RECT_OUT, 0);
        try {
            RECT_RANGE.get(cmd).call(self, cmd, range.get(JAVA_LONG, 0), range.get(JAVA_LONG, 8), actualRange, RECT_OUT);
        } catch (Throwable t) {
            report(t);
            java.util.Arrays.fill(RECT_OUT, 0);
        }
        for (int i = 0; i < 4; i++) {
            RECT_RET.set(JAVA_DOUBLE, 8L * i, RECT_OUT[i]);
        }
        return RECT_RET;
    }

    static long wordPoint(long self, long cmd, MemorySegment point) {
        try {
            return WORD_POINT.get(cmd).call(self, cmd, point.get(JAVA_DOUBLE, 0), point.get(JAVA_DOUBLE, 8));
        } catch (Throwable t) {
            report(t);
            return ObjC.NOT_FOUND;
        }
    }

    static boolean boolWordPointWord(long self, long cmd, long item, MemorySegment point, long view) {
        try {
            return BOOL_WORD_POINT_WORD.get(cmd).call(self, cmd, item, point.get(JAVA_DOUBLE, 0), point.get(JAVA_DOUBLE, 8), view);
        } catch (Throwable failure) {
            report(failure);
            return false;
        }
    }

    private static void report(Throwable t) {
        System.err.println("jui: uncaught exception in an Objective-C method");
        t.printStackTrace();
    }
}
