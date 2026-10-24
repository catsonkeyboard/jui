package jui.ui;

/** A int a control reads and writes; the control writes the user's edit before it returns. */
public final class IntRef {
    private int value;

    public IntRef(int value) {
        this.value = value;
    }

    public int get() {
        return value;
    }

    public void set(int value) {
        this.value = value;
    }
}
