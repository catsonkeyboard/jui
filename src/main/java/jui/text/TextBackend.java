package jui.text;

/**
 * What a platform lays out and rasterizes text with: Core Text on macOS,
 * DirectWrite on Windows, as MyGO splits its text engine per system. Font
 * handles are opaque and stay valid while the backend lives; everything
 * runs on the main thread, as the engines that use it do.
 */
interface TextBackend {
    /**
     * Lays out one paragraph of {@code text} (which holds no newlines) in
     * the system font of {@code size} points and {@code weight} (100 to
     * 900), in lines at most {@code maxWidth} points wide (0 for no
     * wrapping); {@code spans}, when not null, vary the fonts within it.
     */
    TextLayout shape(String text, jui.ui.Spans spans, float size, int weight, float maxWidth);

    /** The metrics of a font a layout reported. */
    TextEngine.FontMetrics metrics(long font);

    /**
     * Rasterizes a glyph at {@code scale} pixels a point, {@code dx} pixels
     * right of a whole pixel, for text of {@code shade}; null for a glyph
     * with no ink. The pixels stay valid until the next call.
     */
    TextEngine.Bitmap rasterize(long font, int glyph, float scale, float dx, int shade);

    /** Probes the FFI shapes rich text needs; only the backend's own tests call it. */
    default void checkRichFfi() {}
}
