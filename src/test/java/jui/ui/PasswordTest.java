package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class PasswordTest {
    final TextRef q = new TextRef("");
    final boolean[] hide = {true};
    final int[] caret = {-1};

    Tester tester() {
        return Tester.of(c -> {
            Element in = textInput(c, q).label("pw");
            if (hide[0]) in.password();
            caret[0] = in.textSelectionEnd();
        }, 300, 200);
    }

    public void testItShowsABulletForEachCharacter() {
        Tester tt = tester();
        tt.click("pw");
        tt.type("a😀中");
        Check.equal("a😀中", q.get());
        Check.equal("•••", tt.shownText("pw"));           // one bullet a code point: the emoji's two UTF-16 units show one
        hide[0] = false;
        tt.frame();
        Check.equal("a😀中", tt.shownText("pw"));          // shown again once password() is not called
    }

    public void testCopyAndCutDoNothingButPasteWorks() {
        q.set("secret");
        Tester tt = tester();
        tt.click("pw");
        tt.key(Shortcut.cmd(Key.A));
        tt.setClipboard("before");
        tt.command(EditCommand.COPY);
        tt.command(EditCommand.CUT);
        Check.equal("before", tt.clipboard());
        Check.equal("secret", q.get());
        tt.command(EditCommand.PASTE);
        Check.equal("before", q.get());
    }

    public void testTheInputMethodSeesOnlyTheComposition() { // Review Focus 2
        q.set("secret");
        Tester tt = tester();
        tt.click("pw");
        Check.equal(0, tt.engine().textLength());
        tt.compose("ni", 2);
        Check.equal(2, tt.engine().textLength());
        Check.equal("ni", tt.engine().textIn(0, 2));
        int[] r = new int[2];
        Check.isTrue(tt.engine().textMarkedRange(r), "marked");
        Check.equal("[0, 2]", java.util.Arrays.toString(r));
        Rect box = tt.find("pw");
        Check.equal(-1, tt.engine().textIndexAt(box.x() + 20, box.y() + box.height() / 2));
        tt.type("你");                                       // the input method commits its choice over the composition
        Check.equal("secret你", q.get());
    }

    public void testAClickBetweenBulletsLandsBetweenCharacters() { // Review Focus 2
        q.set("a😀b");
        Tester tt = tester();
        tt.click("pw");
        tt.key(Shortcut.of(Key.HOME));
        tt.key(Shortcut.of(Key.RIGHT));
        tt.key(Shortcut.of(Key.RIGHT));                       // after the emoji: real index 3, the second bullet's end
        tt.frame();
        Check.equal(3, caret[0]);
        Rect at = tt.textCaret();
        tt.key(Shortcut.of(Key.HOME));
        tt.press(at.x(), at.y() + at.height() / 2);
        tt.release(at.x(), at.y() + at.height() / 2);
        tt.frame();
        Check.equal(3, caret[0]);
    }
}
