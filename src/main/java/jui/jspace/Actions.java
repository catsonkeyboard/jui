package jui.jspace;

import jui.jspace.store.FileOps;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** The file operations as the UI drives them: menus, modals, the clipboard, refreshes. */
final class Actions {
    private final Workspace ws;
    final jui.ui.BoolRef deleteOpen = new jui.ui.BoolRef(false);
    final jui.ui.BoolRef clashOpen = new jui.ui.BoolRef(false);
    private Consumer<FileOps.Decision> clashAnswer;
    private String clashName;
    private Path pendingDelete;
    private long pendingCount;
    private volatile boolean pasteBusy;

    Actions(Workspace ws) { this.ws = ws; }

    boolean pasteBusy() { return pasteBusy; }

    String clashName() { return clashName; }

    Path pendingDelete() { return pendingDelete; }

    long pendingCount() { return pendingCount; }

    Model.TabState tab(int pane) { return ws.model().panes[pane].tab(); }

    void newFolder(int pane) {
        Path dir = tab(pane).dir;
        if (dir == null) return;
        FileOps.mkdir(dir, "未命名文件夹", ws.opsPublish(), err -> {
            note(err == null ? "已新建文件夹" : "新建失败：" + err);
            refresh(dir);
        });
    }

    void startRename(int pane) {
        Model.TabState t = tab(pane);
        if (t.selected != null) {
            t.renameNew = t.selected.getFileName().toString();
            ws.renameRef.set(t.renameNew);
        }
    }

    void submitRename(int pane, String newName) {
        Model.TabState t = tab(pane);
        Path from = t.selected;
        t.renameNew = null;
        if (from == null || newName == null || newName.isBlank()
                || newName.equals(from.getFileName().toString())) return;
        FileOps.rename(from, newName, ws.opsPublish(), err -> {
            if (err != null) note("重命名失败：" + err);
            refresh(from.getParent());
        });
    }

    void askDelete(int pane) {
        if (tab(pane).selected != null) {
            pendingDelete = tab(pane).selected;
            pendingCount = FileOps.countEntries(pendingDelete);  // for the modal's body
            deleteOpen.set(true);
        }
    }

    /** The delete modal's OK button lands here; count was shown in the modal's body. */
    void doDelete() {
        deleteOpen.set(false);
        Path p = pendingDelete;
        pendingDelete = null;
        if (p == null) return;
        FileOps.delete(p, ws.opsPublish(), err -> {
            note(err == null ? "已删除" : "删除失败：" + err);
            refresh(p.getParent());
        });
    }

    void cancelDelete() {
        deleteOpen.set(false);
        pendingDelete = null;
    }

    void copy(int pane, boolean cut) {
        Path sel = tab(pane).selected;
        if (sel != null) ws.model().clip = new Model.Clip(sel, cut);
    }

    void paste(int pane) {
        Model.Clip clip = ws.model().clip;
        Path dir = tab(pane).dir;
        if (clip == null || dir == null) return;
        pasteBusy = true;
        FileOps.paste(clip.path, clip.move, dir, ws.opsPublish(),
                (name, answer) -> {                              // on the main thread: open the modal
                    clashName = name;
                    clashAnswer = answer;
                    clashOpen.set(true);
                },
                err -> {
                    pasteBusy = false;
                    if (err != null) note(err);
                    refresh(clip.path.getParent());
                    refresh(dir);
                });
    }

    /** The clash modal's buttons land here. */
    void decideClash(FileOps.Decision d) {
        clashOpen.set(false);
        if (clashAnswer != null) {
            pasteBusy = false;
            Consumer<FileOps.Decision> a = clashAnswer;
            clashAnswer = null;
            a.accept(d);
        }
    }

    void note(String text) {
        ws.model().status.note = text;
        ws.model().status.noteAt = System.nanoTime();
    }

    /** Rescans every open tab's directory, plus the parents and expanded folders that hold changed. */
    void refresh(Path changed) {
        if (changed == null) return;
        Set<Path> want = new HashSet<>();
        for (Model.PaneState p : ws.model().panes) {
            for (Model.TabState t : p.tabs) {
                if (t.dir != null) want.add(t.dir);
                for (Path e : t.expanded) {
                    if (e.equals(changed) || changed.startsWith(e) || e.startsWith(changed)) want.add(e);
                }
            }
        }
        for (Path d : want) ws.store().scan(d);
    }
}
