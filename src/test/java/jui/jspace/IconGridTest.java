package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IconGridTest {
    static Model grid(Path dir) throws IOException {
        for (int i = 0; i < 7; i++) Files.writeString(dir.resolve("f" + i + ".txt"), "x");
        jui.jspace.store.Settings st = new jui.jspace.store.Settings();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(dir));
        m.panes[0].tab().view = Model.ViewMode.GRID;
        return m;
    }

    static Tester open(Model m) {
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);
        ws.store().scan(m.panes[0].tab().dir);
        Tester tt = Tester.of(ws.view(), 640, 400);
        tt.frame();
        tt.frame();                                              // paneWidth lands after the first frame
        tt.frame();
        return tt;
    }

    public void testCellsLayOutInRows() throws IOException {
        Model m = grid(Files.createTempDirectory("jspace-grid"));
        Tester tt = open(m);
        Check.isTrue(tt.find("cell-f0.txt").width() > 0, "cell-f0");
        float y0 = tt.find("cell-f0.txt").y();
        float y1 = tt.find("cell-f1.txt").y();
        Check.isTrue(y0 == y1, "several cells per row at 640 DIP");
    }

    public void testCellSelects() throws IOException {
        Model m = grid(Files.createTempDirectory("jspace-grid"));
        Tester tt = open(m);
        tt.click("cell-f3.txt");
        tt.frame();
        Check.equal(m.panes[0].tab().dir.resolve("f3.txt"), m.panes[0].tab().selected);
    }

    public void testEveryIconTypeDraws() {
        for (jui.jspace.store.FileEntry.IconType t : jui.jspace.store.FileEntry.IconType.values()) {
            Check.isTrue(FileIcons.draw(t, true) != null, t.name());
            Check.isTrue(FileIcons.draw(t, false) != null, t.name());
        }
        for (String kind : new String[] { "home", "desktop", "downloads", "docs", "pictures",
                "music", "movies", "apps", "trash", "disk", "cloud", "tag" }) {
            Check.isTrue(FileIcons.mini(kind, jui.ui.Color.rgb(0xFFFFFF)) != null, kind);
        }
    }
}
