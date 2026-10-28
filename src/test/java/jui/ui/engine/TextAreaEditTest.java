package jui.ui.engine;

import jui.testing.Check;
import jui.ui.EditCommand;
import jui.ui.Key;
import jui.ui.Mod;

public final class TextAreaEditTest {
    public void testMultilineKeepsAndEditsHardBreaks() {
        TextEdit edit = new TextEdit("ab\ncd", true);
        Check.equal("ab\ncd", edit.text());
        edit.select(2, 2);
        Check.isTrue(!edit.key(Key.ENTER, 0, 0), "newline is not submission");
        Check.equal("ab\n\ncd", edit.text());
        edit.key(Key.DELETE, 0, 1);
        Check.equal("ab\ncd", edit.text());
        edit.insert("\r\nx\ry", 2);
        Check.equal("ab\n\nx\nycd", edit.text());
    }

    public void testEmacsParagraphKeysAndJoining() {
        TextEdit edit = new TextEdit("one\r\ntwo", true);
        edit.key(Key.A, Mod.CTRL, 0);
        Check.equal(5, edit.caret());
        edit.select(3, 3);
        edit.key(Key.K, Mod.CTRL, 1);
        Check.equal("onetwo", edit.text());
        edit.command(EditCommand.UNDO, null, 2);
        Check.equal("one\r\ntwo", edit.text());
    }

    public void testPasteDoesNotJoinTypingAndUndoRestoresAffinity() {
        TextEdit edit = new TextEdit("", true);
        edit.insert("a", 0);
        edit.command(EditCommand.PASTE, "b", 1);
        edit.command(EditCommand.UNDO, null, 2);
        Check.equal("a", edit.text());
        edit.select(1, 1);
        edit.caretUpstream = true;
        edit.insert("x", 3);
        edit.command(EditCommand.UNDO, null, 4);
        Check.isTrue(edit.caretUpstream, "undo restores caret affinity");
    }

    public void testReplacementRangeUsesTheUnnormalizedMarkedDocument() {
        TextEdit edit = new TextEdit("abTAIL", true);
        edit.select(2, 2);
        edit.setMarked("\r\n", 2, 0, -1, 0, 0);
        edit.replace(4, 8, "X", 1);
        Check.equal("ab\nX", edit.text());
        edit.command(EditCommand.UNDO, null, 2);
        Check.equal("abTAIL", edit.text());

        edit.select(2, 2);
        edit.setMarked("x\r\ny", 4, 0, -1, 0, 3);
        edit.replace(3, 5, "-", 4);
        Check.equal("abx-yTAIL", edit.text());
    }
}
