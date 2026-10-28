package jui.text;

import java.util.Arrays;

/**
 * A paragraph laid out by Core Text: its glyphs, each with the font Core Text
 * chose for it (a fallback font for what the first one lacks, such as Han
 * characters), placed in points from the paragraph's top left.
 */
public final class TextLayout {
    /** The size of the text's font, in points. */
    public final float size;
    /** The width of the widest line and the height of all lines, in points. */
    public final float width, height;
    public final int lines;

    // What it was laid out from, for the engine's cache to check a hit.
    final String text;
    final int weight;
    final float maxWidth;
    jui.ui.Spans spans;
    float[] sizes;

    final int count;
    final long[] fonts;
    final short[] glyphs;
    /** The pen position of each glyph, and its baseline. */
    final float[] xs, ys;
    /** The index in the text (UTF-16) of the character each glyph draws. */
    final int[] indices;
    /** The text's grapheme cluster boundaries: where a caret may stop. Made on first use. */
    private int[] stops;
    private final LineTable lineTable;
    private final float[] downstreamX, upstreamX;

    TextLayout(String text, float size, int weight, float maxWidth, float width, float height,
               int count, long[] fonts, short[] glyphs, float[] xs, float[] ys, int[] indices,
               LineTable lineTable, int[] stops, float[] downstreamX, float[] upstreamX) {
        this.text = text;
        this.weight = weight;
        this.maxWidth = maxWidth;
        this.size = size;
        this.width = width;
        this.height = height;
        this.lines = lineTable.count;
        this.count = count;
        this.fonts = fonts;
        this.glyphs = glyphs;
        this.xs = xs;
        this.ys = ys;
        this.indices = indices;
        this.lineTable = lineTable;
        this.stops = stops;
        this.downstreamX = downstreamX;
        this.upstreamX = upstreamX;
    }

    public int glyphCount() {
        return count;
    }

    void copyCarets(float[] downstream, float[] upstream, int offset) {
        System.arraycopy(downstreamX, 0, downstream, offset, downstreamX.length);
        System.arraycopy(upstreamX, 0, upstream, offset, upstreamX.length);
    }

    /** Whether this layout was made from these parameters. */
    boolean is(String text, float size, int weight, float maxWidth) {
        return spans == null && this.size == size && this.weight == weight && this.maxWidth == maxWidth && this.text.equals(text);
    }

    boolean is(jui.ui.Spans spans, float size, int weight, float maxWidth) {
        return this.size == size && this.weight == weight && this.maxWidth == maxWidth && this.spans != null && this.spans.sameShape(spans);
    }
    public float glyphSize(int glyph) { return sizes == null ? size : sizes[glyph]; }
    public float lineBaseline(int line) { return lineTable.baselines[line]; }
    public void caretRect(int index, boolean upstream, float[] out) {
        int line = lineAt(index, upstream);
        out[0] = caretX(index, upstream); out[1] = lineTop(line); out[2] = 1; out[3] = lineHeight(line);
    }

    public long font(int i) {
        return fonts[i];
    }

    public int glyph(int i) {
        return glyphs[i] & 0xFFFF;
    }

    public float x(int i) {
        return xs[i];
    }

    public float baseline(int i) {
        return ys[i];
    }

    /** The text's length, in UTF-16 units. */
    public int length() {
        return text.length();
    }

    /** The index in the text of the character glyph i draws. */
    public int index(int glyph) {
        return indices[glyph];
    }

    /**
     * Where a caret before {@code index} goes, from the line's left: at the
     * first glyph of a character at or after it, else at the end of the line
     * (its trailing spaces in). For a layout of one line, as a text input's;
     * the caret geometry of several lines is the editor's (design §12).
     */
    public float caretX(int index) {
        return caretX(index, false);
    }

    /** The caret stop after {@code index}: the end of its grapheme cluster; the length at the end. */
    public int stopAfter(int index) {
        if (stops == null) {
            stops = TextBounds.graphemes(text);
        }
        return TextBounds.after(stops, index);
    }

    /**
     * The caret stop nearest {@code x}, from the line's left: a grapheme
     * cluster boundary, so never inside an emoji or between a letter and its
     * accent. 0 left of the text, its length right of it. For a layout of
     * one line.
     */
    public int indexAt(float x) {
        return indexInLine(0, x);
    }

    public int lineStart(int line) { return lineTable.starts[line]; }
    public int lineEnd(int line) { return lineTable.ends[line]; }
    public int lineBreakEnd(int line) { return lineTable.breaks[line]; }
    public float lineTop(int line) { return lineTable.tops[line]; }
    public float lineHeight(int line) { return lineTable.heights[line]; }
    public float lineWidth(int line) { return lineTable.widths[line]; }
    public int lineGlyphStart(int line) { return lineTable.glyphStarts[line]; }
    public int lineGlyphEnd(int line) { return lineTable.glyphEnds[line]; }

    public float caretX(int index, boolean upstream) {
        int bounded = TextBounds.floor(stops, Math.max(0, Math.min(index, text.length())));
        return upstream ? upstreamX[bounded] : downstreamX[bounded];
    }

    public int floorIndex(int index) {
        return TextBounds.floor(stops, Math.max(0, Math.min(index, text.length())));
    }

    public int lineAt(int index, boolean upstream) {
        int bounded = TextBounds.floor(stops, Math.max(0, Math.min(index, text.length())));
        int low = 0, high = lines;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (lineStart(middle) <= bounded) low = middle + 1;
            else high = middle;
        }
        int found = Math.max(0, low - 1);
        if (upstream && found > 0 && lineStart(found) == bounded
                && lineEnd(found - 1) == bounded && lineBreakEnd(found - 1) == bounded) {
            found--;
        }
        return found;
    }

    public int lineForY(float y) {
        int low = 0, high = lines;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (lineTop(middle) + lineHeight(middle) <= y) low = middle + 1;
            else high = middle;
        }
        return Math.min(low, lines - 1);
    }

    public boolean softEnd(int line) {
        return line + 1 < lines && lineEnd(line) == lineStart(line + 1);
    }

    public int indexInLine(int line, float x) {
        int best = lineStart(line);
        float bestDistance = Float.POSITIVE_INFINITY;
        int first = Arrays.binarySearch(stops, lineStart(line));
        for (int position = Math.max(0, first); position < stops.length; position++) {
            int stop = stops[position];
            if (stop > lineEnd(line)) break;
            float distance = Math.abs(caretX(stop, stop == lineEnd(line) && softEnd(line)) - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = stop;
            }
        }
        return best;
    }

    public void hitTest(float x, float y, int[] out) {
        int line = lineForY(y);
        out[0] = indexInLine(line, x);
        out[1] = out[0] == lineEnd(line) && softEnd(line) ? 1 : 0;
    }

    public boolean selectionRect(int start, int end, int line, float[] out) {
        int first = Math.max(0, Math.min(start, end));
        int last = Math.min(text.length(), Math.max(start, end));
        int selectedStart = Math.max(first, lineStart(line));
        int selectedEnd = Math.min(last, lineEnd(line));
        boolean newline = lineBreakEnd(line) > lineEnd(line)
                && first <= lineEnd(line) && last > lineEnd(line);
        if (first == last || selectedEnd < selectedStart || selectedEnd == selectedStart && !newline) return false;
        float left = caretX(selectedStart, false);
        float right = caretX(selectedEnd, selectedEnd == lineEnd(line) && softEnd(line));
        out[0] = Math.min(left, right);
        out[1] = lineTop(line);
        out[2] = Math.abs(right - left) + (newline ? size / 3 : 0);
        out[3] = lineHeight(line);
        return out[2] > 0;
    }

    static final class LineTable {
        float[] baselines = new float[16];
        int count;
        int[] starts = new int[16], ends = new int[16], breaks = new int[16];
        int[] glyphStarts = new int[16], glyphEnds = new int[16];
        float[] tops = new float[16], heights = new float[16], widths = new float[16];

        void add(int start, int end, int breakEnd, int glyphStart, int glyphEnd,
                 float top, float height, float width) {
            if (count == starts.length) {
                int capacity = count * 2;
                starts = Arrays.copyOf(starts, capacity);
                ends = Arrays.copyOf(ends, capacity);
                breaks = Arrays.copyOf(breaks, capacity);
                glyphStarts = Arrays.copyOf(glyphStarts, capacity);
                glyphEnds = Arrays.copyOf(glyphEnds, capacity);
                tops = Arrays.copyOf(tops, capacity);
                heights = Arrays.copyOf(heights, capacity);
                widths = Arrays.copyOf(widths, capacity);
                baselines = Arrays.copyOf(baselines, capacity);
            }
            starts[count] = start;
            ends[count] = end;
            breaks[count] = breakEnd;
            glyphStarts[count] = glyphStart;
            glyphEnds[count] = glyphEnd;
            tops[count] = top;
            heights[count] = height;
            widths[count++] = width;
        }
    }
}
