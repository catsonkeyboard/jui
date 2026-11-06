package jui.kit;

import jui.testing.Check;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.IntRef;
import jui.ui.TextRef;
import jui.ui.Tester;

import static jui.ui.Ui.*;

public final class KitAdvancedTest {
    public void testTreeExpandsAndCollapses() {
        TextRef expanded = new TextRef("");
        Tester tt = Tester.of(c -> Tree.tree(c, new Tree.Node[] {
                Tree.node("src",
                        Tree.node("main"),
                        Tree.leaf("README.md", () -> text(c, "readme contents"))),
                Tree.node("docs"),
        }, expanded), 260, 200);
        tt.frame();
        Check.isTrue(!tt.texts().contains("main"), "collapsed at first");
        tt.click("kit-tree-src");
        tt.frame();
        Check.isTrue(tt.texts().contains("main"), "expanded shows child");
        Check.isTrue(tt.texts().contains("README.md"), "leaf child shows");
        tt.click("kit-tree-src");
        tt.frame();
        Check.isTrue(!tt.texts().contains("main"), "collapsed again");
    }

    public void testDataTableSorts() {
        IntRef sortCol = new IntRef(-1);
        BoolRef asc = new BoolRef(true);
        String[][] rows = { {"b", "2"}, {"a", "3"}, {"c", "1"} };
        Tester tt = Tester.of(c -> DataTable.table(c, new DataTable.Column[] {
                DataTable.col("名称", 80), DataTable.col("数量", 60),
        }, rows, sortCol, asc), 240, 160);
        tt.frame();
        tt.click("kit-table-名称");
        tt.frame();
        Check.equal(0, sortCol.get());
        Check.isTrue(tt.texts().contains("a"), "rows render");
        tt.click("kit-table-名称");
        tt.frame();
        Check.isTrue(!asc.get(), "second click flips direction");
    }

    public void testCarouselPagesAndDots() {
        IntRef page = new IntRef(0);
        Tester tt = Tester.of(c -> Carousel.carousel(c, page, 3,
                p -> text(c, "第 " + (p + 1) + " 页")), 280, 120);
        tt.frame();
        Check.isTrue(tt.texts().contains("第 1 页"), "page 1 shows");
        tt.click("kit-carousel-next");
        tt.frame();
        Check.isTrue(tt.texts().contains("第 2 页"), "next page shows");
        tt.click("kit-carousel-dot-0");
        tt.frame();
        Check.isTrue(tt.texts().contains("第 1 页"), "dot jumps back");
    }

    public void testCommandPaletteFiltersAndRuns() {
        BoolRef open = new BoolRef(true);
        TextRef query = new TextRef("");
        IntRef picked = new IntRef(-1);
        Tester tt = Tester.of(c -> Command.palette(c, "命令", open, query, picked::set,
                Command.cmd("新建文件"),
                Command.cmd("关闭窗口")), 400, 300);
        tt.frame();
        open.set(true);                                                  // as the binding would
        tt.frame();
        tt.frame();
        tt.click("kit-command-input");
        tt.type("新建");
        tt.frame();
        Check.isTrue(tt.find("kit-command-新建文件").width() > 0, "matched command");
        Check.isTrue(!tt.texts().contains("关闭窗口"), "others filtered");
        tt.click("kit-command-新建文件");
        tt.frame();
        Check.equal(0, picked.get());
        Check.isTrue(!open.get(), "palette closed");
    }

    public void testDockActivates() {
        IntRef active = new IntRef(0);
        Tester tt = Tester.of(c -> Dock.dock(c, active, "house", "gearshape", "bell"), 260, 90);
        tt.frame();
        tt.click("kit-dock-gearshape");
        tt.frame();
        Check.equal(1, active.get());
    }

    public void testSettingsPageRenders() {
        BoolRef on = new BoolRef(true);
        Tester tt = Tester.of(c -> Settings.page(c, "偏好",
                Settings.line("显示隐藏文件", () -> jui.ui.Ui.toggle(c, on, "")),
                Settings.line("语言", () -> Label.label(c, "简体中文", Kit.Size.SMALL))),
                300, 180);
        tt.frame();
        Check.isTrue(tt.find("kit-set-显示隐藏文件").width() > 0, "settings row");
    }

    public void testChartAndTabsRender() {
        IntRef tab = new IntRef(0);
        Tester tt = Tester.of(c -> column(c).padding(10).gap(10).children(() -> {
            Chart.chart(c, Chart.Kind.LINE, new double[] { 1, 3, 2, 5, 4 }, 200, 100);
            Tabs.tabs(c, tab, "全部", "进行中");
        }), 280, 220);
        tt.frame();
        Check.isTrue(tt.find("kit-chart").width() == 200, "chart box");
        Check.isTrue(tt.find("全部").width() > 0, "tab titles render");
    }

    public void testDiffWalkAndTints() {
        java.util.List<Diff.Row> rows = Diff.lines(
                new String[] {"a", "b", "c"},
                new String[] {"a", "B", "b", "c"});
        Check.equal(4, rows.size());                                   // insert B, keep b and c
        Check.equal(Diff.Kind.SAME, rows.get(0).kind());
        Check.equal(Diff.Kind.ADDED, rows.get(1).kind());
        Check.equal("B", rows.get(1).text());
        Check.equal(Diff.Kind.SAME, rows.get(2).kind());
        Check.equal("b", rows.get(2).text());
        Check.equal(Diff.Kind.SAME, rows.get(3).kind());

        java.util.List<Diff.Row> gone = Diff.lines(
                new String[] {"x"}, new String[] {});
        Check.equal(1, gone.size());
        Check.equal(Diff.Kind.REMOVED, gone.get(0).kind());

        Tester tt = Tester.of(c -> Diff.diff(c, rows), 260, 160);
        tt.frame();
        Check.isTrue(tt.find("kit-diff").width() > 0, "diff renders");
    }

    public void testSpeechToggleAndTranscript() {
        BoolRef listening = new BoolRef(false);
        TextRef transcript = new TextRef("");
        transcript.set("识别结果");
        Tester tt = Tester.of(c -> Speech.speech(c, listening, transcript), 260, 100);
        tt.frame();
        Check.isTrue(tt.texts().contains("识别结果"), "transcript shown");
        Check.isTrue(tt.texts().contains("点击说话"), "idle hint");
        tt.click("kit-speech-toggle");
        tt.frame();
        Check.isTrue(tt.texts().contains("正在聆听…"), "listening hint");
        tt.click("kit-speech-toggle");
        tt.frame();
        Check.isTrue(tt.texts().contains("点击说话"), "back to idle");
    }

    public void testVirtualListAndRootViaApp() {
        IntRef page = new IntRef(0);
        jui.ui.ListState state = new jui.ui.ListState();
        Tester tt = Tester.of(c -> Containers.list(c, state, 2000,
                i -> text(c, "row " + i)), 200, 120);
        tt.frame();
        Check.isTrue(tt.texts().contains("row 5"), "virtualized rows render");
    }
}
