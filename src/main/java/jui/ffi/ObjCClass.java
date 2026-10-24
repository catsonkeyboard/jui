package jui.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;

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

    /** The upcall targets: the static methods below, with their signatures. */
    static final String[] TARGET_NAMES = {"void0", "bool0", "void1", "bool1", "voidSize"};
    static final FunctionDescriptor[] TARGET_SIGS = {
        Sigs.IMP_V, Sigs.IMP_B, Sigs.IMP_V_W, Sigs.IMP_B_W, Sigs.IMP_V_SIZE
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

    private static void report(Throwable t) {
        System.err.println("jui: uncaught exception in an Objective-C method");
        t.printStackTrace();
    }
}
