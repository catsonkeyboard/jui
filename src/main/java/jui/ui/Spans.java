package jui.ui;

import java.util.ArrayList;
import java.util.Arrays;
import jui.text.TextBounds;

public final class Spans {
    public record Shape(int end, float size, int weight, boolean mono, boolean italic) {}
    private final Span[] values;
    private final int[] ends;
    private final Shape[] shapes;
    private final String text;
    private final int shapeHash, hash;

    private Spans(Span[] input) {
        values = Arrays.stream(input).filter(value -> !value.text().isEmpty()).toArray(Span[]::new);
        ends = new int[values.length];
        StringBuilder joined = new StringBuilder();
        ArrayList<Shape> runs = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            Span span = values[index];
            joined.append(span.text()); ends[index] = joined.length();
            Shape previous = runs.isEmpty() ? null : runs.getLast();
            Shape shape = new Shape(ends[index], span.size(), span.weight(), span.mono(), span.oblique());
            if (previous != null && previous.size == shape.size && previous.weight == shape.weight
                    && previous.mono == shape.mono && previous.italic == shape.italic) runs.set(runs.size() - 1, shape);
            else runs.add(shape);
        }
        text = joined.toString();
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (Character.isHighSurrogate(value)) {
                if (++index == text.length() || !Character.isLowSurrogate(text.charAt(index))) throw new IllegalArgumentException("unpaired surrogate");
            } else if (Character.isLowSurrogate(value)) throw new IllegalArgumentException("unpaired surrogate");
        }
        int[] stops = TextBounds.graphemes(text);
        for (int end : ends) if (Arrays.binarySearch(stops, end) < 0) throw new IllegalArgumentException("span splits a grapheme");
        shapes = runs.toArray(Shape[]::new);
        shapeHash = 31 * text.hashCode() + Arrays.hashCode(shapes);
        hash = Arrays.hashCode(values);
    }

    public static Spans of(Span... spans) { return new Spans(spans.clone()); }
    public String text() { return text; }
    public int size() { return values.length; }
    public Span get(int index) { return values[index]; }
    public int start(int index) { return index == 0 ? 0 : ends[index - 1]; }
    public int end(int index) { return ends[index]; }
    public int at(int offset) {
        int found = Arrays.binarySearch(ends, offset + 1);
        return Math.min(values.length - 1, found >= 0 ? found : -found - 1);
    }
    public int shapeCount() { return shapes.length; }
    public Shape shape(int index) { return shapes[index]; }
    public int shapeHash() { return shapeHash; }
    public boolean sameShape(Spans other) { return this == other || other != null && shapeHash == other.shapeHash && text.equals(other.text) && Arrays.equals(shapes, other.shapes); }
    public Spans slice(int start, int end) {
        ArrayList<Span> result = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            int first = Math.max(start, start(index)), last = Math.min(end, end(index));
            if (first < last) result.add(values[index].withText(text.substring(first, last)));
        }
        return of(result.toArray(Span[]::new));
    }
    @Override public boolean equals(Object other) { return this == other || other instanceof Spans spans && Arrays.equals(values, spans.values); }
    @Override public int hashCode() { return hash; }
}
