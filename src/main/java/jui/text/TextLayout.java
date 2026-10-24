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

    TextLayout(String text, float size, int weight, float maxWidth, float width, float height, int lines,
               int count, long[] fonts, short[] glyphs, float[] xs, float[] ys) {
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
}
