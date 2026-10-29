package jui.examples;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.function.Function;
import jui.text.TextBounds;
import jui.text.TextLayout;
import jui.ui.*;

public final class EditorSurface implements TextInputClient {
    private static final Color TEXT = Color.rgb(0x233047), SELECTION = Color.rgba(0x5599FF, .3f), ACCENT = Color.rgb(0x3366CC);
    private static final Duration BLINK = Duration.ofMillis(530);
    private record Snapshot(String text, int anchor, int caret) {}
    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final Function<String, Spans> styler;
    private Context context;
    private String value;
    private Spans spans;
    private TextLayout layout;
    private boolean dirty = true, reveal = true, dragging, upstream;
    private int anchor, caret, markStart = -1, markEnd;
    private Snapshot compositionBefore;
    private int[] stops;
    private float width = 300, height = 180, scrollY, goalX = Float.NaN;
    private long blinkStart;
    private final float[] rectangle = new float[4];
    private final int[] hit = new int[2];
    public boolean focused, takeJ;
    public final InputHandler handler = this::input;
    public final Painter.Draw drawing = this::draw;

    public EditorSurface(String value) { this(value, null); }
    public EditorSurface(String value, Function<String, Spans> styler) {
        this.styler = styler; this.value = value; anchor = caret = value.length();
        stops = TextBounds.graphemes(value);
    }
    public String value() { return value; }
    public TextLayout layout() { ensure(); return layout; }
    public float scrollY() { return scrollY; }
    public void attach(Context context) { this.context = context; }
    public void reset(String value) {
        this.value = value; anchor = caret = value.length(); markStart = -1; compositionBefore = null;
        undo.clear(); redo.clear(); changed();
    }

    private void ensure() {
        if (context == null) return;
        if (dirty || layout == null) {
            if (styler != null && (spans == null || !spans.text().equals(value))) spans = styler.apply(value);
            layout = spans == null ? context.layoutText(value, 14, 400, Math.max(1, width - 20))
                    : context.layoutText(spans, 14, 400, Math.max(1, width - 20));
            dirty = false;
        }
        if (reveal) {
            layout.caretRect(caret, upstream, rectangle);
            if (rectangle[1] < scrollY) scrollY = rectangle[1];
            if (rectangle[1] + rectangle[3] > scrollY + height - 20) scrollY = rectangle[1] + rectangle[3] - height + 20;
            reveal = false;
        }
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, layout.height - height + 20)));
    }

    private void changed() {
        dirty = reveal = true; upstream = false; goalX = Float.NaN;
        stops = TextBounds.graphemes(value);
        if (context != null) blinkStart = context.now();
        ensure();
    }

    private Snapshot snapshot() { return new Snapshot(value, anchor, caret); }
    private void remember(Snapshot before) {
        if (before.text.equals(value)) return;
        undo.addLast(before); if (undo.size() > 100) undo.removeFirst(); redo.clear();
    }
    private void replace(int start, int end, String text) {
        value = value.substring(0, start) + text + value.substring(end);
        anchor = caret = start + text.length(); changed();
    }
    private int floor(int index) { return TextBounds.floor(stops, Math.max(0, Math.min(index, value.length()))); }
    public int length() { return value.length(); }
    public String text(int start, int end, int[] actual) {
        actual[0] = floor(start); actual[1] = floor(Math.max(start, end));
        return value.substring(actual[0], actual[1]);
    }
    public void selection(int[] out) { out[0] = anchor; out[1] = caret; }
    public boolean markedRange(int[] out) { out[0] = markStart; out[1] = markEnd; return markStart >= 0; }
    public void replaceText(TextRange replacement, String text) {
        Snapshot before = compositionBefore == null ? snapshot() : compositionBefore;
        int start = replacement != null ? floor(replacement.start()) : markStart >= 0 ? markStart : Math.min(anchor, caret);
        int end = replacement != null ? floor(replacement.end()) : markStart >= 0 ? markEnd : Math.max(anchor, caret);
        markStart = -1; compositionBefore = null;
        replace(start, end, text.replace("\r\n", "\n").replace('\r', '\n'));
        remember(before);
    }
    public void setMarkedText(TextRange replacement, String text, TextRange selected) {
        if (compositionBefore == null) compositionBefore = snapshot();
        int start = replacement != null ? floor(replacement.start()) : markStart >= 0 ? markStart : Math.min(anchor, caret);
        int end = replacement != null ? floor(replacement.end()) : markStart >= 0 ? markEnd : Math.max(anchor, caret);
        replace(start, end, text);
        markStart = text.isEmpty() ? -1 : start; markEnd = start + text.length();
        anchor = floor(start + Math.min(text.length(), selected.start()));
        caret = floor(start + Math.min(text.length(), selected.end())); reveal = true; ensure();
        if (text.isEmpty()) compositionBefore = null;
    }
    public void unmarkText() {
        markStart = -1;
        if (compositionBefore != null) { remember(compositionBefore); compositionBefore = null; }
    }
    public void resized(float width, float height) {
        dirty |= this.width != width; this.width = width; this.height = height; ensure();
    }
    public boolean boundsFor(int start, int end, float[] out) {
        ensure(); if (layout == null) return false;
        int first = floor(start), last = end <= start ? first : Math.min(TextBounds.after(stops, first), floor(end));
        int line = layout.lineAt(first, first == caret && upstream);
        layout.caretRect(first, first == caret && upstream, rectangle);
        float x = rectangle[0], y = rectangle[1], wide = 1;
        if (last > first && layout.selectionRect(first, last, line, rectangle)) wide = Math.max(1, rectangle[2]);
        out[0] = 10 + x; out[1] = 10 + y - scrollY; out[2] = wide; out[3] = layout.lineHeight(line);
        out[4] = first; out[5] = last;
        return out[1] + out[3] >= 1 && out[1] <= height - 1;
    }
    public int indexAt(float x, float y) {
        ensure(); if (layout == null) return -1;
        layout.hitTest(x - 10, y - 10 + scrollY, hit); return hit[0];
    }
    private void move(int next, boolean shift, boolean affinity) {
        caret = floor(next); upstream = affinity;
        if (!shift) anchor = caret;
        reveal = true; blinkStart = context.now(); ensure();
    }
    private void vertical(int delta, boolean shift) {
        ensure(); if (Float.isNaN(goalX)) goalX = layout.caretX(caret, upstream);
        int line = layout.lineAt(caret, upstream), next = Math.max(0, Math.min(layout.lines - 1, line + delta));
        int index = next == line ? delta < 0 ? 0 : value.length() : layout.indexInLine(next, goalX);
        move(index, shift, layout.softEnd(next) && index == layout.lineEnd(next));
    }
    private void history(ArrayDeque<Snapshot> from, ArrayDeque<Snapshot> to) {
        if (from.isEmpty()) return;
        to.addLast(snapshot()); Snapshot entry = from.removeLast();
        value = entry.text; anchor = entry.anchor; caret = entry.caret; markStart = -1; compositionBefore = null; changed();
    }
    public boolean command(EditCommand command) {
        ensure(); unmarkText();
        int start = Math.min(anchor, caret), end = Math.max(anchor, caret);
        switch (command) {
            case SELECT_ALL -> { anchor = 0; caret = value.length(); reveal = true; }
            case COPY -> { if (start != end) context.copyText(value.substring(start, end)); }
            case CUT -> { if (start != end) { context.copyText(value.substring(start, end)); replaceText(null, ""); } }
            case DELETE -> { if (start != end) replaceText(null, ""); }
            case PASTE, PASTE_AND_MATCH_STYLE -> { String pasted = context.clipboardText(); if (pasted != null && !pasted.isEmpty()) replaceText(null, pasted); }
            case UNDO -> history(undo, redo);
            case REDO -> history(redo, undo);
        }
        ensure(); return true;
    }
    private boolean input(InputEvent event) {
        ensure();
        switch (event.kind()) {
            case COMMAND -> { return command(event.command()); }
            case SCROLL -> {
                float previous = scrollY; scrollY += event.dy(); reveal = false; ensure(); return scrollY != previous;
            }
            case POINTER_DOWN -> {
                if (event.button() != 0) return false;
                unmarkText(); indexAt(event.x(), event.y());
                move(hit[0], (event.mods() & Mod.SHIFT) != 0, hit[1] != 0); dragging = true; goalX = Float.NaN;
                if (event.clicks() == 2) { int[] word = TextBounds.wordAt(value, caret); anchor = word[0]; caret = word[1]; }
                return true;
            }
            case POINTER_MOVE -> {
                if (!dragging) return false;
                indexAt(event.x(), event.y()); move(hit[0], true, hit[1] != 0); return true;
            }
            case POINTER_UP, POINTER_CANCEL -> { boolean wasDragging = dragging; dragging = false; return wasDragging; }
            case KEY_DOWN -> { return key(event.key(), event.mods()); }
            default -> { return false; }
        }
    }
    private boolean key(Key key, int mods) {
        if (takeJ && key == Key.J && (mods & (Mod.CMD | Mod.CTRL)) == 0) return true;
        boolean shift = (mods & Mod.SHIFT) != 0, cmd = (mods & Mod.CMD) != 0;
        if (cmd) {
            EditCommand command = switch (key) {
                case A -> EditCommand.SELECT_ALL; case C -> EditCommand.COPY; case X -> EditCommand.CUT;
                case V -> EditCommand.PASTE; case Z -> shift ? EditCommand.REDO : EditCommand.UNDO; default -> null;
            };
            if (command != null) return command(command);
        }
        if (key == Key.UP || key == Key.DOWN) {
            if (cmd) move(key == Key.UP ? 0 : value.length(), shift, false);
            else vertical(key == Key.UP ? -1 : 1, shift);
            return true;
        }
        goalX = Float.NaN;
        switch (key) {
            case LEFT, RIGHT -> {
                boolean left = key == Key.LEFT;
                int next = !shift && anchor != caret ? left ? Math.min(anchor, caret) : Math.max(anchor, caret)
                        : cmd ? left ? layout.lineStart(layout.lineAt(caret, upstream)) : layout.lineEnd(layout.lineAt(caret, upstream))
                        : (mods & Mod.ALT) != 0 ? left ? TextBounds.prevWord(value, caret) : TextBounds.nextWord(value, caret)
                        : left ? TextBounds.before(stops, caret) : TextBounds.after(stops, caret);
                move(next, shift, cmd && !left); return true;
            }
            case HOME, END -> {
                int line = layout.lineAt(caret, upstream);
                move(cmd || (mods & Mod.CTRL) != 0 ? key == Key.HOME ? 0 : value.length()
                        : key == Key.HOME ? layout.lineStart(line) : layout.lineEnd(line), shift, key == Key.END);
                return true;
            }
            case BACKSPACE, DELETE -> {
                if (anchor == caret) anchor = key == Key.BACKSPACE ? TextBounds.before(stops, caret) : TextBounds.after(stops, caret);
                replaceText(null, ""); return true;
            }
            case ENTER -> { replaceText(null, "\n"); return true; }
            default -> { return false; }
        }
    }
    private void draw(Painter painter, Rect bounds) {
        ensure(); if (layout == null) return;
        painter.clip(1, 1, Math.max(0, bounds.width() - 2), Math.max(0, bounds.height() - 2));
        int first = Math.max(0, layout.lineForY(scrollY - 10) - 1), end = Math.min(layout.lines, layout.lineForY(scrollY + height) + 2);
        if (focused) for (int line = first; line < end; line++) {
            if (layout.selectionRect(anchor, caret, line, rectangle)) painter.box(10 + rectangle[0], 10 + rectangle[1] - scrollY, rectangle[2], rectangle[3], SELECTION);
        }
        if (spans == null) painter.text(layout, 10, 10 - scrollY, TEXT);
        else painter.richText(layout, spans, 10, 10 - scrollY, TEXT);
        if (markStart >= 0) for (int line = first; line < end; line++) {
            if (layout.selectionRect(markStart, markEnd, line, rectangle)) painter.box(10 + rectangle[0], 10 + rectangle[1] + rectangle[3] - scrollY - 2, rectangle[2], 1, ACCENT);
        }
        long phase = Math.max(0, context.now() - blinkStart);
        if (focused && (phase >= 30_000_000_000L || phase / 530_000_000L % 2 == 0)) {
            layout.caretRect(caret, upstream, rectangle);
            painter.box(10 + rectangle[0], 10 + rectangle[1] - scrollY, 1 / painter.scale(), rectangle[3], ACCENT);
        }
        if (focused && phase < 30_000_000_000L) context.after(BLINK);
        painter.popClip();
    }
}
