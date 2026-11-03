package jui.platform;

import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * What the engine's window asks of the platform that shows it: frame,
 * timer, cursor, menu and appearance controls, in view points. MacWindow
 * implements it on macOS, WinWindow on Windows.
 */
public interface PlatformWindow {
    /** Paints the boxes of a frame of {@code width} by {@code height} points. */
    interface Painter {
        void paint(jui.render.Scene scene, double width, double height);
    }

    /**
     * What the window's view receives, as MyGO's surface sends it: points
     * from the view's top left, buttons 0 (primary) and 1 (secondary),
     * modifiers as {@link jui.ui.Mod} bits. On the main thread.
     */
    interface Input {
        void pointerMove(double x, double y, int mods);

        void pointerDown(double x, double y, int button, int clicks, int mods);

        void pointerUp(double x, double y, int button, int mods);

        void pointerLeave();

        void keyDown(jui.ui.Key key, int mods, boolean repeat);
        default boolean dispatchKeyDown(jui.ui.Key key, int mods, boolean repeat) { keyDown(key, mods, repeat); return false; }
        default void keyUp(jui.ui.Key key, int mods) {}

        void modifiersChanged(int mods);

        void windowFocusChanged(boolean focused);

        void appearanceChanged(boolean dark);

        default void windowClosed() {}

        /** The window's engine, for the app's menu bar to report a choice in; null for a view without one. */
        default jui.ui.engine.Engine engine() {
            return null;
        }

        void scroll(double x, double y, double dx, double dy, boolean precise, int mods);

        // Text input, for the platform's text input method: ranges count UTF-16
        // units of the shown text (the committed text with the marked text in
        // it); -1 is no range. Without a text input that takes text they do
        // nothing.

        /** Whether a text input takes text now: keys then go through the input method. */
        default boolean textActive() {
            return false;
        }

        /** Whether keys go through the input method; with it off, a key types its characters (as MyGO's NoInputMethod). */
        default boolean textInputMethod() {
            return true;
        }

        default boolean textComposing() {
            return false;
        }

        default void textInsert(String s, int replStart, int replLength) {}

        default void textMark(String s, int selStart, int selLength, int replStart, int replLength) {}

        default void textUnmark() {}

        default boolean textMarkedRange(int[] out) {
            return false;
        }

        default boolean textSelectedRange(int[] out) {
            return false;
        }

        default int textLength() {
            return -1;
        }

        default String textIn(int start, int end) {
            return null;
        }

        default String textIn(int start, int end, int[] actual) {
            actual[0] = start;
            actual[1] = end;
            return textIn(start, end);
        }

        /** The rect of [start, end) in view points: x, y, width, height, and the end of the range it covers in out[4]. */
        default boolean textRect(int start, int end, float[] out) {
            return false;
        }

        /** The index nearest a point in view points, or -1. */
        default int textIndexAt(double x, double y) {
            return -1;
        }

        /** A command of the Edit menu. */
        default void editCommand(jui.ui.EditCommand c) {}
    }

    /** Asks for a frame; any thread may call it. */
    void requestFrame();

    /** Draws a frame after delayNanos, in place of any timer armed before. */
    void armTimer(long delayNanos);

    void cancelTimer();

    /** Closes the window. */
    void close();

    /** The pointer's shape over the view. */
    void setCursor(jui.ui.Cursor c);

    /** The input method forgets its marked text: the app committed or dropped it. */
    void discardMarkedText();

    /** The input method asks where the caret is again. */
    void invalidateCharacterCoordinates();

    /** Shows {@code model} as a menu at (x, y) in view points, reporting the chosen item's id, or 0. */
    void popupMenu(MenuModel model, float x, float y, IntConsumer result);

    void cancelMenu();

    /** The color frames clear to, 0xRRGGBB; asks for a frame. */
    void setBackground(int rgb);

    /** Whether the view's appearance is dark (as MyGO's system.go decides). */
    boolean dark();

    /** The platform's number for the window, as its own tools capture it by. */
    long windowNumber();

    /** The display's refresh period in nanoseconds, as well as it is known. */
    long refreshNanos();

    /** Wall-clock microseconds at which the first frame was committed. */
    long firstCommitMicros();

    /** Calls {@code then}, on the main thread, with the wall-clock microseconds the first GPU frame completed (0 on failure). */
    void onFirstFrame(LongConsumer then);
}
