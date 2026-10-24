package jui.platform.mac;

import jui.ffi.Libc;
import jui.ffi.ObjC;
import jui.ffi.ObjCClass;

import static jui.ffi.ObjC.cls;
import static jui.ffi.ObjC.send;

/**
 * The AppKit application: a regular app with a menu to quit it, which ends
 * when its last window closes. Must run on the process's main thread.
 */
public final class MacApp {
    private MacApp() {}

    private static final long ACTIVATION_POLICY_REGULAR = 0;

    /** Runs the app; {@code whenReady} runs once it has finished launching. */
    public static void run(Runnable whenReady) {
        ObjC.framework("AppKit");
        long pool = ObjC.poolPush();
        long app = send(cls("NSApplication"), "sharedApplication");
        send(app, "setActivationPolicy:", ACTIVATION_POLICY_REGULAR);
        installMenu(app);

        long delegateClass = ObjCClass.define("JUIAppDelegate", "NSObject")
                .method1("applicationDidFinishLaunching:", (self, cmd, note) -> {
                    activate(app);
                    whenReady.run();
                })
                .boolMethod1("applicationShouldTerminateAfterLastWindowClosed:", (self, cmd, sender) -> true)
                .register();
        send(app, "setDelegate:", send(send(delegateClass, "alloc"), "init"));
        ObjC.poolPop(pool);
        send(app, "run");
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> POSTED =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    /**
     * Runs task on the main thread soon, from any thread: it queues the task
     * and has the main run loop drain the queue (performSelectorOnMainThread:),
     * as AppKit allows from any thread.
     */
    public static void post(Runnable task) {
        POSTED.add(task);
        send(Drainer.TARGET, "performSelectorOnMainThread:withObject:waitUntilDone:", ObjC.sel("juiDrain:"), 0, 0);
    }

    /** The object whose juiDrain: runs posted tasks; made once, by whichever thread posts first. */
    private static final class Drainer {
        static final long TARGET;

        static {
            ObjC.framework("Foundation"); // performSelectorOnMainThread: is NSThread's, in Foundation
            long cls = ObjCClass.define("JUIMainThread", "NSObject")
                    .method1("juiDrain:", (self, cmd, arg) -> drain())
                    .register();
            TARGET = send(send(cls, "alloc"), "init");
        }
    }

    private static void drain() {
        for (Runnable task; (task = POSTED.poll()) != null; ) {
            try {
                task.run();
            } catch (Throwable t) {
                System.err.println("jui: uncaught exception in a task posted to the main thread");
                t.printStackTrace();
            }
        }
    }

    /** Whether this thread is the process's main thread, as AppKit needs. */
    public static boolean onMainThread() {
        return Libc.pthreadMainNp() != 0;
    }

    private static void activate(long app) {
        if (ObjC.respondsTo(app, "activate")) { // macOS 14
            send(app, "activate");
        } else {
            send(app, "activateIgnoringOtherApps:", 1);
        }
    }

    private static void installMenu(long app) {
        long bar = ObjC.create("NSMenu");
        long appItem = ObjC.create("NSMenuItem");
        send(bar, "addItem:", appItem);
        long appMenu = ObjC.create("NSMenu");
        long quit = send(send(cls("NSMenuItem"), "alloc"), "initWithTitle:action:keyEquivalent:",
                ObjC.nsString("Quit"), ObjC.sel("terminate:"), ObjC.nsString("q"));
        send(appMenu, "addItem:", quit);
        send(appItem, "setSubmenu:", appMenu);
        send(app, "setMainMenu:", bar);
    }
}
