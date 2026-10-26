package jui.ui;

/** A string a control reads and writes: a text input's value (design §6.4). Never null. */
public final class TextRef {
    private String value;

    public TextRef(String value) {
        this.value = value == null ? "" : value;
    }

    public String get() {
        return value;
    }

    /** Sets the value; null stores the empty string. */
    public void set(String value) {
        this.value = value == null ? "" : value;
    }
}
