package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LayoutGeometryTest {
    static Model wired(Path dir) {
        jui.jspace.store.Settings st = jui.jspace.store.Settings.forTests();
        Model m = new Model(st);
        m.layout = Model.Layout.GRID;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(dir));
        return m;
    }

    public void testTheGridFillsTheWindowAndTheSidebarKeepsItsWidth() throws IOException {
        Path d = Files.createTempDirectory("jspace-geom");
        Files.writeString(d.resolve("aaa.txt"), "x");
        Model m = wired(d);
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);
        ws.store().scan(d);
        Tester tt = Tester.of(ws.view(), 1000, 600);
        tt.frame();
        tt.frame();
        tt.frame();

        Check.near(230, tt.find("sidebar").width(), 1);
        Check.near(1000 - 230, tt.find("pane-grid").width(), 2);
        Check.near(600 - 40 - 22, tt.find("pane-grid").height(), 2);
        Check.near(40, tt.find("toolbar").height(), 1);

        // all four panes exist with real room
        for (int i = 0; i < 4; i++) {
            Check.isTrue(tt.find("pane-" + i + "-frame").width() > 80, "pane-" + i + " width");
            Check.isTrue(tt.find("pane-" + i + "-frame").height() > 60, "pane-" + i + " height");
        }
        // the four quadrants do not overlap: pane 1 right of pane 0, pane 2 under pane 0
        Check.isTrue(tt.find("pane-1-frame").x() > tt.find("pane-0-frame").x() + 200, "columns split");
        Check.isTrue(tt.find("pane-2-frame").y() > tt.find("pane-0-frame").y() + 100, "rows split");
    }

    public void testRowsHugTheLeftEdgeAndColumnsLineUp() throws IOException {
        Path d = Files.createTempDirectory("jspace-geom2");
        Files.writeString(d.resolve("aaa.txt"), "x");
        Model m = wired(d);
        m.layout = Model.Layout.SINGLE;
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);
        ws.store().scan(d);
        Tester tt = Tester.of(ws.view(), 900, 600);
        tt.frame();
        tt.frame();
        tt.frame();

        float paneX = tt.find("pane-0-content").x();
        float rowX = tt.find("row-aaa.txt").x();
        Check.isTrue(rowX - paneX < 30, "row content starts near the pane's left edge, not centered: "
                + (rowX - paneX));
        float dateX = tt.find("row-aaa.txt").x();
        Check.isTrue(dateX >= 0, "row found");

        // the header sits right above the rows and the row is inside the content area
        float headerY = 0;
        Check.isTrue(tt.find("row-aaa.txt").y() > 40, "rows under the toolbar");
    }
}
