package jui.ui.engine;

/**
 * A scroll bar's geometry, shared by paint and the pointer (as MyGO's
 * scrollThumb): a track down the container's right edge, a thumb at least
 * 24 long, 2 in from the track's ends.
 */
final class ScrollBar {
    private ScrollBar() {}

    /** The thumb's length on a track h high, for content contentH high. */
    static float thumbLength(float h, float contentH) {
        float inner = Math.max(0, h - 4);
        return Math.max(Math.min(24, inner), Math.min(inner, contentH > 0 ? inner * h / contentH : inner));
    }

    /** The thumb's top for a container at y, h high, scrolled to offset. */
    static float thumbTop(float y, float h, float contentH, float offset) {
        float travel = Math.max(0, h - 4 - thumbLength(h, contentH));
        float reach = contentH - h;
        return y + 2 + (reach > 0 ? travel * Math.min(1, offset / reach) : 0);
    }
}
