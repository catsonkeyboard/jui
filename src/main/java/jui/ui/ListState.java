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

    /**
     * Keys the rows: keyOf(i) is row i's key, unique among them (as MyGO's
     * Key). The view stays on the same item as rows come and go above it,
     * and the row's state follows its item: the row callback must not set a
     * key of its own (c.key). Returns this state.
     */
    public ListState keys(java.util.function.IntToLongFunction keyOf) {
        model.keys(keyOf);
        return this;
    }

    /**
     * Follows the list's end, as a log does (as MyGO's FollowEnd): it starts
     * at the end and stays there as rows are added or the last grows.
     * Scrolling up stops it; scrolling back to the end, or scrollToEnd(),
     * starts it again. Returns this state.
     */
    public ListState followEnd(boolean on) {
        model.followEnd(on);
        return this;
    }

    /** Whether the last frame showed the end: the last row at the bottom of the view. */
    public boolean atEnd() {
        return model.atEnd();
    }

    /** Shows the end in the next frame; a list that follows its end follows it again. */
    public void scrollToEnd() {
        model.requestEnd();
    }

    /** Shows row 0 at the top in the next frame. */
    public void scrollToTop() {
        model.requestRow(0);
    }

    /** Shows row at the top of the view in the next frame (as MyGO's ScrollTo(row, Start)). */
    public void scrollTo(int row) {
        model.requestRow(Math.max(0, row));
    }

    /** The first row the last frame showed, or -1 before the list shows. */
    public int first() {
        return model.first();
    }

    /** How many rows the last frame built: for tools. */
    public int rowsBuilt() {
        return Math.max(0, model.builtLast() - model.builtFirst() + 1);
    }

    /** The last row the last frame showed, or -1 before the list shows. */
    public int last() {
        return model.last();
    }
}
