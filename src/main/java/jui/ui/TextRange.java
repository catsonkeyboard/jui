package jui.ui;

/** An immutable half-open range of UTF-16 code units. */
public record TextRange(int start, int end) {
    public TextRange {
        if (start < 0 || end < start) throw new IllegalArgumentException("invalid UTF-16 range");
    }
}
