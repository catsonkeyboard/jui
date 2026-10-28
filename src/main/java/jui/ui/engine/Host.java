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

    default void popupMenu(jui.platform.MenuModel model, float x, float y, java.util.function.IntConsumer result) {
        result.accept(0);
    }

    default void cancelMenu() {}

    /** A host in memory, for Tester and windowless engines: a clipboard string, the cursor last set, counts. */
    final class Memory implements Host {
        public String clipboard;
        public Cursor cursor = Cursor.DEFAULT;
        /** How many times discardMarkedText and textInputChanged were called: for tests. */
        public int discards, changes;
        public jui.platform.MenuModel menuModel;
        public java.util.function.IntConsumer menuResult;
        public float menuX, menuY;
        public int menusOpened;

        @Override
        public void popupMenu(jui.platform.MenuModel model, float x, float y, java.util.function.IntConsumer result) {
            cancelMenu();
            menuModel = model;
            menuX = x;
            menuY = y;
            menuResult = result;
            menusOpened++;
        }

        public void chooseMenu(int id) {
            jui.platform.MenuModel.Item item = menuModel == null ? null : menuModel.find(id);
            if (item == null || !item.enabled() || item.kind() != jui.platform.MenuModel.Kind.ITEM) {
                throw new IllegalStateException("menu item cannot be chosen");
            }
            finishMenu(id);
        }

        @Override
        public void cancelMenu() { finishMenu(0); }

        private void finishMenu(int id) {
            java.util.function.IntConsumer result = menuResult;
            menuResult = null;
            menuModel = null;
            if (result != null) result.accept(id);
        }

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
