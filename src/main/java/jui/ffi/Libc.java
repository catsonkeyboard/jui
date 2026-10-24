package jui.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** The few libc functions the spike needs. */
public final class Libc {
    private Libc() {}

    private static final MemorySegment GETPID = ObjC.symbol(ObjC.LIBC, "getpid");
    private static final MemorySegment PTHREAD_MAIN_NP = ObjC.symbol(ObjC.LIBC, "pthread_main_np");
    private static final MemorySegment SYSCTL = ObjC.symbol(ObjC.LIBC, "sysctl");

    private static final int CTL_KERN = 1;
    private static final int KERN_PROC = 14;
    private static final int KERN_PROC_PID = 1;

    /** Non-zero on the process's main thread. */
    public static int pthreadMainNp() {
        try {
            return (int) Native.INT0.invokeExact(PTHREAD_MAIN_NP);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** The wall clock, in microseconds since the epoch. */
    public static long nowMicros() {
        java.time.Instant now = java.time.Instant.now();
        return now.getEpochSecond() * 1_000_000L + now.getNano() / 1_000;
    }

    /** When this process started, in microseconds since the epoch, from the kernel. */
    public static long processStartMicros() {
        try (Arena arena = Arena.ofConfined()) {
            int pid = (int) Native.INT0.invokeExact(GETPID);
            MemorySegment mib = arena.allocateFrom(JAVA_INT, CTL_KERN, KERN_PROC, KERN_PROC_PID, pid);
            MemorySegment info = arena.allocate(1024, 8); // struct kinfo_proc is 648 bytes
            MemorySegment len = arena.allocateFrom(JAVA_LONG, info.byteSize());
            int rc = (int) Native.SYSCTL.invokeExact(SYSCTL, mib.address(), 4, info.address(), len.address(), 0L, 0L);
            if (rc != 0) {
                return 0;
            }
            // kp_proc.p_starttime, a struct timeval, opens the struct.
            return info.get(JAVA_LONG, 0) * 1_000_000L + info.get(JAVA_INT, 8);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }
}
