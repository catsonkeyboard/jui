package jui.text;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

import jui.util.LongMap;

/**
 * Lays out text and rasterizes glyphs, as MyGO's internal/text does: a
 * platform backend finds the fonts, falls back to others for what a font
 * lacks, shapes and breaks lines, and rasterizes glyphs with the system's
 * text stack — Core Text and Core Graphics on macOS, DirectWrite and
 * Direct2D on Windows. Layouts are cached in two generations; the backend
 * is picked by the platform. Main thread only.
 */
public final class TextEngine {

    /** Shades of text: its color's luminance in quarters, which font smoothing depends on. */
    public static final int SHADES = 4;

    private final TextBackend backend;
    public record FontMetrics(float size, float underline, float thickness, float xHeight, float ascent) {}
    public FontMetrics metrics(long font) { return backend.metrics(font); }

    public void checkRichFfi() {
        backend.checkRichFfi();
    }

    /**
     * Layouts in two generations: when the current one fills up, by count or
     * by glyphs, it becomes the previous one and the old previous one goes;
     * a layout found in the previous generation moves back into the current
     * one. So the cache keeps what frames use and stays bounded, as a window
     * resized across many widths or a text that keeps changing would grow it.
     */
    private static final int MAX_LAYOUTS = 2048;
    private static final long MAX_GLYPHS = 100_000;
    private LongMap<TextLayout> layouts = new LongMap<>();
    private LongMap<TextLayout> previous = new LongMap<>();
    private long glyphs, previousGlyphs;
    private long sourceUnits, previousSourceUnits;

    /** Glyphs rasterized, and the nanoseconds spent laying out and rasterizing. */
    public int rasterized;
    public long layoutNanos, rasterNanos;
    public long shapedLines;

    /** {@code language} picks the glyphs of fonts that have several, as Han characters in Chinese and Japanese. */
    public TextEngine(String language) {
        this(language, null);
    }

    /** A separate layout cache using a named font family; null uses the system font. */
    public TextEngine(String language, String family) {
        backend = jui.platform.Platform.windows()
                ? new DirectWriteBackend(this, language, family)
                : new CoreTextBackend(this, language, family);
    }

    /** Layouts the cache keeps, in both generations. */
    public int cachedLayouts() {
        return layouts.size() + previous.size();
    }

    /** Glyphs of the layouts the cache keeps, in both generations. */
    public long cachedGlyphs() {
        return glyphs + previousGlyphs;
    }

    public long cachedSourceUnits() {
        return sourceUnits + previousSourceUnits;
    }

    /**
     * Lays out {@code text} in the system font of {@code size} points and
     * {@code weight} (100 to 900), in lines at most {@code maxWidth} points
     * wide (0 for no wrapping); lines also break at newlines. A layout made
     * before comes back without allocating.
     */
    public TextLayout layout(String text, float size, int weight, float maxWidth) {
        long key = layoutKey(text, size, weight, maxWidth);
        TextLayout cached = layouts.get(key);
        if (cached != null && cached.is(text, size, weight, maxWidth)) {
            return cached;
        }
        TextLayout old = previous.get(key);
        if (old != null && old.is(text, size, weight, maxWidth)) {
            keep(key, old);
            return old;
        }
        long started = System.nanoTime();
        TextLayout layout = shape(text, null, size, weight, maxWidth);
        layoutNanos += System.nanoTime() - started;
        keep(key, layout);
        return layout;
    }

    public TextLayout layout(jui.ui.Spans spans, float size, int weight, float maxWidth) {
        long key = layoutKey(spans.text(), size, weight, maxWidth) * 31 + spans.shapeHash();
        TextLayout cached = layouts.get(key);
        if (cached != null && cached.is(spans, size, weight, maxWidth)) return cached;
        cached = previous.get(key);
        if (cached != null && cached.is(spans, size, weight, maxWidth)) { keep(key, cached); return cached; }
        long started = System.nanoTime();
        TextLayout layout = shape(spans.text(), spans, size, weight, maxWidth);
        layout.spans = spans;
        layoutNanos += System.nanoTime() - started;
        keep(key, layout);
        return layout;
    }

    /** Puts a layout in the current generation, which first ages into the previous one when full. */
    private void keep(long key, TextLayout layout) {
        if (layouts.size() >= MAX_LAYOUTS || glyphs + layout.count > MAX_GLYPHS
                || sourceUnits + layout.length() > 200_000) {
            LongMap<TextLayout> spare = previous;
            previous = layouts;
            previousGlyphs = glyphs;
            previousSourceUnits = sourceUnits;
            layouts = spare;
            layouts.clear();
            glyphs = 0;
            sourceUnits = 0;
        }
        layouts.put(key, layout);
        glyphs += layout.count;
        sourceUnits += layout.length();
    }

    /** A hash of a layout's parameters; a hit is checked against them, so collisions only miss. */
    private static long layoutKey(String text, float size, int weight, float maxWidth) {
        long h = text.hashCode();
        h = h * 0x9E3779B97F4A7C15L + Float.floatToIntBits(size);
        h = h * 0x9E3779B97F4A7C15L + weight;
        return h * 0x9E3779B97F4A7C15L + Float.floatToIntBits(maxWidth);
    }

    private TextLayout shapeParagraphs(String text, jui.ui.Spans spans, float size, int weight, float maxWidth) {
        int capacity = text.length() + 8;
        long[] fonts = new long[capacity];
        short[] glyphs = new short[capacity];
        float[] xs = new float[capacity], ys = new float[capacity];
        float[] sizes = new float[capacity];
        int[] indices = new int[capacity];
        float[] downstream = new float[text.length() + 1], upstream = new float[text.length() + 1];
        TextLayout.LineTable lines = new TextLayout.LineTable();
        int count = 0;
        float top = 0, widest = 0;
        for (int start = 0; ; ) {
            int end = start;
            while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
            int breakEnd = end;
            if (breakEnd < text.length()) {
                breakEnd++;
                if (text.charAt(end) == '\r' && breakEnd < text.length() && text.charAt(breakEnd) == '\n') breakEnd++;
            }
            TextLayout paragraph = spans == null ? layout(text.substring(start, end), size, weight, maxWidth)
                    : layout(spans.slice(start, end), size, weight, maxWidth);
            if (count + paragraph.count > fonts.length) {
                int grown = Math.max(fonts.length * 2, count + paragraph.count);
                fonts = Arrays.copyOf(fonts, grown);
                glyphs = Arrays.copyOf(glyphs, grown);
                xs = Arrays.copyOf(xs, grown);
                ys = Arrays.copyOf(ys, grown);
                sizes = Arrays.copyOf(sizes, grown);
                indices = Arrays.copyOf(indices, grown);
            }
            System.arraycopy(paragraph.fonts, 0, fonts, count, paragraph.count);
            System.arraycopy(paragraph.glyphs, 0, glyphs, count, paragraph.count);
            System.arraycopy(paragraph.xs, 0, xs, count, paragraph.count);
            for (int glyph = 0; glyph < paragraph.count; glyph++) {
                sizes[count + glyph] = paragraph.glyphSize(glyph);
                ys[count + glyph] = paragraph.ys[glyph] + top;
                indices[count + glyph] = paragraph.indices[glyph] + start;
            }
            paragraph.copyCarets(downstream, upstream, start);
            for (int line = 0; line < paragraph.lines; line++) {
                lines.add(start + paragraph.lineStart(line), start + paragraph.lineEnd(line),
                        line == paragraph.lines - 1 ? breakEnd : start + paragraph.lineBreakEnd(line),
                        count + paragraph.lineGlyphStart(line), count + paragraph.lineGlyphEnd(line),
                        top + paragraph.lineTop(line), paragraph.lineHeight(line), paragraph.lineWidth(line));
                lines.baselines[lines.count - 1] = top + paragraph.lineBaseline(line);
            }
            count += paragraph.count;
            top += paragraph.height;
            widest = Math.max(widest, paragraph.width);
            if (end == text.length()) break;
            start = breakEnd;
        }
        TextLayout result = new TextLayout(text, size, weight, maxWidth, widest, top, count, fonts, glyphs, xs, ys,
                indices, lines, TextBounds.graphemes(text), downstream, upstream);
        result.sizes = sizes;
        return result;
    }

    private TextLayout shape(String text, jui.ui.Spans spans, float size, int weight, float maxWidth) {
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) return shapeParagraphs(text, spans, size, weight, maxWidth);
        return backend.shape(text, spans, size, weight, maxWidth);
    }

    /**
     * The height of a line of text of {@code size} and {@code weight} that
     * holds Latin and Han alike: the backend falls back to a Han font for
     * Han characters, whose line is higher than the system font's, so a text
     * input sized by this keeps its height as Chinese is typed into it.
     */
    public float lineHeight(float size, int weight) {
        return layout("Ag中", size, weight, 0).height;
    }

    /**
     * How many positions within a pixel glyphs of {@code size} points take
     * at {@code scale}: the subpixel quantization the rasterizer honors, at
     * most five a pixel and one from 34 pixels an em.
     */
    public static int positions(float size, float scale) {
        double px = size * scale;
        if (px <= 0) {
            return 1;
        }
        return (int) Math.min(5, Math.max(1, Math.ceil(100 / (3 * px) - 1e-9)));
    }

    /** The shade of text of an sRGB color 0xRRGGBBAA: its luminance rounded to a quarter, at most 3/4. */
    public static int shadeOf(int rgba) {
        double y = 0.2126 * LINEAR[(rgba >>> 24) & 0xFF] + 0.7152 * LINEAR[(rgba >>> 16) & 0xFF]
                + 0.0722 * LINEAR[(rgba >>> 8) & 0xFF];
        return (int) Math.min(y * SHADES + 0.5, SHADES - 1);
    }

    /**
     * sRGB values in linear light, as MyGO tabulates them: every text a
     * frame paints asks, and Math.pow allocates in a native image (216 bytes
     * for the three of a shade, measured).
     */
    private static final double[] LINEAR = new double[256];

    static {
        for (int v = 0; v < 256; v++) {
            double c = v / 255.0;
            LINEAR[v] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
    }

    /** A glyph's coverage, rows from the top, and where it goes from the pen and baseline, in pixels. */
    public record Bitmap(int left, int top, int width, int height, MemorySegment pixels) {}

    /**
     * Rasterizes a glyph at {@code scale} pixels a point, {@code dx} pixels
     * right of a whole pixel, for text of {@code shade}; null for a glyph
     * with no ink. The pixels stay valid until the next call.
     */
    public Bitmap rasterize(long font, int glyph, float scale, float dx, int shade) {
        return backend.rasterize(font, glyph, scale, dx, shade);
    }
}
