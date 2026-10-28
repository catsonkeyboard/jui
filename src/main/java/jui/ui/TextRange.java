package jui.ui;

public record TextRange(int start, int end) {
    public TextRange {
        if (start < 0 || end < start) throw new IllegalArgumentException("invalid UTF-16 range");
    }
}
