package jui.ui;

import java.util.List;

import jui.testing.Check;

import static jui.ui.Ui.column;
import static jui.ui.Ui.text;

public final class TesterTest {
    public void testFindsTextsAndTheirBoxes() {
        Tester tt = Tester.of(c -> column(c).padding(20).gap(8).children(() -> {
            text(c, "标题").fontSize(22); text(c, "正文"); }), 400, 300);
        tt.frame();
        Check.equal(List.of("标题", "正文"), tt.texts());
        Check.isTrue(tt.hasText("正文"), "has 正文");
        Check.near(20, tt.find("标题").x()); Check.near(20, tt.find("标题").y());
        Check.isTrue(tt.find("正文").y() > tt.find("标题").y() + 8, "below the title and the gap");
        Check.isTrue(tt.instances() > 0, "painted");
    }

    public void testInvalidateAsksForAFrame() {
        Tester tt = Tester.of(c -> { text(c, "x"); c.invalidate(); }, 100, 100);
        tt.frame();
        Check.isTrue(tt.framePending(), "invalidate during the build asks for another frame");
    }

    public void testFindFailsClearly() {
        Tester tt = Tester.of(c -> text(c, "x"), 100, 100);
        tt.frame();
        Check.fails(AssertionError.class, () -> tt.find("missing"));
    }
}
