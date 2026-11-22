package jui.kit;

import jui.testing.Check;
import jui.ui.Context;
import jui.ui.Tester;

import java.time.Duration;

import static jui.ui.Ui.*;

public final class ConfirmMorphTest {
    static final Duration TICK = Duration.ofMillis(16);

    /** A PRESS-mode morph with instant work, reporting cancel count. */
    static Tester open(int[] cancels) {
        return Tester.of(c -> ConfirmMorph.confirmMorph(c, "清空", "trash", "确定要清空吗？",
                "确认", "取消", "清空中", "已清空", "没成功", "撤销",
                ConfirmMorph.Mode.PRESS, 1000, 3, true, false,
                ok -> ok.accept(true), null,
                () -> cancels[0]++), 300, 120);
    }

    static void clickOpen(Tester tt) {
        tt.click("kit-confirm-pill");
        tt.frame();
    }

    public void testOpenConfirmAndResultDismisses() {
        int[] cancels = { 0 };
        Tester tt = open(cancels);
        tt.frame();
        clickOpen(tt);
        Check.isTrue(tt.texts().contains("确定要清空吗？"), "the question face shows");
        tt.advance(Duration.ofMillis(300));                  // past the 280 ms guard; width settled
        tt.click("kit-confirm-ok");
        tt.advance(Duration.ofMillis(150));
        Check.isTrue(tt.texts().contains("已清空"), "the done face shows");
        tt.move(280, 110);                                   // off the pill: the result clock runs
        tt.advance(Duration.ofMillis(5500));
        Check.isTrue(tt.texts().contains("清空") && !tt.texts().contains("已清空"),
                "the result dismisses after 5 s");
    }

    public void testAConfirmWithin280MsOfOpeningIsIgnored() {
        Tester tt = open(new int[1]);
        tt.frame();
        clickOpen(tt);
        tt.advance(Duration.ofMillis(250));                  // width settled, guard still up
        tt.click("kit-confirm-ok");
        tt.frame();
        Check.isTrue(!tt.texts().contains("已清空") && !tt.texts().contains("清空中"),
                "the guarded confirm did not run");
        tt.advance(Duration.ofMillis(60));                   // now 310 ms: past the guard
        tt.click("kit-confirm-ok");
        tt.advance(Duration.ofMillis(150));
        Check.isTrue(tt.texts().contains("已清空"), "the same click after the guard runs");
    }

    public void testAnUnansweredQuestionReturnsToRestAfterSixSeconds() {
        int[] cancels = { 0 };
        Tester tt = open(cancels);
        tt.frame();
        clickOpen(tt);
        tt.move(280, 110);                                   // off the pill: the clock runs
        tt.advance(Duration.ofMillis(6500));
        Check.isTrue(tt.texts().contains("清空") && !tt.texts().contains("取消"),
                "the question returns to rest");
        Check.equal(0, cancels[0]);                          // a timeout is not a cancel
    }

    public void testTheTimeoutPausesWhileHovered() {
        Tester tt = open(new int[1]);
        tt.frame();
        clickOpen(tt);                                       // the pointer rests on the pill
        tt.advance(Duration.ofMillis(8000));
        Check.isTrue(tt.texts().contains("取消"), "hovering holds the question open");
        tt.move(280, 110);
        tt.advance(Duration.ofMillis(6500));
        Check.isTrue(!tt.texts().contains("取消"), "leaving lets the clock run out");
    }

    public void testEscapeCancelsWhenTheQuestionHasFocus() {
        int[] cancels = { 0 };
        Tester tt = open(cancels);
        tt.frame();
        clickOpen(tt);
        tt.advance(Duration.ofMillis(300));                  // the confirm button auto-focused
        tt.key(jui.ui.Shortcut.of(jui.ui.Key.ESCAPE));
        tt.frame();
        Check.isTrue(!tt.texts().contains("取消"), "escape closes the question");
        Check.equal(1, cancels[0]);
    }

    public void testEnterConfirmsFromTheQuestion() {
        Tester tt = open(new int[1]);
        tt.frame();
        clickOpen(tt);
        tt.advance(Duration.ofMillis(300));                  // past the guard, confirm focused
        tt.key(jui.ui.Shortcut.of(jui.ui.Key.ENTER));
        tt.advance(Duration.ofMillis(150));
        Check.isTrue(tt.texts().contains("已清空"), "enter confirms");
    }

    public void testAnErrorResultAlsoDismissesAfterFiveSeconds() {
        Tester tt = Tester.of(c -> ConfirmMorph.confirmMorph(c, "清空", "trash", "确定要清空吗？",
                "确认", "取消", "清空中", "已清空", "没成功", "撤销",
                ConfirmMorph.Mode.PRESS, 1000, 3, true, false,
                ok -> ok.accept(false), null, null), 300, 120);
        tt.frame();
        clickOpen(tt);
        tt.advance(Duration.ofMillis(300));
        tt.click("kit-confirm-ok");
        tt.advance(Duration.ofMillis(150));
        Check.isTrue(tt.texts().contains("没成功"), "the error face shows");
        tt.move(280, 110);
        tt.advance(Duration.ofMillis(5500));
        Check.isTrue(!tt.texts().contains("没成功"), "the error result dismisses too");
    }
}
