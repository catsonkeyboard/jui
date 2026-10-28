package jui.spike;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import jui.ffi.ObjC;
import jui.ffi.ObjCClass;

import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Checks the Objective-C methods that take and return ranges, rects and
 * points, as NSTextInputClient's do: it defines a class with one method of
 * each shape and calls each through objc_msgSend. In a native image a
 * signature left unregistered fails only when it is first called, so the
 * check runs there too ({@code ./build.sh ffi-check}).
 */
public final class FfiCheck {
    private FfiCheck() {}

    private static long cls;
    /** What the methods received, and what juiRange returns next. */
    private static String text;
    private static final long[] got = new long[4];
    private static final long[] rangeValue = new long[2];
    private static int menuWake;

    private static synchronized long define() {
        if (cls == 0) {
            cls = ObjCClass.define("JUIFfiCheck", "NSObject")
                    .protocol("NSTextInputClient")
                    .rangeMethod("juiRange", (self, cmd, out) -> {
                        out[0] = rangeValue[0];
                        out[1] = rangeValue[1];
                    })
                    .wordMethod("juiWord", (self, cmd) -> 42)
                    .textRangeMethod("juiText:range:", (self, cmd, s, location, length) -> {
                        text = ObjC.string(s);
                        got[0] = location;
                        got[1] = length;
                    })
                    .textRangesMethod("juiText:range:range:", (self, cmd, s, a, b, c, d) -> {
                        text = ObjC.string(s);
                        got[0] = a;
                        got[1] = b;
                        got[2] = c;
                        got[3] = d;
                    })
                    .wordRangeMethod("juiWordForRange:actual:", (self, cmd, location, length, actual) -> {
                        ObjC.writeRange(actual, location, 1);
                        return location * 10 + length;
                    })
                    .rectRangeMethod("juiRectForRange:actual:", (self, cmd, location, length, actual, out) -> {
                        out[0] = location + 0.5;
                        out[1] = length + 0.25;
                        out[2] = 11.5;
                        out[3] = 22.5;
                    })
                    .wordPointMethod("juiIndexAtPoint:", (self, cmd, x, y) -> (long) (x * 10 + y))
                    .boolWordPointWordMethod("juiMenuProbe:atLocation:inView:",
                            (self, cmd, item, x, y, view) -> item == 7 && x == 12.5 && y == -3.25 && view == 9)
                    .method1("juiMenuProbeWake:", (self, cmd, arg) -> menuWake++)
                    .register();
        }
        return cls;
    }

    /** What did not come through as sent: empty when all is well. */
    public static List<String> check() {
        List<String> bad = new ArrayList<>();
        long pool = ObjC.poolPush();
        try (Arena arena = Arena.ofConfined()) {
            long obj = ObjC.send(ObjC.send(define(), "alloc"), "init");
            try {
                if (!ObjC.sendBool(obj, "conformsToProtocol:", ObjC.protocol("NSTextInputClient"))) {
                    bad.add("the class does not adopt NSTextInputClient");
                }
                long[] r = new long[2];
                rangeValue[0] = 7;
                rangeValue[1] = 3;
                ObjC.sendRange(obj, "juiRange", r);
                expect(bad, "range", r[0] == 7 && r[1] == 3, r[0] + "," + r[1]);
                rangeValue[0] = ObjC.NOT_FOUND;
                rangeValue[1] = 0;
                ObjC.sendRange(obj, "juiRange", r);
                expect(bad, "range not found", r[0] == ObjC.NOT_FOUND && r[1] == 0, r[0] + "," + r[1]);

                ObjC.sendTextRange(obj, "juiText:range:", ObjC.nsString("拼音"), 2, 5);
                expect(bad, "text and range", "拼音".equals(text) && got[0] == 2 && got[1] == 5,
                        text + " " + got[0] + "," + got[1]);
                ObjC.sendTextRanges(obj, "juiText:range:range:", ObjC.nsString("ni"), 1, 0, ObjC.NOT_FOUND, 0);
                expect(bad, "text and ranges", "ni".equals(text) && got[0] == 1 && got[1] == 0
                        && got[2] == ObjC.NOT_FOUND && got[3] == 0, text + " " + got[0] + "," + got[1] + " " + got[2] + "," + got[3]);

                long w = ObjC.send(obj, "juiWord");
                expect(bad, "word", w == 42, Long.toString(w));

                MemorySegment actual = arena.allocate(16, 8);
                long v = ObjC.sendWordRange(obj, "juiWordForRange:actual:", 4, 2, actual.address());
                expect(bad, "word for a range", v == 42 && actual.get(JAVA_LONG, 0) == 4 && actual.get(JAVA_LONG, 8) == 1,
                        v + " actual " + actual.get(JAVA_LONG, 0) + "," + actual.get(JAVA_LONG, 8));
                v = ObjC.sendWordRange(obj, "juiWordForRange:actual:", 4, 2, 0);
                expect(bad, "word for a range without actualRange", v == 42, Long.toString(v));

                double[] rect = new double[4];
                ObjC.sendRectRange(obj, "juiRectForRange:actual:", 4, 2, 0, rect);
                expect(bad, "rect for a range", rect[0] == 4.5 && rect[1] == 2.25 && rect[2] == 11.5 && rect[3] == 22.5,
                        java.util.Arrays.toString(rect));

                long index = ObjC.sendWordPoint(obj, "juiIndexAtPoint:", 3, 4);
                expect(bad, "index at a point", index == 34, Long.toString(index));
                expect(bad, "popup menu ABI true", ObjC.popupMenu(obj, "juiMenuProbe:atLocation:inView:", 7, 12.5, -3.25, 9), "BOOL/point/words");
                expect(bad, "popup menu ABI false", !ObjC.popupMenu(obj, "juiMenuProbe:atLocation:inView:", 8, 12.5, -3.25, 9), "BOOL false");
                menuTimer(obj, bad);
            } finally {
                ObjC.release(obj);
            }
        } catch (RuntimeException e) {
            bad.add("failed: " + e);
        } finally {
            ObjC.poolPop(pool);
        }
        jui.text.TextEngine textEngine = new jui.text.TextEngine("zh-Hans");
        textEngine.checkRichFfi();
        jui.text.TextLayout lines = textEngine.layout("a😀\n中", 13, 400, 80);
        expect(bad, "text line offset", lines.lines == 2 && lines.caretX(3) > 0 && lines.lineStart(1) == 4,
                Integer.toString(lines.lines));
        return bad;
    }

    private static void menuTimer(long target, List<String> bad) {
        long common = ObjC.symbol(ObjC.framework("Foundation"), "NSRunLoopCommonModes").reinterpret(8).get(JAVA_LONG, 0);
        long mode = ObjC.nsString("NSEventTrackingRunLoopMode");
        var core = ObjC.framework("CoreFoundation");
        jui.ffi.Native.call(core.find("CFRunLoopAddCommonMode").orElseThrow(),
                jui.ffi.Native.call(core.find("CFRunLoopGetMain").orElseThrow()), mode);
        int before = menuWake;
        ObjC.performAfter(target, "juiMenuProbeWake:", 0, 0, ObjC.array(common));
        long loop = ObjC.send(ObjC.cls("NSRunLoop"), "mainRunLoop");
        long date = ObjC.send(ObjC.cls("NSDate"), "distantPast");
        long deadline = System.nanoTime() + 500_000_000;
        while (menuWake == before && System.nanoTime() < deadline) ObjC.send(loop, "runMode:beforeDate:", mode, date);
        expect(bad, "common-mode delayed selector", menuWake == before + 1, Integer.toString(menuWake - before));
    }

    private static void expect(List<String> bad, String what, boolean ok, String got) {
        if (!ok) {
            bad.add(what + ": got " + got);
        }
    }

    public static void main(String[] args) {
        List<String> bad = check();
        if (bad.isEmpty()) {
            System.out.println("FFI ok");
            return;
        }
        for (String b : bad) {
            System.out.println("FAIL: " + b);
        }
        System.exit(1);
    }
}
