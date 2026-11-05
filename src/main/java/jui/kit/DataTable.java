package jui.kit;

import java.util.Arrays;
import java.util.Comparator;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's data table: named sortable columns over rows of cells, sorted by
 * the clicked header, after gpui-kit's DataTable (virtualization comes free
 * by feeding the row builder to the framework's list).
 */
public final class DataTable {
    /** One column: header text and width. */
    public record Column(String name, float width) {}

    public static Column col(String name, float width) {
        return new Column(name, width);
    }

    /** Sorts by column, ascending flag live in refs the caller owns. */
    public static Element table(Context c, Column[] columns, String[][] rows,
                                jui.ui.IntRef sortCol, jui.ui.BoolRef sortAsc) {
        return column(c).fillWidth().children(() -> {
            header(c, columns, sortCol, sortAsc);
            String[][] sorted = sorted(rows, sortCol.get(), sortAsc.get());
            for (String[] rowCells : sorted) {
                rowOf(c, columns, rowCells);
            }
        });
    }

    private static void header(Context c, Column[] columns, jui.ui.IntRef sortCol, jui.ui.BoolRef asc) {
        row(c).fillWidth().height(24).background(c.theme().surface()).children(() -> {
            for (int i = 0; i < columns.length; i++) {
                int idx = i;
                boolean on = sortCol.get() == idx;
                Element h = buttonBase(c).width(columns[idx].width()).height(24).padding(0, 6)
                        .label("kit-table-" + columns[idx].name())
                        .justify(Justify.START).gap(3)
                        .children(() -> {
                            text(c, columns[idx].name()).fontSize(10.5f)
                                    .textColor(on ? c.theme().accent() : c.theme().textMuted());
                            if (on) {
                                text(c, asc.get() ? "∧" : "∨").fontSize(9)
                                        .textColor(c.theme().accent());
                            }
                        });
                if (h.clicked()) {
                    if (on) asc.set(!asc.get());
                    else { sortCol.set(idx); asc.set(true); }
                }
            }
        });
    }

    private static void rowOf(Context c, Column[] columns, String[] cells) {
        row(c).fillWidth().height(26).alignItems(Align.CENTER).children(() -> {
            for (int i = 0; i < columns.length; i++) {
                String cell = i < cells.length ? cells[i] : "";
                Element cellBox = box(c).width(columns[i].width()).height(26)
                        .alignItems(Align.CENTER)
                        .children(() -> text(c, cell).fontSize(11.5f).textColor(c.theme().text()));
                if (i == 0) cellBox.padding(0, 6);
            }
        });
    }

    static String[][] sorted(String[][] rows, int col, boolean asc) {
        if (col < 0) return rows;
        String[][] copy = Arrays.copyOf(rows, rows.length);
        Comparator<String[]> by = Comparator.comparing(r ->
                col < r.length ? r[col] : "");
        Arrays.sort(copy, asc ? by : by.reversed());
        return copy;
    }

    private DataTable() {}
}
