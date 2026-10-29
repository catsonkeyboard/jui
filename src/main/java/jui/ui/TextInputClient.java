package jui.ui;

/**
 * An application-owned document and text service, called synchronously on
 * the UI thread. Offsets are UTF-16 and include marked text in the document.
 * Selection outputs anchor/caret; marked ranges output start/end. A null
 * replacement means the marked range, otherwise the selection. Bounds are
 * border-box-local DIPs followed by actual start/end, not start/length.
 * Query output arrays are borrowed for the call and must not be retained.
 * Callbacks must not reenter view construction or painting.
 */
public interface TextInputClient {
    int length();
    String text(int start, int end, int[] actualStartEnd);
    void selection(int[] anchorCaret);
    boolean markedRange(int[] startEnd);
    void replaceText(TextRange replacement, String text);
    void setMarkedText(TextRange replacement, String text, TextRange selected);
    void unmarkText();
    boolean boundsFor(int start, int end, float[] rectAndRange);
    int indexAt(float x, float y);
    default void resized(float width, float height) {}
}
