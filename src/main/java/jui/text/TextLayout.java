package jui.text;

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

    final int count;
    final long[] fonts;
    final short[] glyphs;
    /** The pen position of each glyph, and its baseline. */
    final float[] xs, ys;
    /** The index in the text (UTF-16) of the character each glyph draws. */
    final int[] indices;
    /** The text's grapheme cluster boundaries: where a caret may stop. Made on first use. */
    private int[] stops;

    TextLayout(String text, float size, int weight, float maxWidth, float width, float height, int lines,
               int count, long[] fonts, short[] glyphs, float[] xs, float[] ys, int[] indices) {
        this.text = text;
        this.weight = weight;
        this.maxWidth = maxWidth;
        this.size = size;
        this.width = width;
        this.height = height;
        this.lines = lines;
        this.count = count;
        this.fonts = fonts;
        this.glyphs = glyphs;
        this.xs = xs;
        this.ys = ys;
        this.indices = indices;
    }

    public int glyphCount() {
        return count;
    }

    /** Whether this layout was made from these parameters. */
    boolean is(String text, float size, int weight, float maxWidth) {
        return this.size == size && this.weight == weight && this.maxWidth == maxWidth && this.text.equals(text);
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
        for (int g = 0; g < count; g++) {
            if (indices[g] >= index) {
                return xs[g];
            }
        }
        return width;
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
        if (stops == null) {
            stops = TextBounds.graphemes(text);
        }
        int best = 0;
        float bestDistance = Float.POSITIVE_INFINITY;
        int g = 0;
        for (int stop : stops) {
            while (g < count && indices[g] < stop) {
                g++;
            }
            float d = Math.abs((g < count ? xs[g] : width) - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = stop;
            }
        }
        return best;
    }
}
