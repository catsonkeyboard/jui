package jui.jspace;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class KeysTest {
    static Model tree() throws IOException {
        Path d = Files.createTempDirectory("jspace-keys");
        Files.createDirectories(d.resolve("a/b"));
        Files.writeString(d.resolve("a/b/leaf.txt"), "x");
        Files.writeString(d.resolve("top.txt"), "x");
        jui.jspace.store.Settings st = new jui.jspace.store.Settings();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(d));
        return m;
    }

    static Tester open(Model m) throws InterruptedException {
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);
        ws.store().scan(m.panes[0].tab().dir);
        ws.store().scan(m.panes[0].tab().dir.resolve("a"));
        ws.store().scan(m.panes[0].tab().dir.resolve("a/b"));
        Tester tt = Tester.of(ws.view(), 640, 400);
        tt.frame();
        Thread.sleep(60);                                        // the DIRECT scans land inline-ish
        tt.frame();
        return tt;
    }

    public void testRightExpandsThenEntersLeftCollapsesThenJumpsUp() throws Exception {
        Model m = tree();
        Path root = m.panes[0].tab().dir;
        m.panes[0].tab().selected = root.resolve("a");
        Tester tt = open(m);
        tt.key(Shortcut.of(Key.RIGHT));                          // expand
        tt.frame();
        Check.isTrue(m.panes[0].tab().expanded.contains(root.resolve("a")), "expanded");
        tt.key(Shortcut.of(Key.RIGHT));                          // expanded: enter
        tt.frame();
        Check.equal(root.resolve("a"), m.panes[0].tab().dir);

        // back at the root listing, a still expanded
        m.panes[0].tab().dir = root;
        m.panes[0].tab().back.clear();
        m.panes[0].tab().forward.clear();
        m.panes[0].tab().selected = root.resolve("a");
        tt.frame();
        tt.key(Shortcut.of(Key.LEFT));                           // a selected and open: collapse
        tt.frame();
        Check.isTrue(!m.panes[0].tab().expanded.contains(root.resolve("a")), "collapsed");

        m.panes[0].tab().expanded.add(root.resolve("a"));        // open again, now a child is selected
        m.panes[0].tab().selected = root.resolve("a/b");
        tt.frame();
        tt.key(Shortcut.of(Key.LEFT));                           // b at depth 1: jump to a's row
        tt.frame();
        Check.equal(root.resolve("a"), m.panes[0].tab().selected);
    }

    public void testLeftAtTheRootGoesUp() throws Exception {
        Model m = tree();
        Path root = m.panes[0].tab().dir;
        m.panes[0].tab().selected = root.resolve("top.txt");     // depth 0: LEFT goes to the parent dir
        Tester tt = open(m);
        tt.key(Shortcut.of(Key.LEFT));
        tt.frame();
        Check.equal(root.getParent(), m.panes[0].tab().dir);
    }

    public void testGridArrowsMoveByPerRow() throws Exception {
        Model m = tree();
        m.panes[0].tab().view = Model.ViewMode.GRID;
        Tester tt = open(m);
        tt.frame();
        tt.frame();                                              // paneWidth lands after the first
        tt.key(Shortcut.of(Key.DOWN));
        tt.frame();
        Check.isTrue(m.panes[0].tab().selected != null, "something selected");
        Path first = m.panes[0].tab().selected;
        tt.key(Shortcut.of(Key.DOWN));
        tt.frame();
        Check.isTrue(!first.equals(m.panes[0].tab().selected), "moved down");
    }
}
