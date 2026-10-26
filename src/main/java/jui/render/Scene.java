package jui.render;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;

import jui.text.GlyphAtlas;
import jui.text.TextEngine;
import jui.text.TextLayout;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

/**
 * What a frame draws, as instances for the shader: rect, radii, color,
 * border color and params, 20 floats each (see shader.metal). Boxes and
 * shadows are in points; glyphs are placed on the pixel grid as they go in.
 */
public final class Scene {
    static final int FLOATS = 20;
    public static final int BYTES = FLOATS * 4;
    private static final float KIND_BOX = 0;
    private static final float KIND_GLYPH = 2;

    private float[] data = new float[FLOATS * 64];
    private int count;
    private float scale = 1;
    private TextEngine text;
    private GlyphAtlas atlas;

    /** Starts a frame at {@code scale} pixels a point, whose text goes through {@code text} and {@code atlas}. */
    public void begin(float scale, TextEngine text, GlyphAtlas atlas) {
        this.count = 0;
        this.depth = 0;
        this.clipX0 = this.clipY0 = Float.NEGATIVE_INFINITY;
        this.clipX1 = this.clipY1 = Float.POSITIVE_INFINITY;
        this.batchCount = 0;
        this.clipChanged = true;
        this.scale = scale;
        this.text = text;
        this.atlas = atlas;
    }

    /**
     * A copy of instance i's 20 floats: rect 0–3, radii 4–7, color 8–11,
     * border 12–15, params 16–19 (kind at 18: 0 box, 1 shadow, 2 glyph).
     * Boxes and shadows are in points, glyphs in pixels. For tests.
     */
    /** The pixels a point of the frame being painted. */
    public float scale() {
        return scale;
    }

    public float[] instance(int i) {
        return java.util.Arrays.copyOfRange(data, i * FLOATS, (i + 1) * FLOATS);
    }

    public int count() {
        return count;
    }

    /**
     * Draws {@code s} in the system font, its first line's top at y, in lines
     * at most {@code maxWidth} points wide (0 for no wrapping), and returns
     * its layout, for its size.
     */
    public TextLayout text(String s, float x, float y, float size, int weight, int color, float maxWidth) {
        TextLayout layout = layout(s, size, weight, maxWidth);
        text(layout, x, y, color);
        return layout;
    }

    /** Lays out {@code s} without drawing it, to place it by its size; layouts are cached. */
    public TextLayout layout(String s, float size, int weight, float maxWidth) {
        return text.layout(s, size, weight, maxWidth);
    }

    /**
     * Draws a laid out text. Glyphs go where Core Graphics puts them: the
     * baseline on the whole pixel below it, and the glyph at the quantized
     * position within a pixel left of its pen.
     */
    public void text(TextLayout layout, float x, float y, int color) {
        int n = TextEngine.positions(layout.size, scale);
        int shade = TextEngine.shadeOf(color);
        for (int i = 0; i < layout.glyphCount(); i++) {
            double pen = (x + layout.x(i)) * scale;
            double whole = Math.floor(pen);
            int sub = Math.min(n - 1, (int) Math.floor((pen - whole) * n));
            int baseline = (int) Math.ceil((y + layout.baseline(i)) * scale - 1e-3);
            GlyphAtlas.Entry e = atlas.get(text, layout.font(i), layout.glyph(i), scale, sub, n, shade);
            if (e == null || e.width() == 0) {
                continue;
            }
            int o = next();
            put(o, (float) whole + e.left(), baseline + e.top(), e.width(), e.height());
            put(o + 4, e.x(), e.y(), 0, 0); // where the glyph is in the atlas
            putColor(o + 8, color);
            putColor(o + 12, 0);
            put(o + 16, 0, 0, KIND_GLYPH, 0);
        }
    }

    /** A box with one radius; colors are 0xRRGGBBAA in sRGB. */
    public void box(float x, float y, float w, float h, float radius, int fill) {
        box(x, y, w, h, radius, radius, radius, radius, fill, 0, 0);
    }

    public void box(float x, float y, float w, float h, float radius, int fill, float borderWidth, int border) {
        box(x, y, w, h, radius, radius, radius, radius, fill, borderWidth, border);
    }

    /** A box with a radius per corner: top-left, top-right, bottom-right, bottom-left. */
    public void box(float x, float y, float w, float h, float tl, float tr, float br, float bl,
                    int fill, float borderWidth, int border) {
        int o = next();
        put(o, x, y, w, h);
        put(o + 4, tl, tr, br, bl);
        putColor(o + 8, fill);
        putColor(o + 12, border);
        put(o + 16, borderWidth, 0, 0, 0);
    }

    /** A soft shadow of a rounded box, {@code blur} points across. */
    public void shadow(float x, float y, float w, float h, float radius, int color, float blur) {
        int o = next();
        put(o, x, y, w, h);
        put(o + 4, radius, radius, radius, radius);
        putColor(o + 8, color);
        putColor(o + 12, 0);
        put(o + 16, 0, blur, 1, 0);
    }

    /**
     * Writes up to {@code max} instances into {@code dst} in pixels, and
     * returns how many it wrote. Boxes' edges go to the nearest pixel, as
     * crisp lines need; a box keeps at least a pixel each way it has size.
     */
    public int writeTo(MemorySegment dst, int max) {
        int n = Math.min(count, max);
        for (int i = 0; i < n; i++) {
            int o = i * FLOATS;
            boolean glyph = data[o + 18] == KIND_GLYPH; // already in pixels
            for (int k = 0; k < FLOATS; k++) {
                float v = data[o + k];
                // Lengths scale to pixels: rect, radii, border width and blur.
                boolean length = !glyph && (k < 8 || k == 16 || k == 17);
                dst.setAtIndex(JAVA_FLOAT, o + k, length ? v * scale : v);
            }
            if (data[o + 18] == KIND_BOX) {
                snap(dst, o, data[o], data[o + 2]);
                snap(dst, o + 1, data[o + 1], data[o + 3]);
            }
        }
        return n;
    }

    /** Writes the start (at o) and size (at o + 2) of a box's edges on whole pixels. */
    private void snap(MemorySegment dst, int o, float start, float size) {
        float from = Math.round(start * scale);
        float to = Math.round((start + size) * scale);
        if (to == from && size > 0) {
            to = from + 1;
        }
        dst.setAtIndex(JAVA_FLOAT, o, from);
        dst.setAtIndex(JAVA_FLOAT, o + 2, to - from);
    }

    private int next() {
        if ((count + 1) * FLOATS > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        if (clipChanged) {
            startBatch();
        }
        return count++ * FLOATS;
    }

    // Clipping: a stack of rectangles, in points; instances drawn under one
    // clip form a batch, which the renderer draws with one scissor rectangle.

    private float clipX0, clipY0, clipX1, clipY1;
    private float[] stack = new float[16];
    private int depth;
    private boolean clipChanged;
    private int[] batchStarts = new int[8];
    private int[] scissors = new int[32]; // x, y, width, height in pixels; width -1 for none
    private int batchCount;

    /** Clips what is drawn next to the rectangle, in points, within the clip already set. */
    public void pushClip(float x, float y, float w, float h) {
        if (depth + 4 > stack.length) {
            stack = Arrays.copyOf(stack, stack.length * 2);
        }
        stack[depth++] = clipX0;
        stack[depth++] = clipY0;
        stack[depth++] = clipX1;
        stack[depth++] = clipY1;
        clipX0 = Math.max(clipX0, x);
        clipY0 = Math.max(clipY0, y);
        clipX1 = Math.min(clipX1, x + w);
        clipY1 = Math.min(clipY1, y + h);
        clipChanged = true;
    }

    /** Back to the clip before the last push. */
    public void popClip() {
        clipY1 = stack[--depth];
        clipX1 = stack[--depth];
        clipY0 = stack[--depth];
        clipX0 = stack[--depth];
        clipChanged = true;
    }

    /** Starts a batch for the current clip, unless the last batch has the same scissor. */
    private void startBatch() {
        clipChanged = false;
        int x = -1, y = 0, w = 0, h = 0;
        if (clipX0 != Float.NEGATIVE_INFINITY || clipY0 != Float.NEGATIVE_INFINITY
                || clipX1 != Float.POSITIVE_INFINITY || clipY1 != Float.POSITIVE_INFINITY) {
            // Each edge to the nearest pixel, as MyGO's snap; an empty clip is 0 wide or high.
            x = snap(clipX0);
            y = snap(clipY0);
            w = Math.max(0, snap(clipX1) - x);
            h = Math.max(0, snap(clipY1) - y);
        }
        if (batchCount > 0) {
            int o = (batchCount - 1) * 4;
            if (scissors[o] == x && scissors[o + 1] == y && scissors[o + 2] == w && scissors[o + 3] == h) {
                return;
            }
        }
        if (batchCount == batchStarts.length) {
            batchStarts = Arrays.copyOf(batchStarts, batchCount * 2);
            scissors = Arrays.copyOf(scissors, batchCount * 8);
        }
        batchStarts[batchCount] = count;
        int o = batchCount * 4;
        scissors[o] = x;
        scissors[o + 1] = y;
        scissors[o + 2] = w;
        scissors[o + 3] = h;
        batchCount++;
    }

    private int snap(float points) {
        float v = points * scale;
        if (v < -1e9f) {
            return -1_000_000_000;
        }
        return v > 1e9f ? 1_000_000_000 : Math.round(v);
    }

    /** Batches of instances, each under one clip. */
    public int batches() {
        return batchCount;
    }

    public int batchStart(int i) {
        return batchStarts[i];
    }

    public int batchEnd(int i) {
        return i + 1 < batchCount ? batchStarts[i + 1] : count;
    }

    public boolean batchClipped(int i) {
        return scissors[i * 4] != -1;
    }

    /** The batch's scissor rectangle in pixels, {x, y, width, height}; only for a clipped batch. */
    public void batchScissor(int i, int[] out) {
        System.arraycopy(scissors, i * 4, out, 0, 4);
    }

    private void put(int o, float a, float b, float c, float d) {
        data[o] = a;
        data[o + 1] = b;
        data[o + 2] = c;
        data[o + 3] = d;
    }

    private void putColor(int o, int rgba) {
        put(o, ((rgba >>> 24) & 0xFF) / 255f, ((rgba >>> 16) & 0xFF) / 255f,
                ((rgba >>> 8) & 0xFF) / 255f, (rgba & 0xFF) / 255f);
    }
}
