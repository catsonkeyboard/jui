package jui.ui;

import java.util.ArrayList;
import java.util.List;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class OnTextKeyTest {
    final TextRef q = new TextRef("");
    final List<String> seen = new ArrayList<>(), sent = new ArrayList<>();
    final int[] submits = {0}, escapes = {0};
    final TextKey[] kept = {null};

    Tester tester() {
        return Tester.of(c -> {
            if (c.shortcut(Shortcut.of(Key.ESCAPE))) escapes[0]++;
            column(c).children(() -> {
                Element in = textInput(c, q).label("in").onTextKey(k -> {
                    seen.add(k.key() + "/" + k.mods());
                    kept[0] = k;
                    if (k.key() == Key.ENTER && k.mods() == 0) {
                        sent.add(k.text());
                        k.setText("");
                        return true;
                    }
                    return k.key() == Key.TAB && k.mods() == 0;
                });
                if (in.submitted()) submits[0]++;
                button(c, "next");
            });
        }, 300, 200);
    }

    public void testEnterTakenSendsAndClearsWithoutSubmitting() {
        Tester tt = tester();
        tt.click("in");
        tt.type("hi");
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(List.of("hi"), sent);
        Check.equal("", q.get());
        Check.equal(0, submits[0]);
    }

    public void testItSeesWhatWasTypedJustBeforeTheKey() {
        Tester tt = tester();
        tt.click("in");
        tt.engine().textInsert("ab", -1, 0);                 // no frame between
        tt.engine().keyDown(Key.ENTER, 0, false);
        Check.equal(List.of("ab"), sent);
    }

    public void testKeysThatTypeTextNeverReachIt() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.A));
        tt.key(Shortcut.of(Mod.SHIFT, Key.DIGIT1));
        tt.key(Shortcut.of(Key.SPACE));
        tt.key(Shortcut.of(Mod.ALT, Key.E));
        Check.equal(List.of(), seen);
        tt.key(Shortcut.of(Mod.SHIFT, Key.ENTER));
        tt.key(Shortcut.cmd(Key.ENTER));
        Check.equal(List.of("ENTER/" + Mod.SHIFT, "ENTER/" + Mod.CMD), seen);
    }

    public void testTabTakenKeepsTheFocus() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("in"), "taken: the focus stays");
        tt.key(Shortcut.of(Mod.SHIFT, Key.TAB));
        Check.isTrue(!tt.focused("in"), "left: Shift+Tab moves it");
    }

    public void testAShortcutAroundGoesFirstUnlessTheInputEditsWithTheKey() { // Review Focus 3
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.ESCAPE));
        Check.equal(1, escapes[0]);
        Check.equal(List.of(), seen);                         // the window's Escape took it
        tt.key(Shortcut.of(Key.LEFT));
        Check.equal(List.of("LEFT/0"), seen);                 // the input edits with ←: it comes here first
    }

    public void testWhileComposingKeysAreTheInputMethods() { // Review Focus 3
        Tester tt = tester();
        tt.click("in");
        tt.compose("ni", 2);
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(List.of(), seen);
    }

    public void testAKeptTextKeyFails() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.LEFT));
        Check.fails(IllegalStateException.class, () -> kept[0].key());
        Check.fails(IllegalStateException.class, () -> kept[0].mods());
        Check.fails(IllegalStateException.class, () -> kept[0].text());
        Check.fails(IllegalStateException.class, () -> kept[0].setText("x"));
    }

    public void testAKeptTextKeyStaysDeadInALaterHook() {
        int[] reused = {0};
        TextKey[] first = {null};
        Tester tt = Tester.of(c -> textInput(c, q).label("in").onTextKey(k -> {
            if (first[0] == null) {
                first[0] = k;
            } else {
                try { first[0].text(); reused[0]++; } catch (IllegalStateException expected) { }
                try { first[0].key(); reused[0]++; } catch (IllegalStateException expected) { }
            }
            return k.key() == Key.LEFT;
        }), 300, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.LEFT));
        tt.key(Shortcut.of(Key.RIGHT));
        Check.equal(0, reused[0]);
    }
}
