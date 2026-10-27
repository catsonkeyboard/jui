package jui.ui;

/** A command of the Edit menu, for the text input with focus (as MyGO's command names). */
public enum EditCommand {
    UNDO, REDO, CUT, COPY, PASTE, SELECT_ALL,
    /** Paste and Match Style: in a text input, a paste (as MyGO). */
    PASTE_AND_MATCH_STYLE,
    /** Delete: the selection, if there is one. */
    DELETE
}
