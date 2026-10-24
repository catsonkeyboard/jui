package jui.ui;

/** A double a control reads and writes; the control writes the user's edit before it returns. */
public final class DoubleRef {
    private double value;

    public DoubleRef(double value) {
        this.value = value;
    }

    public double get() {
        return value;
    }

    public void set(double value) {
        this.value = value;
    }
}
