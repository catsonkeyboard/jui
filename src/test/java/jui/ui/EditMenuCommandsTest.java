package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class EditMenuCommandsTest {
    final TextRef q = new TextRef("hello");

    public void testPasteAndMatchStylePastes() {
        Tester tt = Tester.of(c -> textInput(c, q).label("in"), 300, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.setClipboard(" world");
        tt.command(EditCommand.PASTE_AND_MATCH_STYLE);
        Check.equal("hello world", q.get());
    }

    public void testDeleteRemovesTheSelectionOnly() {
        Tester tt = Tester.of(c -> textInput(c, q).label("in"), 300, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.command(EditCommand.DELETE);                      // nothing selected: nothing goes
        Check.equal("hello", q.get());
        tt.key(Shortcut.of(Mod.SHIFT, Key.LEFT));
        tt.key(Shortcut.of(Mod.SHIFT, Key.LEFT));
        tt.command(EditCommand.DELETE);
        Check.equal("hel", q.get());
        tt.key(Shortcut.cmd(Key.Z));
        Check.equal("hello", q.get());                       // one undo step
    }

    public void testWithoutAnInputPasteAndMatchStyleGoesToShortcutsAsItsKeys() {
        int[] fired = {0};
        Tester tt = Tester.of(c -> {
            if (c.shortcut(Shortcut.of(Mod.CMD | Mod.ALT | Mod.SHIFT, Key.V))) fired[0]++;
            text(c, "no input");
        }, 300, 200);
        tt.command(EditCommand.PASTE_AND_MATCH_STYLE);
        tt.command(EditCommand.DELETE);
        Check.equal(1, fired[0]);
    }
}
