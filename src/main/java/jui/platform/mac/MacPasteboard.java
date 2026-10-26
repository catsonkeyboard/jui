package jui.platform.mac;

import jui.ffi.ObjC;

import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static jui.ffi.ObjC.send;

/**
 * A pasteboard's plain text (design §7.7: plain text only in this phase).
 * MyGO writes through an NSPasteboardItem that provides its data lazily;
 * with plain text alone, the string is written as it is.
 */
public final class MacPasteboard {
    /** NSPasteboardTypeString (public.utf8-plain-text), an AppKit NSString constant. */
    private static final long TYPE_STRING = ObjC.symbol(ObjC.framework("AppKit"), "NSPasteboardTypeString")
            .reinterpret(8).get(JAVA_LONG, 0);

    private final long pasteboard;
    private final boolean unique;

    private MacPasteboard(long pasteboard, boolean unique) {
        this.pasteboard = pasteboard;
        this.unique = unique;
    }

    /** The general pasteboard: the clipboard of ⌘C and ⌘V. */
    public static MacPasteboard general() {
        return new MacPasteboard(send(ObjC.cls("NSPasteboard"), "generalPasteboard"), false);
    }

    /** A pasteboard of its own, for tests: release it when done. */
    public static MacPasteboard unique() {
        return new MacPasteboard(ObjC.retain(send(ObjC.cls("NSPasteboard"), "pasteboardWithUniqueName")), true);
    }

    /** Its text, or null when it holds none. */
    public String read() {
        long pool = ObjC.poolPush();
        try {
            long s = send(pasteboard, "stringForType:", TYPE_STRING);
            return s == 0 ? null : ObjC.string(s);
        } finally {
            ObjC.poolPop(pool);
        }
    }

    /** Replaces what it holds with text. */
    public void write(String s) {
        long pool = ObjC.poolPush();
        try {
            send(pasteboard, "clearContents");
            send(pasteboard, "setString:forType:", ObjC.nsString(s), TYPE_STRING);
        } finally {
            ObjC.poolPop(pool);
        }
    }

    /** Lets a unique pasteboard go. */
    public void release() {
        if (unique) {
            send(pasteboard, "releaseGlobally");
            ObjC.release(pasteboard);
        }
    }
}
