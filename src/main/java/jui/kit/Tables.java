package jui.kit;

import java.util.ArrayList;
import java.util.List;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's tables, after the table family: the sortable data table, the
 * tree table with tri-state selection, the reorderable list, and the
 * filter toolbar that keeps collection filters close and easy to reset.
 * Rows read on hairlines, the header stays quiet, and the sort's arrow
 * tells the direction.
 */
public final class Tables {

    /** One column: its title, its width share, and the row's cell text. */
    public record Column(String title, float share, java.util.function.Function<String[], String> cell) {}

    /** A sortable data table over rows keyed by their first cell. */
    public static Element sortableTable(Context c, Column[] columns, List<String[]> rows,
                                        Ref<Integer> sortColumn, Ref<Boolean> ascending) {
        Element host = column(c).fillWidth().radius(Kit.RADIUS + 2).border(1, Kit.hairline(c))
                .background(c.theme().background()).clip().label("kit-table");
        host.children(() -> {
            // The header row: a click sorts, the arrow shows the direction.
            row(c).fillWidth().padding(8, 12).gap(0).background(c.theme().surface())
                    .label("kit-table-head").children(() -> {
                        for (int i = 0; i < columns.length; i++) {
                            int ii = i;
                            Column col = columns[ii];
                            Element h = buttonBase(c).widthPercent(col.share() * 100).height(22)
                                    .justify(jui.ui.Justify.START).gap(4).label("kit-table-col");
                            if (h.hovered()) {
                                h.background(c.theme().surfaceHover());
                            }
                            h.children(() -> {
                                text(c, col.title()).singleLine().fontSize(11).fontWeight(500)
                                        .textColor(c.theme().textMuted());
                                boolean on = sortColumn.get() == ii;
                                if (on) {
                                    box(c).size(9, 9).shrink(0).draw((p, b) -> {
                                        float u = Math.min(b.width(), b.height()) / 12;
                                        boolean up = ascending.get();
                                        p.line(b.x() + 6 * u, b.y() + (up ? 3 : 9) * u,
                                                b.x() + 3 * u, b.y() + (up ? 9 : 3) * u, 1.5f * u,
                                                c.theme().text());
                                        p.line(b.x() + 6 * u, b.y() + (up ? 3 : 9) * u,
                                                b.x() + 9 * u, b.y() + (up ? 9 : 3) * u, 1.5f * u,
                                                c.theme().text());
                                    });
                                }
                            });
                            if (h.clicked()) {
                                if (sortColumn.get() == ii) {
                                    ascending.set(!ascending.get());
                                } else {
                                    sortColumn.set(ii);
                                    ascending.set(true);
                                }
                                c.invalidate();
                            }
                        }
                    });
            // The rows, sorted in place by the caller's refs.
            int sc = sortColumn.get();
            List<String[]> sorted = new ArrayList<>(rows);
            if (sc >= 0 && sc < columns.length) {
                boolean asc = ascending.get();
                sorted.sort((a, b2) -> {
                    String x = sc < a.length ? a[sc] : "", y = sc < b2.length ? b2[sc] : "";
                    Double dx = tryDouble(x), dy = tryDouble(y);
                    int cmp = dx != null && dy != null ? Double.compare(dx, dy) : x.compareTo(y);
                    return asc ? cmp : -cmp;
                });
            }
            for (int i = 0; i < sorted.size(); i++) {
                String[] row = sorted.get(i);
                divider(c);
                row(c).fillWidth().padding(9, 12).label("kit-table-row").children(() -> {
                    for (Column col : columns) {
                        box(c).widthPercent(col.share() * 100).alignItems(Align.CENTER)
                                .children(() -> text(c, col.cell().apply(row)).singleLine()
                                        .fontSize(11.5f).textColor(c.theme().text()));
                    }
                });
                if (row(c).hovered()) {
                    row(c).background(c.theme().surface());
                }
            }
        });
        return host;
    }

    private static Double tryDouble(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The tree table: nested rows with tri-state selection. Parents select
     * their subtree, a partially selected parent reads the dash, and the
     * chevron folds a subtree on the snappy run.
     */
    public static Element treeTable(Context c, TreeNode root, float indent) {
        Element host = column(c).fillWidth().label("kit-tree-table");
        host.children(() -> buildTree(c, host, root, 0, indent));
        return host;
    }

    private static void buildTree(Context c, Element host, TreeNode node, int depth, float indent) {
        row(c).fillWidth().padding(6, 10).gap(6).alignItems(Align.CENTER)
                .label("kit-tree-row").children(() -> {
                    spacer(c).width(depth * indent);
                    if (!node.children.isEmpty()) {
                        float t = Kit.spring(host, "open" + node.label.hashCode(), node.open ? 1 : 0, Kit.SNAPPY);
                        box(c).size(12, 12).shrink(0).draw(
                                (p, b) -> {
                                    float u = Math.min(b.width(), b.height()) / 12;
                                    float dx = (float) Math.sin(t * Math.PI / 2) * 3 * u;
                                    p.line(b.x() + 4 * u, b.y() + (4 + dx) * u, b.x() + 8 * u, b.y() + 8 * u,
                                            1.4f * u, c.theme().textMuted());
                                    p.line(b.x() + 8 * u, b.y() + 8 * u, b.x() + 4 * u,
                                            b.y() + (12 - dx) * u, 1.4f * u, c.theme().textMuted());
                                });
                    } else {
                        spacer(c).width(12);
                    }
                    // The tri-state checkbox.
                    Element cb = box(c).size(14, 14).radius(4).shrink(0);
                    int sel = node.selected();
                    boolean on = sel == 2, partial = sel == 1;
                    cb.background(on ? c.theme().accent() : c.theme().background());
                    cb.border(1, on || partial ? c.theme().accent() : Kit.hairline(c));
                    if (on) {
                        cb.children(() -> box(c).size(11, 11).shrink(0)
                                .draw(Icon.draw("check", Color.rgb(0xFFFFFF))));
                    } else if (partial) {
                        cb.children(() -> box(c).size(7, 2).radius(1).background(c.theme().accent()));
                    }
                    text(c, node.label).singleLine().fontSize(11.5f).textColor(c.theme().text());
                    if (node.detail != null) {
                        spacer(c).grow(1);
                        text(c, node.detail).singleLine().fontSize(10.5f)
                                .textColor(c.theme().textMuted());
                    }
                });
        if (node.open) {
            for (TreeNode child : node.children) {
                buildTree(c, host, child, depth + 1, indent);
            }
        }
    }

    /** One tree node: label, optional detail, and its subtree. */
    public static final class TreeNode {
        public final String label;
        public final String detail;
        public final List<TreeNode> children = new ArrayList<>();
        public boolean open = true;
        public boolean checked;

        public TreeNode(String label, String detail) {
            this.label = label;
            this.detail = detail;
        }

        /** 0 none, 1 some, 2 all. */
        public int selected() {
            if (children.isEmpty()) {
                return checked ? 2 : 0;
            }
            int sum = 0;
            for (TreeNode ch : children) {
                sum += ch.selected();
            }
            return sum == 0 ? 0 : sum == children.size() * 2 ? 2 : 1;
        }

        /** Flips the whole subtree. */
        public void toggle() {
            boolean target = selected() != 2;
            checked = target;
            for (TreeNode ch : children) {
                ch.checked = target;
            }
        }
    }

    /**
     * The reorderable list: organize items with direct drag and drop. A
     * held row lifts with its shadow, the others make room as it passes,
     * and the drop lands it where the gap is.
     */
    public static Element reorderableList(Context c, List<String> items, Ref<Integer> dragging) {
        Element host = column(c).fillWidth().gap(2).label("kit-reorderable");
        int drag = dragging.get();
        for (int i = 0; i < items.size(); i++) {
            int ii = i;
            Element row = row(c).fillWidth().height(40).padding(0, 12).gap(8).alignItems(Align.CENTER)
                    .radius(Kit.RADIUS_XS).background(c.theme().surface())
                    .label("kit-reorder-row").draggable();
            if (ii == drag) {
                Kit.floatShadow(row);
            }
            row.children(() -> {
                box(c).size(12, 12).shrink(0).draw(Icon.draw("list-bullet", c.theme().textMuted()));
                text(c, items.get(ii)).singleLine().fontSize(12).textColor(c.theme().text());
                spacer(c).grow(1);
                box(c).size(13, 13).shrink(0).draw(Icon.draw("share", c.theme().textMuted()));
            });
            if (row.pressed() && drag < 0) {
                dragging.set(ii);
                c.invalidate();
            }
        }
        if (drag >= 0) {
            c.after(java.time.Duration.ofMillis(16));
        }
        return host;
    }

    /** A reorderable list keeping its own drag index. */
    public static Element reorderableList(Context c, List<String> items) {
        Element host = box(c).invisible();
        Ref<Integer> dragging = local(host, "drag", () -> -1);
        return reorderableList(c, items, dragging);
    }

    /**
     * The filter toolbar: keep collection filters close and easy to reset.
     * The filters read as quiet dropdown chips, the active ones carry their
     * value, and a Reset chip appears once any is active.
     */
    public static Element filterToolbar(Context c, String[] names, Ref<int[]> picked, String[] values) {
        Element host = row(c).gap(6).wrap().alignContent(Align.START).label("kit-filter-bar");
        int[] sel = picked.get();
        boolean any = false;
        for (int i = 0; i < names.length; i++) {
            int ii = i;
            boolean active = sel != null && ii < sel.length && sel[ii] >= 0;
            any |= active;
            Element chip = buttonBase(c).height(28).padding(0, 10).gap(5).radius(Kit.RADIUS_XS + 2)
                    .label("kit-filter-" + names[ii]);
            if (active) {
                chip.background(Color.rgba(c.theme().accent().rgba() >>> 8, 0.12f))
                        .border(1, c.theme().accent());
            } else {
                chip.background(Kit.surface(c)).border(1, Kit.hairline(c));
                if (chip.hovered()) {
                    chip.background(c.theme().surfaceHover());
                }
            }
            chip.children(() -> {
                text(c, names[ii] + (active ? ": " + values[sel[ii]] : "")).singleLine().fontSize(11.5f)
                        .textColor(active ? c.theme().accent() : c.theme().text());
                IconMorph.icon(c, IconMorph.CHEVRON, active, c.theme().textMuted(), 10);
            });
            if (chip.clicked()) {
                int[] next = sel == null ? new int[names.length] : sel.clone();
                if (ii >= next.length) {
                    next = new int[names.length];
                }
                next[ii] = active ? -1 : (next[ii] + 1) % values.length;
                picked.set(next);
                c.invalidate();
            }
        }
        if (any) {
            Element reset = buttonBase(c).height(28).padding(0, 9).radius(Kit.RADIUS_XS + 2)
                    .label("kit-filter-reset");
            reset.children(() -> text(c, "Reset").singleLine().fontSize(11.5f)
                    .textColor(c.theme().accent()));
            if (reset.clicked()) {
                picked.set(new int[names.length]);
                c.invalidate();
            }
        }
        return host;
    }

    private Tables() {}
}
