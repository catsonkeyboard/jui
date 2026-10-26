package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class TextInputTest {
    public void testTypingWritesTheRefAndReportsChangedOnce() {
        TextRef q = new TextRef("");
        int[] changes = {0};
        Tester tt = Tester.of(c -> { if (textInput(c, q).placeholder("搜索").changed()) changes[0]++; }, 300, 200);
        tt.click("搜索");
        tt.type("会议");
        Check.equal("会议", q.get());
        Check.equal(1, changes[0]);
        tt.frame();
        Check.equal(1, changes[0]);
    }

    public void testEditsBetweenFramesApplyInOrderAndShowInOneFrame() { // Review Focus 1
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> textInput(c, q).placeholder("搜索"), 300, 200);
        tt.click("搜索");
        tt.engine().textInsert("ab", -1, 0);
        tt.engine().keyDown(Key.LEFT, 0, false);
        tt.engine().textInsert("c", -1, 0);
        tt.frame();                                        // one frame
        Check.equal("acb", q.get());
    }

    public void testEnterSubmitsAndTheAppCanClearTheRef() { // Review Focus 2
        TextRef q = new TextRef("");
        java.util.List<String> added = new java.util.ArrayList<>();
        int[] changes = {0};
        Tester tt = Tester.of(c -> {
            Element in = textInput(c, q).placeholder("新待办");
            if (in.changed()) changes[0]++;
            if (in.submitted()) {
                added.add(q.get());
                q.set("");
            }
        }, 300, 200);
        tt.click("新待办");
        tt.type("买菜");
        int before = changes[0];
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(java.util.List.of("买菜"), added);
        Check.equal("", q.get());
        Check.equal(before, changes[0]);                   // the app's change is not reported
        Check.equal("[0, 0]", java.util.Arrays.toString(tt.textSelection()));
        // The app's clearing folded into the last step ("" → "买菜"), whose "after" is now "":
        // undo goes back to before the user's edit, which is "" too (as MyGO). Nothing changes.
        tt.key(Shortcut.cmd(Key.Z));
        Check.equal("", q.get());
        Check.equal(before, changes[0]);
    }

    public void testAWindowShortcutWorksWhileTypingButPlainKeysDoNot() {
        TextRef q = new TextRef("");
        int[] palette = {0}, slash = {0};
        Tester tt = Tester.of(c -> {
            if (c.shortcut(Shortcut.cmd(Key.P))) palette[0]++;
            if (c.shortcut(Shortcut.of(Key.SLASH))) slash[0]++;
            textInput(c, q).placeholder("搜索");
        }, 300, 200);
        tt.click("搜索");
        tt.key(Shortcut.cmd(Key.P));
        tt.key(Shortcut.of(Key.SLASH));
        Check.equal(1, palette[0]);
        Check.equal(0, slash[0]);                          // it types a slash, which comes as text
    }

    public void testTabIntoAnInputSelectsItsText() {
        TextRef q = new TextRef("hello");
        Tester tt = Tester.of(c -> column(c).children(() -> {
            button(c, "前");
            textInput(c, q);
        }), 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.TAB));
        Check.equal("[0, 5]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testAnAppChangeShowsWithoutChanged() {
        TextRef q = new TextRef("a");
        int[] changes = {0};
        Tester tt = Tester.of(c -> { if (textInput(c, q).placeholder("p").changed()) changes[0]++; }, 300, 200);
        tt.click("a");
        tt.key(Shortcut.of(Key.END));                      // caret 1, wherever the click put it
        q.set("abc");
        tt.frame();                                        // the app's value shows; the caret stays at 1
        Check.equal(0, changes[0]);
        tt.type("!");
        Check.equal("a!bc", q.get());
        Check.equal(1, changes[0]);
    }

    public void testACompositionStaysOutOfTheRefUntilCommitted() {
        TextRef q = new TextRef("");
        int[] changes = {0};
        Tester tt = Tester.of(c -> { if (textInput(c, q).placeholder("搜索").changed()) changes[0]++; }, 300, 200);
        tt.click("搜索");
        tt.compose("hui", 3);
        tt.compose("huiyi", 5);
        Check.equal("", q.get());
        Check.equal(0, changes[0]);
        tt.type("会议");
        Check.equal("会议", q.get());
        Check.equal(1, changes[0]);
    }

    public void testACompositionIsCommittedWhenFocusLeaves() { // Review Focus 3
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).children(() -> {
            textInput(c, q).placeholder("搜索");
            button(c, "别处");
        }), 300, 200);
        jui.ui.engine.Host.Memory host = (jui.ui.engine.Host.Memory) tt.engine().host();
        tt.click("搜索");
        tt.compose("ni", 2);
        int discards = host.discards;
        tt.click("别处");
        Check.equal("ni", q.get());
        Check.equal(discards + 1, host.discards);
        tt.click("搜索");                                  // back in: the caret after "ni"
        tt.key(Shortcut.of(Key.END));
        tt.compose("hao", 3);
        tt.engine().windowFocusChanged(false);             // the window goes to the back
        tt.settle();
        Check.equal("nihao", q.get());
        Check.equal(discards + 2, host.discards);
    }

    public void testCopyAndPasteGoThroughTheClipboard() {
        TextRef q = new TextRef("hello");
        Tester tt = Tester.of(c -> textInput(c, q), 300, 200);
        tt.click("hello");
        tt.key(Shortcut.cmd(Key.A));
        tt.key(Shortcut.cmd(Key.C));
        Check.equal("hello", tt.clipboard());
        tt.setClipboard("a\nb");
        tt.key(Shortcut.cmd(Key.V));
        Check.equal("a b", q.get());
        tt.command(EditCommand.SELECT_ALL);
        tt.command(EditCommand.CUT);
        Check.equal("", q.get());
        Check.equal("a b", tt.clipboard());
    }

    public void testAMenuCommandWithoutAnInputIsTheKey() {
        int[] undos = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(Shortcut.cmd(Key.Z))) undos[0]++; button(c, "x"); }, 300, 200);
        tt.command(EditCommand.UNDO);
        Check.equal(1, undos[0]);
    }

    public void testADisabledInputTakesNoText() {
        TextRef q = new TextRef("");
        boolean[] off = {false};
        Tester tt = Tester.of(c -> textInput(c, q).placeholder("搜索").disabled(off[0]), 300, 200);
        tt.click("搜索");
        off[0] = true;
        tt.frame();
        tt.frame();
        tt.type("x");
        Check.equal("", q.get());
    }

    public void testOnSubmitRunsAfterTheBuild() {
        TextRef q = new TextRef("x");
        java.util.List<String> log = new java.util.ArrayList<>();
        Tester tt = Tester.of(c -> {
            textInput(c, q).onSubmit(() -> log.add("submit " + q.get()));
            log.add("built");
        }, 300, 200);
        tt.click("x");
        log.clear();
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal("submit x", log.get(1));
    }

    public void testTheInputMethodSeesTheSelectionAndTheComposition() {
        TextRef q = new TextRef("ab");
        Tester tt = Tester.of(c -> textInput(c, q), 300, 200);
        tt.click("ab");
        tt.key(Shortcut.of(Key.END));
        tt.compose("ni", 2);
        int[] r = new int[2];
        Check.isTrue(tt.engine().textMarkedRange(r), "composing");
        Check.equal("[2, 2]", java.util.Arrays.toString(r));
        Check.isTrue(tt.engine().textSelectedRange(r), "a selection");
        Check.equal("[4, 0]", java.util.Arrays.toString(r));
        Check.equal("abni", tt.engine().textIn(0, 99));
        Check.equal(4, tt.engine().textLength());
    }

    public void testACompositionAfterOneTheWindowCommittedIsItsOwnUndoStep() { // final review #1
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> textInput(c, q).placeholder("搜索"), 300, 200);
        tt.click("搜索");
        tt.compose("ni", 2);
        tt.engine().windowFocusChanged(false);             // the window goes to the back: "ni" is committed
        tt.settle();
        tt.engine().windowFocusChanged(true);
        tt.settle();
        tt.compose("hao", 3);
        tt.type("好");
        Check.equal("ni好", q.get());
        tt.key(Shortcut.cmd(Key.Z));
        Check.equal("ni", q.get());
    }

    /** A host that notes whether a composition was still open when the input method was told to discard it. */
    static final class OrderHost implements jui.ui.engine.Host {
        jui.ui.engine.Engine engine;
        final java.util.List<Boolean> composingAtDiscard = new java.util.ArrayList<>();
        public String readClipboard() { return null; }
        public void writeClipboard(String s) {}
        public void setCursor(Cursor c) {}
        public void discardMarkedText() { composingAtDiscard.add(engine.textComposing()); }
        public void textInputChanged() {}
    }

    public void testTheCompositionIsCommittedBeforeTheInputMethodIsToldToDiscardIt() { // final review #4
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> textInput(c, q).label("in"), 300, 200);
        OrderHost host = new OrderHost();
        host.engine = tt.engine();
        tt.click("in");
        tt.engine().setHost(host);
        tt.compose("ni", 2);
        tt.click("in");                                    // a press in the input commits it
        tt.compose("hao", 3);
        tt.command(EditCommand.SELECT_ALL);                // so does a menu command
        Check.equal(java.util.List.of(false, false), host.composingAtDiscard);
        Check.equal("nihao", q.get());
    }

    public void testAValueWithALineBreakShowsOnOneLine() { // final review #7
        TextRef one = new TextRef("ab"), two = new TextRef("a\nb");
        Tester tt = Tester.of(c -> column(c).children(() -> {
            textInput(c, one).label("one");
            textInput(c, two).label("two");
        }), 300, 200);
        tt.frame();
        Check.near(tt.find("one").height(), tt.find("two").height());
        Check.isTrue(tt.find("a b") != null, "the break shows as a space");
        Check.equal("a\nb", two.get());                   // the app's value is left as it is until the user edits
        tt.frame();
        Check.equal("a\nb", two.get());
    }
}
