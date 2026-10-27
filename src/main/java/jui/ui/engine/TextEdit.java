package jui.ui.engine;

import java.util.ArrayDeque;

import jui.text.TextBounds;
import jui.text.TextLayout;
import jui.ui.EditCommand;
import jui.ui.Key;
import jui.ui.Mod;

/**
 * A single-line text input's editing, as MyGO's editor does it for a
 * TextInput: the committed text, the caret and the anchor, a composition
 * (an input method's marked text, shown at the caret but not committed),
 * and undo. Indices are UTF-16; the caret and the anchor stay on grapheme
 * cluster boundaries. Times are nanoseconds, given by the caller.
 *
 * <p>Ranges that an input method gives and asks for count in the shown
 * text, the committed text with the composition at the caret.
 */
public final class TextEdit {
    /** Undo keeps this many steps; typing within this long of the last edit joins its step (as MyGO). */
    private static final int MAX_UNDO = 200;
    private static final long COALESCE = 1_000_000_000L;

    private String text;
    private int anchor, caret;
    /** The text's grapheme cluster boundaries. */
    private int[] stops;
    /** The composition, "" for none, and the range the input method selected in it. */
    private String compose = "";
    private int composeSel, composeSelLength;
    /** The shown text while composing, made once a change. */
    private String display;

    /** The user changed the committed text since it was last synced with the TextRef. */
    public boolean dirty;
    /** The TextRef's value as last synced. */
    public String synced;

    // Undo: whole texts and selections before and after each step.
    private static final class Step {
        String beforeText, afterText;
        int beforeAnchor, beforeCaret, afterAnchor, afterCaret;
    }

    private final ArrayDeque<Step> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    /** Whether typing may join the last step, and when the last edit was. */
    private boolean coalesce;
    private long lastEdit;
    /** Inside one composition: its deleted selection, marked text and commit are one step. */
    private boolean compositionActive;

    // The last press: its clicks, and the word a double click selected.
    private int pressClicks;
    private int wordStart, wordEnd;

    // Geometry, written at commit for presses and the input method: the layout of the shown text,
    // where its origin and line are, the clip (the padding box), and the horizontal scroll.
    public TextLayout layout;
    public float originX, lineTop, lineH;
    public float clipX0, clipY0, clipX1, clipY1;
    public float scrollX;

    /** An edit of text, on one line, the caret and the anchor at its end. */
    public TextEdit(String text) {
        this.text = singleLine(text);
        this.stops = TextBounds.graphemes(this.text);
        this.anchor = this.caret = this.text.length();
        this.synced = text == null ? "" : text;
    }

    public String text() {
        return text;
    }

    public int caret() {
        return caret;
    }

    public int anchor() {
        return anchor;
    }

    public int selStart() {
        return Math.min(anchor, caret);
    }

    public int selEnd() {
        return Math.max(anchor, caret);
    }

    /** Selects from anchor to caret, both clamped and put on grapheme boundaries; a composition is committed first. */
    public void select(int anchor, int caret) {
        commitComposition();
        this.anchor = snap(anchor);
        this.caret = snap(caret);
        coalesce = false;
    }

    /** Selects all the text; a composition is committed first. */
    public void selectAll() {
        commitComposition();
        anchor = 0;
        caret = text.length();
        coalesce = false;
    }

    public String compose() {
        return compose;
    }

    public boolean composing() {
        return !compose.isEmpty();
    }

    /** The text shown: the committed text, with the composition at the caret; the text itself when not composing. */
    public String display() {
        if (compose.isEmpty()) {
            return text;
        }
        if (display == null) {
            display = text.substring(0, caret) + compose + text.substring(caret);
        }
        return display;
    }

    /** Where the caret shows in the shown text: in a composition, at the end of its selected range. */
    public int displayCaret() {
        return compose.isEmpty() ? caret : caret + composeSel + composeSelLength;
    }

    // Keys (as MyGO's editor.wants and editor.key on macOS).

    /** Whether a single-line input takes the key, rather than leave it to shortcuts. */
    public static boolean wants(Key key, int mods) {
        int m = mods & ~Mod.SHIFT;
        switch (key) {
            case BACKSPACE, DELETE, HOME, END, LEFT, RIGHT:
                return true;
            case UP, DOWN, PAGE_UP, PAGE_DOWN:
                return m != 0;
            case ENTER:
                return m == 0;
            case TAB, ESCAPE, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12:
                return false;
            default:
                break;
        }
        if (m == Mod.CMD) {
            return switch (key) {
                case A, C, X, V, Z, Y -> true;
                default -> false;
            };
        }
        if (m == Mod.CTRL) {
            return switch (key) {
                case B, F, P, N, H, D, A, E, K -> true;
                default -> false;
            };
        }
        // Printable keys type text, which comes as text input.
        return m == 0 || m == Mod.ALT;
    }

    /** The Edit command of a key, or null. */
    public static EditCommand commandOf(Key key, int mods) {
        if ((mods & ~Mod.SHIFT) != Mod.CMD) {
            return null;
        }
        boolean shift = (mods & Mod.SHIFT) != 0;
        return switch (key) {
            case A -> EditCommand.SELECT_ALL;
            case C -> EditCommand.COPY;
            case X -> EditCommand.CUT;
            case V -> EditCommand.PASTE;
            case Z -> shift ? EditCommand.REDO : EditCommand.UNDO;
            case Y -> EditCommand.REDO;
            default -> null;
        };
    }

    /**
     * Does what a key that {@link #wants} takes and that is no
     * {@link #commandOf command} does; reports whether it submits (Enter).
     */
    public boolean key(Key key, int mods, long now) {
        commitComposition();
        compositionActive = false;
        boolean shift = (mods & Mod.SHIFT) != 0;
        int m = mods & ~Mod.SHIFT;
        int n = text.length();
        if (m == Mod.CTRL) {
            switch (key) {
                case B -> { key = Key.LEFT; m = 0; }
                case F -> { key = Key.RIGHT; m = 0; }
                case P -> { key = Key.UP; m = 0; }
                case N -> { key = Key.DOWN; m = 0; }
                case H -> { key = Key.BACKSPACE; m = 0; }
                case D -> { key = Key.DELETE; m = 0; }
                case A -> { move(0, shift); return false; }
                case E -> { move(n, shift); return false; }
                case K -> {
                    anchor = caret;
                    deleteRange(caret, n, false, now);
                    return false;
                }
                default -> { return false; }
            }
        }
        boolean word = m == Mod.ALT;
        int a = selStart(), z = selEnd();
        switch (key) {
            case LEFT, RIGHT -> {
                boolean left = key == Key.LEFT;
                if (m == Mod.CMD) {
                    move(left ? 0 : n, shift);
                } else if (word) {
                    move(left ? TextBounds.prevWord(text, caret) : TextBounds.nextWord(text, caret), shift);
                } else if (a != z && !shift) {
                    move(left ? a : z, false);
                } else {
                    move(left ? TextBounds.before(stops, caret) : TextBounds.after(stops, caret), shift);
                }
            }
            case UP, PAGE_UP, HOME -> move(0, shift);
            case DOWN, PAGE_DOWN, END -> move(n, shift);
            case BACKSPACE -> {
                if (a != z) {
                    deleteRange(a, z, false, now);
                } else if (m == Mod.CMD) {
                    deleteRange(0, caret, false, now);
                } else if (word) {
                    deleteRange(TextBounds.prevWord(text, caret), caret, false, now);
                } else if (caret > 0) {
                    deleteRange(TextBounds.before(stops, caret), caret, true, now);
                }
            }
            case DELETE -> {
                if (a != z) {
                    deleteRange(a, z, false, now);
                } else if (word) {
                    deleteRange(caret, TextBounds.nextWord(text, caret), false, now);
                } else if (caret < n) {
                    deleteRange(caret, TextBounds.after(stops, caret), false, now);
                }
            }
            case ENTER -> {
                return true;
            }
            default -> { } // printable keys: their text comes as text input
        }
        return false;
    }

    // Text and composition.

    /** Text typed or committed: it replaces the composition if there is one, else the selection. */
    public void insert(String s, long now) {
        s = singleLine(s);
        if (!compose.isEmpty()) {
            // The input method commits: in the composition's undo step.
            if (!compositionActive) {
                record(false, now);
            }
            compose = "";
            display = null;
            replaceRange(caret, caret, s);
            compositionActive = false;
            return;
        }
        compositionActive = false;
        if (s.isEmpty() && anchor == caret) {
            return;
        }
        boolean typing = anchor == caret && s.codePointCount(0, s.length()) == 1;
        record(typing, now);
        replaceRange(selStart(), selEnd(), s);
    }

    /** Text committed over [start, end) of the shown text: the composition is committed first. */
    public void replace(int start, int end, String s, long now) {
        commitComposition();
        s = singleLine(s);
        int a = snap(Math.min(start, end)), z = snapUp(Math.max(start, end));
        if (!compositionActive) {
            record(false, now);
        }
        compositionActive = false;
        replaceRange(a, z, s);
    }

    /**
     * The input method's marked text, with selected range selStart and
     * selLength in it; replStart below 0 for no replacement range (in the
     * shown text). As MyGO's SetMarkedText: starting a composition, or
     * given a replacement range, deletes the selection (or that range); an
     * empty text cancels the composition.
     */
    public void setMarked(String s, int selStart, int selLength, int replStart, int replLength, long now) {
        s = s == null ? "" : singleLine(s);
        if (compose.isEmpty() || replStart >= 0) {
            int a, z;
            if (replStart >= 0) {
                a = committedIndex(replStart);
                z = committedIndex(replStart + Math.max(0, replLength));
            } else {
                a = selStart();
                z = selEnd();
            }
            // A new composition is a step of its own, even after one that unmarkText committed
            // (as MyGO); a replacement inside a composition joins its step.
            if (!(compositionActive && !compose.isEmpty())) {
                record(false, now);
                compositionActive = true;
            }
            compose = "";
            display = null;
            a = snap(Math.min(a, z));
            z = snapUp(Math.max(a, z));
            if (a != z) {
                replaceRange(a, z, "");
            } else {
                anchor = caret = a;
            }
        }
        compose = s;
        display = null;
        if (s.isEmpty()) {
            composeSel = composeSelLength = 0;
            closeStep();
            return;
        }
        int from = Math.max(0, Math.min(selStart, s.length()));
        int to = Math.max(from, Math.min(selStart + Math.max(0, selLength), s.length()));
        if (from > 0 && from < s.length() && Character.isLowSurrogate(s.charAt(from))) {
            from--; // never between the halves of a surrogate pair
        }
        if (to > 0 && to < s.length() && Character.isLowSurrogate(s.charAt(to))) {
            to++;
        }
        composeSel = from;
        composeSelLength = to - from;
        closeStep();
    }

    /** Commits the composition as it stands: it becomes text, the caret after it. */
    public void unmark(long now) {
        commitComposition();
    }

    private void commitComposition() {
        if (compose.isEmpty()) {
            return;
        }
        String c = compose;
        compose = "";
        display = null;
        replaceRange(caret, caret, c);
    }

    /** The shown text's length. */
    public int length() {
        return text.length() + compose.length();
    }

    /** The selection in the shown text, as {start, length}: in a composition, its selected range. */
    public void selectedRange(int[] out) {
        if (!compose.isEmpty()) {
            out[0] = caret + composeSel;
            out[1] = composeSelLength;
        } else {
            out[0] = selStart();
            out[1] = selEnd() - selStart();
        }
    }

    /** The composition in the shown text, as {start, length}; false when not composing. */
    public boolean markedRange(int[] out) {
        if (compose.isEmpty()) {
            return false;
        }
        out[0] = caret;
        out[1] = compose.length();
        return true;
    }

    /** A part of the shown text, clamped to it. */
    public String substring(int start, int end) {
        String d = display();
        int a = Math.max(0, Math.min(start, d.length())), z = Math.max(a, Math.min(end, d.length()));
        return d.substring(a, z);
    }

    // Commands.

    /**
     * Runs a command of the Edit menu, the composition committed first;
     * COPY and CUT return the text for the clipboard (null without a
     * selection), the others null. PASTE takes the clipboard's text.
     */
    public String command(EditCommand c, String clipboard, long now) {
        commitComposition();
        compositionActive = false;
        int a = selStart(), z = selEnd();
        switch (c) {
            case COPY -> {
                return a == z ? null : text.substring(a, z);
            }
            case CUT -> {
                if (a == z) {
                    return null;
                }
                String cut = text.substring(a, z);
                deleteRange(a, z, false, now);
                return cut;
            }
            case PASTE -> {
                if (clipboard != null && !clipboard.isEmpty()) {
                    insert(clipboard, now);
                }
            }
            case SELECT_ALL -> selectAll();
            case UNDO -> takeBack(undo, redo, true);
            case REDO -> takeBack(redo, undo, false);
        }
        return null;
    }

    // The pointer.

    /** A press at index: one click places the caret (Shift extends), two select a word, three all. */
    public void press(int index, int clicks, boolean shift) {
        commitComposition();
        compositionActive = false;
        coalesce = false;
        int i = snap(index);
        pressClicks = Math.min(Math.max(clicks, 1), 3);
        if (pressClicks == 1) {
            caret = i;
            if (!shift) {
                anchor = i;
            }
        } else if (pressClicks == 2) {
            int[] w = TextBounds.wordAt(text, i);
            wordStart = w[0];
            wordEnd = w[1];
            anchor = wordStart;
            caret = wordEnd;
        } else {
            selectAll();
        }
    }

    /** The pointer held after a press moved to index: by characters, or by whole words after a double click. */
    public void drag(int index) {
        int i = snap(index);
        if (pressClicks == 1) {
            caret = i;
        } else if (pressClicks == 2) {
            int[] w = TextBounds.wordAt(text, i);
            if (i < wordStart) {
                anchor = wordEnd;
                caret = w[0];
            } else if (i >= wordEnd) {
                anchor = wordStart;
                caret = w[1];
            } else {
                anchor = wordStart;
                caret = wordEnd;
            }
        }
    }

    // The app's change.

    /**
     * The app changed the value: the caret and the anchor are clamped, the
     * composition goes, the last undo step ends at the new text and redo
     * is cleared (as MyGO's setText). synced keeps the value as the app
     * gave it, line breaks and all, so it is not taken for a new change.
     */
    public void setText(String s) {
        text = singleLine(s); // a line break the app gives shows as a space: the input is one line
        synced = s == null ? "" : s;
        stops = TextBounds.graphemes(text);
        compose = "";
        display = null;
        compositionActive = false;
        coalesce = false;
        anchor = snap(anchor);
        caret = snap(caret);
        Step last = undo.peekLast();
        if (last != null) {
            last.afterText = text;
            last.afterAnchor = anchor;
            last.afterCaret = caret;
        }
        redo.clear();
        dirty = false;
    }

    // Inside.

    private void move(int to, boolean extend) {
        caret = snap(to);
        if (!extend) {
            anchor = caret;
        }
        coalesce = false;
    }

    private void deleteRange(int a, int z, boolean typing, long now) {
        if (a == z) {
            return;
        }
        record(typing, now);
        replaceRange(a, z, "");
    }

    /** Replaces [a, z) of the committed text with s, the caret after it, and ends the open undo step there. */
    private void replaceRange(int a, int z, String s) {
        String old = text;
        text = text.substring(0, a) + s + text.substring(z);
        stops = TextBounds.graphemes(text);
        display = null;
        anchor = caret = a + s.length();
        if (!text.equals(old)) {
            dirty = true;
        }
        closeStep();
    }

    /** Opens an undo step for an edit, unless it is typing that joins the last one. */
    private void record(boolean typing, long now) {
        if (typing && coalesce && now - lastEdit < COALESCE && !undo.isEmpty()) {
            lastEdit = now;
            return;
        }
        Step st = new Step();
        st.beforeText = st.afterText = text;
        st.beforeAnchor = st.afterAnchor = anchor;
        st.beforeCaret = st.afterCaret = caret;
        undo.addLast(st);
        if (undo.size() > MAX_UNDO) {
            undo.removeFirst();
        }
        redo.clear();
        coalesce = typing;
        lastEdit = now;
    }

    /** The open step now ends at the current text and selection. */
    private void closeStep() {
        Step st = undo.peekLast();
        if (st != null) {
            st.afterText = text;
            st.afterAnchor = anchor;
            st.afterCaret = caret;
        }
    }

    private void takeBack(ArrayDeque<Step> from, ArrayDeque<Step> to, boolean back) {
        coalesce = false;
        Step st = from.pollLast();
        if (st == null) {
            return;
        }
        to.addLast(st);
        String t = back ? st.beforeText : st.afterText;
        if (!t.equals(text)) {
            dirty = true;
        }
        text = t;
        stops = TextBounds.graphemes(text);
        display = null;
        anchor = back ? st.beforeAnchor : st.afterAnchor;
        caret = back ? st.beforeCaret : st.afterCaret;
    }

    /** An index of the shown text in the committed text: inside the composition, the caret. */
    private int committedIndex(int i) {
        if (compose.isEmpty() || i <= caret) {
            return i;
        }
        return i >= caret + compose.length() ? i - compose.length() : caret;
    }

    /** i clamped to the text, down to a grapheme boundary. */
    private int snap(int i) {
        return TextBounds.floor(stops, Math.max(0, Math.min(i, text.length())));
    }

    /** i clamped to the text, up to a grapheme boundary. */
    private int snapUp(int i) {
        int c = Math.max(0, Math.min(i, text.length()));
        int f = TextBounds.floor(stops, c);
        return f == c ? c : TextBounds.after(stops, c);
    }

    /** A single line: line breaks become spaces (as MyGO's insert and replace). */
    private static String singleLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", "\n").replace('\n', ' ').replace('\r', ' ');
    }
}
