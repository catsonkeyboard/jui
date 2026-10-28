package jui.text;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;

import jui.ffi.Native;
import jui.ffi.ObjC;
import jui.ffi.Sigs;

import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The Core Foundation, Core Text and Core Graphics functions the text engine
 * calls, through FFM. References are {@code long}s; functions whose names say
 * Create or Copy return owned references, released with {@link #release}.
 */
final class CoreText {
    private CoreText() {}

    private static final SymbolLookup CF = ObjC.framework("CoreFoundation");
    private static final SymbolLookup CT = ObjC.framework("CoreText");
    private static final SymbolLookup CG = ObjC.framework("CoreGraphics");

    private static final MemorySegment CF_RELEASE = ObjC.symbol(CF, "CFRelease");
    private static final MemorySegment CF_RETAIN = ObjC.symbol(CF, "CFRetain");
    private static final MemorySegment CF_STRING_CREATE_WITH_CHARACTERS = ObjC.symbol(CF, "CFStringCreateWithCharacters");
    private static final MemorySegment CF_DICTIONARY_CREATE = ObjC.symbol(CF, "CFDictionaryCreate");
    private static final MemorySegment CF_DICTIONARY_GET_VALUE = ObjC.symbol(CF, "CFDictionaryGetValue");
    private static final MemorySegment CF_ARRAY_GET_COUNT = ObjC.symbol(CF, "CFArrayGetCount");
    private static final MemorySegment CF_ARRAY_GET_VALUE_AT_INDEX = ObjC.symbol(CF, "CFArrayGetValueAtIndex");
    private static final MemorySegment CF_ATTRIBUTED_STRING_CREATE = ObjC.symbol(CF, "CFAttributedStringCreate");

    private static final MemorySegment CT_TYPESETTER_CREATE = ObjC.symbol(CT, "CTTypesetterCreateWithAttributedString");
    private static final MemorySegment CT_TYPESETTER_SUGGEST_LINE_BREAK = ObjC.symbol(CT, "CTTypesetterSuggestLineBreak");
    private static final MemorySegment CT_TYPESETTER_CREATE_LINE = ObjC.symbol(CT, "CTTypesetterCreateLine");
    private static final MemorySegment CT_LINE_GET_TYPOGRAPHIC_BOUNDS = ObjC.symbol(CT, "CTLineGetTypographicBounds");
    private static final MemorySegment CT_LINE_GET_GLYPH_RUNS = ObjC.symbol(CT, "CTLineGetGlyphRuns");
    private static final MemorySegment CT_LINE_GET_OFFSET = ObjC.symbol(CT, "CTLineGetOffsetForStringIndex");
    private static final MemorySegment CT_RUN_GET_ATTRIBUTES = ObjC.symbol(CT, "CTRunGetAttributes");
    private static final MemorySegment CT_RUN_GET_GLYPH_COUNT = ObjC.symbol(CT, "CTRunGetGlyphCount");
    private static final MemorySegment CT_RUN_GET_GLYPHS = ObjC.symbol(CT, "CTRunGetGlyphs");
    private static final MemorySegment CT_RUN_GET_POSITIONS = ObjC.symbol(CT, "CTRunGetPositions");
    private static final MemorySegment CT_RUN_GET_STRING_INDICES = ObjC.symbol(CT, "CTRunGetStringIndices");
    private static final MemorySegment CT_FONT_GET_BOUNDING_RECTS = ObjC.symbol(CT, "CTFontGetBoundingRectsForGlyphs");
    private static final MemorySegment CT_FONT_DRAW_GLYPHS = ObjC.symbol(CT, "CTFontDrawGlyphs");
    private static final MemorySegment CT_FONT_GET_SYMBOLIC_TRAITS = ObjC.symbol(CT, "CTFontGetSymbolicTraits");

    private static final MemorySegment CG_BITMAP_CONTEXT_CREATE = ObjC.symbol(CG, "CGBitmapContextCreate");
    private static final MemorySegment CG_CONTEXT_RELEASE = ObjC.symbol(CG, "CGContextRelease");
    private static final MemorySegment CG_CONTEXT_SET_SHOULD_ANTIALIAS = ObjC.symbol(CG, "CGContextSetShouldAntialias");
    private static final MemorySegment CG_CONTEXT_SET_SHOULD_SMOOTH_FONTS = ObjC.symbol(CG, "CGContextSetShouldSmoothFonts");
    private static final MemorySegment CG_CONTEXT_SET_ALLOWS_SUBPIXEL_POSITIONING =
            ObjC.symbol(CG, "CGContextSetAllowsFontSubpixelPositioning");
    private static final MemorySegment CG_CONTEXT_SET_SHOULD_SUBPIXEL_POSITION =
            ObjC.symbol(CG, "CGContextSetShouldSubpixelPositionFonts");
    private static final MemorySegment CG_CONTEXT_SET_ALLOWS_SUBPIXEL_QUANTIZATION =
            ObjC.symbol(CG, "CGContextSetAllowsFontSubpixelQuantization");
    private static final MemorySegment CG_CONTEXT_SET_SHOULD_SUBPIXEL_QUANTIZE =
            ObjC.symbol(CG, "CGContextSetShouldSubpixelQuantizeFonts");
    private static final MemorySegment CG_CONTEXT_SET_RGB_FILL_COLOR = ObjC.symbol(CG, "CGContextSetRGBFillColor");
    private static final MemorySegment CG_CONTEXT_SCALE_CTM = ObjC.symbol(CG, "CGContextScaleCTM");

    // Constants that the frameworks export as variables.
    static final long FONT_ATTRIBUTE_NAME = constant(CT, "kCTFontAttributeName");
    static final long LANGUAGE_ATTRIBUTE_NAME = constant(CT, "kCTLanguageAttributeName");
    private static final long DICTIONARY_KEY_CALLBACKS = ObjC.symbol(CF, "kCFTypeDictionaryKeyCallBacks").address();
    private static final long DICTIONARY_VALUE_CALLBACKS = ObjC.symbol(CF, "kCFTypeDictionaryValueCallBacks").address();

    static final long FONT_COLOR_GLYPHS_TRAIT = 1L << 13;
    static final long IMAGE_ALPHA_ONLY = 7;

    // Struct-passing argument and return memory (one thread at a time).
    private static final MemorySegment RANGE = Arena.global().allocate(Sigs.CF_RANGE);
    private static final SegmentAllocator RECT_RET = SegmentAllocator.prefixAllocator(
            Arena.global().allocate(Sigs.CG_RECT));

    /** The value of a framework's {@code const CFStringRef} variable. */
    private static long constant(SymbolLookup lib, String name) {
        return ObjC.symbol(lib, name).reinterpret(8).get(JAVA_LONG, 0);
    }

    static void release(long cf) {
        if (cf != 0) {
            Native.call(CF_RELEASE, cf);
        }
    }

    static long retain(long cf) {
        return cf == 0 ? 0 : Native.call(CF_RETAIN, cf);
    }

    /** A CFString of a Java string's UTF-16 code units, owned. */
    static long string(String s) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment chars = arena.allocate(JAVA_CHAR, Math.max(1, s.length()));
            for (int i = 0; i < s.length(); i++) {
                chars.setAtIndex(JAVA_CHAR, i, s.charAt(i));
            }
            return Native.call(CF_STRING_CREATE_WITH_CHARACTERS, 0L, chars.address(), s.length());
        }
    }

    /** A CFDictionary of CF keys and values, owned. */
    static long dictionary(long[] keys, long[] values) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment k = arena.allocateFrom(JAVA_LONG, keys);
            MemorySegment v = arena.allocateFrom(JAVA_LONG, values);
            return Native.call(CF_DICTIONARY_CREATE, 0L, k.address(), v.address(), keys.length,
                    DICTIONARY_KEY_CALLBACKS, DICTIONARY_VALUE_CALLBACKS);
        }
    }

    static long dictionaryGetValue(long dict, long key) {
        return Native.call(CF_DICTIONARY_GET_VALUE, dict, key);
    }

    static long arrayCount(long array) {
        return Native.call(CF_ARRAY_GET_COUNT, array);
    }

    static long arrayGet(long array, long i) {
        return Native.call(CF_ARRAY_GET_VALUE_AT_INDEX, array, i);
    }

    static long attributedString(long string, long attributes) {
        return Native.call(CF_ATTRIBUTED_STRING_CREATE, 0L, string, attributes);
    }

    static long typesetter(long attributedString) {
        return Native.call(CT_TYPESETTER_CREATE, attributedString);
    }

    static long suggestLineBreak(long typesetter, long start, double width) {
        try {
            return (long) Native.SUGGEST_LINE_BREAK.invokeExact(CT_TYPESETTER_SUGGEST_LINE_BREAK, typesetter, start, width);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static long createLine(long typesetter, long start, long length) {
        try {
            RANGE.set(JAVA_LONG, 0, start);
            RANGE.set(JAVA_LONG, 8, length);
            return (long) Native.CREATE_LINE.invokeExact(CT_TYPESETTER_CREATE_LINE, typesetter, RANGE);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** The line's width; ascent, descent and leading go to {@code metrics}, 3 doubles. */
    static double typographicBounds(long line, MemorySegment metrics) {
        try {
            return (double) Native.LINE_BOUNDS.invokeExact(CT_LINE_GET_TYPOGRAPHIC_BOUNDS, line,
                    metrics.address(), metrics.address() + 8, metrics.address() + 16);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static long glyphRuns(long line) {
        return Native.call(CT_LINE_GET_GLYPH_RUNS, line);
    }

    static double lineOffset(long line, long index) {
        try {
            return (double) Native.LINE_OFFSET.invokeExact(CT_LINE_GET_OFFSET, line, index, 0L);
        } catch (Throwable failure) {
            throw Native.rethrow(failure);
        }
    }

    static long runAttributes(long run) {
        return Native.call(CT_RUN_GET_ATTRIBUTES, run);
    }

    static long runGlyphCount(long run) {
        return Native.call(CT_RUN_GET_GLYPH_COUNT, run);
    }

    /** Copies all the run's glyphs (CGGlyph, 2 bytes each) to {@code buffer}. */
    static void runGlyphs(long run, MemorySegment buffer) {
        runGet(CT_RUN_GET_GLYPHS, run, buffer);
    }

    /** Copies all the run's glyph positions (CGPoint, 16 bytes each) to {@code buffer}. */
    static void runPositions(long run, MemorySegment buffer) {
        runGet(CT_RUN_GET_POSITIONS, run, buffer);
    }

    /** Copies the index in the string (UTF-16, CFIndex: 8 bytes each) of each of the run's glyphs to {@code buffer}. */
    static void runStringIndices(long run, MemorySegment buffer) {
        runGet(CT_RUN_GET_STRING_INDICES, run, buffer);
    }

    private static void runGet(MemorySegment fn, long run, MemorySegment buffer) {
        try {
            RANGE.set(JAVA_LONG, 0, 0); // {0, 0}: the whole run
            RANGE.set(JAVA_LONG, 8, 0);
            Native.RUN_GET.invokeExact(fn, run, RANGE, buffer.address());
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** The bounding rect of one glyph, in points with y up, to {@code rect} (a CGRect). */
    static void glyphBounds(long font, MemorySegment glyph, MemorySegment rect) {
        try {
            MemorySegment ignored = (MemorySegment) Native.GLYPH_RECTS.invokeExact(CT_FONT_GET_BOUNDING_RECTS,
                    RECT_RET, font, 0L, glyph.address(), rect.address(), 1L);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void drawGlyphs(long font, MemorySegment glyph, MemorySegment position, long context) {
        Native.call(CT_FONT_DRAW_GLYPHS, font, glyph.address(), position.address(), 1L, context);
    }

    static long symbolicTraits(long font) {
        return Native.call(CT_FONT_GET_SYMBOLIC_TRAITS, font) & 0xFFFFFFFFL;
    }

    /** A bitmap context of alpha alone over {@code pixels}, owned. */
    static long alphaContext(MemorySegment pixels, int width, int height) {
        return Native.call(CG_BITMAP_CONTEXT_CREATE, pixels.address(), width, height, 8L, width, 0L,
                IMAGE_ALPHA_ONLY);
    }

    /** Sets the context up to draw glyphs as AppKit does, smoothed with {@code fill}'s gray, or not. */
    static void prepareContext(long ctx, boolean smooth, double fill, double scale) {
        try {
            Native.call(CG_CONTEXT_SET_SHOULD_ANTIALIAS, ctx, 1);
            Native.call(CG_CONTEXT_SET_SHOULD_SMOOTH_FONTS, ctx, smooth ? 1 : 0);
            Native.call(CG_CONTEXT_SET_ALLOWS_SUBPIXEL_POSITIONING, ctx, 1);
            Native.call(CG_CONTEXT_SET_SHOULD_SUBPIXEL_POSITION, ctx, 1);
            Native.call(CG_CONTEXT_SET_ALLOWS_SUBPIXEL_QUANTIZATION, ctx, 0);
            Native.call(CG_CONTEXT_SET_SHOULD_SUBPIXEL_QUANTIZE, ctx, 0);
            Native.SET_FILL_RGBA.invokeExact(CG_CONTEXT_SET_RGB_FILL_COLOR, ctx, fill, fill, fill, 1.0);
            Native.SCALE_CTM.invokeExact(CG_CONTEXT_SCALE_CTM, ctx, scale, scale);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static void releaseContext(long ctx) {
        Native.call(CG_CONTEXT_RELEASE, ctx);
    }
}
