package jui.ui.engine;

/**
 * Scroll bars' geometry, shared by paint and the pointer (as MyGO's
 * scrollBars and scrollThumb): a vertical track down the container's right
 * edge and a horizontal one along its bottom, each leaving the other the
 * corner when both show; a thumb at least 24 long, 2 in from the track's
 * ends.
 */
final class ScrollBar {
    private ScrollBar() {}

    /** Whether a container h high with content contentH high shows its vertical bar, by its flags. */
    static boolean vertical(int flags, float h, float contentH) {
        return (flags & Node.SCROLL_Y) != 0 && contentH > h + 0.5f;
    }

    /** Whether a container w wide with content contentW wide shows its horizontal bar, by its flags. */
    static boolean horizontal(int flags, float w, float contentW) {
        return (flags & Node.SCROLL_X) != 0 && contentW > w + 0.5f;
    }

    /** What each track leaves the other at their corner: the bar's width and 3 when both show, else 0. */
    static float corner(boolean vertical, boolean horizontal, float width) {
        return vertical && horizontal ? width + 3 : 0;
    }

    /** The thumb's length on a track track long, for a view view long over content content long. */
    static float thumbLength(float track, float view, float content) {
        float inner = Math.max(0, track - 4);
        return Math.max(Math.min(24, inner), Math.min(inner, content > 0 ? inner * view / content : inner));
    }

    /** Where the thumb starts on a track from start, track long, for a view over content scrolled to offset. */
    static float thumbStart(float start, float track, float view, float content, float offset) {
        float travel = Math.max(0, track - 4 - thumbLength(track, view, content));
        float reach = content - view;
        return start + 2 + (reach > 0 ? travel * Math.max(0, Math.min(1, offset / reach)) : 0);
    }
}
