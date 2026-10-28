package jui.text;

import java.text.BreakIterator;
import java.util.Arrays;

/**
 * Where a caret may stop in a text: grapheme cluster boundaries, so that an
 * emoji, a flag or a letter with its accents goes as one; and word
 * boundaries, as MyGO's internal/text/word.go finds them, without a
 * dictionary. Indices are UTF-16. Main thread only.
 */
public final class TextBounds {
    private TextBounds() {}

    /** Extended grapheme clusters (UAX #29); one instance, reused. */
    private static final BreakIterator CHARACTERS = BreakIterator.getCharacterInstance();

    /** The grapheme cluster boundaries of s, 0 and s.length() among them, in order. */
    public static int[] graphemes(String s) {
        int[] stops = new int[s.length() + 1];
        int n = 0;
        CHARACTERS.setText(s);
        for (int i = CHARACTERS.first(); i != BreakIterator.DONE; i = CHARACTERS.next()) {
            stops[n++] = i;
        }
        return n == stops.length ? stops : Arrays.copyOf(stops, n);
    }

    /** The nearest boundary before i; 0 at 0. */
    public static int before(int[] stops, int i) {
        int k = Arrays.binarySearch(stops, i);
        int at = k >= 0 ? k - 1 : -k - 2; // the last stop below i
        return at < 0 ? 0 : stops[at];
    }

    /** The nearest boundary after i; the end at the end. */
    public static int after(int[] stops, int i) {
        int k = Arrays.binarySearch(stops, i);
        int at = k >= 0 ? k + 1 : -k - 1; // the first stop above i
        return at >= stops.length ? stops[stops.length - 1] : stops[at];
    }

    /** The greatest boundary at or before i. */
    public static int floor(int[] stops, int i) {
        int k = Arrays.binarySearch(stops, i);
        int at = k >= 0 ? k : -k - 2;
        return at < 0 ? 0 : stops[at];
    }

    /** Part of a word: a letter, a digit or an underscore. */
    public static boolean isWordChar(int cp) {
        return Character.isLetter(cp) || Character.isDigit(cp) || cp == '_';
    }

    /**
     * A word on its own: a Han ideograph (the iteration mark 々 too), a
     * Hiragana or a Katakana (with the prolonged sound mark ー). Chinese and
     * Japanese have no spaces between words, and only a dictionary finds
     * them; a character at a time is what platforms do without one.
     */
    public static boolean standsAlone(int cp) {
        if (cp < 0x2E80) {
            return false;
        }
        if (cp == 0x30FC || cp == 0xFF70) { // ー and its halfwidth form, of the Common script
            return true;
        }
        if (!isWordChar(cp)) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }

    /** Whether a and b, side by side, are in different words or runs of other characters. */
    public static boolean wordBoundary(int a, int b) {
        return isWordChar(a) != isWordChar(b) || standsAlone(a) || standsAlone(b);
    }

    public static int paragraphStart(String text, int index) {
        int position = Math.max(0, Math.min(index, text.length()));
        while (position > 0 && text.charAt(position - 1) != '\n' && text.charAt(position - 1) != '\r') position--;
        return position;
    }

    public static int paragraphEnd(String text, int index) {
        int position = Math.max(0, Math.min(index, text.length()));
        while (position < text.length() && text.charAt(position) != '\n' && text.charAt(position) != '\r') position++;
        return position;
    }

    public static int breakEnd(String text, int index) {
        int position = index;
        if (position < text.length() && text.charAt(position) == '\r') position++;
        if (position < text.length() && text.charAt(position) == '\n') position++;
        return position;
    }

    /** The end of the word after i, skipping what is between (as MyGO's nextWord). */
    public static int nextWord(String s, int i) {
        boolean word = false;
        int prev = 0;
        while (i < s.length()) {
            int r = s.codePointAt(i);
            if (word && wordBoundary(prev, r)) {
                break;
            }
            word = word || isWordChar(r);
            prev = r;
            i += Character.charCount(r);
        }
        return i;
    }

    /** The start of the word before i (as MyGO's prevWord). */
    public static int prevWord(String s, int i) {
        boolean word = false;
        int next = 0;
        while (i > 0) {
            int r = s.codePointBefore(i);
            if (word && wordBoundary(r, next)) {
                break;
            }
            word = word || isWordChar(r);
            next = r;
            i -= Character.charCount(r);
        }
        return i;
    }

    /** {start, end} of the word, or the run of other characters, around i (as MyGO's wordAt). */
    public static int[] wordAt(String s, int i) {
        int n = s.length();
        if (n == 0) {
            return new int[] {0, 0};
        }
        i = Math.max(0, Math.min(i, n - 1));
        if (i > 0 && Character.isLowSurrogate(s.charAt(i)) && Character.isHighSurrogate(s.charAt(i - 1))) {
            i--;
        }
        int at = s.codePointAt(i);
        int start = i;
        for (int next = at; start > 0; ) {
            int r = s.codePointBefore(start);
            if (r == '\n' || r == '\r' || wordBoundary(r, next)) {
                break;
            }
            next = r;
            start -= Character.charCount(r);
        }
        int end = i;
        for (int prev = at; end < n; ) {
            int r = s.codePointAt(end);
            if (r == '\n' || r == '\r' || end > i && wordBoundary(prev, r)) {
                break;
            }
            prev = r;
            end += Character.charCount(r);
        }
        return new int[] {start, end};
    }
}
