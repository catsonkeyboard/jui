package jui.ui;

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
