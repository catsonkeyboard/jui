package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

import java.nio.file.Files;
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

    /** Clicks until the state settles: background scans make one click's settle window unreliable. */
    static void clickUntil(Tester tt, String what, java.util.function.BooleanSupplier done)
            throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!done.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("click on " + what + " never took effect");
            tt.click(what);
            tt.frame();
            Thread.sleep(5);
        }
    }

    public void testTabsShowClickAndClose() throws Exception {
        Model m = model();
        Tester tt = open(m);
        Check.isTrue(tt.find("tab-0").width() > 0, "tab-0");
        Check.isTrue(tt.find("tab-1").width() > 0, "tab-1");
        clickUntil(tt, "tab-0", () -> m.panes[0].current == 0);
        clickUntil(tt, "tab-1", () -> m.panes[0].current == 1);
        clickUntil(tt, "tab-1-close", () -> m.panes[0].tabs.size() == 1);
        Check.equal(1, m.panes[0].tabs.size());
    }

    public void testNewTabButtonUsesTheSetting() throws Exception {
        Model m = model();
        Tester tt = open(m);
        clickUntil(tt, "new-tab", () -> m.panes[0].tabs.size() == 3);
        Check.equal(3, m.panes[0].tabs.size());
        Check.equal(HOME, m.panes[0].tabs.get(2).dir);          // NewTabAt.HOME
    }

    public void testBreadcrumbClicksNavigateAndBackForward() throws Exception {
        Model m = model();
        m.panes[0].current = 0;
        m.panes[0].tab().dir = HOME.resolve("Downloads");       // already in Downloads
        Tester tt = open(m);
        Check.isTrue(tt.find("crumb-Downloads").width() > 0, "crumb-Downloads");
        clickUntil(tt, "crumb-home", () -> HOME.equals(m.panes[0].tab().dir));
        clickUntil(tt, "nav-back", () -> HOME.resolve("Downloads").equals(m.panes[0].tab().dir));
        clickUntil(tt, "nav-forward", () -> HOME.equals(m.panes[0].tab().dir));
    }

    public void testTheConstructorScansTheOpenTabs() throws Exception {
        Model m = new Model(jui.jspace.store.Settings.forTests());
        Path d = java.nio.file.Files.createTempDirectory("jspace-ctor-scan");
        Files.writeString(d.resolve("hello.txt"), "x");
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(d));
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);   // no explicit scan here
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (ws.store().snap(d).state() == jui.jspace.store.FileStore.State.LOADING) {
            if (System.nanoTime() > deadline) throw new AssertionError("constructor never scanned " + d);
            Thread.sleep(5);
        }
        Check.equal(jui.jspace.store.FileStore.State.READY, ws.store().snap(d).state());
    }

    public void testActivePaneHasTheFrame() throws Exception {
        Model m = model();
        m.layout = Model.Layout.DOUBLE;
        m.active = 1;
        Tester tt = open(m);
        Check.isTrue(tt.find("pane-1-frame").height() > 0, "pane-1-frame");
        Check.isTrue(tt.find("pane-0-frame").height() > 0, "pane-0-frame");
    }
}
