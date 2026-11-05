package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.testing.Check;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class EntriesTest {
    static FileEntry f(String name, boolean dir, long size, long mtime) {
        Path p = Path.of("/t/" + name);
        return new FileEntry(name, p, dir, size, mtime,
                jui.jspace.store.KindMap.of(name, dir), FileEntry.IconType.of(name, dir));
    }

    public void testFoldersFirstThenName() {
        FileEntry[] es = { f("b.txt", false, 1, 1), f("D", true, -1, 1), f("a.txt", false, 1, 1), f("C", true, -1, 1) };
        Model m = new Model();
        List<Entries.Row> rows = Entries.view(es, p -> null, m.panes[0].tab().sort, true, "", false, Set.of());
        Check.equal("C", rows.get(0).entry().name());
        Check.equal("D", rows.get(1).entry().name());
        Check.equal("a.txt", rows.get(2).entry().name());
        Check.equal("b.txt", rows.get(3).entry().name());
    }

    public void testSortKeysAndDirection() {
        FileEntry[] es = { f("a", false, 30, 300), f("b", false, 10, 100), f("c", false, 20, 200) };
        Model.SortState s = new Model.SortState();
        s.key = Entries.SortKey.SIZE; s.asc = false;
        List<Entries.Row> rows = Entries.view(es, p -> null, s, false, "", false, Set.of());
        Check.equal("a", rows.get(0).entry().name());          // 30 first, descending
        s.asc = true;
        rows = Entries.view(es, p -> null, s, false, "", false, Set.of());
        Check.equal("b", rows.get(0).entry().name());          // 10 first, ascending
    }

    public void testSearchAndHidden() {
        FileEntry[] es = { f("Report.pdf", false, 1, 1), f(".secret", false, 1, 1), f("notes.txt", false, 1, 1) };
        Model m = new Model();
        List<Entries.Row> rows = Entries.view(es, p -> null, m.panes[0].tab().sort, true, "port", false, Set.of());
        Check.equal(1, rows.size());
        Check.equal("Report.pdf", rows.get(0).entry().name());
        rows = Entries.view(es, p -> null, m.panes[0].tab().sort, true, "", false, Set.of());
        Check.equal(2, rows.size());                            // .secret hidden by default
        rows = Entries.view(es, p -> null, m.panes[0].tab().sort, true, "", true, Set.of());
        Check.equal(3, rows.size());
    }

    public void testTreeFlatteningWithDepth() {
        FileEntry[] root = { f("a", true, -1, 1), f("z", false, 1, 1) };
        FileEntry[] child = { f("inner", false, 5, 1) };
        jui.jspace.store.FileStore.DirSnap childSnap =
                new jui.jspace.store.FileStore.DirSnap(jui.jspace.store.FileStore.State.READY, child, null);
        Path a = Path.of("/t/a");
        Model m = new Model();
        List<Entries.Row> rows = Entries.view(root, p -> p.equals(a) ? childSnap : null,
                m.panes[0].tab().sort, true, "", false, Set.of(a));
        Check.equal(3, rows.size());
        Check.equal(0, rows.get(0).depth());
        Check.equal(1, rows.get(1).depth());
        Check.equal("inner", rows.get(1).entry().name());
        Check.equal(0, rows.get(2).depth());
    }
}
