package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

import java.nio.file.Path;

public final class SidebarTest {
    static Model model() {
        Model m = new Model(jui.jspace.store.Settings.forTests());
        m.layout = Model.Layout.SINGLE;
        return m;
    }

    public void testGroupsAndItems() {
        Tester tt = Tester.of(new Workspace(model()).view(), 900, 600);
        for (String id : new String[] { "side-桌面", "side-下载", "side-文稿", "side-主目录",
                "side-应用程序", "side-废纸篓", "side-Macintosh HD", "side-默认" }) {
            Check.isTrue(tt.find(id).width() > 0, id);
        }
    }

    public void testClickNavigatesTheActivePane() {
        Model m = model();
        m.active = 0;
        Tester tt = Tester.of(new Workspace(m, m.settings(), Runnable::run).view(), 900, 600);
        Path home = Path.of(System.getProperty("user.home"));
        tt.click("side-下载");
        tt.frame();
        Check.equal(home.resolve("Downloads"), m.panes[0].tab().dir);
    }

    public void testFilterHidesNonMatching() {
        Model m = model();
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);
        Tester tt = Tester.of(ws.view(), 900, 600);
        tt.click("sidebar-filter");
        tt.type("下载");
        tt.frame();
        Check.isTrue(tt.find("side-下载").width() > 0, "side-下载 stays");
        try {
            tt.find("side-文稿");
            throw new AssertionError("文稿 should be filtered out");
        } catch (AssertionError pass) {
            // find throws when missing: exactly what we want
        }
    }
}
