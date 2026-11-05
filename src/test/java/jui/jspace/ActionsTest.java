package jui.jspace;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ActionsTest {
    static Model wired() throws IOException {
        Path d = Files.createTempDirectory("jspace-act");
        Files.writeString(d.resolve("f.txt"), "x");
        jui.jspace.store.Settings st = jui.jspace.store.Settings.forTests();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(d));
        return m;
    }

    static Workspace open(Model m) {
        Workspace ws = new Workspace(m, m.settings(), Runnable::run);
        ws.store().scan(m.panes[0].tab().dir);
        return ws;
    }

    /** Ops run on virtual threads with a direct publish; wait a bounded while for the disk. */
    static void until(String what, java.util.function.BooleanSupplier cond) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out: " + what);
            Thread.sleep(5);
        }
    }

    public void testNewFolderMakesOne() throws Exception {
        Model m = wired();
        Workspace ws = open(m);
        ws.newFolder(0);
        until("folder made", () -> Files.isDirectory(m.panes[0].tab().dir.resolve("未命名文件夹")));
    }

    public void testDeleteAsksThenDeletes() throws Exception {
        Model m = wired();
        Workspace ws = open(m);
        Path f = m.panes[0].tab().dir.resolve("f.txt");
        m.panes[0].tab().selected = f;
        ws.askDelete(0);
        Check.isTrue(ws.actions().deleteOpen.get(), "the modal is up");
        Tester tt = Tester.of(ws.view(), 700, 500);
        tt.frame();
        tt.click("confirm-delete-ok");
        tt.frame();
        until("deleted", () -> !Files.exists(f));
        Check.isTrue(!Files.exists(f), "gone");
    }

    public void testCopyPasteAcrossPanes() throws Exception {
        Model m = wired();
        Path other = Files.createTempDirectory("jspace-other");
        m.layout = Model.Layout.DOUBLE;
        m.panes[1].tabs.clear();
        m.panes[1].tabs.add(new Model.TabState(other));
        Workspace ws = open(m);
        ws.store().scan(other);
        m.active = 0;
        m.panes[0].tab().selected = m.panes[0].tab().dir.resolve("f.txt");
        ws.copy(0, false);
        m.active = 1;
        ws.paste(1);
        until("copied", () -> Files.exists(other.resolve("f.txt")));
        Check.isTrue(Files.exists(m.panes[0].tab().dir.resolve("f.txt")), "a copy kept it");
    }

    public void testConflictGoesThroughTheModal() throws Exception {
        Model m = wired();
        Path other = Files.createTempDirectory("jspace-conf");
        Files.writeString(other.resolve("f.txt"), "old");
        m.layout = Model.Layout.DOUBLE;
        m.panes[1].tabs.clear();
        m.panes[1].tabs.add(new Model.TabState(other));
        Workspace ws = open(m);
        ws.store().scan(other);
        m.active = 0;
        m.panes[0].tab().selected = m.panes[0].tab().dir.resolve("f.txt");
        ws.copy(0, false);
        m.active = 1;
        ws.paste(1);                                             // clash: the modal opens, paste waits
        until("the clash modal is up", () -> ws.actions().clashOpen.get());
        Tester tt = Tester.of(ws.view(), 700, 500);
        tt.frame();
        tt.click("confirm-overwrite-skip");
        tt.frame();
        until("settled", () -> !ws.actions().pasteBusy());
        Check.equal("old", Files.readString(other.resolve("f.txt")));   // untouched
    }

    static String readSafe(Path p) {
        try { return Files.readString(p); } catch (IOException e) { return "?"; }
    }

    public void testRenameInlineInput() throws Exception {
        Model m = wired();
        Workspace ws = open(m);
        m.panes[0].tab().selected = m.panes[0].tab().dir.resolve("f.txt");
        ws.startRename(0);
        Check.equal("f.txt", m.panes[0].tab().renameNew);
        Tester tt = Tester.of(ws.view(), 700, 500);
        tt.frame();
        Check.isTrue(tt.find("rename-input").width() > 0, "rename-input");
        tt.key(Shortcut.of(Key.ENTER));                          // same name: nothing breaks
        tt.frame();
        Check.isTrue(Files.exists(m.panes[0].tab().dir.resolve("f.txt")), "still there");
        Check.isTrue(m.panes[0].tab().renameNew == null, "input closed");
    }
}
