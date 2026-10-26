package jui.ui;

import jui.ui.engine.ListModel;

/**
 * A list's state, kept in the app's state and passed to list(...) on every
 * build: the heights it has measured, where its view is, and which rows the
 * last frame showed.
 */
public final class ListState {
    final ListModel model = new ListModel();

    public ListState() {}

    /** The first row the last frame showed, or -1 before the list shows. */
    public int first() {
        return model.first();
    }

    /** The last row the last frame showed, or -1 before the list shows. */
    public int last() {
        return model.last();
    }
}
