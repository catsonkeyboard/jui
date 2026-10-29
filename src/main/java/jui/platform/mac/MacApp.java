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

    /** Runs the app; {@code whenReady} runs once it has finished launching; appMenu, when not
     *  null, gives the app's own menus in the menu bar (App.menu). */
    public static void run(Runnable whenReady, java.util.function.Consumer<jui.ui.Menu> appMenu) {
        ObjC.framework("AppKit");
        long pool = ObjC.poolPush();
        long app = send(cls("NSApplication"), "sharedApplication");
        send(app, "setActivationPolicy:", ACTIVATION_POLICY_REGULAR);
        installMenu(app, appMenu);
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
    /** Runs the app; {@code whenReady} runs once it has finished launching. */
    public static void run(Runnable whenReady) {
        run(whenReady, null);
    }

    /** The window whose key window's menu bar choices are reported in; the last one to take it. */
    private static MacWindow focused;

    /** The key window changed, as the window's delegate heard it. */
    static void focusedWindow(MacWindow window) {
        focused = window;
    }

    /** The focused window's engine, or null with no window: where a menu bar choice is reported. */
    static jui.ui.engine.Engine focusedEngine() {
        return focused == null ? null : focused.engine();
    }
    /**
     * The main menu: the app's, with Quit, and Edit, whose commands go to
     * the first responder, the window's view, which gives them to the text
     * input with focus (design §6.7). Edit's items are always enabled, as
     * MyGO's; English titles until the app menu of M5.
     */
    static void installMenu(long app, java.util.function.Consumer<jui.ui.Menu> appMenu) {
        long bar = ObjC.create("NSMenu");
        long appItem = ObjC.create("NSMenuItem");
        send(bar, "addItem:", appItem);
        long appSubmenu = ObjC.create("NSMenu");
        long quit = send(send(cls("NSMenuItem"), "alloc"), "initWithTitle:action:keyEquivalent:",
                ObjC.nsString("Quit"), ObjC.sel("terminate:"), ObjC.nsString("q"));
        send(appSubmenu, "addItem:", quit);
        send(appItem, "setSubmenu:", appSubmenu);

        if (appMenu != null) {
            MacMenus.bar(appMenu).install(bar);               // the app's own menus, before Edit
        }

        long editItem = ObjC.create("NSMenuItem");
        send(bar, "addItem:", editItem);
        long edit = send(send(cls("NSMenu"), "alloc"), "initWithTitle:", ObjC.nsString("Edit"));
        send(edit, "setAutoenablesItems:", 0);
        addItem(edit, "Undo", "undo:", "z");
        addItem(edit, "Redo", "redo:", "Z"); // a capital takes Shift
        send(edit, "addItem:", send(cls("NSMenuItem"), "separatorItem"));
        addItem(edit, "Cut", "cut:", "x");
        addItem(edit, "Copy", "copy:", "c");
        addItem(edit, "Paste", "paste:", "v");
        addItem(edit, "Paste and Match Style", "pasteAsPlainText:", "v", COMMAND | OPTION | SHIFT); // ⌥⇧⌘V (as MyGO)
        addItem(edit, "Delete", "delete:", "");
        addItem(edit, "Select All", "selectAll:", "a");
        send(editItem, "setSubmenu:", edit);
        ObjC.release(edit);
        ObjC.release(editItem);
        send(app, "setMainMenu:", bar);
    }

    /** NSEventModifierFlags for an item's key equivalent. */
    private static final long SHIFT = 1L << 17, OPTION = 1L << 19, COMMAND = 1L << 20;

    /** An item for action, sent to the first responder, with ⌘ and key. */
    private static void addItem(long menu, String title, String action, String key) {
        addItem(menu, title, action, key, COMMAND);
    }

    /** An item for action, sent to the first responder, with key and the modifier keys in mask. */
    private static void addItem(long menu, String title, String action, String key, long mask) {
        long item = send(send(cls("NSMenuItem"), "alloc"), "initWithTitle:action:keyEquivalent:",
                ObjC.nsString(title), ObjC.sel(action), ObjC.nsString(key));
        if (mask != COMMAND) {
            send(item, "setKeyEquivalentModifierMask:", mask);
        }
        send(menu, "addItem:", item);
        ObjC.release(item);
    }
}
