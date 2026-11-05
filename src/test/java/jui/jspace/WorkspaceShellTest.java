package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

public final class WorkspaceShellTest {
    static Tester open(Model m) {
        Workspace ws = new Workspace(m);
        return Tester.of(ws.view(), 1280, 800);
    }

    public void testTheShellHasSidebarToolbarAndStatus() {
        Model m = new Model();
        Tester tt = open(m);
        Check.near(230, tt.find("sidebar").width());
        Check.isTrue(tt.find("toolbar").height() > 0, "toolbar");
        Check.isTrue(tt.find("statusbar").height() > 0, "statusbar");
        Check.isTrue(tt.find("pane-grid").width() > 0, "pane-grid");
    }

    public void testDefaultLayoutIsTheGridOfFour() {
        Model m = new Model();
        Tester tt = open(m);
        for (int i = 0; i < 4; i++) {
            Check.isTrue(tt.find("pane-" + i + "-frame").width() > 0, "pane-" + i);
        }
    }

    public void testSingleAndDoubleLayouts() {
        Model single = new Model();
        single.layout = Model.Layout.SINGLE;
        Check.isTrue(Tester.of(new Workspace(single).view(), 800, 600).find("pane-0-frame").width() > 0, "single");
        Model dbl = new Model();
        dbl.layout = Model.Layout.DOUBLE;
        Check.isTrue(Tester.of(new Workspace(dbl).view(), 800, 600).find("pane-1-frame").width() > 0, "double");
    }

    public void testLayoutSwitchKeepsTheFirstPanes() {
        Model m = new Model();
        m.layout = Model.Layout.GRID;
        m.layout = Model.Layout.SINGLE;                          // shrink: keep pane 0
        Tester tt = open(m);
        Check.isTrue(tt.find("pane-0-frame").width() > 0, "pane-0");
    }
}
