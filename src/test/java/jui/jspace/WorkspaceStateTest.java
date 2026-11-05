package jui.jspace;

import jui.testing.Check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspaceStateTest {
    public void testRoundTrip() throws IOException {
        Path f = Files.createTempFile("jspace-ws", ".properties");
        jui.jspace.store.Settings st = jui.jspace.store.Settings.forTests();
        Model m = new Model(st);
        m.layout = Model.Layout.DOUBLE;
        m.active = 1;
        m.panes[0].tab().view = Model.ViewMode.GRID;
        WorkspaceState.save(m, f);
        Model back = new Model(st);
        WorkspaceState.apply(back, f);
        Check.equal(Model.Layout.DOUBLE, back.layout);
        Check.equal(1, back.active);
        Check.equal(Model.ViewMode.GRID, back.panes[0].tab().view);
        Check.equal(m.panes[0].tab().dir, back.panes[0].tab().dir);
    }

    public void testMissingFileLeavesTheModelAlone() {
        Model m = new Model(jui.jspace.store.Settings.forTests());
        Path home = m.panes[0].tab().dir;
        WorkspaceState.apply(m, Path.of("/no/such/workspace.properties"));
        Check.equal(home, m.panes[0].tab().dir);
        Check.equal(Model.Layout.GRID, m.layout);
    }
}
