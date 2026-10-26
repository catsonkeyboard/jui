package jui.ui.engine;

/**
 * One animated value of an element, as MyGO's motion channels: from a value
 * to a target over a duration, eased out, from the frame its target changed.
 */
final class Motion {
    float from, to, value;
    long start, duration;
    boolean moving;

    /** Moves the value to where it is at now. */
    void advance(long now) {
        if (!moving) {
            return;
        }
        double t = (double) (now - start) / duration;
        if (t >= 1) {
            value = to;
            moving = false;
        } else if (t > 0) {
            double u = 1 - t;
            value = (float) (from + (to - from) * (1 - u * u * u));
        }
    }
}
