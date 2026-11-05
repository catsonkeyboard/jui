package jui.jspace;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ToolbarTest {
    static Model model() throws IOException {
        Path d = Files.createTempDirectory("jspace-tb");
        jui.jspace.store.Settings st = jui.jspace.store.Settings.forTests();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(d));
        return m;
    }

    static Tester open(Model m) {
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);
        return Tester.of(ws.view(), 900, 600);
    }

    public void testViewSwitchChangesTheTab() throws IOException {
        Model m = model();
        Tester tt = open(m);
        tt.click("view-grid");
        tt.frame();
        Check.equal(Model.ViewMode.GRID, m.panes[0].tab().view);
        tt.click("view-list");
        tt.frame();
        Check.equal(Model.ViewMode.LIST, m.panes[0].tab().view);
    }

    public void testLayoutSwitch() throws IOException {
        Model m = model();
        m.layout = Model.Layout.SINGLE;
        Tester tt = open(m);
        tt.click("layout-grid");
        tt.frame();
        Check.equal(Model.Layout.GRID, m.layout);
        Check.isTrue(tt.find("pane-3-frame").width() > 0, "pane-3-frame");
    }

    public void testSidebarToggles() throws IOException {
        Model m = model();
        Tester tt = open(m);
        Check.isTrue(m.sidebarOpen, "open to begin with");
        tt.click("toggle-sidebar");
        tt.frame();
        Check.isTrue(!m.sidebarOpen, "hidden");
        tt.click("toggle-sidebar");
        tt.frame();
        Check.isTrue(m.sidebarOpen, "back");
    }

    public void testSearchViaShortcutAndClears() throws IOException {
        Model m = model();
        Files.writeString(m.panes[0].tab().dir.resolve("keep.md"), "x");
        Files.writeString(m.panes[0].tab().dir.resolve("drop.txt"), "y");
        Tester tt = open(m);
        tt.key(Shortcut.cmd(Key.F));
        tt.type("keep");
        tt.frame();
        Check.equal("keep", m.panes[0].tab().search);
        tt.key(Shortcut.of(Key.ESCAPE));
        tt.frame();
        Check.equal("", m.panes[0].tab().search);
    }

    public void testActivePaneFollowsHover() throws IOException {
        Model m = model();
        m.layout = Model.Layout.GRID;
        Tester tt = open(m);
        tt.frame();
        tt.move(700, 500);                                       // somewhere in pane 3
        tt.frame();
        Check.equal(3, m.active);
    }
}
