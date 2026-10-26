package jui.examples;

import jui.testing.Check;
import jui.ui.Tester;
import jui.ui.TextRef;

public final class FilesDemoTest {
    public void testTheSearchBoxFiltersTheList() {
        Object[] parts = FilesDemo.forTools();
        Tester tt = Tester.of((jui.ui.View) parts[0], 360, 640);
        tt.click("搜索笔记");
        tt.type("会议纪要");
        for (String s : tt.texts()) {
            if (s.startsWith("笔记 ")) Check.isTrue(s.contains("会议纪要"), "only matching titles: " + s);
        }
        Check.equal("会议纪要", ((TextRef) parts[2]).get());
        tt.type("不存在的词");
        Check.isTrue(tt.hasText("没有匹配的笔记"), "the empty state");
    }

    public void testPinyinCompositionDoesNotFilterUntilCommitted() {
        Object[] parts = FilesDemo.forTools();
        Tester tt = Tester.of((jui.ui.View) parts[0], 360, 640);
        tt.click("搜索笔记");
        tt.compose("huiyi", 5);
        Check.isTrue(tt.hasText("10000 篇笔记"), "a composition does not filter");
        tt.type("会议");
        Check.isTrue(!tt.hasText("10000 篇笔记"), "the committed text does");
    }
}
