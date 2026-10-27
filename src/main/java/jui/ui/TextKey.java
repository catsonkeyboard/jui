package jui.ui;

/**
 * A key pressed in a text input, as its onTextKey hook sees it before the
 * input takes it (as MyGO's TextKey). It is valid only while the hook runs:
 * the hook runs between frames, where elements kept from the view are not,
 * so it reads and replaces the input's text through it.
 */
public interface TextKey {
    Key key();

    /** The modifier keys held, as {@link Mod} bits. */
    int mods();

    /** The input's committed text, with what was typed just before the key. */
    String text();

    /** Replaces the input's text, and the app's reference with it; it joins the last undo step. */
    void setText(String s);
}
