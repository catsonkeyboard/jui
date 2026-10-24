package jui.text;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Glyph coverage, one byte a pixel, packed on shelves into one texture's
 * worth of memory: the renderer uploads the rectangle that changed. Each
 * glyph is kept for its font, size in pixels, position within a pixel and
 * shade, as MyGO's glyph keys are.
 *
 * <p>A spike's atlas: it neither grows nor forgets glyphs (MyGO repacks,
 * grows and frees what frames stop drawing).
 */
public final class GlyphAtlas {
    public final int width, height;
    private final MemorySegment pixels;
    /** Glyphs by key (see key): a frame looks up every glyph it draws, without allocating. */
    private final LongMap<Entry> entries = new LongMap<>();
    /** A small number for each font, which keys take in place of its 64-bit reference. */
    private final LongMap<Integer> fontIds = new LongMap<>();
    private final List<int[]> shelves = new ArrayList<>(); // {y, height, next x}
    private int nextY;
    private boolean full;
    // The rectangle that changed since the last upload.
    private int dirtyX0 = Integer.MAX_VALUE, dirtyY0 = Integer.MAX_VALUE, dirtyX1, dirtyY1;

    /** Where a glyph's bitmap is in the atlas, and where it goes from the pen and baseline. */
    public record Entry(int x, int y, int width, int height, int left, int top) {
        static final Entry EMPTY = new Entry(0, 0, 0, 0, 0, 0);
    }

    public GlyphAtlas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = Arena.global().allocate((long) width * height);
    }

    public MemorySegment pixels() {
        return pixels;
    }

    public int glyphs() {
        return entries.size();
    }

    /**
     * The glyph drawn at {@code scale} and position {@code sub} of {@code n}
     * within a pixel, rasterizing it the first time; null when the atlas is
     * full, and an empty entry for a glyph without ink.
     */
    public Entry get(TextEngine engine, long font, int glyph, float scale, int sub, int n, int shade) {
        long key = key(font, glyph, scale, sub, shade);
        Entry e = entries.get(key);
        if (e != null) {
            return e;
        }
        TextEngine.Bitmap b = engine.rasterize(font, glyph, scale, (float) sub / n, shade);
        if (b == null) {
            entries.put(key, Entry.EMPTY);
            return Entry.EMPTY;
        }
        int[] at = place(b.width(), b.height());
        if (at == null) {
            if (!full) {
                full = true;
                System.err.println("jui: the glyph atlas is full");
            }
            return null;
        }
        for (int row = 0; row < b.height(); row++) {
            MemorySegment.copy(b.pixels(), (long) row * b.width(), pixels, (long) (at[1] + row) * width + at[0],
                    b.width());
        }
        dirtyX0 = Math.min(dirtyX0, at[0]);
        dirtyY0 = Math.min(dirtyY0, at[1]);
        dirtyX1 = Math.max(dirtyX1, at[0] + b.width());
        dirtyY1 = Math.max(dirtyY1, at[1] + b.height());
        e = new Entry(at[0], at[1], b.width(), b.height(), b.left(), b.top());
        entries.put(key, e);
        return e;
    }

    /**
     * A glyph's key: its font's number, the glyph, the scale in 1/256ths of
     * a pixel a point, the position within a pixel and the shade, in 16, 16,
     * 16, 8 and 8 bits.
     */
    private long key(long font, int glyph, float scale, int sub, int shade) {
        Integer id = fontIds.get(font);
        if (id == null) {
            id = fontIds.size();
            fontIds.put(font, id);
        }
        long scale256 = Math.round(scale * 256) & 0xFFFF;
        return ((long) (id & 0xFFFF) << 48) | ((long) (glyph & 0xFFFF) << 32) | (scale256 << 16)
                | ((sub & 0xFF) << 8) | (shade & 0xFF);
    }

    /**
     * Writes the rectangle that changed since the last call to {@code out}
     * as x, y, width, height, and reports whether there was one.
     */
    public boolean takeDirty(int[] out) {
        if (dirtyX0 >= dirtyX1) {
            return false;
        }
        out[0] = dirtyX0;
        out[1] = dirtyY0;
        out[2] = dirtyX1 - dirtyX0;
        out[3] = dirtyY1 - dirtyY0;
        dirtyX0 = dirtyY0 = Integer.MAX_VALUE;
        dirtyX1 = dirtyY1 = 0;
        return true;
    }

    /** Finds room for w by h pixels, a pixel apart from others: the lowest shelf that fits. */
    private int[] place(int w, int h) {
        int[] best = null;
        for (int[] shelf : shelves) {
            if (h <= shelf[1] && shelf[2] + w <= width && (best == null || shelf[1] < best[1])) {
                best = shelf;
            }
        }
        if (best == null || best[1] > h * 2) { // a new shelf rather than wasting most of a tall one
            if (nextY + h > height || w > width) {
                if (best == null) {
                    return null;
                }
            } else {
                best = new int[] {nextY, h, 0};
                shelves.add(best);
                nextY += h + 1;
            }
        }
        int[] at = {best[2], best[0]};
        best[2] += w + 1;
        return at;
    }
}
