package jui.examples;

import java.util.List;

import jui.testing.Check;
import jui.ui.Rect;
import jui.ui.Tester;

public final class CardDemoTest {
    public void testTheCardShowsEverything() {
        Tester tt = Tester.of(CardDemo::view, 760, 560);
        tt.frame();
        for (String s : List.of("j-ui 文字渲染", "Regular 常规", "Bold 粗体", "已完成", "Metal", "GraalVM 25", "确定 OK", "取消 Cancel")) {
            Check.isTrue(tt.hasText(s), "shows " + s);
        }
        Rect title = tt.find("j-ui 文字渲染");
        Check.near(65, title.x()); Check.near(59, title.y());  // outer padding 32 + border 1 + card padding
        Rect ok = tt.find("确定 OK");
        Check.isTrue(ok.y() + ok.height() <= 560 - 32 - 1 - 32 + 0.01f, "the buttons sit at the bottom inside the card");
    }
}
