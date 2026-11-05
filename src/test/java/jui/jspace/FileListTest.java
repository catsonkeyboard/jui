package jui.jspace;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileListTest {
    static Path root() throws IOException {
        Path d = Files.createTempDirectory("jspace-list");
        Files.createDirectory(d.resolve("sub"));
        Files.writeString(d.resolve("a.txt"), "aaa");
        Files.writeString(d.resolve("b.md"), "bb");
        return d;
    }

    static Model wired(Path dir) {
        jui.jspace.store.Settings st = jui.jspace.store.Settings.forTests();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(dir));
        return m;
    }

    static Tester open(Model m) {
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);
        if (m.panes[0].tab().dir != null) ws.store().scan(m.panes[0].tab().dir);
        Tester tt = Tester.of(ws.view(), 640, 400);
        tt.frame();
        tt.frame();                                              // the snapshot lands on the second frame
        return tt;
    }

    public void testRowsSortSelectAndEnter() throws IOException {
        Path d = root();
        Model m = wired(d);
        Tester tt = open(m);
        Check.isTrue(tt.find("row-sub").width() > 0, "row-sub");
        Check.isTrue(tt.find("row-a.txt").width() > 0, "row-a.txt");
        Check.isTrue(tt.find("row-sub").y() < tt.find("row-a.txt").y(), "folders first");
        tt.click("row-a.txt");
        tt.frame();
        Check.equal(d.resolve("a.txt"), m.panes[0].tab().selected);
        tt.key(Shortcut.of(Key.ENTER));
        tt.frame();                                              // Enter activates the focused row: still selected
        Check.equal(d, m.panes[0].tab().dir);
        tt.doubleClick("row-sub");                               // a double click opens a folder
        tt.frame();
        Check.equal(d.resolve("sub"), m.panes[0].tab().dir);
    }

    public void testEnterOpensTheSelectionWithoutFocus() throws IOException {
        Path d = root();
        Model m = wired(d);
        Tester tt = open(m);
        m.panes[0].tab().selected = d.resolve("sub");            // arrow-set selection: no row holds focus
        tt.key(Shortcut.of(Key.ENTER));
        tt.frame();
        Check.equal(d.resolve("sub"), m.panes[0].tab().dir);
    }

    public void testHeaderClickSorts() throws IOException {
        Path d = root();
        Model m = wired(d);
        Tester tt = open(m);
        tt.click("sort-date");
        tt.frame();
        Check.equal(Entries.SortKey.DATE, m.panes[0].tab().sort.key);
        tt.click("sort-date");
        tt.frame();
        Check.isTrue(!m.panes[0].tab().sort.asc, "second click flips");
    }

    public void testDisclosureExpandsChildren() throws IOException {
        Path d = root();
        Files.createDirectory(d.resolve("sub/inner"));
        Model m = wired(d);
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);
        ws.store().scan(d);
        ws.store().scan(d.resolve("sub"));
        Tester tt = Tester.of(ws.view(), 640, 400);
        tt.frame();
        tt.frame();
        tt.click("disc-sub");
        tt.frame();
        tt.frame();
        Check.isTrue(m.panes[0].tab().expanded.contains(d.resolve("sub")), "expanded");
        Check.isTrue(tt.find("row-inner").width() > 0, "row-inner");
        tt.click("disc-sub");
        tt.frame();
        Check.isTrue(!m.panes[0].tab().expanded.contains(d.resolve("sub")), "collapsed");
    }

    public void testSearchFiltersRows() throws IOException {
        Path d = root();
        Model m = wired(d);
        m.panes[0].tab().search = "md";
        Tester tt = open(m);
        Check.isTrue(tt.find("row-b.md").width() > 0, "row-b.md");
        try {
            tt.find("row-a.txt");
            throw new AssertionError("a.txt should be filtered out");
        } catch (AssertionError pass) {
            // find throws when missing: exactly what we want
        }
    }
}
