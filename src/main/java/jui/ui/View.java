package jui.ui;

/**
 * A view: builds a window's interface from the app's state. It runs on the
 * main thread for every frame, possibly more than once.
 */
@FunctionalInterface
public interface View {
    void build(Context c);
}
