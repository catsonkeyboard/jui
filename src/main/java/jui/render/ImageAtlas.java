package jui.render;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The atlas decoded images live in, so the renderer samples every picture
 * from one RGBA8 texture. Packing is shelf by shelf; an image that does
 * not fit grows the atlas and repacks, and ids keep across a repack. The
 * pixels sit premultiplied in off-heap memory, as the blend and the
 * renderer's upload both expect.
 */
public final class ImageAtlas {
    public int width, height;

    /** One uploaded image: its place in the atlas, in atlas pixels. */
    public static final class Entry {
        int x, y, width, height;

        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int height() { return height; }
    }

    private record Pic(int w, int h, int[] pixels) {}

    private static final int SHELF_GAP = 1;
    private Entry[] entries = new Entry[16];
    private final java.util.ArrayList<Pic> pics = new java.util.ArrayList<>();
    private int count;
    private int shelfTop;     // where full shelves end
    private int shelfX;       // where the open shelf's next image goes
    private int shelfW;       // the open shelf's tallest image so far
    private MemorySegment pixels;
    private int generation;
    private int repacks;
    private final int maxSize;

    public ImageAtlas(int width, int height) {
        this(width, height, 4096);
    }

    public ImageAtlas(int width, int height, int maxSize) {
        this.width = width;
        this.height = height;
        this.maxSize = maxSize;
        this.pixels = alloc(width, height);
    }

    public int generation() { return generation; }
    public int repacks() { return repacks; }
    public MemorySegment pixels() { return pixels; }
    public int count() { return count; }

    /**
     * Adds an image of straight ARGB pixels, row-major, top-left first,
     * and returns its id. The atlas copies and premultiplies the pixels,
     * so the caller's array is free to reuse.
     */
    public int add(int w, int h, int[] argb) {
        if (w <= 0 || h <= 0 || w > maxSize || h > maxSize) {
            throw new IllegalArgumentException("image " + w + "x" + h + " out of the atlas's range");
        }
        int[] copy = argb.clone();
        premultiply(copy);
        int id = pics.size();
        pics.add(new Pic(w, h, copy));
        ensure(id, place(id, w, h));
        count = pics.size();
        generation++;
        return id;
    }

    // One entry's placement and pixel write; ensure grows first when it must.
    private Entry place(int id, int w, int h) {
        Entry e = new Entry();
        e.width = w;
        e.height = h;
        // The open shelf takes it; else a new shelf opens; else the atlas grows.
        if (shelfX + w + SHELF_GAP > width) {
            newShelf(h);
        }
        if (shelfTop + h > height) {
            grow(Math.max(width * 2, w + SHELF_GAP), Math.max(height * 2, h + SHELF_GAP));
        }
        e.x = shelfX;
        e.y = shelfTop;
        return e;
    }

    private void ensure(int id, Entry e) {
        if (id >= entries.length) {
            Entry[] grown = new Entry[Math.max(16, id * 2)];
            System.arraycopy(entries, 0, grown, 0, entries.length);
            entries = grown;
        }
        Pic p = pics.get(id);
        copyRows(MemorySegment.ofArray(p.pixels()), p.w(), e);
        entries[id] = e;
        shelfX += p.w() + SHELF_GAP;
        shelfW = Math.max(shelfW, p.h());
        if (shelfX + SHELF_GAP >= width) {
            newShelf(0);
        }
    }

    /** Grows the atlas and replays every image in add order; the ids keep. */
    private void grow(int newWidth, int newHeight) {
        // Read every resident image back out, then replay into the bigger sheet.
        int[][] sources = new int[pics.size()][];
        for (int id = 0; id < pics.size(); id++) {
            sources[id] = pics.get(id).pixels();
        }
        width = newWidth;
        height = newHeight;
        pixels = alloc(width, height);
        count = 0;
        shelfTop = 0;
        shelfX = 0;
        shelfW = 0;
        repacks++;
        for (int id = 0; id < sources.length; id++) {
            ensure(id, place(id, pics.get(id).w(), pics.get(id).h()));
        }
        count = pics.size();
        generation++;
    }

    private void newShelf(int firstHeight) {
        shelfTop += shelfW + SHELF_GAP;
        shelfX = 0;
        shelfW = firstHeight;
    }

    private static MemorySegment alloc(int w, int h) {
        return Arena.global().allocate((long) w * h * 4, 4);
    }

    /** Copies w×h premultiplied pixels from a heap array into the sheet at e. */
    private void copyRows(MemorySegment src, int srcW, Entry e) {
        for (int row = 0; row < e.height; row++) {
            MemorySegment.copy(src, JAVA_INT, (long) row * srcW * 4,
                    pixels, JAVA_INT, ((long) (e.y + row) * width + e.x) * 4, e.width);
        }
    }

    /** Straight ARGB to premultiplied ARGB, in place. */
    private static void premultiply(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = (p >>> 24) & 0xFF;
            if (a == 0xFF || a == 0) {
                continue;
            }
            int r = ((p >>> 16) & 0xFF) * a / 255;
            int g = ((p >>> 8) & 0xFF) * a / 255;
            int b = (p & 0xFF) * a / 255;
            px[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    Entry entry(int id) {
        return id >= 0 && id < count ? entries[id] : null;
    }

    static final class Access {
        static Entry entryOf(ImageAtlas a, int id) {
            return a.entry(id);
        }
    }
}
