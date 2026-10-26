package jui.ui.engine;

import jui.ui.Cursor;

/**
 * What the engine asks of the platform that shows it: the clipboard, the
 * pointer's shape, and the input method. On the main thread.
 */
public interface Host {
    /** The clipboard's text, or null when it holds none. */
    String readClipboard();

    void writeClipboard(String s);

    /** The pointer's shape changed. */
    void setCursor(Cursor c);

    /** The engine committed or dropped a composition itself: the input method forgets its marked text. */
    void discardMarkedText();

    /** The caret moved, or text input began or ended: the input method asks where the caret is again. */
    void textInputChanged();

    /** A host in memory, for Tester and windowless engines: a clipboard string, the cursor last set, counts. */
    final class Memory implements Host {
        public String clipboard;
        public Cursor cursor = Cursor.DEFAULT;
        /** How many times discardMarkedText and textInputChanged were called: for tests. */
        public int discards, changes;

        @Override
        public String readClipboard() {
            return clipboard;
        }

        @Override
        public void writeClipboard(String s) {
            clipboard = s;
        }

        @Override
        public void setCursor(Cursor c) {
            cursor = c;
        }

        @Override
        public void discardMarkedText() {
            discards++;
        }

        @Override
        public void textInputChanged() {
            changes++;
        }
    }
}
