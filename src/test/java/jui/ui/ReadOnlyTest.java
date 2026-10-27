package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ReadOnlyTest {
    final TextRef q = new TextRef("hello");
    final boolean[] readOnly = {true};
    final int[] submits = {0};

    Tester tester() {
        return Tester.of(c -> {
            if (textInput(c, q).label("in").readOnly(readOnly[0]).submitted()) submits[0]++;
        }, 300, 200);
    }

    public void testItTakesNoTypingButSelectsAndCopies() {
        Tester tt = tester();
        tt.click("in");
        Check.isTrue(tt.focused("in"), "it takes the focus");
        tt.type("x");
        tt.key(Shortcut.of(Key.BACKSPACE));
        tt.setClipboard("pasted");
        tt.command(EditCommand.PASTE);
        tt.command(EditCommand.CUT);
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal("hello", q.get());
        Check.equal(0, submits[0]);
        tt.key(Shortcut.of(Key.END));                        // plain End is not taken: the caret stays
        tt.key(Shortcut.of(Mod.SHIFT, Key.HOME));
        tt.key(Shortcut.cmd(Key.C));
        Check.equal("hello", tt.clipboard());
        readOnly[0] = false;
        tt.frame();
        tt.key(Shortcut.of(Key.END));
        tt.type("!");
        Check.equal("hello!", q.get());
    }

    public void testItShowsNoCaretAndTakesNoTextInput() {
        Tester tt = tester();
        tt.click("in");
        Check.isTrue(!tt.textInputActive(), "the input method has nothing to type into");
        Check.equal(Long.MAX_VALUE, tt.engine().nextWake()); // no caret to blink
    }

    public void testTypingBeforeTheFrameThatTurnsItReadOnlyStays() { // Review Focus 1
        readOnly[0] = false;
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.engine().textInsert("!", -1, 0);                  // between frames, while it shows editable
        readOnly[0] = true;
        tt.frame();
        Check.equal("hello!", q.get());
        tt.type("?");
        Check.equal("hello!", q.get());
    }

    public void testTurningReadOnlyWhileComposingDropsTheComposition() { // Review Focus 5
        readOnly[0] = false;
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.compose("ni", 2);
        readOnly[0] = true;
        tt.frame();
        Check.equal("hello", q.get());
        Check.equal("hello", tt.shownText("in"));         // shown without the composition
    }

    public void testInAScrollContainerPlainKeysScrollAndTabSelectsAll() { // Review Focus 4
        ScrollState st = new ScrollState();
        boolean[] pasted = {false};
        Tester tt = Tester.of(c -> {
            if (c.shortcut(Shortcut.cmd(Key.V))) pasted[0] = true;
            scroll(c, st).size(300, 100).children(() -> {
                textInput(c, q).label("in").readOnly(true);
                box(c).height(1000);
            });
        }, 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("in"), "Tab reaches it");
        tt.key(Shortcut.cmd(Key.C));
        Check.equal("hello", tt.clipboard());               // Tab selected all
        tt.key(Shortcut.of(Key.DOWN));
        Check.near(40, st.y());                              // the container scrolled
        tt.command(EditCommand.PASTE);
        Check.isTrue(!pasted[0], "the menu's Paste does not go on to the shortcut");
    }
}
