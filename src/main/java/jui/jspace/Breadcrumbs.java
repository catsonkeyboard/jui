package jui.jspace;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static jui.ui.Ui.*;

/** The pane's path line: back and forward, home, then one crumb per step, clickable to jump. */
final class Breadcrumbs {
    private final Workspace ws;
    private final int pane;

    Breadcrumbs(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model.TabState t = ws.model().panes[pane].tab();
        boolean active = ws.model().active == pane;
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        row(c).height(28).padding(0, 6).gap(4).alignItems(Align.CENTER)
                .radius(4)
                .background(active ? Color.rgba(c.theme().accent().rgba() >>> 8, 0.08f) : pal.bar())
                .label("crumbs-" + pane)
                .children(() -> {
                    arrow(c, "nav-back", !t.back.isEmpty(), () -> ws.goBack(pane), "‹");
                    arrow(c, "nav-forward", !t.forward.isEmpty(), () -> ws.goForward(pane), "›");
                    home(c);
                    for (Path step : steps(t.dir)) {
                        text(c, "›").fontSize(12).textColor(c.theme().textMuted());
                        crumb(c, step);
                    }
                });
    }

    private void arrow(Context c, String id, boolean enabled, Runnable go, String glyph) {
        Element b = buttonBase(c).width(20).height(20).radius(4).label(id).alignItems(Align.CENTER)
                .children(() -> text(c, glyph).fontSize(14)
                        .textColor(enabled ? c.theme().text() : c.theme().textMuted()));
        if (enabled && b.clicked()) go.run();
    }

    /** The path steps under home, or the whole absolute chain when outside it. */
    static List<Path> steps(Path dir) {
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> out = new ArrayList<>();
        if (dir == null) return out;
        Path cur = dir;
        while (cur != null && !cur.equals(home)) {
            out.add(0, cur);
            cur = cur.getParent();
        }
        if (cur == null) {                                       // outside home: start from the root
            out.clear();
            cur = dir;
            while (cur != null) {
                out.add(0, cur);
                cur = cur.getParent();
            }
        }
        return out;
    }

    private void home(Context c) {
        Element h = buttonBase(c).height(20).radius(4).label("crumb-home").alignItems(Align.CENTER)
                .padding(0, 4)
                .children(() -> text(c, "⌂").fontSize(14).textColor(c.theme().textMuted()));
        if (h.clicked()) ws.navigate(pane, Path.of(System.getProperty("user.home")));
    }

    private void crumb(Context c, Path step) {
        String name = step.getFileName() == null ? "/" : step.getFileName().toString();
        Element b = buttonBase(c).height(20).radius(4).padding(0, 4).alignItems(Align.CENTER)
                .label("crumb-" + name)
                .children(() -> text(c, name).fontSize(12).textColor(c.theme().text()));
        if (b.clicked()) ws.navigate(pane, step);
    }
}
