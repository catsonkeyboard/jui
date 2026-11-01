package jui.examples;

import java.util.Arrays;
import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Painter;
import jui.ui.Theme;
import static jui.ui.Ui.canvas;

/** M6 Painter acceptance: a small layered note graph, built only from public APIs. */
public final class GraphDemo {
    private static final String[] LABELS = {"笔记", "项目", "阅读", "设计", "想法", "归档"};
    private static final float[] X = {90, 300, 300, 550, 550, 90};
    private static final float[] Y = {150, 125, 285, 180, 350, 350};
    private static final int[][] EDGES = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {2, 4}, {5, 2}, {5, 4}};
    private final Painter.Draw[] nodes = new Painter.Draw[LABELS.length];
    private final Painter.Draw graph = this::drawGraph;
    private Theme theme;
    private int hovered = -1;
    private long began = -1;
    private float progress;

    public GraphDemo() {
        for (int i = 0; i < nodes.length; i++) {
            int index = i;
            nodes[i] = (p, bounds) -> {
                float radius = (hovered == index ? 21 : 16) * (.35f + .65f * progress);
                p.circle(50, 22, radius, hovered == index ? theme.accent() : theme.textMuted());
                p.text(LABELS[index], 36, 52, 14, 400, theme.text(), 100);
            };
        }
    }

    public void view(Context c) {
        theme = c.theme();
        if (began < 0) began = c.now();
        progress = Math.min(1, Math.max(0, (c.now() - began) / 280_000_000f));
        hovered = -1;
        canvas(c).fill().background(theme.background()).draw(graph).children(() -> {
            for (int i = 0; i < nodes.length; i++) {
                Element node = canvas(c.key(i)).absolute().left(X[i] - 50).top(Y[i] - 22)
                        .size(100, 76).label("graph-node-" + i).draw(nodes[i]);
                if (node.hovered()) hovered = i;
            }
        });
    }

    private void drawGraph(Painter p, jui.ui.Rect bounds) {
        p.text("知识图谱", 24, 20, 24, 700, theme.text(), 200);
        p.text("悬停节点查看关联", 24, 58, 13, 400, theme.textMuted(), 200);
        for (int[] edge : EDGES) {
            boolean active = hovered == edge[0] || hovered == edge[1];
            Color color = active ? theme.accent() : theme.border();
            p.line(X[edge[0]], Y[edge[0]], X[edge[1]], Y[edge[1]], active ? 2 : 1, color);
        }
        if (progress < 1) p.animationFrame();
    }

    public static void main(String[] args) {
        GraphDemo demo = new GraphDemo();
        App.run(() -> {
            Window w = Window.open(WindowOptions.of("知识图谱").size(760, 480).content(demo::view));
            if (Arrays.asList(args).contains("--print-window")) System.out.println("window=" + w.windowNumber());
            if (Arrays.asList(args).contains("--quit-after-first-frame"))
                w.onFirstFrame(done -> System.exit(done > 0 ? 0 : 1));
        });
    }
}
