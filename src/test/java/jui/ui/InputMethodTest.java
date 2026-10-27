package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class InputMethodTest {
    final TextRef q = new TextRef("");
    final boolean[] on = {false};

    Tester tester() {
        return Tester.of(c -> textInput(c, q).label("in").inputMethod(on[0]), 300, 200);
    }

    public void testOffItComposesNothingAndKeysType() {
        Tester tt = tester();
        tt.click("in");
        Check.isTrue(tt.textInputActive() && !tt.engine().textInputMethod(), "takes text, without the input method");
        tt.compose("ni", 2);
        Check.equal("", q.get());
        tt.type("j");
        Check.equal("j", q.get());
        on[0] = true;
        tt.frame();
        tt.compose("ni", 2);
        Check.equal("jni", tt.shownText("in"));             // back on, it composes again
        tt.type("你");
        Check.equal("j你", q.get());
    }

    public void testTurningItOffWhileComposingCommits() { // Review Focus 5
        on[0] = true;
        Tester tt = tester();
        tt.click("in");
        tt.compose("ni", 2);
        on[0] = false;
        tt.frame();
        Check.equal("ni", q.get());
    }
}
