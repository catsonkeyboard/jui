package jui.ui.engine;

/**
 * One animated value of an element, as MyGO's motion channels: from a value
 * to a target over a duration, eased out, from the frame its target changed;
 * or a damped spring that keeps its velocity across retargets, so a width
 * morph reversing mid-flight glides through instead of restarting.
 */
final class Motion {
    static final byte TWEEN = 0, SPRING = 1;

    byte mode = TWEEN;
    float from, to, value;
    long start, duration;
    boolean moving;

    // Spring mode: semi-implicit Euler on stiffness/damping, substepped so a
    // long frame reads the same as the several short ones it spans.
    float velocity;
    double stiffness, damping;
    long springLast;

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

    /** Retargets the spring, keeping its velocity — the morph's memory. A
     *  tween channel switches here, its velocity starting at rest. */
    void springTo(long now, float target, double stiffness, double damping) {
        if (mode == TWEEN) {
            mode = SPRING;
            velocity = 0;
        }
        this.to = target;
        this.stiffness = stiffness;
        this.damping = damping;
        this.springLast = now;
        this.moving = true;                              // the next advance settles a no-op
    }

    /** Integrates the spring to now, then folds it away once it is at rest
     *  at its target: value and velocity inside the settle window snap to
     *  the target exactly, so the channel stops asking for frames. */
    void springAdvance(long now) {
        if (mode != SPRING || !moving) {
            return;
        }
        float dt = (now - springLast) / 1_000_000_000f;
        springLast = now;
        if (dt <= 0) {
            return;
        }
        int steps = Math.min(64, Math.max(1, (int) Math.ceil(dt * 240f)));
        float h = dt / steps;
        float k = (float) stiffness, d = (float) damping;
        for (int i = 0; i < steps; i++) {
            float a = k * (to - value) - d * velocity;
            velocity += a * h;
            value += velocity * h;
        }
        if (Math.abs(to - value) < 0.01f && Math.abs(velocity) < 0.01f) {
            value = to;
            velocity = 0;
            moving = false;
        }
    }
}
