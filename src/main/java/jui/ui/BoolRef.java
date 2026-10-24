package jui.ui;

/**
 * A boolean a control reads and writes, as MyGO's controls take a *bool:
 * the control writes the user's edit before it returns.
 */
public final class BoolRef {
    private boolean value;

    public BoolRef(boolean value) {
        this.value = value;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean value) {
        this.value = value;
    }
}
