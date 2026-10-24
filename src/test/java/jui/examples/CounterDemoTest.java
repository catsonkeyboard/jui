package jui.examples;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

public final class CounterDemoTest {
    public void testButtonsArrowsAndTheStepSlider() {
        CounterDemo s = new CounterDemo();
        Tester tt = Tester.of(s::view, 360, 300);
        tt.click("增加"); tt.click("增加"); tt.click("减少");
        Check.equal(1, s.n);
        Check.isTrue(tt.hasText("1"), "shows 1");
        tt.key(Shortcut.of(Key.UP)); tt.key(Shortcut.of(Key.UP)); tt.key(Shortcut.of(Key.DOWN));
        Check.equal(2, s.n);
        tt.click("重置");
        Check.equal(0, s.n);
        tt.click("重置");                                   // disabled at 0: nothing happens
        Check.equal(0, s.n);
    }

    public void testTabReachesTheButtonsAndTheSlider() {
        CounterDemo s = new CounterDemo();
        Tester tt = Tester.of(s::view, 360, 300);
        tt.key(Shortcut.of(Key.TAB)); Check.isTrue(tt.focused("减少"), "减少 first");
        tt.key(Shortcut.of(Key.TAB)); Check.isTrue(tt.focused("增加"), "重置 is disabled at 0, skipped");
        tt.key(Shortcut.of(Key.SPACE)); Check.equal(1, s.n);
        tt.key(Shortcut.of(Key.TAB)); Check.isTrue(tt.focused("步长"), "then the slider");
        tt.key(Shortcut.of(Key.RIGHT)); tt.key(Shortcut.of(Key.RIGHT));
        Check.near(3, s.step.get());
        tt.key(Shortcut.of(Key.UP));                         // the slider has focus and takes ↑
        Check.near(4, s.step.get());
        Check.equal(1, s.n);
    }
}
