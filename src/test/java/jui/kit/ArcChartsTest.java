package jui.kit;

import jui.testing.Check;
import jui.ui.BoolRef;
import jui.ui.Tester;

import java.time.Duration;

import static jui.ui.Ui.*;

public final class ArcChartsTest {

    public void testFunnelRadarSplitSunburstSankeyRender() {
        int[] base = new int[1];
        Tester plain = Tester.of(c -> box(c).size(320, 200), 320, 200);
        plain.frame();
        base[0] = plain.instances();

        Tester tt = Tester.of(c -> column(c).gap(8).children(() -> {
            Charts.funnelChart(c, new String[]{"访问", "注册", "付费"},
                    new float[]{800, 300, 90}, 280, 120);
            Charts.radarChart(c, new String[]{"速度", "稳定", "成本"},
                    new float[]{80, 60, 40}, 100, 130);
            Charts.splitBar(c, new String[]{"桌面", "移动"}, new float[]{60, 40}, 260, 18);
            Charts.sunburstChart(c, new String[]{"前端", "后端"}, new float[]{60, 40}, 110);
            Charts.sankeyFlow(c, new String[]{"上报"}, new String[]{"网关", "落库"},
                    new float[][]{{500, 200}}, 300, 110);
        }), 340, 700);
        tt.frame();
        tt.frame();
        Check.isTrue(tt.instances() > base[0] + 40, "the five charts paint their plots");
        for (String label : new String[] {"kit-chart-funnel", "kit-chart-radar",
                "kit-chart-splitbar", "kit-chart-sunburst", "kit-chart-sankey"}) {
            Check.isTrue(tt.find(label).width() > 0, label);
        }
    }

    public void testUndoToastRunsTheCommitOnExpiry() {
        BoolRef alive = new BoolRef(false);
        int[] commits = { 0 }, undos = { 0 };
        Tester tt = Tester.of(c -> Toast.undoToast(c, "已删除 3 个文件", "此操作不可自动恢复",
                Duration.ofMillis(600), () -> undos[0]++, () -> commits[0]++, alive), 400, 120);
        tt.frame();
        Check.isTrue(tt.texts().contains("已删除 3 个文件"), "toast visible");
        Check.isTrue(tt.texts().contains("撤销"), "the undo affordance shows");
        tt.advance(Duration.ofMillis(900));                  // expiry with no undo
        Check.isTrue(commits[0] == 1, "the commit ran once on expiry");
        Check.equal(0, undos[0]);
        Check.isTrue(!alive.get(), "the toast is gone");
    }

    public void testUndoToastUndoCancelsTheCommit() {
        BoolRef alive = new BoolRef(false);
        int[] commits = { 0 }, undos = { 0 };
        Tester tt = Tester.of(c -> Toast.undoToast(c, "已删除 3 个文件", "",
                Duration.ofSeconds(10), () -> undos[0]++, () -> commits[0]++, alive), 400, 120);
        tt.frame();
        tt.click("kit-undo-action");
        tt.frame();
        Check.equal(1, undos[0]);
        Check.equal(0, commits[0]);
        Check.isTrue(!alive.get(), "gone after undo");
        tt.advance(Duration.ofSeconds(12));
        Check.equal(0, commits[0]);                          // expiry never fires after undo
    }
}
