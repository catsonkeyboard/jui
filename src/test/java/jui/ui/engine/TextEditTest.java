package jui.ui.engine;

import jui.testing.Check;
import jui.ui.EditCommand;
import jui.ui.Key;
import jui.ui.Mod;

public final class TextEditTest {
    static final long S = 1_000_000_000L;

    static TextEdit at(String text, int anchor, int caret) {
        TextEdit e = new TextEdit(text);
        e.select(anchor, caret);
        return e;
    }

    public void testTypingReplacesTheSelection() {
        TextEdit e = at("hello", 1, 4);
        e.insert("i", 0);
        Check.equal("hio", e.text());
        Check.equal(2, e.caret());
        Check.equal(2, e.anchor());
        Check.isTrue(e.dirty, "dirty");
    }

    public void testArrowsStepByGraphemesAndCollapseASelection() {
        TextEdit e = at("a😀b", 4, 4);
        e.key(Key.LEFT, 0, 0);
        Check.equal(3, e.caret());
        e.key(Key.LEFT, 0, 0);
        Check.equal(1, e.caret());
        e = at("hello", 1, 4);
        e.key(Key.LEFT, 0, 0);
        Check.equal(1, e.caret());
        Check.equal(1, e.anchor());
        e = at("hello", 4, 1);
        e.key(Key.RIGHT, 0, 0);
        Check.equal(4, e.caret());
        e = at("hello", 1, 1);
        e.key(Key.RIGHT, Mod.SHIFT, 0);
        e.key(Key.RIGHT, Mod.SHIFT, 0);
        Check.equal(1, e.selStart());
        Check.equal(3, e.selEnd());
    }

    public void testOptionMovesByWordsAndCmdToTheEnds() {
        TextEdit e = at("hello world", 0, 0);
        e.key(Key.RIGHT, Mod.ALT, 0);
        Check.equal(5, e.caret());
        e.key(Key.RIGHT, Mod.ALT, 0);
        Check.equal(11, e.caret());
        e.key(Key.LEFT, Mod.ALT, 0);
        Check.equal(6, e.caret());
        e.key(Key.LEFT, Mod.CMD, 0);
        Check.equal(0, e.caret());
        e.key(Key.END, Mod.SHIFT, 0);
        Check.equal(0, e.anchor());
        Check.equal(11, e.caret());
        e = at("Go语言", 4, 4);
        e.key(Key.LEFT, Mod.ALT, 0);
        Check.equal(3, e.caret());
    }

    public void testUpAndDownAreLeftToOthersUnlessModified() {
        Check.isTrue(!TextEdit.wants(Key.UP, 0) && !TextEdit.wants(Key.DOWN, Mod.SHIFT), "plain up and down");
        Check.isTrue(!TextEdit.wants(Key.PAGE_DOWN, 0), "page down");
        Check.isTrue(TextEdit.wants(Key.UP, Mod.ALT) && TextEdit.wants(Key.DOWN, Mod.CMD), "with a modifier");
        TextEdit e = at("hello", 2, 2);
        e.key(Key.UP, Mod.ALT, 0);
        Check.equal(0, e.caret());
        e.key(Key.DOWN, Mod.CMD | Mod.SHIFT, 0);
        Check.equal(5, e.caret());
        Check.equal(0, e.anchor());
    }

    public void testWhatItWants() {
        Check.isTrue(TextEdit.wants(Key.A, 0) && TextEdit.wants(Key.A, Mod.SHIFT) && TextEdit.wants(Key.A, Mod.ALT),
                "printable keys type text");
        Check.isTrue(TextEdit.wants(Key.A, Mod.CMD) && TextEdit.wants(Key.Z, Mod.CMD | Mod.SHIFT), "edit shortcuts");
        Check.isTrue(!TextEdit.wants(Key.P, Mod.CMD) && !TextEdit.wants(Key.ENTER, Mod.CMD), "the app's shortcuts");
        Check.isTrue(!TextEdit.wants(Key.TAB, 0) && !TextEdit.wants(Key.ESCAPE, 0) && !TextEdit.wants(Key.F3, 0), "others");
        Check.isTrue(TextEdit.wants(Key.ENTER, Mod.SHIFT), "shift enter submits");
        Check.isTrue(TextEdit.wants(Key.B, Mod.CTRL) && !TextEdit.wants(Key.Q, Mod.CTRL), "emacs keys only");
        Check.equal(EditCommand.REDO, TextEdit.commandOf(Key.Z, Mod.CMD | Mod.SHIFT));
        Check.equal(EditCommand.REDO, TextEdit.commandOf(Key.Y, Mod.CMD));
        Check.equal(null, TextEdit.commandOf(Key.A, 0));
    }

    public void testBackspaceDeletesWholeClustersWordsAndToTheStart() {
        TextEdit e = at("a👨‍👩‍👧", 9, 9);
        e.key(Key.BACKSPACE, 0, 0);
        Check.equal("a", e.text());
        e = at("Go语言", 4, 4);
        e.key(Key.BACKSPACE, Mod.ALT, 0);
        Check.equal("Go语", e.text());
        e = at("hello world", 8, 8);
        e.key(Key.BACKSPACE, Mod.CMD, 0);
        Check.equal("rld", e.text());
        Check.equal(0, e.caret());
        e = at("hello world", 6, 6);
        e.key(Key.DELETE, Mod.ALT, 0);
        Check.equal("hello ", e.text());
    }

    public void testEmacsKeys() {
        TextEdit e = at("hello", 2, 2);
        e.key(Key.A, Mod.CTRL, 0);
        Check.equal(0, e.caret());
        e.key(Key.E, Mod.CTRL, 0);
        Check.equal(5, e.caret());
        e.key(Key.B, Mod.CTRL, 0);
        Check.equal(4, e.caret());
        e.key(Key.H, Mod.CTRL, 0);
        Check.equal("helo", e.text());
        e.key(Key.A, Mod.CTRL, 0);
        e.key(Key.K, Mod.CTRL, 0);
        Check.equal("", e.text());
    }

    public void testEnterSubmits() {
        TextEdit e = at("x", 1, 1);
        Check.isTrue(e.key(Key.ENTER, 0, 0), "enter");
        Check.isTrue(e.key(Key.ENTER, Mod.SHIFT, 0), "shift enter");
        Check.isTrue(!e.key(Key.LEFT, 0, 0), "not a submit");
    }

    public void testCopyCutPaste() {
        TextEdit e = at("hello world", 0, 5);
        Check.equal("hello", e.command(EditCommand.COPY, null, 0));
        Check.equal("hello", e.command(EditCommand.CUT, null, 0));
        Check.equal(" world", e.text());
        e.select(0, 0);
        Check.equal(null, e.command(EditCommand.COPY, null, 0));
        e.command(EditCommand.PASTE, "a\r\nb\nc\rd", 0);
        Check.equal("a b c d world", e.text());
        e.command(EditCommand.SELECT_ALL, null, 0);
        Check.equal(0, e.selStart());
        Check.equal(13, e.selEnd());
    }

    public void testTypingWithinASecondIsOneUndoStep() {
        TextEdit e = new TextEdit("");
        e.insert("a", 0);
        e.insert("b", S / 2);
        e.insert("c", S / 2 + 9 * S / 10);    // 0.9 s after the last: the same step
        e.insert("d", 3 * S);                 // a pause: a new step
        e.command(EditCommand.UNDO, null, 3 * S);
        Check.equal("abc", e.text());
        e.command(EditCommand.UNDO, null, 3 * S);
        Check.equal("", e.text());
        e.command(EditCommand.REDO, null, 3 * S);
        Check.equal("abc", e.text());
    }

    public void testACaretMoveStartsANewStepAndBackspaceJoinsTyping() {
        TextEdit e = new TextEdit("");
        e.insert("a", 0);
        e.insert("b", 1);
        e.key(Key.BACKSPACE, 0, 2);           // joins the typing
        e.key(Key.LEFT, 0, 3);
        e.insert("x", 4);                     // after a move: a new step
        e.command(EditCommand.UNDO, null, 5);
        Check.equal("a", e.text());
        e.command(EditCommand.UNDO, null, 5);
        Check.equal("", e.text());
    }

    public void testUndoRestoresTheSelectionAndANewEditClearsRedo() {
        TextEdit e = at("hello", 1, 4);
        e.insert("i", 0);
        e.command(EditCommand.UNDO, null, S);
        Check.equal("hello", e.text());
        Check.equal(1, e.anchor());
        Check.equal(4, e.caret());
        e.insert("z", 2 * S);                 // over the restored selection "ell": "hzo"
        e.command(EditCommand.REDO, null, 3 * S);
        Check.equal("hzo", e.text());         // the new edit cleared redo: nothing changes
    }

    public void testUndoKeepsAtMost200Steps() {
        TextEdit e = new TextEdit("");
        for (int i = 0; i < 250; i++) {
            e.insert("x", i * 2 * S);         // a pause each time: 250 steps
        }
        for (int i = 0; i < 300; i++) {
            e.command(EditCommand.UNDO, null, 600 * S);
        }
        Check.equal(50, e.text().length());
    }

    public void testACompositionShowsButIsNotCommitted() {
        TextEdit e = at("ab", 2, 2);
        e.setMarked("ni", 2, 0, -1, 0, 0);
        Check.equal("ab", e.text());
        Check.equal("abni", e.display());
        Check.equal(4, e.displayCaret());
        Check.isTrue(!e.dirty, "a composition is not an edit of the text");
        int[] r = new int[2];
        Check.isTrue(e.markedRange(r), "marked");
        Check.equal(2, r[0]);
        Check.equal(2, r[1]);
        e.selectedRange(r);
        Check.equal(4, r[0]);
        Check.equal(0, r[1]);
        e.insert("你", 1);
        Check.equal("ab你", e.text());
        Check.equal(3, e.caret());
        Check.isTrue(!e.composing() && e.dirty, "committed");
    }

    public void testACompositionOverASelectionIsOneUndoStep() {
        TextEdit e = at("cafe", 3, 4);
        e.setMarked("é", 1, 0, -1, 0, 0);
        Check.equal("caf", e.text());
        Check.equal("café", e.display());
        e.insert("é", 1);
        Check.equal("café", e.text());
        e.command(EditCommand.UNDO, null, 2);
        Check.equal("cafe", e.text());
        Check.equal(3, e.selStart());
        Check.equal(4, e.selEnd());
    }

    public void testUnmarkCommitsAndAnEmptyMarkCancels() {
        TextEdit e = at("", 0, 0);
        e.setMarked("hui", 3, 0, -1, 0, 0);
        e.unmark(1);
        Check.equal("hui", e.text());
        Check.equal(3, e.caret());
        e.setMarked("x", 1, 0, -1, 0, 2);
        e.setMarked("", 0, 0, -1, 0, 3);
        Check.equal("hui", e.display());
        Check.isTrue(!e.composing(), "cancelled");
    }

    public void testAReplacementRangeCountsInTheShownText() {
        TextEdit e = at("abc", 3, 3);
        e.setMarked("xy", 2, 0, -1, 0, 0);
        e.replace(3, 5, "Z", 1);
        Check.equal("abcZ", e.text());
        Check.equal(4, e.caret());
    }

    public void testAnAppChangeClampsTheCaretAndFoldsIntoUndo() {
        TextEdit e = new TextEdit("x");
        e.insert("y", 0);                     // "xy"
        e.setText("xy!");                     // the app appends
        Check.equal(2, e.caret());
        Check.equal("xy!", e.synced);
        e.command(EditCommand.UNDO, null, S);
        Check.equal("x", e.text());           // back to before the last user edit
        e = at("hello", 5, 5);
        e.setText("he");
        Check.equal(2, e.caret());
        Check.equal(2, e.anchor());
    }

    public void testPressPlacesSelectsWordsAndAll() {
        TextEdit e = new TextEdit("hello world");
        e.press(2, 1, false);
        Check.equal(2, e.caret());
        e.press(4, 1, true);
        Check.equal(2, e.anchor());
        Check.equal(4, e.caret());
        e.press(1, 2, false);
        Check.equal(0, e.selStart());
        Check.equal(5, e.selEnd());
        e.drag(8);                            // by whole words, the first kept
        Check.equal(0, e.selStart());
        Check.equal(11, e.selEnd());
        e.press(1, 3, false);
        Check.equal(0, e.selStart());
        Check.equal(11, e.selEnd());
    }

    public void testACancelledCompositionLeavesNoUndoStep() { // M4 final review minor
        TextEdit e = new TextEdit("");
        e.insert("ab", 0);                                 // a step: "" to "ab"
        e.setMarked("x", 1, 0, -1, 0, S);                  // a composition opens a step
        e.setMarked("", 0, 0, -1, 0, S);                   // and is cancelled, having changed nothing
        e.command(EditCommand.UNDO, null, 2 * S);
        Check.equal("", e.text());                         // the first undo undoes the typing
        e = at("cafe", 3, 4);
        e.setMarked("é", 1, 0, -1, 0, 0);                  // it deleted the selection
        e.setMarked("", 0, 0, -1, 0, 0);
        Check.equal("caf", e.text());
        e.command(EditCommand.UNDO, null, S);
        Check.equal("cafe", e.text());                     // that step stays
    }
}
