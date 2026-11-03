package jui.text;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jui.ffi.DWrite;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Lays out text and rasterizes glyphs with DirectWrite, as the Windows side
 * of {@link TextBackend}: an IDWriteTextLayout of the paragraph breaks its
 * lines (with the system's font fallback for what Segoe UI lacks), the text
 * analyzer shapes each script and span run in a font of its own, and a glyph
 * run analysis rasterizes coverage. Main thread only.
 */
final class DirectWriteBackend implements TextBackend {
    private final TextEngine engine;

    // COM objects held for the backend's life.
    private final long factory;
    private final long analyzer;
    private final MemorySegment locale;

    /**
     * One font the backend shapes with: DirectWrite's own IDWriteFontFace
     * object — which it shares between requests for the same font — and the
     * metrics scaled to the size the handle was asked for.
     */
    private record Face(long pointer, TextEngine.FontMetrics metrics) {}

    /**
     * Font handles are the backend's own ids, one per style and size as the
     * mac's per-size CTFont handles: DirectWrite hands out the same
     * IDWriteFontFace pointer for a font of any size, so the pointer alone
     * cannot tell a 12-point run from a 48-point one. The same style and
     * size gives the same handle, so equivalent runs share atlas entries.
     */
    private final Map<FontKey, Long> handles = new HashMap<>();
    private final List<Face> faces = new ArrayList<>();

    private MemorySegment pixels = Arena.ofAuto().allocate(64 * 64);

    /** The named font family layouts use instead of the system one; null for Segoe UI. */
    private final String family;

    DirectWriteBackend(TextEngine engine, String language, String family) {
        this.engine = engine;
        this.family = family;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            DWrite.createFactory(out);
            this.factory = out.get(ADDRESS, 0).address();
            MemorySegment analyzerOut = arena.allocate(ADDRESS);
            createAnalyzer(this.factory, analyzerOut);
            this.analyzer = analyzerOut.get(ADDRESS, 0).address();
            this.locale = DWrite.wide(Arena.global(), language == null ? "" : language);
        }
    }

    // The analyzer comes from the factory's CreateTextAnalyzer, slot 21.
    private void createAnalyzer(long factory, MemorySegment out) {
        DWrite.createTextAnalyzer(MemorySegment.ofAddress(factory), out);
    }

    // ---------------------------------------------------------------- fonts

    /** What makes one run's font: the family picked, its traits and size. */
    private record FontKey(int family, int weight, boolean italic, float size) {}

    private static final int LATIN = 0, HAN = 1, HANGUL = 2, ARABIC = 3, HEBREW = 4, THAI = 5;

    /** The font a run of {@code script} and style is set in. */
    private long fontFace(int script, boolean mono, int weight, boolean italic, float size) {
        int bucket = mono ? 6 : script; // Consolas for monospace spans
        FontKey key = new FontKey(bucket, weight, italic, size);
        Long cached = handles.get(key);
        if (cached != null) return cached;
        String name = switch (bucket) {
            case HAN -> "Microsoft YaHei";
            case HANGUL -> "Malgun Gothic";
            case ARABIC, HEBREW, LATIN -> family != null ? family : "Segoe UI";
            case THAI -> "Leelawadee UI";
            case 6 -> "Consolas";
            default -> "Segoe UI";
        };
        long pointer = createFace(name, weight, italic);
        long handle = faces.size() + 1;
        faces.add(new Face(pointer, fontMetrics(pointer, size)));
        handles.put(key, handle);
        return handle;
    }

    /** The face's metrics scaled to {@code size}, from its design units. */
    private TextEngine.FontMetrics fontMetrics(long pointer, float size) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment metrics = arena.allocate(DWrite.FONT_METRICS);
            DWrite.getFontMetrics(MemorySegment.ofAddress(pointer), metrics);
            int unitsPerEm = metrics.get(JAVA_SHORT, 0) & 0xFFFF;
            int ascent = metrics.get(JAVA_SHORT, 1 * 2) & 0xFFFF;
            int underlinePosition = metrics.get(JAVA_SHORT, 6 * 2); // signed
            int underlineThickness = metrics.get(JAVA_SHORT, 7 * 2) & 0xFFFF;
            int xHeight = metrics.get(JAVA_SHORT, 5 * 2) & 0xFFFF;
            float scale = size / unitsPerEm;
            return new TextEngine.FontMetrics(size, -underlinePosition * scale, underlineThickness * scale,
                    xHeight * scale, ascent * scale);
        }
    }

    private Face face(long handle) {
        return faces.get((int) (handle - 1));
    }

    /**
     * A font face through the system collection: FindFamilyName gives the
     * family, GetFirstMatchingFont the font of its weight and style, and the
     * font's own CreateFontFace the face. The objects between are released
     * once the face is made; the face itself stays, as fonts live as long as
     * the backend.
     */
    private long createFace(String family, int weight, boolean italic) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            // 3: IDWriteFactory::GetSystemFontCollection
            DWrite.raw(factory, DWrite.FACTORY_GET_SYSTEM_FONT_COLLECTION, out.address(), 0);
            long collection = out.get(ADDRESS, 0).address();
            MemorySegment name = DWrite.wide(arena, family);
            MemorySegment index = arena.allocate(JAVA_INT);
            MemorySegment exists = arena.allocate(JAVA_INT);
            if (findFamily(collection, name, index, exists) != DWrite.OK || exists.get(JAVA_INT, 0) == 0)
                throw new IllegalStateException("font family not found: " + family);
            long fontFamily = getFontFamily(collection, index.get(JAVA_INT, 0));
            long font = firstMatchingFont(fontFamily, weight, italic ? 2 /* italic */ : 0, 5 /* normal stretch */);
            MemorySegment faceOut = arena.allocate(ADDRESS);
            // 13: IDWriteFont::CreateFontFace(&face)
            DWrite.raw(font, DWrite.FONT_CREATE_FONT_FACE /* 13: IDWriteFont::CreateFontFace */, faceOut.address());
            long face = faceOut.get(ADDRESS, 0).address();
            DWrite.release(MemorySegment.ofAddress(font));
            DWrite.release(MemorySegment.ofAddress(fontFamily));
            return face;
        }
    }

    /** 5: IDWriteFontCollection::FindFamilyName. */
    private int findFamily(long collection, MemorySegment name, MemorySegment index, MemorySegment exists) {
        return (int) DWrite.raw(collection, 5, name.address(), index.address(), exists.address());
    }

    /** 4: IDWriteFontCollection::GetFontFamily, its pointer left in out. */
    private long getFontFamily(long collection, int index) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            DWrite.raw(collection, 4, index, out.address());
            return out.get(ADDRESS, 0).address();
        }
    }

    /** 7: IDWriteFontFamily::GetFirstMatchingFont(weight, stretch, style), after IDWriteFontList's 3-5. */
    private long firstMatchingFont(long family, int weight, int style, int stretch) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            DWrite.raw(family, 7, weight, stretch, style, out.address());
            return out.get(ADDRESS, 0).address();
        }
    }

    // ---------------------------------------------------------------- shaping

    @Override
    public TextLayout shape(String text, jui.ui.Spans spans, float size, int weight, float maxWidth) {
        int capacity = text.length() + 8;
        long[] fonts = new long[capacity];
        short[] glyphs = new short[capacity];
        float[] xs = new float[capacity];
        float[] ys = new float[capacity];
        float[] sizes = new float[capacity];
        int[] indices = new int[capacity];
        int count = 0;
        TextLayout.LineTable lineTable = new TextLayout.LineTable();
        int[] stops = TextBounds.graphemes(text);
        float[] downstreamX = new float[text.length() + 1], upstreamX = new float[text.length() + 1];
        float top = 0, widest = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment wide = DWrite.wide(arena, text);
            int length = text.length();
            Broken broken = breakLines(wide, length, spans, size, weight, maxWidth, arena);
            List<Line> lines = broken.lines();
            MemorySegment layout = broken.layout();
            int[] boundaries = runBoundaries(text, spans, stops);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                Line line = lines.get(lineIndex);
                if (line.start == line.end) {
                    float emptyHeight = engine.lineHeight(size, weight);
                    lineTable.add(line.start, line.start, line.breakEnd, count, count, top, emptyHeight, 0);
                    lineTable.baselines[lineTable.count - 1] = top + line.baseline;
                    downstreamX[line.start] = 0;
                    top += emptyHeight;
                    continue;
                }
                // A line that ends without a newline wraps: the next line starts
                // where this one ends, and the caret there has two positions.
                boolean soft = line.newline == 0 && lineIndex + 1 < lines.size();
                engine.shapedLines++;
                float baseline = top + line.baseline;
                int glyphStart = count;
                // Runs: a span style or a script change begins one.
                int previous = 0;
                for (int b = 1; b < boundaries.length; b++) {
                    if (boundaries[b] <= line.start) { previous = b; continue; }
                    if (boundaries[b] > line.end) break;
                    int runStart = Math.max(line.start, boundaries[previous]);
                    int runEnd = boundaries[b];
                    if (runEnd <= runStart) continue;
                    jui.ui.Spans.Shape style = styleAt(spans, runStart);
                    count = shapeRun(text, wide, runStart, runEnd, style, size, weight, baseline, arena,
                            fonts, glyphs, xs, ys, sizes, indices, count);
                    previous = b;
                }
                // DirectWrite's trailing edge of a wrapped position lands on the
                // next line, so the line's own edge is the trailing edge of its
                // last character, trailing spaces and all.
                float rowWidth = soft
                        ? hitTest(layout, line.end - 1, true)
                        : hitTest(layout, line.end, false);
                for (int position = 0; position < stops.length; position++) {
                    int stop = stops[position];
                    if (stop < line.start) continue;
                    if (stop > line.end) break;
                    float offset = stop == line.end && soft ? rowWidth : hitTest(layout, stop, false);
                    downstreamX[stop] = offset;
                    if (stop != line.start || lineTable.count == 0 || lineTable.breaks[lineTable.count - 1] > line.start) {
                        upstreamX[stop] = offset;
                    }
                }
                lineTable.add(line.start, line.end, line.breakEnd, glyphStart, count, top, line.height, rowWidth);
                lineTable.baselines[lineTable.count - 1] = baseline;
                top += line.height;
                widest = Math.max(widest, rowWidth);
            }
        }
        TextLayout result = new TextLayout(text, size, weight, maxWidth, widest, top, count, fonts, glyphs, xs, ys,
                indices, lineTable, stops, downstreamX, upstreamX);
        result.sizes = sizes;
        return result;
    }

    /** The span shape holding {@code index}: the first of the runs whose end passes it. */
    private jui.ui.Spans.Shape styleAt(jui.ui.Spans spans, int index) {
        if (spans == null || spans.shapeCount() == 0) return null;
        int low = 0, high = spans.shapeCount() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (spans.shape(middle).end() <= index) low = middle + 1;
            else high = middle;
        }
        return spans.shape(low);
    }

    /** One laid-out line: its text range (trailing spaces in, the newline out),
     * where the break's whitespace ends, and its height and baseline. */
    private record Line(int start, int end, int breakEnd, int newline,
                        float height, float baseline) {}

    /**
     * Breaks the paragraph with an IDWriteTextLayout of it in the base font,
     * its span runs applied so lines break and size as the fonts they hold:
     * fallback for what the base font lacks comes with the layout, and each
     * DWRITE_LINE_METRICS tells where its line ends and how tall it is. The
     * layout is returned live, for hit tests of the caret stops.
     */
    private record Broken(List<Line> lines, MemorySegment layout) {}

    private Broken breakLines(MemorySegment wide, int length, jui.ui.Spans spans, float size, int weight,
                              float maxWidth, Arena arena) {
        String base = family != null ? family : "Segoe UI";
        MemorySegment formatOut = arena.allocate(ADDRESS);
        DWrite.createTextFormat(factory, DWrite.wide(arena, base), weight, 0, 5, size, locale, formatOut);
        MemorySegment format = formatOut.get(ADDRESS, 0);
        DWrite.setWordWrapping(format, maxWidth > 0 ? DWrite.WRAP : DWrite.NO_WRAP);
        MemorySegment layoutOut = arena.allocate(ADDRESS);
        DWrite.createTextLayout(factory, wide, length, format,
                maxWidth > 0 ? maxWidth : 1e6f, 1e6f, layoutOut);
        MemorySegment layout = layoutOut.get(ADDRESS, 0);
        if (spans != null && spans.shapeCount() > 0) {
            int start = 0;
            for (int i = 0; i < spans.shapeCount(); i++) {
                jui.ui.Spans.Shape style = spans.shape(i);
                int spanLength = style.end() - start;
                if (spanLength > 0) {
                    long range = ((long) spanLength << 32) | (start & 0xFFFFFFFFL);
                    DWrite.setFontFamilyName(layout, DWrite.wide(arena, style.mono() ? "Consolas" : base), range);
                    DWrite.setFontWeight(layout, style.weight() == 0 ? weight : style.weight(), range);
                    DWrite.setFontStyle(layout, style.italic() ? 2 : 0, range);
                    DWrite.setFontSize(layout, style.size() == 0 ? size : style.size(), range);
                }
                start = style.end();
            }
        }
        int maxLines = length + 2;
        MemorySegment lineArray = arena.allocate(DWrite.LINE_METRICS, maxLines);
        MemorySegment actual = arena.allocate(JAVA_INT);
        int lineCount = DWrite.getLineMetrics(layout, lineArray, maxLines, actual);
        List<Line> lines = new ArrayList<>(Math.max(1, lineCount));
        int start = 0;
        for (int i = 0; i < lineCount; i++) {
            MemorySegment m = lineArray.asSlice(i * DWrite.LINE_METRICS.byteSize(), DWrite.LINE_METRICS);
            int len = m.get(JAVA_INT, 0);
            int newline = m.get(JAVA_INT, 8);
            float height = m.get(JAVA_FLOAT, 12);
            float baseline = m.get(JAVA_FLOAT, 16);
            // Trailing spaces stay in the line, as Core Text keeps them: the
            // next line begins after them, at the same index this line ends.
            int end = Math.max(start, start + len - newline);
            int breakEnd = newline > 0 ? start + len : end;
            lines.add(new Line(start, end, breakEnd, newline, height, baseline));
            start = start + len;
        }
        return new Broken(lines, layout);
    }

    /** 65: IDWriteTextLayout::HitTestTextPosition's x for a caret stop. */
    private float hitTest(MemorySegment layout, int stop, boolean trailing) {
        return DWrite.hitTestX(layout, stop, trailing);
    }

    /**
     * Run boundaries: span boundaries and script changes, with 0 and length,
     * sorted and deduplicated.
     */
    private static int[] runBoundaries(String text, jui.ui.Spans spans, int[] graphemes) {
        int length = text.length();
        int[] boundaries = new int[(spans == null ? 0 : spans.shapeCount()) + length + 2];
        int count = 0;
        boundaries[count++] = 0;
        if (spans != null) {
            for (int i = 0; i < spans.shapeCount(); i++) boundaries[count++] = spans.shape(i).end();
        }
        int script = -1;
        for (int i = 0; i < length; i++) {
            int codePoint = text.codePointAt(i);
            int current = scriptOf(codePoint);
            if (script == -1) script = current;
            else if (current != script) {
                boundaries[count++] = i;
                script = current;
            }
            if (Character.isHighSurrogate(text.charAt(i))) i++;
        }
        boundaries[count++] = length;
        int[] result = Arrays.copyOf(boundaries, count);
        Arrays.sort(result);
        int unique = 1;
        for (int i = 1; i < result.length; i++) {
            if (result[i] != result[unique - 1]) result[unique++] = result[i];
        }
        return Arrays.copyOf(result, unique);
    }

    /** The script families jui picks fonts by. */
    private static int scriptOf(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return switch (script) {
            case HAN -> HAN;
            case HANGUL -> HANGUL;
            case ARABIC -> ARABIC;
            case HEBREW -> HEBREW;
            case THAI -> THAI;
            default -> LATIN;
        };
    }

    /** The usp10 script id a DWRITE_SCRIPT_ANALYSIS takes. */
    private static int scriptId(int script) {
        return switch (script) {
            case HAN -> 0x36;          // Script_CJK_Han
            case HANGUL -> 0x3a;       // Script_Hangul
            case ARABIC -> 0x06;       // Script_Arabic
            case HEBREW -> 0x08;       // Script_Hebrew
            case THAI -> 0x14;         // Script_Thai
            default -> 0x01;           // Script_Latin
        };
    }

    /**
     * Shapes one run of one span style and script in its font with the
     * analyzer, appending its glyphs in point coordinates relative to the
     * paragraph; returns the new glyph count.
     */
    private int shapeRun(String text, MemorySegment wide, int start, int end, jui.ui.Spans.Shape style,
                         float size, int weight, float baseline, Arena arena,
                         long[] fonts, short[] glyphs, float[] xs, float[] ys, float[] sizes,
                         int[] indices, int count) {
        float runSize = style == null || style.size() == 0 ? size : style.size();
        int runWeight = style == null || style.weight() == 0 ? weight : style.weight();
        boolean mono = style != null && style.mono();
        boolean italic = style != null && style.italic();
        int script = scriptOf(text.codePointAt(start));
        long font = fontFace(script, mono, runWeight, italic, runSize);
        MemorySegment face = MemorySegment.ofAddress(face(font).pointer());
        int length = end - start;
        int max = length * 8 + 16;
        MemorySegment clusterMap = arena.allocate(JAVA_SHORT, length);
        MemorySegment textProps = arena.allocate(JAVA_SHORT, length);
        MemorySegment glyphIndices = arena.allocate(JAVA_SHORT, max);
        MemorySegment glyphProps = arena.allocate(JAVA_SHORT, max);
        MemorySegment actual = arena.allocate(JAVA_INT);
        MemorySegment analysis = arena.allocate(DWrite.SCRIPT_ANALYSIS);
        analysis.set(JAVA_SHORT, 0, (short) scriptId(script));
        DWrite.getGlyphs(MemorySegment.ofAddress(analyzer), wide.asSlice(2L * start), length, face, analysis, locale,
                clusterMap, textProps, glyphIndices, glyphProps, max, actual);
        int glyphCount = actual.get(JAVA_INT, 0);
        if (glyphCount == 0) return count;
        MemorySegment advances = arena.allocate(JAVA_FLOAT, glyphCount);
        MemorySegment offsets = arena.allocate(DWrite.GLYPH_OFFSET, glyphCount);
        DWrite.getGlyphPlacements(MemorySegment.ofAddress(analyzer), wide.asSlice(2L * start), clusterMap, textProps, length,
                glyphIndices, glyphProps, glyphCount, face, runSize, analysis, locale, advances, offsets);
        if (count + glyphCount > fonts.length) {
            int grown = Math.max(fonts.length * 2, count + glyphCount);
            fonts = Arrays.copyOf(fonts, grown);
            glyphs = Arrays.copyOf(glyphs, grown);
            xs = Arrays.copyOf(xs, grown);
            ys = Arrays.copyOf(ys, grown);
            sizes = Arrays.copyOf(sizes, grown);
            indices = Arrays.copyOf(indices, grown);
        }
        float x = 0;
        for (int j = 0; j < glyphCount; j++) {
            float advance = advances.get(JAVA_FLOAT, 4L * j);
            MemorySegment offset = offsets.asSlice(j * DWrite.GLYPH_OFFSET.byteSize(), DWrite.GLYPH_OFFSET);
            fonts[count] = font;
            sizes[count] = runSize;
            glyphs[count] = glyphIndices.get(JAVA_SHORT, 2L * j);
            xs[count] = x + offset.get(JAVA_FLOAT, 0);
            // DirectWrite's ascenderOffset goes up from the baseline; the
            // layout's ys go down to the glyph origin.
            ys[count] = baseline - offset.get(JAVA_FLOAT, 4);
            indices[count] = start + Short.toUnsignedInt(clusterMap.get(JAVA_SHORT, 2L * Math.min(j, length - 1)));
            count++;
            x += advance;
        }
        return count;
    }

    // ---------------------------------------------------------------- metrics

    @Override
    public TextEngine.FontMetrics metrics(long font) {
        return face(font).metrics();
    }

    // ---------------------------------------------------------------- raster

    @Override
    public TextEngine.Bitmap rasterize(long font, int glyph, float scale, float dx, int shade) {
        long started = System.nanoTime();
        try {
            Face held = face(font);
            MemorySegment face = MemorySegment.ofAddress(held.pointer());
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment index = arena.allocate(JAVA_SHORT);
                index.set(JAVA_SHORT, 0, (short) glyph);
                // The ink box in design units, from the glyph's metrics.
                MemorySegment metrics = arena.allocate(DWrite.GLYPH_METRICS);
                DWrite.getDesignGlyphMetrics(face, index, 1, metrics);
                TextEngine.FontMetrics fm = held.metrics();
                float unitsPerEm = designUnitsPerEm(face, arena);
                float toPoints = fm.size() / unitsPerEm;
                float advance = metrics.get(JAVA_INT, 4);
                float lsb = metrics.get(JAVA_INT, 0);
                float rsb = metrics.get(JAVA_INT, 8);
                float tsb = metrics.get(JAVA_INT, 12);
                float advanceHeight = metrics.get(JAVA_INT, 16);
                float bsb = metrics.get(JAVA_INT, 20);
                float inkWidth = advance - lsb - rsb;
                float inkHeight = advanceHeight - tsb - bsb;
                if (inkWidth <= 0 && inkHeight <= 0) return null;
                float left = (float) Math.floor(lsb * toPoints * scale + dx) - 2;
                float right = (float) Math.ceil((lsb + Math.max(inkWidth, 0)) * toPoints * scale + dx) + 2;
                float glyphTop = -tsb;
                float top = (float) Math.floor(glyphTop * toPoints * scale) - 2;
                float bottom = (float) Math.ceil((glyphTop + Math.max(inkHeight, 0)) * toPoints * scale) + 2;
                int w = (int) (right - left), h = (int) (bottom - top);
                if (w <= 0 || h <= 0) return null;
                if (w > 2048 || h > 2048) throw new IllegalStateException("glyph exceeds the 2048-pixel raster limit");
                MemorySegment run = arena.allocate(DWrite.GLYPH_RUN);
                // The run renders at the scaled size with one pixel per DIP,
                // so the texture's pixels and the box above are one unit.
                run.set(ADDRESS, 0, face);
                run.set(JAVA_FLOAT, 8, fm.size() * scale);
                run.set(JAVA_INT, 12, 1);
                run.set(ADDRESS, 16, index);
                run.set(ADDRESS, 24, MemorySegment.NULL);
                run.set(ADDRESS, 32, MemorySegment.NULL);
                run.set(JAVA_INT, 40, 0);
                run.set(JAVA_INT, 44, 0);
                MemorySegment analysisOut = arena.allocate(ADDRESS);
                // The baseline origin lands the ink where the layout put it,
                // subpixel dx included.
                DWrite.createGlyphRunAnalysis(MemorySegment.ofAddress(factory), run, 1.0f, MemorySegment.NULL,
                        DWrite.RENDERING_NATURAL, DWrite.MEASURING_NATURAL,
                        -left, -top, analysisOut);
                MemorySegment analysis = analysisOut.get(ADDRESS, 0);
                // The whole padded box is extracted, its margins clear: boxes
                // then size as the mac backend's do, atlas pressure included.
                int stride = w * 3;
                MemorySegment rgb = arena.allocate((long) stride * h);
                MemorySegment box = arena.allocate(DWrite.RECT);
                box.set(JAVA_INT, 0, 0);
                box.set(JAVA_INT, 4, 0);
                box.set(JAVA_INT, 8, w);
                box.set(JAVA_INT, 12, h);
                DWrite.createAlphaTexture(analysis, DWrite.TEXTURE_CLEARTYPE, box, rgb, stride * h);
                DWrite.release(analysis);
                if (pixels.byteSize() < (long) w * h) {
                    pixels = Arena.ofAuto().allocate(Math.max((long) w * h, pixels.byteSize() * 2));
                }
                MemorySegment pix = pixels.asSlice(0, (long) w * h);
                for (int p = 0; p < w * h; p++) {
                    int o = p * 3;
                    pix.set(JAVA_BYTE, p, (byte) (((rgb.get(JAVA_BYTE, o) & 255)
                            + (rgb.get(JAVA_BYTE, o + 1) & 255) + (rgb.get(JAVA_BYTE, o + 2) & 255)) / 3));
                }
                engine.rasterized++;
                return new TextEngine.Bitmap((int) left, (int) top, w, h, pix);
            }
        } finally {
            engine.rasterNanos += System.nanoTime() - started;
        }
    }

    private float designUnitsPerEm(MemorySegment face, Arena arena) {
        MemorySegment metrics = arena.allocate(DWrite.FONT_METRICS);
        DWrite.getFontMetrics(face, metrics);
        return metrics.get(JAVA_SHORT, 0) & 0xFFFF;
    }
}
