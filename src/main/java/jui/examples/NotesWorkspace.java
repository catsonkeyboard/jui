package jui.examples;

import java.time.Duration;
import jui.text.TextEngine;
import jui.ui.*;
import static jui.ui.Ui.*;

/** One three-pane view of NotesDemo's catalog, with system appearance and interruptible motion. */
final class NotesWorkspace {
    record Colors(Color nav, Color list, Color paper, Color card, Color ink, Color muted,
                  Color accent, Color accentInk, Color rose, Color hover, Color well, Color line) {}
    private static Color rgb(int value) { return Color.rgb(value); }
    static final Colors DARK = new Colors(rgb(0x121212),rgb(0x141414),rgb(0x181818),rgb(0x22231F),
            rgb(0xD8D8D0),rgb(0x92948D),rgb(0xABA1CD),rgb(0x282235),rgb(0xBF819C),rgb(0x292A26),rgb(0x20211E),rgb(0x30302E));
    static final Colors LIGHT = new Colors(rgb(0xF1F1F0),rgb(0xF8F8F6),rgb(0xFFFFFF),rgb(0xE9E8E2),
            rgb(0x252525),rgb(0x74746F),rgb(0xD8D0EB),rgb(0x342A48),rgb(0xA15D7C),rgb(0xE7E7E3),rgb(0xECECE8),rgb(0xDEDEDA));
    private static final String[] FILTER_NAMES = {"Inbox", "All", "Last 7 Days"};
    private static final String[] FILTER_KEYS = {"filter-inbox", "filter-all", "filter-recent"};
    private static final String[] FILTER_ICONS = {"inbox", "sheets", "clock"};
    private static final float[] FILTER_WIDTHS = {132, 108, 136};
    private static final Duration SLIDE = Duration.ofMillis(320), FILTER = Duration.ofMillis(280);
    final NotesDemo demo;
    Colors colors = LIGHT;
    private float navigationWidth = 252, shownListWidth = 296, roomWidth = 1180;
    private TextEngine serif;
    private float headingHeight = 44;
    private final Painter.Draw heading = this::heading;
    private final Painter.Draw measure = this::measure;
    private final InputHandler resizeKeys = this::resizeKey;

    private boolean resizeKey(InputEvent event) {
        if (event.kind() == InputEvent.Kind.KEY_DOWN && (event.key() == Key.LEFT || event.key() == Key.RIGHT)) {
            resize(demo.sidebarWidth.get() + (event.key() == Key.LEFT ? -8 : 8));
            return true;
        }
        return false;
    }

    NotesWorkspace(NotesDemo demo) { this.demo = demo; }
    Duration motion(Duration d) { return demo.reducedMotion ? Duration.ofMillis(1) : d; }

    void view(Context c) {
        colors = c.theme().isDark() ? DARK : LIGHT;
        if (c.shortcut(Shortcut.cmd(Key.DIGIT1))) demo.selectFilter(0);
        if (c.shortcut(Shortcut.cmd(Key.DIGIT2))) demo.selectFilter(1);
        if (c.shortcut(Shortcut.cmd(Key.DIGIT3))) demo.selectFilter(2);
        if (c.shortcut(Shortcut.cmd(Key.B))) demo.navigation = !demo.navigation;
        if (c.shortcut(Shortcut.cmd(Key.F))) { demo.sheetList = true; demo.focusSearch = true; }
        if (c.shortcut(Shortcut.of(Key.ESCAPE))) demo.query.set("");
        demo.playback(c);
        Element shell = row(c.key("notes-workspace")).fill().alignItems(Align.STRETCH)
                .background(colors.paper).textColor(colors.ink).fontSize(13).clip().draw(measure);
        float visible = shell.animate("sheets", demo.sheetList ? 1 : 0, motion(SLIDE));
        navigationWidth = Math.min(shell.animate("navigation", demo.navigation ? 252 : 0, motion(SLIDE)),
                Math.max(0, roomWidth - 160 - visible));
        shownListWidth = Math.min(demo.sidebarWidth.get(), Math.max(0, roomWidth - navigationWidth - 160 - visible)) * visible;
        shell.children(() -> {
            column(c.key("navigation-pane")).width(navigationWidth).minWidth(0).shrink(1).fillHeight()
                    .background(colors.nav).clip().disabled(navigationWidth < 1).label("navigation-pane")
                    .children(() -> navigation(c));
            column(c.key("notes-pane")).width(shownListWidth).minWidth(0).shrink(1).fillHeight()
                    .background(colors.list).clip().disabled(shownListWidth < 1).label("notes-pane")
                    .children(() -> demo.sidebar(c, c.theme()));
            Element divider = box(c.key("divider")).width(8 * visible).shrink(0).fillHeight()
                    .margin(0, -3.5f * visible, 0, -3.5f * visible).focusable().cursor(Cursor.RESIZE_EW).label("divider")
                    .disabled(!demo.sheetList || visible < .999f || shownListWidth < 120).handleInput(resizeKeys);
            float delta = divider.dragX();
            if (delta != 0) { resize(demo.sidebarWidth.get() + delta); c.invalidate(); }
            divider.children(() -> box(c).absolute().left(3.5f * visible).width(visible).top(0).bottom(0)
                    .background(divider.hovered() ? colors.accent : colors.line));
            column(c.key("editor-pane")).grow(1).minWidth(160).shrink(0).fillHeight().background(colors.paper).clip()
                    .label("editor").children(() -> demo.editor(c, c.theme()));
        });
    }

    private void measure(Painter p, Rect bounds) {
        if (roomWidth != bounds.width()) { roomWidth = bounds.width(); p.animationFrame(); }
    }

    private void resize(float value) {
        demo.sidebarWidth.set(Math.max(120, Math.min(value, Math.max(120, roomWidth - navigationWidth - 161))));
    }

    private void navigation(Context c) {
        column(c).width(252).shrink(0).fillHeight().padding(0, 16).children(() -> {
            row(c).height(44).shrink(0).justify(Justify.END).gap(6).children(() -> {
                if (iconButton(c, "search-notes", "search").clicked()) { demo.sheetList = true; demo.focusSearch = true; }
                if (iconButton(c, "nav-collapse", "navigation").clicked()) demo.navigation = false;
            });
            text(c, "Notes.").fontSize(24).bold().height(38).singleLine();
            filters(c);
            box(c).height(28).shrink(0);
            text(c, "Groups").fontSize(11).textColor(colors.muted).height(27).shrink(0);
            scroll(c.key("groups-scroll")).grow(1).children(() -> {
                for (int i = 0; i < demo.groups.size(); i++) {
                    final int index = i;
                    NotesDemo.Group data = demo.groups.get(i);
                    float progress = data.born < 0 ? 1 : Math.min(1, (float) (c.now() - data.born) / motion(FILTER).toNanos());
                    if (progress < 1) c.invalidate();
                    float reveal = 1 - (1-progress)*(1-progress)*(1-progress);
                    Element group = row(c.key("group-" + i)).height(36 * reveal).shrink(0).opacity(reveal)
                            .radius(6).gap(9).padding(0, 7).focusable().cursor(Cursor.POINTER).label("group-" + i);
                    group.background(demo.selectedGroup == i ? colors.well : group.hovered() ? colors.hover : Color.TRANSPARENT);
                    if (group.clicked()) demo.selectGroup(index);
                    group.children(() -> { icon(c, "group", colors.rose, 17); text(c, data.title).singleLine().fontSize(13); });
                }
                if (dashedButton(c, "new-group", "New group").clicked()) demo.addGroup(c.now());
            });
        });
    }

    private void filters(Context c) {
        Element bar = box(c.key("filters")).height(34).shrink(0);
        float[] widths = new float[3], xs = new float[3];
        for (int i=0;i<3;i++) {
            widths[i] = bar.animate("width-"+i, demo.filter == i && demo.selectedGroup < 0 ? FILTER_WIDTHS[i] : 34, motion(FILTER));
            if (i>0) xs[i]=xs[i-1]+widths[i-1]+8;
        }
        float pillX=bar.animate("pill-x",demo.filter*42,motion(FILTER));
        float pillWidth=bar.animate("pill-width",demo.selectedGroup<0?FILTER_WIDTHS[demo.filter]:34,motion(FILTER));
        float alpha=bar.animate("pill-alpha",demo.selectedGroup<0?1:0,motion(FILTER));
        bar.children(() -> {
            for(int i=0;i<3;i++) box(c.key("filter-well-"+i)).absolute().left(xs[i]).top(0).size(widths[i],34).radius(17).background(colors.well);
            box(c.key("filter-pill")).absolute().left(pillX).top(0).size(pillWidth,34).radius(17).background(colors.accent).opacity(alpha).label("filter-pill");
            for(int i=0;i<3;i++) {
                final int index=i;
                boolean active=demo.filter==i && demo.selectedGroup<0;
                Element choice=row(c.key(FILTER_KEYS[i])).absolute().left(xs[i]).top(0).size(widths[i],34)
                        .radius(17).padding(0,9).gap(6).clip().focusable().cursor(Cursor.POINTER).label(FILTER_KEYS[i]);
                if(choice.clicked()) demo.selectFilter(index);
                choice.children(() -> {
                    icon(c,FILTER_ICONS[index],active?colors.accentInk:colors.muted,16);
                    if(widths[index]>42) text(c,FILTER_NAMES[index]).singleLine().fontSize(12)
                            .textColor(active?colors.accentInk:colors.muted)
                            .opacity(Math.max(0,(widths[index]-50)/(FILTER_WIDTHS[index]-50)));
                });
            }
        });
    }

    String heading() { return demo.selectedGroup<0 ? FILTER_NAMES[demo.filter] : demo.groups.get(demo.selectedGroup).title; }

    void document(Context c, String body) {
        scroll(c.key("document-scroll")).grow(1).clip().label("document-scroll").children(() ->
                column(c).fillWidth().padding(28, 32).gap(16).label("document-content").children(() -> {
                    canvas(c.key("document-heading")).fillWidth().height(headingHeight).shrink(0).draw(heading);
                    if(body==null) richText(c,NotesDemo.PREVIEW).fontSize(16);
                    else if (body.isEmpty()) text(c,"空白笔记").fontSize(16).textColor(colors.muted);
                    else text(c,body).fontSize(16);
                }));
    }

    private void heading(Painter p, Rect bounds) {
        var note=demo.current();
        if(note==null) return;
        if(serif==null) serif=new TextEngine("zh-Hans","Times New Roman");
        var layout=serif.layout(note.title,28,700,Math.max(1,bounds.width()));
        p.text(layout,0,0,colors.ink);
        if(headingHeight!=layout.height) { headingHeight=layout.height; p.animationFrame(); }
    }

    Element iconButton(Context c, String key, String symbol) {
        Element e = box(c.key(key)).size(28, 28).radius(5).focusable().cursor(Cursor.POINTER).label(key);
        e.background(e.hovered() ? colors.hover : Color.TRANSPARENT);
        return e.draw((p, b) -> drawIcon(p, symbol, (b.width() - 16) / 2, (b.height() - 16) / 2, 16, colors.muted));
    }

    private void icon(Context c, String symbol, Color color, float size) {
        canvas(c).size(size, size).shrink(0).draw((p, b) -> drawIcon(p, symbol, 0, 0, size, color));
    }

    Element dashedButton(Context c, String key, String title) {
        Element e = row(c.key(key)).height(34).shrink(0).radius(6).focusable().cursor(Cursor.POINTER).label(key);
        e.background(e.hovered() ? colors.well : Color.TRANSPARENT);
        e.draw((p, b) -> {
            for (float x = 7; x < b.width() - 7; x += 8) {
                p.line(x, 1, Math.min(x + 4, b.width() - 7), 1, 1, colors.line);
                p.line(x, b.height() - 1, Math.min(x + 4, b.width() - 7), b.height() - 1, 1, colors.line);
            }
            for (float y = 7; y < b.height() - 5; y += 8) {
                p.line(1, y, 1, y + 4, 1, colors.line);
                p.line(b.width() - 1, y, b.width() - 1, y + 4, 1, colors.line);
            }
        });
        return e.justify(Justify.CENTER).gap(6).children(() -> {
            icon(c, "plus", colors.muted, 13);
            text(c, title).fontSize(12).textColor(colors.muted).singleLine();
        });
    }

    private static void drawIcon(Painter p, String name, float x, float y, float size, Color color) {
        float k = size / 16;
        switch (name) {
            case "inbox" -> {
                line(p,x,y,k,2,6,5,2,color); line(p,x,y,k,5,2,11,2,color); line(p,x,y,k,11,2,14,6,color);
                line(p,x,y,k,2,6,2,13,color); line(p,x,y,k,14,6,14,13,color); line(p,x,y,k,2,13,14,13,color);
                line(p,x,y,k,2,8,6,8,color); line(p,x,y,k,6,8,7,10,color); line(p,x,y,k,7,10,10,10,color);
                line(p,x,y,k,10,10,11,8,color); line(p,x,y,k,11,8,14,8,color);
            }
            case "clock" -> {
                ring(p, x+8*k, y+8*k, 6*k, color); line(p,x,y,k,8,4,8,8,color); line(p,x,y,k,8,8,11,10,color);
            }
            case "search" -> {
                ring(p,x+6.5f*k,y+6.5f*k,4.5f*k,color); line(p,x,y,k,10,10,14,14,color);
            }
            case "navigation", "sheets-toggle" -> {
                outline(p,x+2*k,y+3*k,12*k,10*k,color); line(p,x,y,k,6,3,6,13,color);
                if (name.equals("sheets-toggle")) { line(p,x,y,k,11,6,9,8,color); line(p,x,y,k,9,8,11,10,color); }
            }
            case "document", "group", "sheets" -> {
                outline(p,x+4*k,y+2*k,9*k,12*k,color);
                if (name.equals("sheets")) { line(p,x,y,k,2,5,2,15,color); line(p,x,y,k,2,15,10,15,color); }
                else if (name.equals("group")) { line(p,x,y,k,8,8,14,2,color); line(p,x,y,k,3,5,6,5,color); line(p,x,y,k,3,8,6,8,color); }
                else { line(p,x,y,k,7,6,11,6,color); line(p,x,y,k,7,9,11,9,color); line(p,x,y,k,7,12,11,12,color); }
            }
            case "plus" -> { line(p,x,y,k,3,8,13,8,color); line(p,x,y,k,8,3,8,13,color); }
            case "close" -> { line(p,x,y,k,4,4,12,12,color); line(p,x,y,k,12,4,4,12,color); }
            case "play" -> { line(p,x,y,k,5,3,13,8,color); line(p,x,y,k,13,8,5,13,color); line(p,x,y,k,5,13,5,3,color); }
            case "stop" -> outline(p,x+4*k,y+4*k,8*k,8*k,color);
            default -> { }
        }
    }

    private static void line(Painter p, float x, float y, float k, float a, float b, float c, float d, Color color) {
        p.line(x+a*k, y+b*k, x+c*k, y+d*k, 1.3f*k, color);
    }
    private static void outline(Painter p, float x, float y, float w, float h, Color color) {
        p.line(x,y,x+w,y,1.2f,color); p.line(x+w,y,x+w,y+h,1.2f,color);
        p.line(x+w,y+h,x,y+h,1.2f,color); p.line(x,y+h,x,y,1.2f,color);
    }
    private static void ring(Painter p, float x, float y, float r, Color color) {
        for (int i=0; i<24; i++) {
            double a=i*Math.PI/12, b=(i+1)*Math.PI/12;
            p.line(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,
                    x+(float)Math.cos(b)*r,y+(float)Math.sin(b)*r,1.3f,color);
        }
    }


}
