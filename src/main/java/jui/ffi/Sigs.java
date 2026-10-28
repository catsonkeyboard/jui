package jui.ffi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Every native signature the app calls, or is called back with.
 *
 * <p>native-image generates a stub for each signature at build time, so they
 * all live here and {@link NativeImageFeature} registers these lists. As in
 * MyGO's {@code send}, pointers and Objective-C ids travel as {@code long}
 * (a machine word), which keeps the set of signatures small. Handles are made
 * from these very objects, so registration and use always match.
 */
public final class Sigs {
    private Sigs() {}

    public static final List<FunctionDescriptor> DOWNCALLS = new ArrayList<>();
    public static final List<FunctionDescriptor> UPCALLS = new ArrayList<>();

    // 64-bit Core Graphics and Metal structs.
    public static final StructLayout CG_POINT = MemoryLayout.structLayout(
            JAVA_DOUBLE.withName("x"), JAVA_DOUBLE.withName("y"));
    public static final StructLayout CG_SIZE = MemoryLayout.structLayout(
            JAVA_DOUBLE.withName("width"), JAVA_DOUBLE.withName("height"));
    public static final StructLayout CG_RECT = MemoryLayout.structLayout(
            CG_POINT.withName("origin"), CG_SIZE.withName("size"));
    public static final StructLayout MTL_CLEAR_COLOR = MemoryLayout.structLayout(
            JAVA_DOUBLE.withName("red"), JAVA_DOUBLE.withName("green"),
            JAVA_DOUBLE.withName("blue"), JAVA_DOUBLE.withName("alpha"));

    // C functions and objc_msgSend whose arguments are all words: long f(long...).
    // A void function goes through these too; its return value is ignored.
    public static final FunctionDescriptor W0 = down(FunctionDescriptor.of(JAVA_LONG));
    public static final FunctionDescriptor W1 = down(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
    public static final FunctionDescriptor W2 = down(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
    public static final FunctionDescriptor W3 = down(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    public static final FunctionDescriptor W4 = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    public static final FunctionDescriptor W5 = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    public static final FunctionDescriptor W6 = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    public static final FunctionDescriptor W7 = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));

    /** int f(void): getpid, pthread_main_np. */
    public static final FunctionDescriptor INT0 = down(FunctionDescriptor.of(JAVA_INT));
    /** int sysctl(int *name, u_int namelen, void *oldp, size_t *oldlenp, void *newp, size_t newlen). */
    public static final FunctionDescriptor SYSCTL = down(FunctionDescriptor.of(JAVA_INT,
            JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));

    // Core Text and Core Graphics.
    /** CFRange: location and length. */
    public static final StructLayout CF_RANGE = MemoryLayout.structLayout(
            JAVA_LONG.withName("location"), JAVA_LONG.withName("length"));
    /** CFIndex CTTypesetterSuggestLineBreak(CTTypesetterRef, CFIndex start, double width). */
    public static final FunctionDescriptor SUGGEST_LINE_BREAK = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, JAVA_DOUBLE));
    /** CTLineRef CTTypesetterCreateLine(CTTypesetterRef, CFRange). */
    public static final FunctionDescriptor CREATE_LINE = down(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, CF_RANGE));
    /** void CTRunGetGlyphs / CTRunGetPositions(CTRunRef, CFRange, buffer). */
    public static final FunctionDescriptor RUN_GET = down(FunctionDescriptor.ofVoid(JAVA_LONG, CF_RANGE, JAVA_LONG));
    /** double CTLineGetTypographicBounds(CTLineRef, CGFloat *ascent, CGFloat *descent, CGFloat *leading). */
    public static final FunctionDescriptor LINE_BOUNDS = down(FunctionDescriptor.of(JAVA_DOUBLE,
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    public static final FunctionDescriptor LINE_OFFSET = down(FunctionDescriptor.of(JAVA_DOUBLE,
            JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** CGRect CTFontGetBoundingRectsForGlyphs(CTFontRef, orientation, const CGGlyph *, CGRect *, CFIndex). */
    public static final FunctionDescriptor GLYPH_RECTS = down(FunctionDescriptor.of(CG_RECT,
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** void CGContextSetRGBFillColor(CGContextRef, CGFloat r, g, b, a). */
    public static final FunctionDescriptor SET_FILL_RGBA = down(FunctionDescriptor.ofVoid(JAVA_LONG,
            JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));
    /** void CGContextScaleCTM(CGContextRef, CGFloat sx, CGFloat sy). */
    public static final FunctionDescriptor SCALE_CTM = down(FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_DOUBLE, JAVA_DOUBLE));

    // Metal.
    /** MTLRegion: origin x, y, z and size width, height, depth. */
    public static final StructLayout MTL_REGION = MemoryLayout.structLayout(
            JAVA_LONG.withName("x"), JAVA_LONG.withName("y"), JAVA_LONG.withName("z"),
            JAVA_LONG.withName("width"), JAVA_LONG.withName("height"), JAVA_LONG.withName("depth"));
    /** MTLScissorRect: x, y, width, height in pixels. */
    public static final StructLayout MTL_SCISSOR = MemoryLayout.structLayout(
            JAVA_LONG.withName("x"), JAVA_LONG.withName("y"), JAVA_LONG.withName("width"), JAVA_LONG.withName("height"));
    /** void (id, SEL, MTLScissorRect): setScissorRect:. */
    public static final FunctionDescriptor SET_SCISSOR = down(FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, MTL_SCISSOR));
    /** void (id, SEL, MTLRegion, NSUInteger, const void *, NSUInteger): replaceRegion:mipmapLevel:withBytes:bytesPerRow:. */
    public static final FunctionDescriptor REPLACE_REGION = down(FunctionDescriptor.ofVoid(
            JAVA_LONG, JAVA_LONG, MTL_REGION, JAVA_LONG, JAVA_LONG, JAVA_LONG));

    // objc_msgSend with floats or structs, which travel in other registers.
    /** CGRect (id, SEL): bounds, frame. */
    public static final FunctionDescriptor RECT_OF = down(FunctionDescriptor.of(CG_RECT, JAVA_LONG, JAVA_LONG));
    /** double (id, SEL): backingScaleFactor, presentedTime. */
    public static final FunctionDescriptor DOUBLE_OF = down(FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG, JAVA_LONG));
    /** void (id, SEL, double): setContentsScale:. */
    public static final FunctionDescriptor SET_DOUBLE = down(FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_DOUBLE));
    /** id (id, SEL, double, double): systemFontOfSize:weight:. */
    public static final FunctionDescriptor DOUBLE2_ID = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, JAVA_DOUBLE, JAVA_DOUBLE));
    /** void (id, SEL, CGSize): setDrawableSize:, and setFrameSize: through objc_msgSendSuper. */
    public static final FunctionDescriptor SET_SIZE = down(FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, CG_SIZE));
    /** NSPoint [obj sel], as -[NSEvent locationInWindow]. */
    public static final FunctionDescriptor POINT_OF = down(FunctionDescriptor.of(CG_POINT, JAVA_LONG, JAVA_LONG));
    /** NSPoint [view convertPoint:p fromView:other]. */
    public static final FunctionDescriptor CONVERT_POINT = down(FunctionDescriptor.of(CG_POINT,
            JAVA_LONG, JAVA_LONG, CG_POINT, JAVA_LONG));
    /** [area initWithRect:options:owner:userInfo:]. */
    public static final FunctionDescriptor INIT_TRACKING_AREA = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, CG_RECT, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** void (id, SEL, CGRect): setFrame:. */
    public static final FunctionDescriptor SET_RECT = down(FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, CG_RECT));
    /** id (id, SEL, CGRect): initWithFrame:. */
    public static final FunctionDescriptor INIT_RECT = down(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, CG_RECT));
    /** id (id, SEL, CGRect, NSUInteger, NSUInteger, BOOL): initWithContentRect:styleMask:backing:defer:. */
    public static final FunctionDescriptor INIT_WINDOW = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, CG_RECT, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** void (id, SEL, MTLClearColor): setClearColor:. */
    public static final FunctionDescriptor SET_CLEAR_COLOR = down(FunctionDescriptor.ofVoid(
            JAVA_LONG, JAVA_LONG, MTL_CLEAR_COLOR));
    /** void (id, SEL, SEL, id, NSTimeInterval): performSelector:withObject:afterDelay:. */
    public static final FunctionDescriptor PERFORM_AFTER = down(FunctionDescriptor.ofVoid(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_DOUBLE));
    public static final FunctionDescriptor PERFORM_AFTER_MODES = down(FunctionDescriptor.ofVoid(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_DOUBLE, JAVA_LONG));
    public static final FunctionDescriptor POPUP_MENU = down(FunctionDescriptor.of(JAVA_BOOLEAN,
            JAVA_LONG, JAVA_LONG, JAVA_LONG, CG_POINT, JAVA_LONG));
    /** CGRect (id, SEL, CGRect, id): convertRect:toView:, convertRect:fromView:. */
    public static final FunctionDescriptor RECT_RECT_W = down(FunctionDescriptor.of(CG_RECT,
            JAVA_LONG, JAVA_LONG, CG_RECT, JAVA_LONG));
    /** CGRect (id, SEL, CGRect): convertRectToScreen:, convertRectFromScreen:. */
    public static final FunctionDescriptor RECT_RECT = down(FunctionDescriptor.of(CG_RECT, JAVA_LONG, JAVA_LONG, CG_RECT));

    // NSTextInputClient's shapes, for calling such methods (FfiCheck, tests).
    /** NSRange: location and length, the same layout as CFRange. */
    public static final StructLayout NS_RANGE = CF_RANGE;
    /** NSRange (id, SEL). */
    public static final FunctionDescriptor MSG_RANGE = down(FunctionDescriptor.of(NS_RANGE, JAVA_LONG, JAVA_LONG));
    /** void (id, SEL, id, NSRange). */
    public static final FunctionDescriptor MSG_V_W_RANGE = down(FunctionDescriptor.ofVoid(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, NS_RANGE));
    /** void (id, SEL, id, NSRange, NSRange). */
    public static final FunctionDescriptor MSG_V_W_RANGE2 = down(FunctionDescriptor.ofVoid(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, NS_RANGE, NS_RANGE));
    /** id (id, SEL, NSRange, NSRange *). */
    public static final FunctionDescriptor MSG_W_RANGE_W = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, NS_RANGE, JAVA_LONG));
    /** NSRect (id, SEL, NSRange, NSRange *). */
    public static final FunctionDescriptor MSG_RECT_RANGE_W = down(FunctionDescriptor.of(CG_RECT,
            JAVA_LONG, JAVA_LONG, NS_RANGE, JAVA_LONG));
    /** NSUInteger (id, SEL, NSPoint). */
    public static final FunctionDescriptor MSG_W_POINT = down(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, CG_POINT));

    // Objective-C methods implemented in Java (IMPs). BOOL is C's bool on arm64.
    /** void (id, SEL). */
    public static final FunctionDescriptor IMP_V = up(FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
    /** BOOL (id, SEL). */
    public static final FunctionDescriptor IMP_B = up(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_LONG, JAVA_LONG));
    /** void (id, SEL, id). */
    public static final FunctionDescriptor IMP_V_W = up(FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** BOOL (id, SEL, id). */
    public static final FunctionDescriptor IMP_B_W = up(FunctionDescriptor.of(JAVA_BOOLEAN,
            JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** void (id, SEL, NSSize). */
    public static final FunctionDescriptor IMP_V_SIZE = up(FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, CG_SIZE));
    /** NSRange (id, SEL): selectedRange, markedRange. */
    public static final FunctionDescriptor IMP_RANGE = up(FunctionDescriptor.of(NS_RANGE, JAVA_LONG, JAVA_LONG));
    /** id (id, SEL): validAttributesForMarkedText. */
    public static final FunctionDescriptor IMP_W = up(FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** void (id, SEL, id, NSRange): insertText:replacementRange:. */
    public static final FunctionDescriptor IMP_V_W_RANGE = up(FunctionDescriptor.ofVoid(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, NS_RANGE));
    /** void (id, SEL, id, NSRange, NSRange): setMarkedText:selectedRange:replacementRange:. */
    public static final FunctionDescriptor IMP_V_W_RANGE2 = up(FunctionDescriptor.ofVoid(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, NS_RANGE, NS_RANGE));
    /** id (id, SEL, NSRange, NSRange *): attributedSubstringForProposedRange:actualRange:. */
    public static final FunctionDescriptor IMP_W_RANGE_W = up(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, NS_RANGE, JAVA_LONG));
    /** NSRect (id, SEL, NSRange, NSRange *): firstRectForCharacterRange:actualRange:. */
    public static final FunctionDescriptor IMP_RECT_RANGE_W = up(FunctionDescriptor.of(CG_RECT,
            JAVA_LONG, JAVA_LONG, NS_RANGE, JAVA_LONG));
    /** NSUInteger (id, SEL, NSPoint): characterIndexForPoint:. */
    public static final FunctionDescriptor IMP_W_POINT = up(FunctionDescriptor.of(JAVA_LONG,
            JAVA_LONG, JAVA_LONG, CG_POINT));

    private static FunctionDescriptor down(FunctionDescriptor fd) {
        DOWNCALLS.add(fd);
        return fd;
    }

    private static FunctionDescriptor up(FunctionDescriptor fd) {
        UPCALLS.add(fd);
        return fd;
    }
}
