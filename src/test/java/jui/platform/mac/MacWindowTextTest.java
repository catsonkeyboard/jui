package jui.platform.mac;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import jui.ffi.ObjC;
import jui.testing.Check;
import jui.ui.EditCommand;
import jui.ui.Key;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/** The window's view as an NSTextInputClient, and how keys reach the input method. */
public final class MacWindowTextTest {
    private static final MemorySegment MSG_SEND = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global())
            .find("objc_msgSend").orElseThrow();
    private static final StructLayout POINT = MemoryLayout.structLayout(JAVA_DOUBLE.withName("x"), JAVA_DOUBLE.withName("y"));
    private static final MethodHandle KEY_EVENT = Linker.nativeLinker().downcallHandle(MSG_SEND,
            FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, POINT, JAVA_LONG, JAVA_DOUBLE, JAVA_LONG,
                    JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_BOOLEAN, JAVA_SHORT));

    /** Records what the view told it; a text input has focus. */
    static final class Recorder implements MacWindow.Input {
        final List<String> events = new ArrayList<>();
        boolean composing;
        int[] selected = {0, 0}, marked;
        String shown = "abcdef";
        int index = 5;

        public void pointerMove(double x, double y, int mods) {}
        public void pointerDown(double x, double y, int button, int clicks, int mods) {}
        public void pointerUp(double x, double y, int button, int mods) {}
        public void pointerLeave() {}
        public void keyDown(Key key, int mods, boolean repeat) { events.add("key " + key + " " + mods + " " + repeat); }
        public void modifiersChanged(int mods) {}
        public void windowFocusChanged(boolean focused) {}
        public void appearanceChanged(boolean dark) {}
        public void scroll(double x, double y, double dx, double dy, boolean precise, int mods) {}

        @Override public boolean textActive() { return true; }
        @Override public boolean textComposing() { return composing; }
        @Override public void textInsert(String s, int replStart, int replLength) {
            events.add("insert " + s + " " + replStart + " " + replLength);
        }
        @Override public void textMark(String s, int selStart, int selLength, int replStart, int replLength) {
            events.add("mark " + s + " " + selStart + " " + selLength + " " + replStart + " " + replLength);
        }
        @Override public void textUnmark() { events.add("unmark"); }
        @Override public boolean textMarkedRange(int[] out) {
            if (marked == null) return false;
            out[0] = marked[0];
            out[1] = marked[1];
            return true;
        }
        @Override public boolean textSelectedRange(int[] out) {
            out[0] = selected[0];
            out[1] = selected[1];
            return true;
        }
        @Override public int textLength() { return shown.length(); }
        @Override public String textIn(int start, int end) { return shown.substring(start, end); }
        @Override public boolean textRect(int start, int end, float[] out) {
            out[0] = 20;
            out[1] = 30;
            out[2] = 1;
            out[3] = 16;
            if (out.length >= 5) out[4] = end > start ? start + 1 : start;   // the first grapheme
            return true;
        }
        @Override public int textIndexAt(double x, double y) { return index; }
        @Override public void editCommand(EditCommand c) { events.add("edit " + c); }
    }

    /** Sends keyDown: with an event of these characters, key code and modifier flags. */
    static void keyDown(MacWindow w, String chars, int code, long flags) throws Throwable {
        long pool = ObjC.poolPush();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment at = arena.allocate(POINT);
            long event = (long) KEY_EVENT.invokeExact(ObjC.cls("NSEvent"),
                    ObjC.sel("keyEventWithType:location:modifierFlags:timestamp:windowNumber:context:characters:charactersIgnoringModifiers:isARepeat:keyCode:"),
                    10L, at, flags, 0.0, w.windowNumber(), 0L, ObjC.nsString(chars), ObjC.nsString(chars), false, (short) code);
            ObjC.send(w.view(), "keyDown:", event);
        } finally {
            ObjC.poolPop(pool);
        }
    }

    static MacWindow open(Recorder r) {
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        return MacWindow.open("text", 200, 150, 0xFFFFFF, null, (s, width, height) -> {}, r);
    }

    public void testAKeyGoesRawFirstThenToTheInputMethod() throws Throwable {
        Recorder r = new Recorder();
        MacWindow w = open(r);
        try {
            w.interpretHook = e -> r.events.add("ime");
            keyDown(w, "", 0x7B, 0);
            Check.equal(List.of("key LEFT 0 false", "ime"), r.events);
        } finally {
            w.close();
        }
    }

    public void testCmdKeysSkipTheInputMethod() throws Throwable {
        Recorder r = new Recorder();
        MacWindow w = open(r);
        try {
            w.interpretHook = e -> r.events.add("ime");
            keyDown(w, "p", 0x23, 1L << 20);
            Check.equal(List.of("key P 8 false"), r.events);
        } finally {
            w.close();
        }
    }

    public void testWhileComposingAKeyGoesToTheInputMethodAndAReturnedCommandSendsItOnce() throws Throwable {
        Recorder r = new Recorder();
        MacWindow w = open(r);
        try {
            r.composing = true;
            w.interpretHook = e -> {
                r.events.add("ime");
                ObjC.send(w.view(), "doCommandBySelector:", ObjC.sel("moveLeft:"));
                ObjC.send(w.view(), "doCommandBySelector:", ObjC.sel("moveLeft:"));
            };
            keyDown(w, "", 0x7B, 0);
            Check.equal(List.of("ime", "key LEFT 0 false"), r.events);
        } finally {
            w.close();
        }
    }

    public void testACommandWhenNotComposingAddsNothing() throws Throwable {
        Recorder r = new Recorder();
        MacWindow w = open(r);
        try {
            w.interpretHook = e -> ObjC.send(w.view(), "doCommandBySelector:", ObjC.sel("moveLeft:"));
            keyDown(w, "", 0x7B, 0);
            Check.equal(List.of("key LEFT 0 false"), r.events);
        } finally {
            w.close();
        }
    }

    public void testInsertAndMarkedTextReachTheInput() {
        Recorder r = new Recorder();
        MacWindow w = open(r);
        long pool = ObjC.poolPush();
        try {
            ObjC.sendTextRange(w.view(), "insertText:replacementRange:", ObjC.nsString("你好"), ObjC.NOT_FOUND, 0);
            ObjC.sendTextRanges(w.view(), "setMarkedText:selectedRange:replacementRange:", ObjC.nsString("ni"), 2, 0,
                    ObjC.NOT_FOUND, 0);
            long attributed = ObjC.send(ObjC.send(ObjC.cls("NSAttributedString"), "alloc"), "initWithString:",
                    ObjC.nsString("hao"));
            ObjC.sendTextRanges(w.view(), "setMarkedText:selectedRange:replacementRange:", attributed, 3, 0, 1, 2);
            ObjC.release(attributed);
            ObjC.send(w.view(), "unmarkText");
            ObjC.send(w.view(), "insertText:", ObjC.nsString("x"));
            Check.equal(List.of("insert 你好 -1 0", "mark ni 2 0 -1 0", "mark hao 3 0 1 2", "unmark", "insert x -1 0"),
                    r.events);
        } finally {
            ObjC.poolPop(pool);
            w.close();
        }
    }

    public void testRangesComeFromTheInput() {
        Recorder r = new Recorder();
        MacWindow w = open(r);
        try {
            long[] out = new long[2];
            ObjC.sendRange(w.view(), "markedRange", out);
            Check.equal(ObjC.NOT_FOUND, out[0]);
            Check.equal(0, out[1]);
            Check.isTrue(!ObjC.sendBool(w.view(), "hasMarkedText"), "no marked text");
            r.marked = new int[] {1, 2};
            ObjC.sendRange(w.view(), "markedRange", out);
            Check.equal(1, out[0]);
            Check.equal(2, out[1]);
            Check.isTrue(ObjC.sendBool(w.view(), "hasMarkedText"), "marked text");
            r.selected = new int[] {3, 2};
            ObjC.sendRange(w.view(), "selectedRange", out);
            Check.equal(3, out[0]);
            Check.equal(2, out[1]);
        } finally {
            w.close();
        }
    }

    public void testTheFirstRectIsInScreenCoordinates() {
        Recorder r = new Recorder();
        MacWindow w = open(r);
        try (Arena arena = Arena.ofConfined()) {
            double[] rect = new double[4];
            MemorySegment actual = arena.allocate(16, 8);
            ObjC.sendRectRange(w.view(), "firstRectForCharacterRange:actualRange:", 0, 3, actual.address(), rect);
            double[] inWindow = ObjC.convertRect(w.view(), "convertRect:toView:", 20, 30, 1, 16, 0);
            double[] expected = ObjC.convertRect(w.window(), "convertRectToScreen:", inWindow[0], inWindow[1], inWindow[2],
                    inWindow[3]);
            for (int i = 0; i < 4; i++) {
                Check.near(expected[i], rect[i]);
            }
            Check.equal(0, actual.get(JAVA_LONG, 0));
            Check.equal(1, actual.get(JAVA_LONG, 8));        // its first grapheme
        } finally {
            w.close();
        }
    }

    public void testTheShownTextAndTheIndexAtAPoint() {
        Recorder r = new Recorder();
        MacWindow w = open(r);
        long pool = ObjC.poolPush();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment actual = arena.allocate(16, 8);
            long s = ObjC.sendWordRange(w.view(), "attributedSubstringForProposedRange:actualRange:", 1, 30,
                    actual.address());
            Check.equal("bcdef", ObjC.string(ObjC.send(s, "string")));     // clamped to the shown text
            Check.equal(1, actual.get(JAVA_LONG, 0));
            Check.equal(5, actual.get(JAVA_LONG, 8));
            Check.equal(0, ObjC.send(ObjC.send(w.view(), "validAttributesForMarkedText"), "count"));
            Check.equal(5, ObjC.sendWordPoint(w.view(), "characterIndexForPoint:", 10, 10));
            r.index = -1;
            Check.equal(ObjC.NOT_FOUND, ObjC.sendWordPoint(w.view(), "characterIndexForPoint:", 10, 10));
        } finally {
            ObjC.poolPop(pool);
            w.close();
        }
    }

    public void testTheEditMenuHasTheStandardItems() {
        long pool = ObjC.poolPush();
        try {
            long app = ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
            MacApp.installMenu(app);
            long bar = ObjC.send(app, "mainMenu");
            long edit = 0;
            for (long k = 0, n = ObjC.send(bar, "numberOfItems"); k < n; k++) {
                long sub = ObjC.send(ObjC.send(bar, "itemAtIndex:", k), "submenu");
                if (sub != 0 && "Edit".equals(ObjC.string(ObjC.send(sub, "title")))) {
                    edit = sub;
                }
            }
            Check.isTrue(edit != 0, "an Edit menu");
            String[] actions = {"undo:", "redo:", null, "cut:", "copy:", "paste:", "pasteAsPlainText:", "delete:", "selectAll:"};
            String[] keys = {"z", "Z", null, "x", "c", "v", "v", "", "a"};
            // A launched app gets AppKit's own items after these (AutoFill, Start Dictation, Emoji & Symbols).
            Check.isTrue(ObjC.send(edit, "numberOfItems") >= actions.length, "the standard items first");
            for (int k = 0; k < actions.length; k++) {
                long item = ObjC.send(edit, "itemAtIndex:", k);
                if (actions[k] == null) {
                    Check.isTrue(ObjC.sendBool(item, "isSeparatorItem"), "a separator at " + k);
                    continue;
                }
                Check.equal(ObjC.sel(actions[k]), ObjC.send(item, "action"));
                Check.equal(keys[k], ObjC.string(ObjC.send(item, "keyEquivalent")));
                Check.equal(1L << 20, ObjC.send(item, "keyEquivalentModifierMask") & (1L << 20));
            }
            // Paste and Match Style is ⌥⇧⌘V (as MyGO).
            Check.equal((1L << 20) | (1L << 19) | (1L << 17), ObjC.send(ObjC.send(edit, "itemAtIndex:", 6L), "keyEquivalentModifierMask"));
            Check.isTrue(!ObjC.sendBool(edit, "autoenablesItems"), "items always enabled (as MyGO)");
        } finally {
            ObjC.poolPop(pool);
        }
    }

    public void testMenuActionsBecomeEditCommands() {
        Recorder r = new Recorder();
        MacWindow w = open(r);
        try {
            ObjC.send(w.view(), "copy:", 0);
            ObjC.send(w.view(), "undo:", 0);
            ObjC.send(w.view(), "redo:", 0);
            ObjC.send(w.view(), "cut:", 0);
            ObjC.send(w.view(), "paste:", 0);
            ObjC.send(w.view(), "selectAll:", 0);
            ObjC.send(w.view(), "pasteAsPlainText:", 0);
            ObjC.send(w.view(), "delete:", 0);
            Check.equal(List.of("edit COPY", "edit UNDO", "edit REDO", "edit CUT", "edit PASTE", "edit SELECT_ALL",
                    "edit PASTE_AND_MATCH_STYLE", "edit DELETE"), r.events);
        } finally {
            w.close();
        }
    }

    public void testCursorsMapToAppKit() {
        Check.equal(ObjC.send(ObjC.cls("NSCursor"), "IBeamCursor"), MacCursors.of(jui.ui.Cursor.TEXT));
        Check.equal(ObjC.send(ObjC.cls("NSCursor"), "arrowCursor"), MacCursors.of(jui.ui.Cursor.DEFAULT));
        for (jui.ui.Cursor c : jui.ui.Cursor.values()) {
            Check.isTrue(MacCursors.of(c) != 0, "a cursor for " + c);
        }
    }
}
