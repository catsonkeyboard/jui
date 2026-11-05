package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's tree: indented nodes that fold at the disclosure, after gpui-kit's
 * Tree. Expansion lives in a TextRef holding a comma-separated key set.
 */
public final class Tree {
    /** One tree node: a label, optional leaf content, and children. */
    public record Node(String label, Runnable leaf, Node[] children) {}

    public static Node node(String label, Node... children) {
        return new Node(label, null, children);
    }

    public static Node leaf(String label, Runnable content) {
        return new Node(label, content, new Node[0]);
    }

    public static Element tree(Context c, Node[] roots, jui.ui.TextRef expandedKeys) {
        return column(c).fillWidth().children(() -> {
            for (Node n : roots) {
                emit(c, n, 0, expandedKeys);
            }
        });
    }

    private static void emit(Context c, Node n, int depth, jui.ui.TextRef expandedKeys) {
        boolean hasChildren = n.children().length > 0;
        String key = n.label();
        boolean open = hasChildren && expandedKeys.get().contains(key);
        Element rowEl = buttonBase(c).fillWidth().height(26).padding(0, 8)
                .label("kit-tree-" + key)
                .justify(Justify.START).gap(4)
                .children(() -> {
                    box(c).width(depth * 14);
                    if (hasChildren) {
                        box(c).size(12, 12).draw(Icon.draw(open ? "chevron-down" : "chevron-right",
                                c.theme().textMuted()));
                    } else {
                        box(c).width(12);
                    }
                    text(c, key).fontSize(12).textColor(c.theme().text());
                });
        if (rowEl.hovered()) rowEl.background(c.theme().surfaceHover());
        if (rowEl.clicked() && hasChildren) {
            toggle(expandedKeys, key);
        }
        if (open) {
            if (n.leaf() != null) {
                box(c).fillWidth().padding(4, 8).children(() -> {
                    box(c).width((depth + 1) * 14);
                    n.leaf().run();
                });
            }
            for (Node child : n.children()) {
                emit(c, child, depth + 1, expandedKeys);
            }
        }
    }

    private static void toggle(jui.ui.TextRef keys, String key) {
        String cur = keys.get();
        java.util.List<String> list = new java.util.ArrayList<>(
                java.util.Arrays.asList(cur.isEmpty() ? new String[0] : cur.split(",")));
        if (list.contains(key)) list.remove(key);
        else list.add(key);
        keys.set(String.join(",", list));
    }

    private Tree() {}
}
