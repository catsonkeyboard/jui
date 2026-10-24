package jui.testing;

import java.util.Objects;

/** Assertions for the zero-dependency test runner (see RunTests). */
public final class Check {
    private Check() {}

    public static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void equal(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /** Equal within 0.01, for lengths in DIPs. */
    public static void near(double expected, double actual) {
        near(expected, actual, 0.01);
    }

    public static void near(double expected, double actual, double tolerance) {
        if (!(Math.abs(expected - actual) <= tolerance)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Runs code, which must throw a {@code type}, and returns what it threw. */
    public static <T extends Throwable> T fails(Class<T> type, Runnable code) {
        try {
            code.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("expected <" + type.getName() + "> but was <" + t + ">", t);
        }
        throw new AssertionError("expected <" + type.getName() + "> but nothing was thrown");
    }
}
