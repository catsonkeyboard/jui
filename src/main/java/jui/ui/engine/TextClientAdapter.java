package jui.ui.engine;

import jui.ui.TextInputClient;
import jui.ui.TextRange;

final class TextClientAdapter {
    final Engine engine;
    final ElementState owner;
    final TextInputClient client;
    final long born;
    private boolean active = true;
    private boolean imeOff;
    private final int[] range = new int[2];
    private final float[] bounds = new float[6];
    private float caretX = Float.NaN, caretY, caretH;

    TextClientAdapter(Engine engine, ElementState owner) {
        this.engine = engine; this.owner = owner; client = owner.textClient; born = owner.born;
        imeOff = owner.noInputMethod;
    }

    boolean live() {
        return active && !engine.isClosed() && owner.born == born && owner.textClient == client
                && engine.states().get(owner.id) == owner && !owner.disabledLast && engine.focused(owner.id);
    }

    int length() {
        if (!live()) return -1;
        engine.beginCallback();
        try { return Math.max(0, client.length()); } finally { engine.endCallback(); }
    }

    private int clamp(int index, int length) { return Math.max(0, Math.min(index, length)); }

    boolean selected(int[] out) {
        int length = length();
        if (length < 0) return false;
        engine.beginCallback();
        try { client.selection(range); } finally { engine.endCallback(); }
        int anchor = clamp(range[0], length), caret = clamp(range[1], length);
        out[0] = Math.min(anchor, caret); out[1] = Math.abs(caret - anchor);
        return true;
    }

    boolean marked(int[] out) {
        int length = length();
        if (length < 0) return false;
        boolean marked;
        engine.beginCallback();
        try { marked = client.markedRange(range); } finally { engine.endCallback(); }
        if (!marked) return false;
        out[0] = clamp(range[0], length); out[1] = Math.max(out[0], clamp(range[1], length)) - out[0];
        return true;
    }

    boolean composing() { return marked(range); }

    String text(int start, int end, int[] actual) {
        int length = length();
        if (length < 0) return null;
        start = clamp(start, length); end = Math.max(start, clamp(end, length));
        engine.beginCallback();
        try {
            String text = client.text(start, end, actual);
            if (text != null && (actual[0] < 0 || actual[1] < actual[0] || actual[1] > length
                    || text.length() != actual[1] - actual[0])) throw new IllegalStateException("invalid text actual range");
            return text;
        } finally { engine.endCallback(); }
    }

    private TextRange replacement(int start, int count) {
        if (start < 0) return null;
        int length = length();
        int first = clamp(start, length);
        return new TextRange(first, (int) Math.min(length, (long) first + Math.max(0, count)));
    }

    void replace(String text, int start, int count) {
        if (!live()) return;
        TextRange replacement = replacement(start, count);
        engine.beginCallback();
        try { client.replaceText(replacement, text == null ? "" : text); }
        finally { engine.endCallback(); changed(); }
    }

    void mark(String text, int selectedStart, int selectedLength, int start, int count) {
        if (!live() || owner.noInputMethod) return;
        text = text == null ? "" : text;
        TextRange replacement = replacement(start, count);
        int first = clamp(selectedStart, text.length());
        TextRange selected = new TextRange(first, (int) Math.min(text.length(), (long) first + Math.max(0, selectedLength)));
        engine.beginCallback();
        try { client.setMarkedText(replacement, text, selected); }
        finally { engine.endCallback(); changed(); }
    }

    void unmark() {
        if (!live()) return;
        engine.beginCallback();
        try { client.unmarkText(); } finally { engine.endCallback(); changed(); }
    }

    private void changed() { engine.invalidate(); refresh(); }

    boolean rect(int start, int end, float[] out) {
        int length = length();
        if (length < 0) return false;
        start = clamp(start, length); end = Math.max(start, clamp(end, length));
        engine.beginCallback();
        boolean available;
        try { available = client.boundsFor(start, end, bounds); } finally { engine.endCallback(); }
        if (!available) return false;
        if (!Float.isFinite(bounds[0]) || !Float.isFinite(bounds[1]) || !Float.isFinite(bounds[2])
                || !Float.isFinite(bounds[3]) || bounds[2] < 0 || bounds[3] < 0 || bounds[4] < 0
                || bounds[5] < bounds[4] || bounds[5] > length) throw new IllegalStateException("invalid text bounds");
        float actualStart = bounds[4], actualEnd = bounds[5];
        out[0] = owner.x + bounds[0]; out[1] = owner.y + bounds[1]; out[2] = bounds[2]; out[3] = bounds[3];
        if (out.length > 4) out[4] = actualEnd;
        if (out.length > 5) out[5] = actualStart;
        return true;
    }

    boolean caret(float[] out) {
        int length = length();
        if (length < 0) return false;
        engine.beginCallback();
        try { client.selection(range); } finally { engine.endCallback(); }
        int caret = clamp(range[1], length);
        if (rect(caret, caret, out)) return true;
        out[0] = owner.x; out[1] = owner.y; out[2] = 1; out[3] = Math.max(1, owner.h);
        if (out.length > 4) out[4] = caret;
        if (out.length > 5) out[5] = caret;
        return true;
    }

    int index(float x, float y) {
        int length = length();
        if (length < 0) return -1;
        engine.beginCallback();
        try { int index = client.indexAt(x - owner.x, y - owner.y); return index < 0 ? -1 : clamp(index, length); }
        finally { engine.endCallback(); }
    }

    void refresh() {
        if (!live()) return;
        if (imeOff != owner.noInputMethod) {
            imeOff = owner.noInputMethod;
            if (imeOff && composing()) {
                engine.beginCallback();
                try { client.unmarkText(); }
                finally { engine.endCallback(); engine.host().discardMarkedText(); engine.invalidate(); }
            }
            engine.host().textInputChanged();
        }
        if (caret(bounds) && (caretX != bounds[0] || caretY != bounds[1] || caretH != bounds[3])) {
            caretX = bounds[0]; caretY = bounds[1]; caretH = bounds[3]; engine.host().textInputChanged();
        }
    }

    void release() {
        if (!active) return;
        active = false;
        boolean marked = false;
        engine.beginCallback();
        try { marked = client.markedRange(range); if (marked) client.unmarkText(); }
        finally {
            engine.endCallback();
            if (marked) engine.host().discardMarkedText();
            engine.host().textInputChanged(); engine.invalidate();
        }
    }
}
