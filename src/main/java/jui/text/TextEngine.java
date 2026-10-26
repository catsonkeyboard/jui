package jui.text;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import jui.ffi.ObjC;
import jui.util.LongMap;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Lays out text and rasterizes glyphs with Core Text and Core Graphics, as
 * MyGO's internal/text/coretext_darwin.go does: a typesetter finds the fonts,
 * falls back to others for what a font lacks, shapes and breaks lines, and
 * Core Graphics draws glyphs with font smoothing, as AppKit draws text.
 * Main thread only.
 */
public final class TextEngine {

    /** Shades of text: its color's luminance in quarters, which font smoothing depends on. */
    public static final int SHADES = 4;

    private final boolean smoothing = fontSmoothing();
    private final long language;
    private final Map<Long, Long> systemFonts = new HashMap<>();
    /**
     * One font for each PostScript name and size. Core Text makes new
     * fallback fonts for each typesetter (PingFang for Han characters, say),
     * and the glyph atlas keys on the font: without one font each, every new
     * text would rasterize its glyphs again, until the atlas is full.
     */
    private final Map<String, Long> canonicalFonts = new HashMap<>();

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

    // Scratch memory for rasterizing.
    private final MemorySegment glyphArg = Arena.global().allocate(JAVA_SHORT);
    private final MemorySegment rectArg = Arena.global().allocate(32, 8);
    private final MemorySegment pointArg = Arena.global().allocate(16, 8);
    private MemorySegment pixels = Arena.ofAuto().allocate(64 * 64);

    /** Glyphs rasterized, and the nanoseconds spent laying out and rasterizing. */
    public int rasterized;
    public long layoutNanos, rasterNanos;

    /** {@code language} picks the glyphs of fonts that have several, as Han characters in Chinese and Japanese. */
    public TextEngine(String language) {
        ObjC.framework("AppKit"); // the system font comes from NSFont, even without an app (as in tests)
        this.language = CoreText.string(language);
    }

    /** Layouts the cache keeps, in both generations. */
    public int cachedLayouts() {
        return layouts.size() + previous.size();
    }

    /** Glyphs of the layouts the cache keeps, in both generations. */
    public long cachedGlyphs() {
        return glyphs + previousGlyphs;
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
        TextLayout layout = shape(text, size, weight, maxWidth);
        layoutNanos += System.nanoTime() - started;
        keep(key, layout);
        return layout;
    }

    /** Puts a layout in the current generation, which first ages into the previous one when full. */
    private void keep(long key, TextLayout layout) {
        if (layouts.size() >= MAX_LAYOUTS || glyphs + layout.count > MAX_GLYPHS) {
            LongMap<TextLayout> spare = previous;
            previous = layouts;
            previousGlyphs = glyphs;
            layouts = spare;
            layouts.clear();
            glyphs = 0;
        }
        layouts.put(key, layout);
        glyphs += layout.count;
    }

    /** A hash of a layout's parameters; a hit is checked against them, so collisions only miss. */
    private static long layoutKey(String text, float size, int weight, float maxWidth) {
        long h = text.hashCode();
        h = h * 0x9E3779B97F4A7C15L + Float.floatToIntBits(size);
        h = h * 0x9E3779B97F4A7C15L + weight;
        return h * 0x9E3779B97F4A7C15L + Float.floatToIntBits(maxWidth);
    }

    private TextLayout shape(String text, float size, int weight, float maxWidth) {
        long font = systemFont(size, weight);
        if (text.isEmpty()) {
            return new TextLayout(text, size, weight, maxWidth, 0, 0, 0, 0, new long[0], new short[0], new float[0],
                    new float[0], new int[0]);
        }
        long str = CoreText.string(text);
        long attrs = CoreText.dictionary(
                new long[] {CoreText.FONT_ATTRIBUTE_NAME, CoreText.LANGUAGE_ATTRIBUTE_NAME},
                new long[] {font, language});
        long attributed = CoreText.attributedString(str, attrs);
        long typesetter = CoreText.typesetter(attributed);

        int cap = text.length() + 8;
        long[] fonts = new long[cap];
        short[] glyphs = new short[cap];
        float[] xs = new float[cap];
        float[] ys = new float[cap];
        int[] indices = new int[cap];
        int count = 0, lines = 0;
        double top = 0, widest = 0;
        // Without a width, lines still break at newlines.
        double width = maxWidth > 0 ? maxWidth + 1.0 / 1024 : 1e7;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment metrics = arena.allocate(24, 8);
            for (long start = 0; start < text.length(); ) {
                long n = Math.max(1, CoreText.suggestLineBreak(typesetter, start, width));
                long line = CoreText.createLine(typesetter, start, n);
                if (line == 0) {
                    break;
                }
                double lineWidth = CoreText.typographicBounds(line, metrics);
                double ascent = metrics.get(JAVA_DOUBLE, 0);
                double descent = metrics.get(JAVA_DOUBLE, 8);
                double leading = metrics.get(JAVA_DOUBLE, 16);
                double baseline = top + ascent;

                long runs = CoreText.glyphRuns(line);
                for (long r = 0, nr = CoreText.arrayCount(runs); r < nr; r++) {
                    long run = CoreText.arrayGet(runs, r);
                    long runFont = canonical(CoreText.dictionaryGetValue(CoreText.runAttributes(run),
                            CoreText.FONT_ATTRIBUTE_NAME));
                    int ng = (int) CoreText.runGlyphCount(run);
                    if (ng == 0) {
                        continue;
                    }
                    MemorySegment ids = arena.allocate(JAVA_SHORT, ng);
                    MemorySegment points = arena.allocate(16L * ng, 8);
                    MemorySegment where = arena.allocate(JAVA_LONG, ng);
                    CoreText.runGlyphs(run, ids);
                    CoreText.runPositions(run, points);
                    CoreText.runStringIndices(run, where);
                    if (count + ng > fonts.length) {
                        int grown = Math.max(fonts.length * 2, count + ng);
                        fonts = Arrays.copyOf(fonts, grown);
                        glyphs = Arrays.copyOf(glyphs, grown);
                        xs = Arrays.copyOf(xs, grown);
                        ys = Arrays.copyOf(ys, grown);
                        indices = Arrays.copyOf(indices, grown);
                    }
                    for (int j = 0; j < ng; j++) {
                        fonts[count] = runFont;
                        glyphs[count] = ids.getAtIndex(JAVA_SHORT, j);
                        xs[count] = (float) points.get(JAVA_DOUBLE, 16L * j);
                        // Core Text's y goes up from the baseline.
                        ys[count] = (float) (baseline - points.get(JAVA_DOUBLE, 16L * j + 8));
                        indices[count] = (int) where.getAtIndex(JAVA_LONG, j);
                        count++;
                    }
                }
                CoreText.release(line);
                top += ascent + descent + leading;
                widest = Math.max(widest, lineWidth);
                lines++;
                start += n;
            }
        } finally {
            CoreText.release(typesetter);
            CoreText.release(attributed);
            CoreText.release(attrs);
            CoreText.release(str);
        }
        return new TextLayout(text, size, weight, maxWidth, (float) widest, (float) top, lines, count, fonts, glyphs, xs, ys,
                indices);
    }

    /**
     * The height of a line of text of {@code size} and {@code weight} that
     * holds Latin and Han alike: Core Text falls back to PingFang for Han
     * characters, whose line is higher than the system font's, so a text
     * input sized by this keeps its height as Chinese is typed into it.
     */
    public float lineHeight(float size, int weight) {
        return layout("Ag中", size, weight, 0).height;
    }

    /**
     * The font kept for font's PostScript name and size: the first such font
     * seen, retained, as the glyph atlas keys on it. Others are the run's own,
     * freed with it, so their references are never kept.
     */
    private long canonical(long font) {
        long pool = ObjC.poolPush();
        try {
            String key = ObjC.string(ObjC.send(font, "fontName")) + "@" + ObjC.sendDouble(font, "pointSize");
            Long kept = canonicalFonts.get(key);
            if (kept != null) {
                return kept;
            }
            CoreText.retain(font); // CTFont and NSFont are the same objects
            canonicalFonts.put(key, font);
            return font;
        } finally {
            ObjC.poolPop(pool);
        }
    }

    /** The system font, as NSFont gives it with its weights, cached. */
    private long systemFont(float size, int weight) {
        long key = ((long) Float.floatToIntBits(size) << 32) | weight;
        Long font = systemFonts.get(key);
        if (font != null) {
            return font;
        }
        long pool = ObjC.poolPush();
        try {
            long f = ObjC.sendDoubles(ObjC.cls("NSFont"), "systemFontOfSize:weight:", size, nsWeight(weight));
            CoreText.retain(f); // NSFont and CTFont are the same objects
            systemFonts.put(key, f);
            return f;
        } finally {
            ObjC.poolPop(pool);
        }
    }

    /** NSFontWeight for a CSS weight, as MyGO maps them. */
    static double nsWeight(int w) {
        double[] steps = {-0.8, -0.6, -0.4, 0, 0.23, 0.3, 0.4, 0.56, 0.62};
        double f = (Math.max(100, Math.min(w, 900)) - 100) / 100.0;
        int i = Math.min((int) f, steps.length - 2);
        return steps[i] + (steps[i + 1] - steps[i]) * (f - i);
    }

    /**
     * How many positions within a pixel glyphs of {@code size} points take
     * at {@code scale}: Core Graphics' subpixel quantization, which AppKit
     * leaves on, at most five a pixel and one from 34 pixels an em.
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

    /** The gray that font smoothing fills glyphs of a shade with. */
    private static double shadeGray(int shade) {
        double y = (double) shade / SHADES;
        return y <= 0.0031308 ? y * 12.92 : 1.055 * Math.pow(y, 1 / 2.4) - 0.055;
    }

    /** A glyph's coverage, rows from the top, and where it goes from the pen and baseline, in pixels. */
    public record Bitmap(int left, int top, int width, int height, MemorySegment pixels) {}

    /**
     * Rasterizes a glyph at {@code scale} pixels a point, {@code dx} pixels
     * right of a whole pixel, for text of {@code shade}; null for a glyph
     * with no ink. The pixels stay valid until the next call.
     */
    public Bitmap rasterize(long font, int glyph, float scale, float dx, int shade) {
        if ((CoreText.symbolicTraits(font) & CoreText.FONT_COLOR_GLYPHS_TRAIT) != 0) {
            return null; // color glyphs (emoji) need a color atlas: not in this spike
        }
        long started = System.nanoTime();
        try {
            glyphArg.set(JAVA_SHORT, 0, (short) glyph);
            CoreText.glyphBounds(font, glyphArg, rectArg);
            double rx = rectArg.get(JAVA_DOUBLE, 0), ry = rectArg.get(JAVA_DOUBLE, 8);
            double rw = rectArg.get(JAVA_DOUBLE, 16), rh = rectArg.get(JAVA_DOUBLE, 24);
            if (rw <= 0 || rh <= 0) {
                return null;
            }
            double s = scale, x = dx;
            // Smoothing spreads a glyph up to a pixel further.
            int pad = smoothing ? 2 : 1;
            // Core Graphics' y goes up: the box from ry to ry+rh above the
            // baseline is from -(ry+rh) to -ry below it.
            int left = (int) Math.floor(rx * s + x) - pad;
            int right = (int) Math.ceil((rx + rw) * s + x) + pad;
            int top = (int) Math.floor(-(ry + rh) * s) - pad;
            int bottom = (int) Math.ceil(-ry * s) + pad;
            int w = right - left, h = bottom - top;
            if (w > 2048 || h > 2048) {
                return null;
            }
            if (pixels.byteSize() < (long) w * h) {
                pixels = Arena.ofAuto().allocate(Math.max((long) w * h, pixels.byteSize() * 2));
            }
            MemorySegment pix = pixels.asSlice(0, (long) w * h);
            pix.fill((byte) 0);
            long ctx = CoreText.alphaContext(pix, w, h);
            if (ctx == 0) {
                return null;
            }
            // Font smoothing emboldens glyphs more the lighter the fill is,
            // even in a context of alpha alone: the gray of the shade sets how much.
            CoreText.prepareContext(ctx, smoothing, smoothing ? shadeGray(shade) : 0, s);
            // The bitmap's rows go from its top; the context's origin is at
            // its bottom left, the baseline bottom pixels above it.
            pointArg.set(JAVA_DOUBLE, 0, (x - left) / s);
            pointArg.set(JAVA_DOUBLE, 8, bottom / s);
            CoreText.drawGlyphs(font, glyphArg, pointArg, ctx);
            CoreText.releaseContext(ctx);
            rasterized++;
            return new Bitmap(left, top, w, h, pix);
        } finally {
            rasterNanos += System.nanoTime() - started;
        }
    }

    /** Whether the user leaves font smoothing on, read as AppKit reads it. */
    private static boolean fontSmoothing() {
        long pool = ObjC.poolPush();
        try {
            long defaults = ObjC.send(ObjC.cls("NSUserDefaults"), "standardUserDefaults");
            long key = ObjC.nsString("AppleFontSmoothing");
            if (ObjC.send(defaults, "objectForKey:", key) == 0) {
                return true;
            }
            return ObjC.send(defaults, "integerForKey:", key) != 0;
        } finally {
            ObjC.poolPop(pool);
        }
    }
}
