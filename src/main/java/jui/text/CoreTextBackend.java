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
 * MyGO's internal/text/coretext_darwin.go does: a typesetter finds the
 * fonts, falls back to others for what a font lacks, shapes and breaks
 * lines, and Core Graphics draws glyphs with font smoothing, as AppKit
 * draws text. The macOS side of {@link TextBackend}; main thread only.
 */
final class CoreTextBackend implements TextBackend {
    private final TextEngine engine;
    private final boolean smoothing;
    private final long language;
    private final String family;
    private final Map<Long, Long> systemFonts = new HashMap<>();
    /**
     * Fonts bucketed by CFHash and compared with CFEqual, so equivalent
     * fallback copies share atlas entries without merging distinct traits.
     */
    private final Map<Long, java.util.ArrayList<Long>> canonicalFonts = new HashMap<>();
    private final LongMap<TextEngine.FontMetrics> fontMetrics = new LongMap<>();

    // Scratch memory for rasterizing.
    private final MemorySegment glyphArg = Arena.global().allocate(JAVA_SHORT);
    private final MemorySegment rectArg = Arena.global().allocate(32, 8);
    private final MemorySegment pointArg = Arena.global().allocate(16, 8);
    private MemorySegment pixels = Arena.ofAuto().allocate(64 * 64);

    CoreTextBackend(TextEngine engine, String language, String family) {
        this.engine = engine;
        ObjC.framework("AppKit"); // the system font comes from NSFont, even without an app (as in tests)
        this.language = CoreText.string(language);
        this.family = family;
        this.smoothing = fontSmoothing();
    }

    @Override
    public void checkRichFfi() {
        long font = styledFont(18, 400, true, false);
        long copy = CoreText.fontCopy(font);
        try {
            if (copy == 0 || canonical(copy) != font) throw new IllegalStateException("equivalent fonts did not canonicalize");
        } finally { CoreText.release(copy); }
        long italic = styledFont(18, 400, true, true);
        if (italic == font || CoreText.equal(font, italic)) throw new IllegalStateException("italic font collapsed");
        TextLayout layout = engine.layout(jui.ui.Spans.of(jui.ui.Span.text("mono").monospace().italic(), jui.ui.Span.text(" BIG").fontSize(24).bold()), 18, 400, 100);
        if (layout.count == 0 || metrics(italic).size() != 18) throw new IllegalStateException("rich font FFI failed");
    }

    @Override
    public TextLayout shape(String text, jui.ui.Spans spans, float size, int weight, float maxWidth) {
        long font = systemFont(size, weight);
        long str = CoreText.string(text);
        long attrs = CoreText.dictionary(
                new long[] {CoreText.FONT_ATTRIBUTE_NAME, CoreText.LANGUAGE_ATTRIBUTE_NAME},
                new long[] {font, language});
        long attributed = CoreText.attributedString(str, attrs);
        if (spans != null && spans.shapeCount() > 0) {
            long mutable = CoreText.mutableCopy(attributed);
            CoreText.release(attributed);
            attributed = mutable;
            int start = 0;
            for (int index = 0; index < spans.shapeCount(); index++) {
                jui.ui.Spans.Shape style = spans.shape(index);
                long resolved = styledFont(style.size() == 0 ? size : style.size(), style.weight() == 0 ? weight : style.weight(), style.mono(), style.italic());
                CoreText.fontAttribute(attributed, start, style.end(), resolved);
                start = style.end();
            }
        }
        long typesetter = CoreText.typesetter(attributed);

        int cap = text.length() + 8;
        long[] fonts = new long[cap];
        short[] glyphs = new short[cap];
        float[] xs = new float[cap];
        float[] ys = new float[cap];
        float[] sizes = new float[cap];
        int[] indices = new int[cap];
        int count = 0;
        TextLayout.LineTable lineTable = new TextLayout.LineTable();
        int[] stops = TextBounds.graphemes(text);
        float[] downstreamX = new float[text.length() + 1], upstreamX = new float[text.length() + 1];
        double top = 0, widest = 0;
        // Without a width, lines still break at newlines.
        double width = maxWidth > 0 ? maxWidth + 1.0 / 1024 : 1e7;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment metrics = arena.allocate(24, 8);
            int paragraphEnd = text.length(), breakEnd = text.length();
            for (int start = 0; ; ) {
                if (paragraphEnd == start) {
                    float emptyHeight = engine.lineHeight(size, weight);
                    lineTable.add(start, start, breakEnd, count, count, (float) top, emptyHeight, 0);
                    lineTable.baselines[lineTable.count - 1] = (float) top + CoreText.ascent(font);
                    downstreamX[start] = 0;
                    top += emptyHeight;
                    if (breakEnd == text.length() && start == text.length()) break;
                    start = breakEnd;
                    continue;
                }
                int end = Math.min(paragraphEnd, start + (int) Math.max(1, CoreText.suggestLineBreak(typesetter, start, width)));
                end = TextBounds.floor(stops, end);
                if (end <= start) end = TextBounds.after(stops, start);
                long n = end - start;
                long line = CoreText.createLine(typesetter, start, n);
                engine.shapedLines++;
                if (line == 0) {
                    break;
                }
                double lineWidth = CoreText.typographicBounds(line, metrics);
                double ascent = metrics.get(JAVA_DOUBLE, 0);
                double descent = metrics.get(JAVA_DOUBLE, 8);
                double leading = metrics.get(JAVA_DOUBLE, 16);
                double baseline = top + ascent;
                int glyphStart = count;

                long runs = CoreText.glyphRuns(line);
                for (long r = 0, nr = CoreText.arrayCount(runs); r < nr; r++) {
                    long run = CoreText.arrayGet(runs, r);
                    long runFont = canonical(CoreText.dictionaryGetValue(CoreText.runAttributes(run),
                            CoreText.FONT_ATTRIBUTE_NAME));
                    float runSize = metrics(runFont).size();
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
                        sizes = Arrays.copyOf(sizes, grown);
                        indices = Arrays.copyOf(indices, grown);
                    }
                    for (int j = 0; j < ng; j++) {
                        fonts[count] = runFont;
                        sizes[count] = runSize;
                        glyphs[count] = ids.getAtIndex(JAVA_SHORT, j);
                        xs[count] = (float) points.get(JAVA_DOUBLE, 16L * j);
                        // Core Text's y goes up from the baseline.
                        ys[count] = (float) (baseline - points.get(JAVA_DOUBLE, 16L * j + 8));
                        indices[count] = (int) where.getAtIndex(JAVA_LONG, j);
                        count++;
                    }
                }
                int firstStop = Arrays.binarySearch(stops, start);
                for (int position = firstStop; position < stops.length && stops[position] <= end; position++) {
                    int stop = stops[position];
                    float offset = (float) CoreText.lineOffset(line, stop);
                    downstreamX[stop] = offset;
                    if (stop != start || lineTable.count == 0 || lineTable.breaks[lineTable.count - 1] > start) {
                        upstreamX[stop] = offset;
                    }
                }
                float rowWidth = (float) Math.max(lineWidth, CoreText.lineOffset(line, end));
                lineTable.add(start, end, end == paragraphEnd ? breakEnd : end, glyphStart, count,
                        (float) top, (float) (ascent + descent + leading), rowWidth);
                lineTable.baselines[lineTable.count - 1] = (float) baseline;
                CoreText.release(line);
                top += ascent + descent + leading;
                widest = Math.max(widest, rowWidth);
                if (end == text.length()) break;
                start = end == paragraphEnd ? breakEnd : end;
            }
        } finally {
            CoreText.release(typesetter);
            CoreText.release(attributed);
            CoreText.release(attrs);
            CoreText.release(str);
        }
        TextLayout result = new TextLayout(text, size, weight, maxWidth, (float) widest, (float) top, count, fonts, glyphs, xs, ys,
                indices, lineTable, stops, downstreamX, upstreamX);
        result.sizes = sizes;
        return result;
    }

    @Override
    public TextEngine.FontMetrics metrics(long font) {
        return fontMetrics.get(font);
    }

    @Override
    public TextEngine.Bitmap rasterize(long font, int glyph, float scale, float dx, int shade) {
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
            double pixelWidth = Math.ceil((rx + rw) * s + x) - Math.floor(rx * s + x) + 2 * pad;
            double pixelHeight = Math.ceil(-ry * s) - Math.floor(-(ry + rh) * s) + 2 * pad;
            if (!Double.isFinite(pixelWidth) || !Double.isFinite(pixelHeight) || pixelWidth > 2048 || pixelHeight > 2048)
                throw new IllegalStateException("glyph exceeds the 2048-pixel raster limit");
            // Core Graphics' y goes up: the box from ry to ry+rh above the
            // baseline is from -(ry+rh) to -ry below it.
            int left = (int) Math.floor(rx * s + x) - pad;
            int right = (int) Math.ceil((rx + rw) * s + x) + pad;
            int top = (int) Math.floor(-(ry + rh) * s) - pad;
            int bottom = (int) Math.ceil(-ry * s) + pad;
            int w = right - left, h = bottom - top;
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
            engine.rasterized++;
            return new TextEngine.Bitmap(left, top, w, h, pix);
        } finally {
            engine.rasterNanos += System.nanoTime() - started;
        }
    }

    /**
     * The retained canonical font with equal Core Text semantics. Temporary
     * run fonts are not kept after their lines are released.
     */
    private long canonical(long font) {
        long hash = CoreText.hash(font);
        java.util.ArrayList<Long> bucket = canonicalFonts.get(hash);
        if (bucket == null) { bucket = new java.util.ArrayList<>(); canonicalFonts.put(hash, bucket); }
        for (long kept : bucket) if (CoreText.equal(kept, font)) return kept;
        CoreText.retain(font);
        bucket.add(font);
        fontMetrics.put(font, new TextEngine.FontMetrics(CoreText.fontSize(font), CoreText.underline(font), CoreText.thickness(font), CoreText.xHeight(font), CoreText.ascent(font)));
        return font;
    }

    private long styledFont(float size, int weight, boolean mono, boolean italic) {
        long key = ((long) Float.floatToIntBits(size) << 32) | weight | (mono ? 1L << 20 : 0) | (italic ? 1L << 21 : 0);
        Long cached = systemFonts.get(key);
        if (cached != null) return cached;
        long pool = ObjC.poolPush();
        try {
            long font = ObjC.sendDoubles(ObjC.cls("NSFont"), mono ? "monospacedSystemFontOfSize:weight:" : "systemFontOfSize:weight:", size, nsWeight(weight));
            if (family != null && !mono) {
                long manager = ObjC.send(ObjC.cls("NSFontManager"), "sharedFontManager");
                long converted = ObjC.send(manager, "convertFont:toFamily:", font, ObjC.nsString(family));
                if (converted != 0) font = converted;
            }
            long copy = italic ? CoreText.italic(font) : 0;
            long resolved = canonical(copy == 0 ? font : copy);
            if (copy != 0) CoreText.release(copy);
            systemFonts.put(key, resolved);
            return resolved;
        } finally { ObjC.poolPop(pool); }
    }

    /** The system font, as NSFont gives it with its weights, cached. */
    private long systemFont(float size, int weight) {
        return styledFont(size, weight, false, false);
    }

    /** NSFontWeight for a CSS weight, as MyGO maps them. */
    private static double nsWeight(int w) {
        double[] steps = {-0.8, -0.6, -0.4, 0, 0.23, 0.3, 0.4, 0.56, 0.62};
        double f = (Math.max(100, Math.min(w, 900)) - 100) / 100.0;
        int i = Math.min((int) f, steps.length - 2);
        return steps[i] + (steps[i + 1] - steps[i]) * (f - i);
    }

    /** The gray that font smoothing fills glyphs of a shade with. */
    private static double shadeGray(int shade) {
        double y = (double) shade / TextEngine.SHADES;
        return y <= 0.0031308 ? y * 12.92 : 1.055 * Math.pow(y, 1 / 2.4) - 0.055;
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
