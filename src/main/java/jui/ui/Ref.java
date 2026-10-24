package jui.ui;

/** A value a control or a local state holds, read and written in place. */
public final class Ref<T> {
    private T value;

    public Ref(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}
