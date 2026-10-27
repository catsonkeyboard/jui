package jui.ui;

import java.util.ArrayList;
import java.util.List;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class OnPasteTest {
    final TextRef q = new TextRef("ab");
    final List<String> seen = new ArrayList<>();
    final boolean[] take = {false};
    final boolean[] readOnly = {false};

    Tester tester() {
        return Tester.of(c -> textInput(c, q).label("in").readOnly(readOnly[0]).onPaste(s -> {
            seen.add(s);
            return take[0];
        }), 300, 200);
    }

    public void testAPasteNotTakenInserts() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.setClipboard("cd");
        tt.key(Shortcut.cmd(Key.V));
        Check.equal("abcd", q.get());
        Check.equal(List.of("cd"), seen);
    }

    public void testAPasteTakenChangesNothing() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.cmd(Key.A));
        take[0] = true;
        tt.setClipboard("cd");
        tt.key(Shortcut.cmd(Key.V));
        tt.command(EditCommand.PASTE);
        tt.command(EditCommand.PASTE_AND_MATCH_STYLE);
        Check.equal("ab", q.get());
        Check.equal(List.of("cd", "cd", "cd"), seen);
        tt.command(EditCommand.DELETE);                      // the selection stayed
        Check.equal("", q.get());
        tt.key(Shortcut.cmd(Key.Z));
        Check.equal("ab", q.get());                          // and no step of its own
    }

    public void testAnEmptyClipboardIsSeenAsEmpty() {
        Tester tt = tester();
        tt.click("in");
        tt.setClipboard(null);
        tt.command(EditCommand.PASTE);
        Check.equal(List.of(""), seen);
        Check.equal("ab", q.get());
    }

    public void testAReadOnlyInputNeverAsks() {
        readOnly[0] = true;
        Tester tt = tester();
        tt.click("in");
        tt.setClipboard("cd");
        tt.command(EditCommand.PASTE);
        Check.equal(List.of(), seen);
    }
}
