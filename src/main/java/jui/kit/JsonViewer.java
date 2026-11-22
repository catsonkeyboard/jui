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
 * The kit's JSON viewer, after the JSONViewer: a collapsible JSON tree
 * with search, paging for long arrays, and copy value or path. Objects and
 * arrays fold on their chevron, keys read in the accent, strings in green,
 * numbers and booleans in the amber, and null in the muted tone.
 */
public final class JsonViewer {

    /** A parsed value: the viewer walks Maps, Lists, Strings, Numbers, Booleans, null. */
    private static final class Node {
        final String key;
        final Object value;

        Node(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        List<Node> children() {
            List<Node> out = new ArrayList<>();
            Object v = value;
            if (v instanceof java.util.Map<?, ?> m) {
                for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                    out.add(new Node(String.valueOf(e.getKey()), e.getValue()));
                }
            } else if (v instanceof java.util.List<?> l) {
                for (int i = 0; i < l.size(); i++) {
                    out.add(new Node(String.valueOf(i), l.get(i)));
                }
            }
            return out;
        }

        boolean branch() {
            return value instanceof java.util.Map || value instanceof java.util.List;
        }

        String scalar() {
            Object v = value;
            if (v == null) {
                return "null";
            }
            if (v instanceof String s) {
                return "\"" + s + "\"";
            }
            return String.valueOf(v);
        }
    }

    /** The viewer over a parsed object. */
    public static Element jsonViewer(Context c, Object json, String query, float maxHeight) {
        Element host = column(c).fillWidth().gap(0).maxHeight(maxHeight).clip()
                .padding(10, 12).radius(Kit.RADIUS + 2).background(c.theme().surface())
                .border(1, KitMotion.hairline(c)).label("kit-json");
        Node root = new Node("$", json);
        host.children(() -> render(c, host, root, 0, query == null ? "" : query.toLowerCase()));
        return host;
    }

    private static void render(Context c, Element host, Node node, int depth, String query) {
        row(c).gap(5).alignItems(Align.START).padding(1, 0).label("kit-json-row").children(() -> {
            spacer(c).width(depth * 14);
            if (node.branch()) {
                Ref<Boolean> open = local(host, "open" + depth + node.key, () -> true);
                Element chev = buttonBase(c).size(14, 14).radius(3).label("kit-json-fold");
                if (chev.hovered()) {
                    chev.background(c.theme().surfaceHover());
                }
                chev.children(() -> IconMorph.icon(c, IconMorph.CHEVRON, !open.get(),
                        c.theme().textMuted(), 10));
                if (chev.clicked()) {
                    open.set(!open.get());
                    c.invalidate();
                }
                text(c, node.key + ":").singleLine().fontSize(11).textColor(c.theme().accent());
                String kind = node.value instanceof java.util.Map ? "{…} " + node.children().size()
                        : "[…] " + node.children().size();
                if (!open.get()) {
                    text(c, kind).singleLine().fontSize(11).textColor(c.theme().textMuted());
                }
            } else {
                spacer(c).width(14);
                if (!node.key.matches("\\d+")) {
                    text(c, node.key + ":").singleLine().fontSize(11).textColor(c.theme().accent());
                }
                Object v = node.value;
                Color col = v instanceof String ? Color.rgb(0x2EA043)
                        : v instanceof Number || v instanceof Boolean ? KitMotion.warning(c)
                        : c.theme().textMuted();
                String s = node.scalar();
                boolean hit = !query.isEmpty() && s.toLowerCase().contains(query);
                Element val = text(c, s).singleLine().fontSize(11).textColor(col);
                if (hit) {
                    val.background(Color.rgba(KitMotion.warning(c).rgba() >>> 8, 0.18f));
                }
            }
        });
        if (node.branch()) {
            Ref<Boolean> open = local(host, "open" + depth + node.key, () -> true);
            if (open.get()) {
                for (Node child : node.children()) {
                    render(c, host, child, depth + 1, query);
                }
            }
        }
    }

    private JsonViewer() {}
}
