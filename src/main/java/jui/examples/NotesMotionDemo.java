package jui.examples;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ffi.ObjC;
import jui.text.TextEngine;
import jui.ui.*;
import static jui.ui.Ui.*;

/** A notes shell demonstrating styling and animated navigation. ./build.sh demo NotesMotion */
public final class NotesMotionDemo {
    private static final Color NAV = Color.rgb(0x121212), LIST = Color.rgb(0x141414), PAPER = Color.rgb(0x181818);
    private static final Color CARD = Color.rgb(0x22231F), LINE = Color.rgb(0x202020);
    private static final Color INK = Color.rgb(0xD8D8D0), MUTED = Color.rgb(0x777973), QUIET = Color.rgb(0x9A9B94);
    private static final Color LILAC = Color.rgb(0xABA1CD), PILL_INK = Color.rgb(0x282235), ROSE = Color.rgb(0xBF819C);
    private static final Color HOVER = Color.rgb(0x292A26), WELL = Color.rgb(0x20211E);
    private static final Duration SLIDE = Duration.ofMillis(320), FILTER = Duration.ofMillis(280);
    private static final String BODY = "Lorem sint deserunt adipisicing laborum tempor nulla ut in aute anim labore. "
            + "Duis enim consectetur sint velit laboris Lorem Lorem nulla labore adipisicing. "
            + "Proident do commodo minim nostrud laborum sunt officia.\n\n"
            + "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt "
            + "ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco "
            + "laboris nisi ut aliquip ex ea commodo consequat.";
    private static final String SUMMARY = "Lorem sint deserunt adipisicing laborum tempor nulla ut in aute anim labore. Duis enim consect…";
    private static final String[] FILTER_NAMES = {"Inbox", "All", "Last 7 Days"};
    private static final float[] FILTER_WIDTHS = {116, 98, 136};
    private static final String[] FILTER_KEYS = {"filter-inbox", "filter-all", "filter-recent"};
    private static final String[] FILTER_ICONS = {"inbox", "sheets", "clock"};

    static final class Sheet {
        final long id;
        final String title, body;
        final int group;
        Sheet(long id, String title, String body, int group) {
            this.id = id; this.title = title; this.body = body; this.group = group;
        }
    }

    final List<Sheet> sheets = new ArrayList<>();
    private final List<String> groups = new ArrayList<>(List.of("Simple Group"));
    private final List<Long> groupBirths = new ArrayList<>(List.of(-1L));
    int filter;
    private int selectedGroup = -1;
    private long nextId = 2;
    private Sheet selected;
    private boolean navigation = true, sheetList = true, search;
    private final TextRef query = new TextRef("");
    private TextEngine serif;
    private boolean autoplay, reducedMotion;
    private long playbackStart = -1;
    private int playbackStep;
    private float navWidth = 252, listWidth = 296;
    private float documentHeight = 1100;

    public NotesMotionDemo() {
        selected = new Sheet(1, "Hello world!", BODY, -1);
        sheets.add(selected);
    }

    Sheet current() { return selected; }

    void view(Context c) {
        if (serif == null) serif = new TextEngine("en", "Times New Roman");
        if (c.shortcut(Shortcut.cmd(Key.DIGIT1))) { filter = 0; selectedGroup = -1; }
        if (c.shortcut(Shortcut.cmd(Key.DIGIT2))) { filter = 1; selectedGroup = -1; }
        if (c.shortcut(Shortcut.cmd(Key.DIGIT3))) { filter = 2; selectedGroup = -1; }
        if (c.shortcut(Shortcut.cmd(Key.B))) navigation = !navigation;
        if (c.shortcut(Shortcut.cmd(Key.BACKSLASH))) sheetList = !sheetList;
        if (c.shortcut(Shortcut.cmd(Key.N))) newSheet();
        if (c.shortcut(Shortcut.cmd(Key.F))) { navigation = true; search = !search; }
        if (c.shortcut(Shortcut.of(Key.ESCAPE))) { search = false; query.set(""); }
        playback(c);

        Element shell = row(c.key("notes-motion")).fill().background(PAPER).textColor(INK).fontSize(13).clip();
        navWidth = shell.animate("navigation", navigation ? 252 : 0, motion(SLIDE));
        listWidth = shell.animate("sheets", sheetList ? 296 : 0, motion(SLIDE));
        shell.children(() -> {
            column(c.key("navigation-pane")).width(navWidth).shrink(0).fillHeight().background(NAV).clip().disabled(navWidth < 1)
                    .children(() -> navigation(c));
            column(c.key("sheets-pane")).width(listWidth).shrink(0).fillHeight().background(LIST).clip().disabled(listWidth < 1)
                    .children(() -> sheetList(c));
            column(c.key("editor-pane")).grow(1).minWidth(0).fillHeight().background(PAPER).label("editor")
                    .children(() -> editor(c));
        });
    }

    private Duration motion(Duration duration) { return reducedMotion ? Duration.ofMillis(1) : duration; }

    private void navigation(Context c) {
        column(c).width(252).shrink(0).fillHeight().padding(0, 16).children(() -> {
            row(c).height(44).shrink(0).justify(Justify.END).gap(6).children(() -> {
                Element find = iconButton(c, "search", "search");
                if (find.clicked()) search = !search;
                // The toolbar stays reachable in the editor when this pane has gone.
                if (iconButton(c, "nav-collapse", "navigation").clicked()) navigation = false;
            });
            text(c, "Notes.").fontSize(24).bold().height(38).singleLine();
            filters(c);
            box(c).height(28).shrink(0);
            if (search) {
                textInput(c.key("search-input"), query).placeholder("Find a sheet…").label("find-sheet")
                        .background(WELL).textColor(INK).border(1, LINE).height(30).autoFocus();
                box(c).height(14);
            }
            text(c, "Groups").fontSize(11).textColor(MUTED).height(27).shrink(0);
            scroll(c.key("groups-scroll")).grow(1).children(() -> {
                for (int i = 0; i < groups.size(); i++) {
                    final int index = i;
                    Element group = row(c.key("group-" + i)).height(36).shrink(0).radius(6).gap(9).padding(0, 7)
                            .focusable().cursor(Cursor.POINTER).label("group-" + i);
                    float t = groupBirths.get(i) < 0 ? 1 : Math.min(1,
                            (float) (c.now() - groupBirths.get(i)) / motion(FILTER).toNanos());
                    if (t < 1) c.invalidate();
                    float reveal = 1 - (1 - t) * (1 - t) * (1 - t);
                    group.height(36 * Math.max(0, reveal)).opacity(reveal);
                    group.background(selectedGroup == i ? WELL : group.hovered() ? HOVER : Color.TRANSPARENT);
                    if (group.clicked()) { selectedGroup = index; selected = firstShown(); }
                    group.children(() -> {
                        icon(c, "group", ROSE, 17);
                        text(c, groups.get(index)).singleLine().fontSize(13);
                    });
                }
                Element add = dashedButton(c, "new-group", "New group");
                if (add.clicked()) addGroup(c.now());
            });
        });
        box(c).absolute().right(0).top(0).bottom(0).width(1).background(LINE);
    }

    private void addGroup(long now) {
        groups.add("Untitled group");
        groupBirths.add(now);
    }

    private void filters(Context c) {
        Element bar = box(c.key("filters")).height(34).shrink(0);
        float[] widths = new float[3], xs = new float[3];
        for (int i = 0; i < 3; i++) {
            widths[i] = bar.animate("width-" + i, filter == i && selectedGroup < 0 ? FILTER_WIDTHS[i] : 34, motion(FILTER));
            if (i > 0) xs[i] = xs[i - 1] + widths[i - 1] + 8;
        }
        float targetX = 0;
        for (int i = 0; i < filter; i++) targetX += 42;
        float pillX = bar.animate("pill-x", targetX, motion(FILTER));
        float pillWidth = bar.animate("pill-width", selectedGroup < 0 ? FILTER_WIDTHS[filter] : 34, motion(FILTER));
        float pillAlpha = bar.animate("pill-alpha", selectedGroup < 0 ? 1 : 0, motion(FILTER));
        bar.children(() -> {
            for (int i = 0; i < 3; i++) box(c.key("filter-well-" + i)).absolute().left(xs[i]).top(0)
                    .size(widths[i], 34).radius(17).background(WELL);
            box(c.key("pill")).absolute().left(pillX).top(0).size(pillWidth, 34).radius(17)
                    .background(LILAC).opacity(pillAlpha).label("filter-pill");
            for (int i = 0; i < 3; i++) {
                final int index = i;
                boolean active = filter == i && selectedGroup < 0;
                Element choice = row(c.key(FILTER_KEYS[i])).absolute().left(xs[i]).top(0).size(widths[i], 34)
                        .radius(17).padding(0, 9).gap(6).clip().focusable().cursor(Cursor.POINTER).label(FILTER_KEYS[i]);
                if (choice.clicked()) { filter = index; selectedGroup = -1; }
                choice.children(() -> {
                    icon(c, FILTER_ICONS[index], active ? PILL_INK : QUIET, 16);
                    if (widths[index] > 42) {
                        text(c, FILTER_NAMES[index] + (index < 2 ? "  " + sheets.size() : "")).singleLine()
                                .fontSize(12).textColor(active ? PILL_INK : QUIET)
                                .opacity(Math.max(0, (widths[index] - 50) / (FILTER_WIDTHS[index] - 50)));
                    }
                });
            }
        });
    }

    private String heading() { return selectedGroup < 0 ? FILTER_NAMES[filter] : groups.get(selectedGroup); }

    private List<Sheet> shown() {
        String q = query.get().toLowerCase(java.util.Locale.ROOT);
        return sheets.stream().filter(n -> selectedGroup < 0 || n.group == selectedGroup)
                .filter(n -> q.isBlank() || n.title.toLowerCase(java.util.Locale.ROOT).contains(q)
                        || n.body.toLowerCase(java.util.Locale.ROOT).contains(q)).toList();
    }

    private Sheet firstShown() { return shown().stream().findFirst().orElse(null); }

    private void sheetList(Context c) {
        column(c).width(296).shrink(0).fillHeight().padding(0, 10).children(() -> {
            row(c).height(44).shrink(0).gap(8).children(() -> {
                icon(c, selectedGroup < 0 ? FILTER_ICONS[filter] : "group", QUIET, 18);
                column(c).grow(1).children(() -> {
                    text(c, heading()).fontSize(13).singleLine();
                    int count = shown().size();
                    text(c, count + (count == 1 ? " sheet" : " sheets") + " · " + (count == 1 ? "150" : count * 150) + " words")
                            .fontSize(10).textColor(MUTED).singleLine();
                });
                if (iconButton(c, "list-collapse", "sheets-toggle").clicked()) sheetList = false;
            });
            scroll(c.key("sheet-scroll")).grow(1).children(() -> {
                List<Sheet> visible = shown();
                if (visible.isEmpty()) text(c, "No sheets yet").padding(20, 8).textColor(MUTED).fontSize(12);
                for (Sheet note : visible) {
                    Element card = column(c.key(note.id)).height(90).shrink(0).padding(10).radius(7)
                            .focusable().cursor(Cursor.POINTER).label("sheet-" + note.id);
                    card.background(note == selected ? CARD : card.hovered() ? WELL : Color.TRANSPARENT);
                    if (card.clicked()) selected = note;
                    card.children(() -> {
                        row(c).height(20).children(() -> {
                            text(c, note.title).fontSize(13).singleLine().grow(1);
                            text(c, "02:02").fontSize(11).textColor(MUTED);
                        });
                        text(c, note.body.isEmpty() ? "Start writing…" : SUMMARY).fontSize(11).textColor(MUTED).height(32).clip();
                        text(c, note.body.isEmpty() ? "0 words" : "150 words").fontSize(11).textColor(MUTED).height(16);
                    });
                    box(c).height(6).shrink(0);
                }
                if (dashedButton(c, "new-sheet", "New sheet").clicked()) newSheet();
            });
        });
        box(c).absolute().right(0).top(0).bottom(0).width(1).background(LINE);
    }

    private void newSheet() {
        selected = new Sheet(nextId++, "Untitled", "", selectedGroup);
        sheets.add(selected);
    }

    private void editor(Context c) {
        row(c.key("editor-toolbar")).height(44).shrink(0).padding(0, 10).gap(6).children(() -> {
            if (iconButton(c, "toggle-navigation", "navigation").clicked()) navigation = !navigation;
            if (iconButton(c, "toggle-sheets", "sheets-toggle").clicked()) sheetList = !sheetList;
            if (selected != null) {
                row(c.key("document-tab")).height(32).padding(0, 10).gap(7).radius(6).background(Color.rgb(0x1B1B1B))
                        .children(() -> {
                            icon(c, "document", LILAC, 15);
                            text(c, selected.title).singleLine().fontSize(12);
                            if (iconButton(c, "close-sheet", "close").size(22, 24).clicked()) selected = null;
                        });
            }
            spacer(c);
            Element replay = iconButton(c, "replay", autoplay ? "stop" : "play");
            if (replay.clicked()) {
                autoplay = !autoplay;
                playbackStart = -1;
                playbackStep = 0;
            }
        });
        scroll(c.key("document-scroll")).grow(1).clip().children(() -> {
            canvas(c.key("document")).fillWidth().height(documentHeight).shrink(0).label("document-canvas").draw((p, bounds) -> {
                if (selected == null) {
                    documentHeight(p, 400);
                    p.text("Select a sheet", 48, 90, 18, 400, MUTED, 300);
                    return;
                }
                float inset = Math.max(46, (bounds.width() - 610) / 2);
                float width = Math.max(120, bounds.width() - 2 * inset);
                p.text(serif.layout("#", 32, 400, 0), inset, 80, MUTED);
                p.text(serif.layout(selected.title, 32, 700, width - 32), inset + 30, 80, INK);
                if (selected.body.isEmpty()) {
                    documentHeight(p, 400);
                    p.text(serif.layout("Start writing…", 21, 400, width), inset, 140, MUTED);
                } else {
                    var layout = serif.layout(selected.body, 21, 400, width);
                    documentHeight(p, Math.max(400, 142 + layout.lines * 33 + 80));
                    // Generous manuscript leading, like the reference, without changing framework defaults.
                    for (int line = 0; line < layout.lines; line++) {
                        float y = 142 + line * 33;
                        p.clip(inset, y, width, layout.lineHeight(line));
                        p.text(layout, inset, y - layout.lineTop(line), INK);
                        p.popClip();
                    }
                }
            });
        });
    }

    /** Paint knows the final wrapping width; request one layout pass when that changes the extent. */
    private void documentHeight(Painter p, float height) {
        if (documentHeight != height) {
            documentHeight = height;
            p.animationFrame();
        }
    }

    private static Element iconButton(Context c, String key, String symbol) {
        Element e = box(c.key(key)).size(28, 28).radius(5).focusable().cursor(Cursor.POINTER).label(key);
        e.background(e.hovered() ? HOVER : Color.TRANSPARENT);
        return e.draw((p, b) -> drawIcon(p, symbol, (b.width() - 16) / 2, (b.height() - 16) / 2, 16, QUIET));
    }

    private static void icon(Context c, String symbol, Color color, float size) {
        canvas(c).size(size, size).shrink(0).draw((p, b) -> drawIcon(p, symbol, 0, 0, size, color));
    }

    private static Element dashedButton(Context c, String key, String title) {
        Element e = row(c.key(key)).height(34).shrink(0).radius(6).focusable().cursor(Cursor.POINTER).label(key);
        e.background(e.hovered() ? WELL : Color.TRANSPARENT);
        e.draw((p, b) -> {
            for (float x = 7; x < b.width() - 7; x += 8) {
                p.line(x, 1, Math.min(x + 4, b.width() - 7), 1, 1, LINE);
                p.line(x, b.height() - 1, Math.min(x + 4, b.width() - 7), b.height() - 1, 1, LINE);
            }
            for (float y = 7; y < b.height() - 5; y += 8) {
                p.line(1, y, 1, y + 4, 1, LINE);
                p.line(b.width() - 1, y, b.width() - 1, y + 4, 1, LINE);
            }
        });
        return e.justify(Justify.CENTER).gap(6).children(() -> {
            icon(c, "plus", MUTED, 13);
            text(c, title).fontSize(12).textColor(MUTED).singleLine();
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

    private void playback(Context c) {
        if (!autoplay) return;
        if (playbackStart < 0) {
            playbackStart = c.now(); playbackStep = 0;
            filter = 0; selectedGroup = -1; navigation = sheetList = true;
        }
        long elapsed = (c.now() - playbackStart) / 1_000_000;
        int[] times = {900, 1700, 2500, 3300, 4100, 4900, 5700, 6500, 7700};
        while (playbackStep < times.length && elapsed >= times[playbackStep]) {
            switch (playbackStep++) {
                case 0 -> filter = 1;
                case 1 -> filter = 2;
                case 2 -> filter = 0;
                case 3 -> navigation = false;
                case 4 -> sheetList = false;
                case 5 -> sheetList = true;
                case 6 -> navigation = true;
                case 7 -> { if (groups.size() < 2) addGroup(c.now()); }
                default -> { autoplay = false; return; }
            }
        }
        if (playbackStep < times.length) c.after(Duration.ofMillis(Math.max(1, times[playbackStep] - elapsed)));
    }

    public static void main(String[] args) {
        NotesMotionDemo demo = new NotesMotionDemo();
        demo.autoplay = Arrays.asList(args).contains("--autoplay");
        demo.reducedMotion = Arrays.asList(args).contains("--reduced-motion");
        App.run(() -> {
            Window window = Window.open(WindowOptions.of("笔记样式和动画").size(1180, 690).content(demo::view));
            // Activation can finish after launch; resolve this window by number instead of relying on keyWindow.
            long nativeWindow = ObjC.send(ObjC.send(ObjC.cls("NSApplication"), "sharedApplication"),
                    "windowWithWindowNumber:", window.windowNumber());
            long appearance = ObjC.send(ObjC.cls("NSAppearance"), "appearanceNamed:", ObjC.nsString("NSAppearanceNameDarkAqua"));
            ObjC.send(nativeWindow, "setAppearance:", appearance);
            ObjC.send(nativeWindow, "setTitleVisibility:", 1);
            ObjC.send(nativeWindow, "setTitlebarAppearsTransparent:", 1);
            ObjC.send(nativeWindow, "setBackgroundColor:", ObjC.sendDoubles(ObjC.cls("NSColor"), "colorWithWhite:alpha:", 0.071, 1));
            if (Arrays.asList(args).contains("--print-window")) System.out.println("window=" + window.windowNumber());
        });
    }
}
