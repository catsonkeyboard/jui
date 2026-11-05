package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.jspace.store.FileStore;
import jui.ui.Context;

import java.util.List;

import static jui.ui.Ui.*;

/** The pane's grid view: fixed cells in wrapping rows, virtualized row by row. */
final class IconGrid {
    private static final float CELL_W = 96, CELL_H = 116;
    private final Workspace ws;
    private final int pane;

    IconGrid(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model m = ws.model();
        Model.TabState t = m.panes[pane].tab();
        FileStore.DirSnap snap = ws.store().snap(t.dir);
        List<Entries.Row> rows = Entries.view(snap.entries(), ws.store()::snap, t.sort,
                ws.settings().foldersFirst, t.search, ws.settings().showHidden, t.expanded);
        int perRow = Math.max(1, (int) (t.paneWidth / CELL_W));
        int rowCount = (rows.size() + perRow - 1) / perRow;
        int pr = perRow;
        t.gridRows.keys(i -> i * pr);                            // anchor on the row's first entry

        box(c).fill().clip().children(() ->
                list(c, t.gridRows, rowCount, r ->
                        gridRow(c, t, rows, r * pr, Math.min(rows.size(), (r + 1) * pr))));
    }

    private void gridRow(Context c, Model.TabState t, List<Entries.Row> rows, int from, int to) {
        row(c).height(CELL_H).fillWidth().children(() -> {
            for (int i = from; i < to; i++) {
                cell(c, t, rows.get(i).entry());
            }
        });
    }

    private void cell(Context c, Model.TabState t, FileEntry e) {
        boolean sel = e.path().equals(t.selected);
        jui.ui.Element cellBtn = buttonBase(c).width(CELL_W).height(CELL_H).padding(6, 4)
                .label("cell-" + e.name())
                .children(() -> column(c).gap(6).alignItems(jui.ui.Align.CENTER).children(() -> {
                    box(c).size(48, 48).draw(FileIcons.draw(e.icon(), true));
                    text(c, Texts.fit(c, e.name(), 11, 400, CELL_W - 8)).fontSize(11)
                            .textColor(sel ? c.theme().accent() : c.theme().text());
                }));
        if (sel) cellBtn.border(1.5f, c.theme().accent()).radius(8);
        if (cellBtn.clicked()) t.selected = e.path();
        if (cellBtn.doubleClicked()) ws.openEntry(pane, e);
    }
}
