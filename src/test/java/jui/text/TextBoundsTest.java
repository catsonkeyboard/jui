package jui.text;

import jui.testing.Check;

public final class TextBoundsTest {
    public void testGraphemesKeepClustersWhole() {
        String s = "é😀👨‍👩‍👧中🇨🇳a";
        Check.equal("[0, 2, 4, 12, 13, 17, 18]", java.util.Arrays.toString(TextBounds.graphemes(s)));
        Check.equal("[0]", java.util.Arrays.toString(TextBounds.graphemes("")));
    }

    public void testStepsBeforeAndAfter() {
        int[] g = TextBounds.graphemes("a😀b");          // 0, 1, 3, 4
        Check.equal(1, TextBounds.before(g, 3));
        Check.equal(3, TextBounds.after(g, 1));
        Check.equal(0, TextBounds.before(g, 0));
        Check.equal(4, TextBounds.after(g, 4));
        Check.equal(1, TextBounds.floor(g, 2));
    }

    public void testWordsAreRunsOfLettersAndEachHanAlone() {
        Check.equal(5, TextBounds.nextWord("hello world", 0));
        Check.equal(11, TextBounds.nextWord("hello world", 5));
        Check.equal(6, TextBounds.prevWord("hello world", 11));
        Check.equal(2, TextBounds.nextWord("Go语言", 0));
        Check.equal(3, TextBounds.prevWord("Go语言", 4));
        Check.equal(2, TextBounds.prevWord("Go语言", 3));
        Check.equal(0, TextBounds.prevWord("Go语言", 2));
        Check.equal(10, TextBounds.nextWord("snake_case", 0));   // _ is a word char: one word
    }

    public void testWordAtTakesTheWordOrTheRunAround() {
        Check.equal("[0, 5]", java.util.Arrays.toString(TextBounds.wordAt("hello world", 2)));
        Check.equal("[5, 6]", java.util.Arrays.toString(TextBounds.wordAt("hello world", 5)));
        Check.equal("[2, 3]", java.util.Arrays.toString(TextBounds.wordAt("Go语言", 2)));
        Check.equal("[1, 3]", java.util.Arrays.toString(TextBounds.wordAt("a  b", 1)));
        Check.equal("[6, 11]", java.util.Arrays.toString(TextBounds.wordAt("hello world", 11)));
        Check.equal("[0, 0]", java.util.Arrays.toString(TextBounds.wordAt("", 0)));
    }

    public void testKanaAndTheProlongedSoundMarkStandAlone() {
        Check.isTrue(TextBounds.standsAlone('あ') && TextBounds.standsAlone('カ') && TextBounds.standsAlone('ー'), "kana");
        Check.isTrue(TextBounds.standsAlone('々'), "the iteration mark");
        Check.isTrue(!TextBounds.standsAlone('A') && !TextBounds.standsAlone('한'), "Latin and Hangul do not");
    }
}
