package jui.ui;

public record Span(String text, float size, int weight, boolean mono, boolean oblique, int rgba,
                   boolean colored, int fill, float rounding, boolean underlined, boolean struck) {
    public Span {
        java.util.Objects.requireNonNull(text);
        if (!Float.isFinite(size) || size < 0 || weight != 0 && (weight < 100 || weight > 900)
                || !Float.isFinite(rounding) || rounding < 0) throw new IllegalArgumentException("invalid span style");
    }
    public static Span text(String text) { return new Span(text, 0, 0, false, false, 0, false, 0, 0, false, false); }
    public Span fontSize(float value) { return new Span(text, value, weight, mono, oblique, rgba, colored, fill, rounding, underlined, struck); }
    public Span fontWeight(int value) { return new Span(text, size, value, mono, oblique, rgba, colored, fill, rounding, underlined, struck); }
    public Span bold() { return fontWeight(700); }
    public Span monospace() { return new Span(text, size, weight, true, oblique, rgba, colored, fill, rounding, underlined, struck); }
    public Span italic() { return new Span(text, size, weight, mono, true, rgba, colored, fill, rounding, underlined, struck); }
    public Span color(Color value) { return new Span(text, size, weight, mono, oblique, value.rgba(), true, fill, rounding, underlined, struck); }
    public Span background(Color value) { return new Span(text, size, weight, mono, oblique, rgba, colored, value.rgba(), rounding, underlined, struck); }
    public Span radius(float value) { return new Span(text, size, weight, mono, oblique, rgba, colored, fill, value, underlined, struck); }
    public Span underline() { return new Span(text, size, weight, mono, oblique, rgba, colored, fill, rounding, true, struck); }
    public Span strikethrough() { return new Span(text, size, weight, mono, oblique, rgba, colored, fill, rounding, underlined, true); }
    public Span withText(String value) { return new Span(value, size, weight, mono, oblique, rgba, colored, fill, rounding, underlined, struck); }
}
