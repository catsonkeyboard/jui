package jui.ui;

import jui.testing.Check;
import static jui.ui.Ui.*;

public final class TextAreaOptionsTest {
    public void testTabHookCanKeepFocusInTheArea() {
        TextRef value = new TextRef("");
        Tester tester = Tester.of(context -> {
            textArea(context, value).label("area").onTextKey(key -> key.key() == Key.TAB);
            button(context, "next");
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        Check.isTrue(tester.focused("area"), "first Tab enters the area");
        tester.key(Shortcut.of(Key.TAB));
        Check.isTrue(tester.focused("area"), "hook takes Tab before focus moves");
    }

    public void testPasswordDoesNothingAndReadOnlyCopiesWholeLines() {
        TextRef value = new TextRef("one\ntwo");
        Tester tester = Tester.of(context -> textArea(context, value).readOnly(true).password().size(220, 100).label("area"), 300, 200);
        tester.key(Shortcut.of(Key.TAB));
        Check.equal(value.get(), tester.shownText("area"));
        tester.command(EditCommand.COPY);
        Check.equal(value.get(), tester.clipboard());
        tester.type("bad");
        Check.equal("one\ntwo", value.get());
    }

    public void testEnterCanSendWhileShiftEnterInsertsNewline() {
        TextRef value = new TextRef("");
        TextRef sent = new TextRef("");
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 100).label("area").onTextKey(key -> {
            if (key.key() == Key.ENTER && key.mods() == 0) {
                sent.set(key.text());
                key.setText("");
                return true;
            }
            return false;
        }), 300, 200);
        tester.key(Shortcut.of(Key.TAB));
        tester.type("one");
        tester.key(Shortcut.of(Mod.SHIFT, Key.ENTER));
        tester.type("two");
        tester.key(Shortcut.of(Key.ENTER));
        Check.equal("one\ntwo", sent.get());
        Check.equal("", value.get());
    }

    public void testRejectedPasteLeavesSelectionAndHistoryUntouched() {
        TextRef value = new TextRef("one\ntwo");
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 100).label("area").onPaste(text -> true), 300, 200);
        tester.key(Shortcut.of(Key.TAB));
        tester.setClipboard("x\ny");
        tester.command(EditCommand.PASTE);
        tester.command(EditCommand.DELETE);
        Check.equal("", value.get());
        tester.command(EditCommand.UNDO);
        Check.equal("one\ntwo", value.get());
    }
}
