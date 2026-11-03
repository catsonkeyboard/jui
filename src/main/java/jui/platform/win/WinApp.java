package jui.platform.win;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import jui.ffi.Win32FFI;

/**
 * The Win32 application: a message loop that ends when its last window
 * closes, and posts from any thread through the loop's own wake-up
 * message. Must run on the process's main thread.
 */
public final class WinApp {
    private WinApp() {}

    private static final int WM_APP_POST = 0x8000;
    private static final int WM_QUIT = 0x0012;

    private static final MemorySegment GET_MESSAGE_W = Win32FFI.symbol("user32", "GetMessageW");
    private static final MemorySegment PEEK_MESSAGE_W = Win32FFI.symbol("user32", "PeekMessageW");
    private static final MemorySegment POST_MESSAGE_W = Win32FFI.symbol("user32", "PostMessageW");
    private static final MemorySegment POST_THREAD_MESSAGE_W = Win32FFI.symbol("user32", "PostThreadMessageW");
    private static final MemorySegment TRANSLATE_MESSAGE = Win32FFI.symbol("user32", "TranslateMessage");
    private static final MemorySegment DISPATCH_MESSAGE_W = Win32FFI.symbol("user32", "DispatchMessageW");
    private static final MemorySegment GET_CURRENT_THREAD_ID = Win32FFI.symbol("kernel32", "GetCurrentThreadId");

    private static final java.lang.invoke.MethodHandle GET = bind(GET_MESSAGE_W,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle POST = bind(POST_MESSAGE_W,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle POST_THREAD = bind(POST_THREAD_MESSAGE_W,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle DISPATCH = bind(DISPATCH_MESSAGE_W,
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle TRANSLATE = bind(TRANSLATE_MESSAGE,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle THREAD_ID = bind(GET_CURRENT_THREAD_ID,
            FunctionDescriptor.of(ValueLayout.JAVA_INT));

    /** A downcall of {@code fn} with {@code d}: the address binds at build, calls pass no symbol. */
    private static java.lang.invoke.MethodHandle bind(MemorySegment fn, FunctionDescriptor d) {
        try {
            return java.lang.foreign.Linker.nativeLinker().downcallHandle(fn, d);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    /** The message loop's thread, as its id: posts from other threads wake it. */
    private static volatile int loopThreadId;

    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> POSTED =
            new ConcurrentLinkedQueue<>();

    public static void run(Runnable whenReady) {
        run(whenReady, null);
    }

    /** Runs the app; {@code whenReady} runs once the loop is up; appMenu is Windows' menu-bar-less equivalent and is ignored. */
    public static void run(Runnable whenReady, Consumer<jui.ui.Menu> appMenu) {
        loopThreadId = threadId();
        whenReady.run();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment msg = arena.allocate(48, 8); // struct MSG: hwnd, message, wParam, lParam, time, pt(8), lPrivate
            drain(); // what run(whenReady) itself posted
            while (true) {
                int r = getMessage(msg);
                if (r <= 0) { // WM_QUIT or failure
                    break;
                }
                if (isPost(msg.get(ValueLayout.JAVA_INT, 8))) {
                    drain();
                    continue;
                }
                dispatch(msg);
            }
        }
    }

    /** Whether this thread is the process's UI thread. */
    public static boolean onMainThread() {
        int loop = loopThreadId;
        return loop == 0 || loop == threadId();
    }

    /** Runs task on the UI thread soon, from any thread. */
    public static void post(Runnable task) {
        POSTED.add(task);
        int loop = loopThreadId;
        if (loop != 0 && loop != threadId()) {
            postThreadMessage(loop, WM_APP_POST);
        }
    }

    /** Drains the posted tasks; called by the loop for the wake-up message. */
    static void drain() {
        Runnable task;
        while ((task = POSTED.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException | Error t) {
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), t);
            }
        }
    }

    static boolean isPost(int message) {
        return message == WM_APP_POST;
    }

    /** The loop quits once its message queue sees WM_QUIT (the last window posts it). */
    static void quit() {
        postMessage(0, WM_QUIT);
    }

    private static int threadId() {
        try {
            return (int) THREAD_ID.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static int getMessage(MemorySegment msg) {
        try {
            return (int) GET.invokeExact(msg, 0L, 0L, 0L);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static void dispatch(MemorySegment msg) {
        try {
            int translated = (int) TRANSLATE.invokeExact(msg);
            long dispatched = (long) DISPATCH.invokeExact(msg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static void postMessage(long hwnd, int message) {
        try {
            int posted = (int) POST.invokeExact(hwnd, message, 0L, 0L);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void postThreadMessage(int thread, int message) {
        try {
            int posted = (int) POST_THREAD.invokeExact(thread, message, 0L, 0L);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}
