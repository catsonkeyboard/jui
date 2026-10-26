package jui.platform.mac;

import jui.ffi.ObjC;
import jui.ui.Cursor;

/** AppKit's cursors for the pointer shapes (as MyGO's surface maps them). */
public final class MacCursors {
    private MacCursors() {}

    /** The NSCursor of a shape: AppKit's shared object, not owned. */
    public static long of(Cursor c) {
        String name = switch (c) {
            case DEFAULT -> "arrowCursor";
            case POINTER -> "pointingHandCursor";
            case TEXT -> "IBeamCursor";
            case RESIZE_EW -> "resizeLeftRightCursor";
            case RESIZE_NS -> "resizeUpDownCursor";
            case NOT_ALLOWED -> "operationNotAllowedCursor";
            case CROSSHAIR -> "crosshairCursor";
            case GRAB -> "openHandCursor";
            case GRABBING -> "closedHandCursor";
        };
        return ObjC.send(ObjC.cls("NSCursor"), name);
    }
}
