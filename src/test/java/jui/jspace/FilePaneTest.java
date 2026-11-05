package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

import java.nio.file.Path;

public final class FilePaneTest {
    static final Path HOME = Path.of(System.getProperty("user.home"));

    static Model model() {
        Model m = new Model(jui.jspace.store.Settings.forTests());
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(HOME));
        m.panes[0].tabs.add(new Model.TabState(HOME.resolve("Downloads")));
        m.panes[0].current = 1;
        return m;
    }

    static Tester open(Model m) {
        if (m.layout == Model.Layout.GRID) m.layout = Model.Layout.SINGLE;   // one pane unless a test says
        return Tester.of(new Workspace(m, m.settings(), Runnable::run).view(), 800, 600);
    }

    public void testTabsShowClickAndClose() {
        Model m = model();
        Tester tt = open(m);
        Check.isTrue(tt.find("tab-0").width() > 0, "tab-0");
        Check.isTrue(tt.find("tab-1").width() > 0, "tab-1");
        tt.click("tab-0");
        tt.frame();
        Check.equal(0, m.panes[0].current);
        tt.click("tab-1");
        tt.frame();
        tt.click("tab-1-close");
        tt.frame();
        Check.equal(1, m.panes[0].tabs.size());
    }

    public void testNewTabButtonUsesTheSetting() {
        Model m = model();
        Tester tt = open(m);
        tt.click("new-tab");
        tt.frame();
        Check.equal(3, m.panes[0].tabs.size());
        Check.equal(HOME, m.panes[0].tabs.get(2).dir);          // NewTabAt.HOME
    }

    public void testBreadcrumbClicksNavigateAndBackForward() {
        Model m = model();
        m.panes[0].current = 0;
        m.panes[0].tab().dir = HOME.resolve("Downloads");       // already in Downloads
        Tester tt = open(m);
        Check.isTrue(tt.find("crumb-Downloads").width() > 0, "crumb-Downloads");
        tt.click("crumb-home");                                 // home: one step up
        tt.frame();
        Check.equal(HOME, m.panes[0].tab().dir);
        tt.click("nav-back");
        tt.frame();
        Check.equal(HOME.resolve("Downloads"), m.panes[0].tab().dir);
        tt.click("nav-forward");
        tt.frame();
        Check.equal(HOME, m.panes[0].tab().dir);
    }

    public void testActivePaneHasTheFrame() {
        Model m = model();
        m.layout = Model.Layout.DOUBLE;
        m.active = 1;
        Tester tt = open(m);
        Check.isTrue(tt.find("pane-1-frame").height() > 0, "pane-1-frame");
        Check.isTrue(tt.find("pane-0-frame").height() > 0, "pane-0-frame");
    }
}
