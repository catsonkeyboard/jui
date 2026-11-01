package jui.text;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import jui.util.LongMap;

/**
 * Glyph coverage, one byte a pixel, packed on shelves into one texture's
 * worth of memory: the renderer uploads the rectangle that changed. Each
 * glyph is kept for its font, size in pixels, position within a pixel and
 * shade, as MyGO's glyph keys are.
 *
 * <p>The two-argument constructor has fixed capacity. An explicit maximum
 * enables growth up to 4096, then reclaims cold glyphs by transactional repacking.
 */
public final class GlyphAtlas {
    public int width, height;
    private MemorySegment pixels;
    private Arena arena;
    private final int maxSize;
    private final boolean scalable;
    private int generation, repacks;
    private long frame;
    private List<Entry> live = new ArrayList<>();
    public int repacks() { return repacks; }
    /** Scene starts each paint with a new age; current-frame entries are pinned. */
    public void beginFrame() { frame++; }
    public int generation() { return generation; }
    /** Glyphs by key (see key): a frame looks up every glyph it draws, without allocating. */
    private final LongMap<Entry> entries = new LongMap<>();
    /** A small number for each font, which keys take in place of its 64-bit reference. */
    private final LongMap<Integer> fontIds = new LongMap<>();
    private List<int[]> shelves = new ArrayList<>(); // {y, height, next x}
    private int nextY;
    private boolean full;
    // The rectangle that changed since the last upload.
    private int dirtyX0 = Integer.MAX_VALUE, dirtyY0 = Integer.MAX_VALUE, dirtyX1, dirtyY1;

    /** Where a glyph's bitmap is in the atlas, and where it goes from the pen and baseline. */
    public static final class Entry {
        private int x, y;
        private final int width, height, left, top;
        private long key, usedFrame;
        private boolean resident = true;
        private Entry(int x, int y, int width, int height, int left, int top) {
            this.x = x; this.y = y; this.width = width; this.height = height; this.left = left; this.top = top;
        }
        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int height() { return height; }
        public int left() { return left; }
        public int top() { return top; }
        public boolean resident() { return resident; }
    }

    public GlyphAtlas(int width, int height) {
        this(width, height, Math.max(width, height), false);
    }

    public GlyphAtlas(int width, int height, int maxSize) {
        this(width, height, maxSize, true);
    }

    private GlyphAtlas(int width, int height, int maxSize, boolean scalable) {
        if (width < 1 || height < 1 || maxSize < Math.max(width, height) || maxSize > 4096)
            throw new IllegalArgumentException("invalid atlas dimensions");
        this.width = width;
        this.height = height;
        this.maxSize = maxSize;
        this.scalable = scalable;
        this.arena = Arena.ofAuto();
        this.pixels = arena.allocate((long) width * height);
    }

    public MemorySegment pixels() {
        return pixels;
    }

    public int glyphs() {
        return entries.size();
    }

    /** Whether a glyph found no room: it was not drawn. */
    public boolean isFull() {
        return full;
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
            e.usedFrame = frame;
            return e;
        }
        TextEngine.Bitmap b = engine.rasterize(font, glyph, scale, (float) sub / n, shade);
        if (b == null) {
            e = new Entry(0, 0, 0, 0, 0, 0);
            keep(key, e);
            return e;
        }
        int[] at = place(b.width(), b.height());
        while (at == null && grow()) at = place(b.width(), b.height());
        if (at == null && scalable && b.width() <= width && b.height() <= height)
            at = reclaim(b.width(), b.height());
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
        keep(key, e);
        return e;
    }

    private void keep(long key, Entry e) {
        e.key = key; e.usedFrame = frame;
        entries.put(key, e); live.add(e);
    }

    /** Try recent frames first, then current-frame pins only. Failed attempts change nothing. */
    private int[] reclaim(int incomingW, int incomingH) {
        for (long oldest = Math.max(0, frame - 1); oldest <= frame; oldest++) {
            List<Entry> survivors = new ArrayList<>();
            for (Entry e : live) if (e.usedFrame >= oldest) survivors.add(e);
            survivors.sort(java.util.Comparator.comparingInt(Entry::height).reversed());
            GlyphAtlas trial = new GlyphAtlas(width, height);
            int[] xs = new int[survivors.size()], ys = new int[survivors.size()];
            boolean fits = true;
            for (int i = 0; i < survivors.size(); i++) {
                Entry e = survivors.get(i);
                if (e.width == 0) continue;
                int[] at = trial.place(e.width, e.height);
                if (at == null) { fits = false; break; }
                xs[i] = at[0]; ys[i] = at[1];
            }
            int[] incoming = fits ? trial.place(incomingW, incomingH) : null;
            if (incoming == null) continue;
            // Copy from the old allocation before committing any coordinate changes.
            for (int i = 0; i < survivors.size(); i++) {
                Entry e = survivors.get(i);
                for (int y = 0; y < e.height; y++) MemorySegment.copy(pixels, (long) (e.y + y) * width + e.x,
                        trial.pixels, (long) (ys[i] + y) * width + xs[i], e.width);
            }
            for (Entry e : live) if (e.usedFrame < oldest) { entries.remove(e.key); e.resident = false; }
            for (int i = 0; i < survivors.size(); i++) { survivors.get(i).x = xs[i]; survivors.get(i).y = ys[i]; }
            live = survivors; shelves = trial.shelves; nextY = trial.nextY;
            arena = trial.arena; pixels = trial.pixels;
            generation++; repacks++;
            dirtyX0 = dirtyY0 = 0; dirtyX1 = width; dirtyY1 = height;
            return incoming;
        }
        return null;
    }

    /**
     * A glyph's key: its font's number, the glyph, the scale in 1/256ths of
     * a pixel a point, the position within a pixel and the shade, in 16, 16,
     * 16, 8 and 8 bits.
     */
    private long key(long font, int glyph, float scale, int sub, int shade) {
        Integer id = fontIds.get(font);
        if (id == null) {
            if (fontIds.size() >= 65536) throw new IllegalStateException("atlas font IDs exhausted");
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

    private boolean grow() {
        if (!scalable) return false;
        int nextWidth = Math.min(maxSize, width * 2), nextHeight = Math.min(maxSize, height * 2);
        if (nextWidth == width && nextHeight == height) return false;
        Arena nextArena = Arena.ofAuto();
        MemorySegment next = nextArena.allocate((long) nextWidth * nextHeight);
        for (int row = 0; row < height; row++) MemorySegment.copy(pixels, (long) row * width, next, (long) row * nextWidth, width);
        arena = nextArena; pixels = next; width = nextWidth; height = nextHeight;
        generation++;
        dirtyX0 = dirtyY0 = 0; dirtyX1 = width; dirtyY1 = height;
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
