package jui.ui;

/** A float a control reads and writes; the control writes the user's edit before it returns. */
public final class FloatRef {
    private float value;

    public FloatRef(float value) {
        this.value = value;
    }

    public float get() {
        return value;
    }

    public void set(float value) {
        this.value = value;
    }
}
