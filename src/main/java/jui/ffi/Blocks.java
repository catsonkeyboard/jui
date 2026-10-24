package jui.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Objective-C blocks backed by Java, built by hand in native memory.
 *
 * <p>They are global blocks, which {@code Block_copy} returns as they are:
 * made once, they live as long as the process, so APIs may keep them. As with
 * methods, one upcall stub serves them all; the block's address routes each
 * call. Apple may call a block on any thread.
 */
public final class Blocks {
    private Blocks() {}

    /** A block {@code void (^)(id)}. */
    public interface Block1 {
        void call(long arg);
    }

    private static final int BLOCK_IS_GLOBAL = 1 << 28;
    private static final long GLOBAL_BLOCK_ISA = ObjC.symbol(ObjC.LIBC, "_NSConcreteGlobalBlock").address();

    private static final ConcurrentHashMap<Long, Block1> CALLS = new ConcurrentHashMap<>();
    private static MemorySegment invoke1Stub;

    static MethodHandle target() throws ReflectiveOperationException {
        return MethodHandles.lookup().findStatic(Blocks.class, "invoke1", Sigs.IMP_V.toMethodType());
    }

    /** Makes a block that calls {@code call}; it is never freed. */
    public static synchronized long block1(Block1 call) {
        if (invoke1Stub == null) {
            try {
                // void invoke(struct Block_literal *self, id arg): the same shape as void (id, SEL).
                invoke1Stub = ObjC.LINKER.upcallStub(target(), Sigs.IMP_V, Arena.global());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        MemorySegment b = Arena.global().allocate(32, 8);
        b.set(JAVA_LONG, 0, GLOBAL_BLOCK_ISA);
        b.set(JAVA_INT, 8, BLOCK_IS_GLOBAL);
        b.set(JAVA_INT, 12, 0);
        b.set(JAVA_LONG, 16, invoke1Stub.address());
        b.set(JAVA_LONG, 24, descriptor().address());
        CALLS.put(b.address(), call);
        return b.address();
    }

    /** struct Block_descriptor_1: reserved, then the size of the block literal. */
    private static MemorySegment descriptor;

    private static MemorySegment descriptor() {
        if (descriptor == null) {
            descriptor = Arena.global().allocate(16, 8);
            descriptor.set(JAVA_LONG, 0, 0);
            descriptor.set(JAVA_LONG, 8, 32);
        }
        return descriptor;
    }

    static void invoke1(long block, long arg) {
        try {
            CALLS.get(block).call(arg);
        } catch (Throwable t) {
            System.err.println("jui: uncaught exception in a block");
            t.printStackTrace();
        }
    }
}
