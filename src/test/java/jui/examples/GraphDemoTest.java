package jui.examples;

import java.time.Duration;
import jui.testing.Check;
import jui.ui.Tester;

public final class GraphDemoTest {
    public void testPublicPainterDrawsSixNodesSevenEdgesAndAllLabels() {
        GraphDemo demo = new GraphDemo();
        Tester t = Tester.of(demo::view, 760, 480);
        try {
            t.frame();
            Check.isTrue(t.framePending(), "entrance requests animation frames");
            t.advance(Duration.ofMillis(500));
            int circles = 0, edges = 0, glyphs = 0;
            for (int i = 0; i < t.instances(); i++) {
                float[] p = t.scene().instance(i);
                if (p[18] == 3) edges++;
                else if (p[18] == 2) glyphs++;
                else if (p[18] == 0 && p[4] == p[2] / 2 && p[2] > 0) circles++;
            }
            Check.equal(6, circles); Check.equal(7, edges); Check.equal(24, glyphs);
            Check.isTrue(!t.framePending(), "animation stops when settled");
        } finally { t.close(); }
    }

    public void testHoverEnlargesNodeAndHighlightsOnlyItsAdjacentEdges() {
        GraphDemo demo = new GraphDemo();
        Tester t = Tester.of(demo::view, 760, 480);
        try {
            t.frame(); t.advance(Duration.ofMillis(500));
            float[] edge = firstEdge(t);
            float radius = firstCircleWidth(t);
            t.hover("graph-node-0"); t.frame();
            float[] highlighted = firstEdge(t);
            Check.isTrue(edge[8] != highlighted[8] || edge[9] != highlighted[9] || edge[10] != highlighted[10], "incident edge changes color");
            Check.isTrue(firstCircleWidth(t) > radius, "hover enlarges the node");
            int active = 0;
            for (int i = 0; i < t.instances(); i++) {
                float[] p = t.scene().instance(i);
                if (p[18] == 3 && p[8] == highlighted[8] && p[9] == highlighted[9] && p[10] == highlighted[10]) active++;
            }
            Check.equal(2, active);
            t.move(750, 470); t.frame();
            Check.near(radius, firstCircleWidth(t));
        } finally { t.close(); }
    }

    private static float[] firstEdge(Tester t) {
        for (int i = 0; i < t.instances(); i++) { float[] p = t.scene().instance(i); if (p[18] == 3) return p; }
        throw new AssertionError("no graph edges");
    }
    private static float firstCircleWidth(Tester t) {
        for (int i = 0; i < t.instances(); i++) {
            float[] p = t.scene().instance(i);
            if (p[18] == 0 && p[4] == p[2] / 2 && p[2] > 0) return p[2];
        }
        throw new AssertionError("no graph nodes");
    }
}
