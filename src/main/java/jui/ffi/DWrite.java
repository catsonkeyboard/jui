package jui.ffi;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The DirectWrite pieces the Windows text backend calls, through FFM
 * downcalls with no C shim: DWriteCreateFactory, and COM methods, which are
 * read out of the object's vtable and passed the object as their first
 * argument. Handles are built once from fixed descriptors; main thread only.
 */
public final class DWrite {
    /** IID_IDWriteFactory {b859ee5a-d838-4b5b-a2e8-1adc7d93db48}, GUID bytes in memory order. */
    public static final MemorySegment IID_IDWRITE_FACTORY = guid(Arena.global(),
            (byte) 0x5a, (byte) 0xee, (byte) 0x59, (byte) 0xb8,  // Data1, little-endian
            (byte) 0x38, (byte) 0xd8,                             // Data2, little-endian
            (byte) 0x5b, (byte) 0x4b,                             // Data3, little-endian
            (byte) 0xa2, (byte) 0xe8, (byte) 0x1a, (byte) 0xdc,  // Data4, in order
            (byte) 0x7d, (byte) 0x93, (byte) 0xdb, (byte) 0x48);
    public static final int MEASURING_NATURAL = 0;
    /** DWRITE_RENDERING_MODE_NATURAL, which ClearType natural is. */
    public static final int RENDERING_NATURAL = 4;
    /** DWRITE_TEXTURE_CLEARTYPE_3x1. */
    public static final int TEXTURE_CLEARTYPE = 1;
    /** DWRITE_WORD_WRAPPING_WRAP and NO_WRAP. */
    public static final int WRAP = 0, NO_WRAP = 1;

    /** S_OK. */
    public static final int OK = 0;

    // ---- COM vtable slots, IUnknown's 0-2 first ----

    public static final int COM_RELEASE = 2;

    // IDWriteFactory
    /** 3: IDWriteFactory::GetSystemFontCollection. */
    public static final int FACTORY_GET_SYSTEM_FONT_COLLECTION = 3;
    /** 9: IDWriteFactory::CreateFontFace. */
    public static final int FACTORY_CREATE_FONT_FACE = 9;
    /** 13: IDWriteFont::CreateFontFace. */
    public static final int FONT_CREATE_FONT_FACE = 13;
    /** 15: IDWriteFactory::CreateTextFormat. */
    public static final int FACTORY_CREATE_TEXT_FORMAT = 15;
    /** 18: IDWriteFactory::CreateTextLayout. */
    public static final int FACTORY_CREATE_TEXT_LAYOUT = 18;
    /** 21: IDWriteFactory::CreateTextAnalyzer. */
    public static final int FACTORY_CREATE_TEXT_ANALYZER = 21;
    /** 23: IDWriteFactory::CreateGlyphRunAnalysis. */
    public static final int FACTORY_CREATE_GLYPH_RUN_ANALYSIS = 23;

    // IDWriteFontFace
    /** 8: IDWriteFontFace::GetMetrics. */
    public static final int FONT_FACE_GET_METRICS = 8;
    /** 10: IDWriteFontFace::GetDesignGlyphMetrics. */
    public static final int FONT_FACE_GET_DESIGN_GLYPH_METRICS = 10;

    // IDWriteTextFormat
    /** 5: IDWriteTextFormat::SetWordWrapping. */
    public static final int FORMAT_SET_WORD_WRAPPING = 5;

    // IDWriteTextLayout (after IDWriteTextFormat's 25 slots)
    /** 31: IDWriteTextLayout::SetFontFamilyName. */
    public static final int LAYOUT_SET_FONT_FAMILY_NAME = 31;
    /** 32: IDWriteTextLayout::SetFontWeight. */
    public static final int LAYOUT_SET_FONT_WEIGHT = 32;
    /** 33: IDWriteTextLayout::SetFontStyle. */
    public static final int LAYOUT_SET_FONT_STYLE = 33;
    /** 35: IDWriteTextLayout::SetFontSize. */
    public static final int LAYOUT_SET_FONT_SIZE = 35;
    /** 59: IDWriteTextLayout::GetLineMetrics. */
    public static final int LAYOUT_GET_LINE_METRICS = 59;
    /** 65: IDWriteTextLayout::HitTestTextPosition. */
    public static final int LAYOUT_HIT_TEST_TEXT_POSITION = 65;

    // IDWriteGlyphRunAnalysis
    /** 4: IDWriteGlyphRunAnalysis::CreateAlphaTexture. */
    public static final int GRA_CREATE_ALPHA_TEXTURE = 4;

    // IDWriteTextAnalyzer
    /** 7: IDWriteTextAnalyzer::GetGlyphs. */
    public static final int ANALYZER_GET_GLYPHS = 7;
    /** 8: IDWriteTextAnalyzer::GetGlyphPlacements. */
    public static final int ANALYZER_GET_GLYPH_PLACEMENTS = 8;

    // ---- struct layouts ----

    /** DWRITE_LINE_METRICS. */
    public static final StructLayout LINE_METRICS = MemoryLayout.structLayout(
            JAVA_INT.withName("length"),
            JAVA_INT.withName("trailingWhitespaceLength"),
            JAVA_INT.withName("newlineLength"),
            JAVA_FLOAT.withName("height"),
            JAVA_FLOAT.withName("baseline"),
            JAVA_INT.withName("isTrimmed")).withName("DWRITE_LINE_METRICS");

    /** DWRITE_FONT_METRICS, all in design units. */
    public static final StructLayout FONT_METRICS = MemoryLayout.structLayout(
            JAVA_SHORT.withName("designUnitsPerEm"),
            JAVA_SHORT.withName("ascent"),
            JAVA_SHORT.withName("descent"),
            JAVA_SHORT.withName("lineGap"),
            JAVA_SHORT.withName("capHeight"),
            JAVA_SHORT.withName("xHeight"),
            JAVA_SHORT.withName("underlinePosition"),
            JAVA_SHORT.withName("underlineThickness"),
            JAVA_SHORT.withName("strikethroughPosition"),
            JAVA_SHORT.withName("strikethroughThickness")).withName("DWRITE_FONT_METRICS");

    /** DWRITE_GLYPH_METRICS, in design units. */
    public static final StructLayout GLYPH_METRICS = MemoryLayout.structLayout(
            JAVA_INT.withName("leftSideBearing"),
            JAVA_INT.withName("advanceWidth"),
            JAVA_INT.withName("rightSideBearing"),
            JAVA_INT.withName("topSideBearing"),
            JAVA_INT.withName("advanceHeight"),
            JAVA_INT.withName("bottomSideBearing"),
            JAVA_INT.withName("verticalOriginY")).withName("DWRITE_GLYPH_METRICS");

    /** DWRITE_SCRIPT_ANALYSIS: USHORT script; union { INT shapes; } (4 bytes). */
    public static final StructLayout SCRIPT_ANALYSIS = MemoryLayout.structLayout(
            JAVA_SHORT.withName("script"),
            MemoryLayout.paddingLayout(2),
            JAVA_INT.withName("shapes")).withName("DWRITE_SCRIPT_ANALYSIS");

    /** DWRITE_GLYPH_OFFSET. */
    public static final StructLayout GLYPH_OFFSET = MemoryLayout.structLayout(
            JAVA_FLOAT.withName("advanceOffset"),
            JAVA_FLOAT.withName("ascenderOffset")).withName("DWRITE_GLYPH_OFFSET");

    /** DWRITE_GLYPH_RUN on x64: fontFace@0, emSize@8, glyphCount@12, glyphIndices@16,
     * glyphAdvances@24, glyphOffsets@32, isSideways@40, bidiLevel@44; 48 bytes. */
    public static final StructLayout GLYPH_RUN = MemoryLayout.structLayout(
            ADDRESS.withName("fontFace"),
            JAVA_FLOAT.withName("emSize"),
            JAVA_INT.withName("glyphCount"),
            ADDRESS.withName("glyphIndices"),
            ADDRESS.withName("glyphAdvances"),
            ADDRESS.withName("glyphOffsets"),
            JAVA_INT.withName("isSideways"),
            JAVA_INT.withName("bidiLevel")).withName("DWRITE_GLYPH_RUN");


    /** Win32 RECT, for alpha texture bounds. */
    public static final StructLayout RECT = MemoryLayout.structLayout(
            JAVA_INT.withName("left"), JAVA_INT.withName("top"),
            JAVA_INT.withName("right"), JAVA_INT.withName("bottom")).withName("RECT");

    private static final Linker LINKER = Linker.nativeLinker();
    /** An address layout whose loads return segments that cover everything, for vtable walks. */
    private static final AddressLayout FAR = ADDRESS.withTargetLayout(JAVA_LONG);

    // DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED=0, IID_IDWriteFactory, &out).

    private static final MethodHandle CREATE_FACTORY = LINKER.downcallHandle(
            Win32.symbol("dwrite", "DWriteCreateFactory"),
            FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));

    // Typed COM method descriptors; the function address is the first argument.

    // HRESULT (this, BOOL) — GetSystemFontCollection takes (out, checkForUpdates); SetWordWrapping takes (mode).
    private static final MethodHandle CALL_OUT_WORD = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle CALL_WORD = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT));
    // CreateTextFormat(family, collection, weight, style, stretch, size, locale, &format)
    private static final MethodHandle CREATE_TEXT_FORMAT = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_FLOAT, ADDRESS, ADDRESS));
    // CreateTextLayout(text, length, format, maxWidth, maxHeight, &layout)
    private static final MethodHandle CREATE_TEXT_LAYOUT = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_FLOAT, JAVA_FLOAT, ADDRESS));
    // GetLineMetrics(array, count, &actual)
    private static final MethodHandle GET_LINE_METRICS = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    // HitTestTextPosition(index, isTrailing(BOOL=4 bytes), &x, &y, &isInside(BOOL))
    private static final MethodHandle HIT_TEST_TEXT_POSITION = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    // CreateGlyphRunAnalysis(run, ppdip, transform, rendering, measuring, x, y, &out)
    private static final MethodHandle CREATE_GLYPH_RUN_ANALYSIS = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_FLOAT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_FLOAT, JAVA_FLOAT, ADDRESS));
    // GetGlyphs(text, length, fontFace, sideways, rtl, analysis, locale, subst, features, featLens, ranges, max, clustermap, textProps, glyphIndices, glyphProps, &actual)
    private static final MethodHandle GET_GLYPHS = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
            ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    // GetGlyphPlacements(text, clustermap, props, length, glyphIndices, glyphProps, glyphCount, fontFace, emSize, sideways, rtl, analysis, locale, features, featLens, ranges, advances, offsets)
    private static final MethodHandle GET_GLYPH_PLACEMENTS = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS,
            JAVA_FLOAT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    // GetDesignGlyphMetrics(indices, count, metrics, isSideways)
    private static final MethodHandle GET_DESIGN_GLYPH_METRICS = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
    // CreateAlphaTexture(type, &rect, buffer, size)
    private static final MethodHandle CREATE_ALPHA_TEXTURE = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    // SetFontFamilyName(family, range) / SetFontWeight(weight, range)
    // SetFontStyle(style, range) / SetFontSize(size, range). DWRITE_TEXT_RANGE
    // passes by value: its 8 bytes travel whole in one integer register, so
    // the range goes as a packed long, not a pointer.
    private static final MethodHandle SET_FONT_FAMILY_NAME = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG));
    private static final MethodHandle SET_FONT_WEIGHT = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
    private static final MethodHandle SET_FONT_STYLE = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
    private static final MethodHandle SET_FONT_SIZE = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_FLOAT, JAVA_LONG));
    // GetMetrics(&fontMetrics) on a font face
    private static final MethodHandle GET_FONT_METRICS = LINKER.downcallHandle(
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    // CreateFontFace(type, files, count, indices, simFlags, &face)
    private static final MethodHandle CREATE_FONT_FACE = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    // CreateTextAnalyzer(&analyzer)
    private static final MethodHandle CREATE_TEXT_ANALYZER = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS));
    // Release()
    private static final MethodHandle RELEASE = com(FunctionDescriptor.of(
            JAVA_INT, ADDRESS));

    private static MethodHandle com(FunctionDescriptor fd) {
        return LINKER.downcallHandle(fd);
    }

    /** A raw COM call of slot {@code slot} on {@code object}: fn(object, a). */
    public static long raw(long object, int slot, long a) {
        return Native.call(method(MemorySegment.ofAddress(object), slot), object, a);
    }

    /** fn(object, a, b). */
    public static long raw(long object, int slot, long a, long b) {
        return Native.call(method(MemorySegment.ofAddress(object), slot), object, a, b);
    }

    /** fn(object, a, b, c). */
    public static long raw(long object, int slot, long a, long b, long c) {
        return Native.call(method(MemorySegment.ofAddress(object), slot), object, a, b, c);
    }

    /** fn(object, a, b, c, d). */
    public static long raw(long object, int slot, long a, long b, long c, long d) {
        return Native.call(method(MemorySegment.ofAddress(object), slot), object, a, b, c, d);
    }

    /** The method in vtable slot {@code slot} of a COM {@code object}. */
    public static MemorySegment method(MemorySegment object, int slot) {
        MemorySegment vtable = object.reinterpret(8).get(FAR, 0);
        return vtable.reinterpret(8L * (slot + 1)).getAtIndex(FAR, slot);
    }

    /** Writes the 16 GUID bytes into fresh memory. */
    public static MemorySegment guid(Arena arena, byte... bytes) {
        MemorySegment g = arena.allocate(16);
        for (int i = 0; i < 16; i++) g.set(JAVA_BYTE, i, bytes[i]);
        return g;
    }

    /** Encodes {@code text} as NUL-terminated UTF-16LE. */
    public static MemorySegment wide(Arena arena, String text) {
        MemorySegment w = arena.allocate(2L * (text.length() + 1));
        for (int i = 0; i < text.length(); i++) w.set(JAVA_SHORT, 2L * i, (short) text.charAt(i));
        return w;
    }

    private static RuntimeException failed(String what, int hr) {
        return new IllegalStateException(what + " failed: HRESULT 0x" + Integer.toHexString(hr));
    }

    // ---- typed calls, one per method the backend uses ----

    /** DWriteCreateFactory(shared, IID, out); the factory pointer is left in out. */
    public static void createFactory(MemorySegment out) {
        int hr;
        try {
            hr = (int) CREATE_FACTORY.invokeExact(0, IID_IDWRITE_FACTORY, out);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("DWriteCreateFactory", hr);
    }

    public static void release(MemorySegment object) {
        try {
            int released = (int) RELEASE.invokeExact(method(object, COM_RELEASE), object);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** IDWriteFactory::GetSystemFontCollection(&collection, FALSE), slot 3. */
    public static void getSystemFontCollection(MemorySegment factory, MemorySegment collection) {
        int hr;
        try {
            hr = (int) CALL_OUT_WORD.invokeExact(method(factory, FACTORY_GET_SYSTEM_FONT_COLLECTION), factory, collection, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("GetSystemFontCollection", hr);
    }

    /** IDWriteFactory::CreateFontFace(...), slot 9, from one file; the face pointer is left in out. */
    public static void createFontFace(MemorySegment factory, int fontFaceType, MemorySegment files,
                                      int numberOfFiles, MemorySegment faceIndices, int simFlags, MemorySegment out) {
        int hr;
        try {
            hr = (int) CREATE_FONT_FACE.invokeExact(method(factory, FACTORY_CREATE_FONT_FACE), factory,
                    fontFaceType, files, numberOfFiles, faceIndices, simFlags, out);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("CreateFontFace", hr);
    }

    /** IDWriteFactory::CreateTextFormat, slot 15; the format pointer is left in out. */
    public static void createTextFormat(long factory, MemorySegment family, int weight, int style,
                                        int stretch, float size, MemorySegment locale, MemorySegment out) {
        createTextFormat(MemorySegment.ofAddress(factory), family, weight, style, stretch, size, locale, out);
    }

    public static void createTextLayout(long factory, MemorySegment text, int length, MemorySegment format,
                                        float maxWidth, float maxHeight, MemorySegment out) {
        createTextLayout(MemorySegment.ofAddress(factory), text, length, format, maxWidth, maxHeight, out);
    }

    public static void createTextFormat(MemorySegment factory, MemorySegment family, int weight, int style,
                                        int stretch, float size, MemorySegment locale, MemorySegment out) {
        int hr;
        try {
            hr = (int) CREATE_TEXT_FORMAT.invokeExact(method(factory, FACTORY_CREATE_TEXT_FORMAT), factory,
                    family, MemorySegment.NULL, weight, style, stretch, size, locale, out);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("CreateTextFormat", hr);
    }

    /** IDWriteFactory::CreateTextLayout, slot 18; the layout pointer is left in out. */
    public static void createTextLayout(MemorySegment factory, MemorySegment text, int length,
                                        MemorySegment format, float maxWidth, float maxHeight, MemorySegment out) {
        int hr;
        try {
            hr = (int) CREATE_TEXT_LAYOUT.invokeExact(method(factory, FACTORY_CREATE_TEXT_LAYOUT), factory,
                    text, length, format, maxWidth, maxHeight, out);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("CreateTextLayout", hr);
    }

    /** IDWriteTextFormat::SetWordWrapping, slot 5. */
    public static void setWordWrapping(MemorySegment format, int wrapping) {
        int hr;
        try {
            hr = (int) CALL_WORD.invokeExact(method(format, FORMAT_SET_WORD_WRAPPING), format, wrapping);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("SetWordWrapping", hr);
    }

    /** IDWriteFactory::CreateTextAnalyzer, slot 21; the analyzer is left in out. */
    public static void createTextAnalyzer(MemorySegment factory, MemorySegment out) {
        int hr;
        try {
            hr = (int) CREATE_TEXT_ANALYZER.invokeExact(method(factory, FACTORY_CREATE_TEXT_ANALYZER), factory, out);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("CreateTextAnalyzer", hr);
    }

    /** IDWriteTextLayout::GetLineMetrics, slot 59; returns the line count written. */
    public static int getLineMetrics(MemorySegment layout, MemorySegment array, int capacity, MemorySegment actual) {
        int hr;
        try {
            hr = (int) GET_LINE_METRICS.invokeExact(method(layout, LAYOUT_GET_LINE_METRICS), layout,
                    array, capacity, actual);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("GetLineMetrics", hr);
        return actual.get(JAVA_INT, 0);
    }

    /** The range setters the layout applies before lines are measured;
     * {@code range} packs DWRITE_TEXT_RANGE as (length << 32) | startPosition. */
    public static void setFontFamilyName(MemorySegment layout, MemorySegment family, long range) {
        int hr;
        try {
            hr = (int) SET_FONT_FAMILY_NAME.invokeExact(method(layout, LAYOUT_SET_FONT_FAMILY_NAME), layout,
                    family, range);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("SetFontFamilyName", hr);
    }

    public static void setFontWeight(MemorySegment layout, int weight, long range) {
        int hr;
        try {
            hr = (int) SET_FONT_WEIGHT.invokeExact(method(layout, LAYOUT_SET_FONT_WEIGHT), layout, weight, range);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("SetFontWeight", hr);
    }

    public static void setFontStyle(MemorySegment layout, int style, long range) {
        int hr;
        try {
            hr = (int) SET_FONT_STYLE.invokeExact(method(layout, LAYOUT_SET_FONT_STYLE), layout, style, range);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("SetFontStyle", hr);
    }

    public static void setFontSize(MemorySegment layout, float size, long range) {
        int hr;
        try {
            hr = (int) SET_FONT_SIZE.invokeExact(method(layout, LAYOUT_SET_FONT_SIZE), layout, size, range);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("SetFontSize", hr);
    }

    /** IDWriteTextLayout::HitTestTextPosition, slot 65; x and y (FLOAT) are written into out[0..1]. */
    /** HitTestTextPosition's x alone, in DIPs from the line's left. */
    public static float hitTestX(MemorySegment layout, int index, boolean trailing) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment xy = arena.allocate(2 * JAVA_FLOAT.byteSize());
            MemorySegment hit = arena.allocate(32); // DWRITE_HIT_TEST_METRICS
            hitTestTextPosition(layout, index, trailing, xy, hit);
            return xy.get(JAVA_FLOAT, 0);
        }
    }

    public static void hitTestTextPosition(MemorySegment layout, int index, boolean trailing, MemorySegment outXY,
                                           MemorySegment hit) {
        try {
            // The BOOL return is isInside; the x and y are written either way.
            int inside = (int) HIT_TEST_TEXT_POSITION.invokeExact(
                    method(layout, LAYOUT_HIT_TEST_TEXT_POSITION), layout, index, trailing ? 1 : 0, outXY,
                    outXY.asSlice(4), hit);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** IDWriteFontFace::GetMetrics, slot 8. */
    public static void getFontMetrics(MemorySegment face, MemorySegment metrics) {
        try {
            GET_FONT_METRICS.invokeExact(method(face, FONT_FACE_GET_METRICS), face, metrics);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /** IDWriteFontFace::GetDesignGlyphMetrics, slot 10. */
    public static void getDesignGlyphMetrics(MemorySegment face, MemorySegment indices, int count,
                                             MemorySegment metrics) {
        int hr;
        try {
            hr = (int) GET_DESIGN_GLYPH_METRICS.invokeExact(
                    method(face, FONT_FACE_GET_DESIGN_GLYPH_METRICS), face, indices, count, metrics, 0);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("GetDesignGlyphMetrics", hr);
    }

    /** IDWriteTextAnalyzer::GetGlyphs, slot 7; the glyph count is left in actual. */
    public static void getGlyphs(MemorySegment analyzer, MemorySegment text, int length, MemorySegment face,
                                 MemorySegment analysis, MemorySegment locale, MemorySegment clusterMap,
                                 MemorySegment textProps, MemorySegment glyphIndices, MemorySegment glyphProps,
                                 int maxGlyphCount, MemorySegment actual) {
        int hr;
        try {
            hr = (int) GET_GLYPHS.invokeExact(method(analyzer, ANALYZER_GET_GLYPHS), analyzer,
                    text, length, face, 0, 0, analysis, locale, MemorySegment.NULL, MemorySegment.NULL,
                    MemorySegment.NULL, 0, maxGlyphCount, clusterMap, textProps, glyphIndices, glyphProps, actual);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("GetGlyphs", hr);
    }

    /** IDWriteTextAnalyzer::GetGlyphPlacements, slot 8. */
    public static void getGlyphPlacements(MemorySegment analyzer, MemorySegment text, MemorySegment clusterMap,
                                          MemorySegment textProps, int length, MemorySegment glyphIndices,
                                          MemorySegment glyphProps, int glyphCount, MemorySegment face,
                                          float emSize, MemorySegment analysis, MemorySegment locale,
                                          MemorySegment advances, MemorySegment offsets) {
        int hr;
        try {
            hr = (int) GET_GLYPH_PLACEMENTS.invokeExact(method(analyzer, ANALYZER_GET_GLYPH_PLACEMENTS), analyzer,
                    text, clusterMap, textProps, length, glyphIndices, glyphProps, glyphCount, face, emSize,
                    0, 0, analysis, locale, MemorySegment.NULL, MemorySegment.NULL, 0, advances, offsets);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("GetGlyphPlacements", hr);
    }

    /** IDWriteFactory::CreateGlyphRunAnalysis, slot 23; the analysis is left in out. */
    public static void createGlyphRunAnalysis(MemorySegment factory, MemorySegment glyphRun, float pixelsPerDip,
                                              MemorySegment transform, int renderingMode, int measuringMode,
                                              float baselineX, float baselineY, MemorySegment out) {
        int hr;
        try {
            hr = (int) CREATE_GLYPH_RUN_ANALYSIS.invokeExact(
                    method(factory, FACTORY_CREATE_GLYPH_RUN_ANALYSIS), factory,
                    glyphRun, pixelsPerDip, transform, renderingMode, measuringMode, baselineX, baselineY, out);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("CreateGlyphRunAnalysis", hr);
    }

    /** IDWriteGlyphRunAnalysis::CreateAlphaTexture, slot 4. */
    public static void createAlphaTexture(MemorySegment analysis, int textureType, MemorySegment rect,
                                          MemorySegment buffer, int bufferSize) {
        int hr;
        try {
            hr = (int) CREATE_ALPHA_TEXTURE.invokeExact(
                    method(analysis, GRA_CREATE_ALPHA_TEXTURE), analysis, textureType, rect, buffer, bufferSize);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
        if (hr != OK) throw failed("CreateAlphaTexture", hr);
    }
}
