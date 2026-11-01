package jui.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.FloatRef;
import jui.ui.IntRef;
import jui.ui.Key;
import jui.ui.ListState;
import jui.ui.Menu;
import jui.ui.Mod;
import jui.ui.Shortcut;
import jui.ui.Span;
import jui.ui.Spans;
import jui.ui.TextRef;
import jui.ui.Theme;

import static jui.ui.Ui.button;
import static jui.ui.Ui.column;
import static jui.ui.Ui.divider;
import static jui.ui.Ui.list;
import static jui.ui.Ui.modal;
import static jui.ui.Ui.popover;
import static jui.ui.Ui.primaryButton;
import static jui.ui.Ui.row;
import static jui.ui.Ui.spacer;
import static jui.ui.Ui.tabs;
import static jui.ui.Ui.text;
import static jui.ui.Ui.textInput;

/**
 * One notes example: animated three-pane workspace, virtualized catalog,
 * search, open tabs, command palette, information popover and native menus.
 * Readonly previews and in-memory new notes; not a complete Markdown editor.
 * <pre>./build.sh demo Notes [--autoplay] [--reduced-motion]</pre>
 */
public final class NotesDemo {
    final List<FilesDemo.Note> notes;
    private final java.util.IdentityHashMap<FilesDemo.Note, String> previewBodies = new java.util.IdentityHashMap<>();
    private final List<FilesDemo.Note> createdNotes = new ArrayList<>();
    private boolean benchmarkRawKeys;
    final NotesWorkspace workspace = new NotesWorkspace(this);
    static final class Group {
        final String title;
        final long born;
        Group(String title, long born) { this.title = title; this.born = born; }
    }
    final List<Group> groups = new ArrayList<>(List.of(new Group("Simple Group", -1)));
    private final java.util.IdentityHashMap<FilesDemo.Note, Integer> noteGroups = new java.util.IdentityHashMap<>();
    int filter, selectedGroup = -1;
    boolean navigation = true, sheetList = true, focusSearch, autoplay, reducedMotion;
    private long notesVersion, shownVersion = -1, playbackStart = -1, nextId = 10001;
    private int filteredGroup = -2, filteredFilter = -1, playbackStep;
    private String countLabel = "";


    public NotesDemo() { notes = new FilesDemo().notes; }

    NotesDemo(NotesVault vault) {
        notes = new ArrayList<>(List.of(vault.selected));
        previewBodies.put(vault.selected, vault.body);
        open(vault.selected);
    }
    final ListState files = new ListState();
    final TextRef query = new TextRef("");
    private final jui.ui.Handle searchHandle = new jui.ui.Handle();
    final FloatRef sidebarWidth = new FloatRef(296);
    final BoolRef paletteOpen = new BoolRef(false);
    final TextRef command = new TextRef("");
    final IntRef tab = new IntRef(0);
    final BoolRef infoOpen = new BoolRef(false);
    /** The notes the query matches, made again only when the query changes. */
    private String filteredFor;
    private List<FilesDemo.Note> shown;
    /** The notes open in the editor, one tab each; the chosen tab is the selected note. */
    final List<FilesDemo.Note> open = new ArrayList<>();
    /** The editor's info button, this pass's handle: the popover anchors on it. */
    private Element infoButton;

    /** The read-only preview every note shows for now, rich text as it stands. */
    static final Spans PREVIEW = Spans.of(
            Span.text("标题 ").fontSize(22).bold(),
            Span.text("mono").monospace().italic(),
            Span.text("\n一段中英混排的预览：j-ui draws rich text through Core Text, wrap and all.\n")
                    .fontSize(14),
            Span.text("下划线").underline(),
            Span.text(" 与 ").fontSize(14),
            Span.text("删除线").strikethrough().fontSize(14),
            Span.text("，以及带底色的片段。").fontSize(14));

    /** The notes whose titles hold the query, case aside. */
    private List<FilesDemo.Note> shown() {
        String q = query.get();
        if (!q.equals(filteredFor) || selectedGroup != filteredGroup || filter != filteredFilter || notesVersion != shownVersion) {
            filteredFor = q; filteredGroup = selectedGroup; filteredFilter = filter; shownVersion = notesVersion;
            if (q.isEmpty() && selectedGroup < 0) shown = notes;
            else {
                shown = new ArrayList<>();
                String needle = q.toLowerCase(Locale.ROOT);
                for (FilesDemo.Note n : notes) {
                    Integer group = selectedGroup < 0 ? null : noteGroups.get(n);
                    if ((selectedGroup < 0 || group != null && group.intValue() == selectedGroup)
                            && n.lower.contains(needle)) shown.add(n);
                }
            }
            countLabel = shown.size() + (shown.size() == 1 ? " note" : " notes");
        }
        return shown;
    }

    void selectFilter(int value) { filter = value; selectedGroup = -1; files.scrollToTop(); }
    void selectGroup(int value) { selectedGroup = value; files.scrollToTop(); }
    void addGroup(long now) { groups.add(new Group("Untitled group", now)); }

    void newNote() {
        boolean occupied;
        do {
            occupied = false;
            for (FilesDemo.Note n : notes) if (n.id == nextId) { occupied = true; nextId++; break; }
        } while (occupied);
        FilesDemo.Note note = new FilesDemo.Note(nextId++, "Untitled", null);
        notes.add(note); createdNotes.add(note); previewBodies.put(note, "");
        if (selectedGroup >= 0) noteGroups.put(note, selectedGroup);
        notesVersion++;
        query.set(""); open(note); sheetList = true; files.scrollToEnd();
    }

    void replaceNotes(List<FilesDemo.Note> loaded) {
        notes.clear(); notes.addAll(loaded);
        for (FilesDemo.Note note : createdNotes) if (!notes.contains(note)) notes.add(note);
        notesVersion++; filteredFor = null;
    }

    void closeTab() {
        if (open.isEmpty()) return;
        int at = Math.min(tab.get(), open.size() - 1);
        open.remove(at);
        tab.set(Math.max(0, Math.min(at, open.size() - 1)));
        infoOpen.set(false);
    }

    void playback(Context c) {
        if (!autoplay) return;
        if (playbackStart < 0) {
            playbackStart = c.now(); playbackStep = 0;
            filter = 0; selectedGroup = -1; navigation = sheetList = true;
        }
        long elapsed = (c.now() - playbackStart) / 1_000_000;
        long[] times = {900,1700,2500,3300,4100,4900,5700,6500,7700};
        while (playbackStep < times.length && elapsed >= times[playbackStep]) {
            switch (playbackStep++) {
                case 0 -> selectFilter(1);
                case 1 -> selectFilter(2);
                case 2 -> selectFilter(0);
                case 3 -> navigation = false;
                case 4 -> sheetList = false;
                case 5 -> sheetList = true;
                case 6 -> navigation = true;
                case 7 -> { if (groups.size() < 2) addGroup(c.now()); }
                default -> { autoplay = false; return; }
            }
        }
        if (playbackStep < times.length) c.after(java.time.Duration.ofMillis(Math.max(1, times[playbackStep]-elapsed)));
    }

    /** The notes the palette's command matches, at most a screenful. */
    private List<FilesDemo.Note> matching() {
        String k = command.get().toLowerCase(Locale.ROOT);
        List<FilesDemo.Note> found = new ArrayList<>();
        for (FilesDemo.Note n : shown()) {
            if (k.isEmpty() || n.lower.contains(k)) {
                found.add(n);
                if (found.size() == 9) {
                    break;
                }
            }
        }
        return found;
    }

    /** Opens a note in its own tab, choosing it. */
    void open(FilesDemo.Note n) {
        int at = open.indexOf(n);
        if (at < 0) {
            open.add(n);
            at = open.size() - 1;
        }
        tab.set(at);
    }

    /** The note the chosen tab holds, or null with none open. */
    FilesDemo.Note current() {
        return open.isEmpty() ? null : open.get(Math.min(tab.get(), open.size() - 1));
    }

    /** The actual shell workload, with one note open, shared by performance tools. */
    public static Object[] forTools() { return forTools(false); }

    /** Native key timing disables OS composition only in this benchmark fixture. */
    public static Object[] forTools(boolean rawKeys) {
        NotesDemo d = new NotesDemo();
        d.benchmarkRawKeys = rawKeys;
        d.open(d.notes.getFirst());
        return new Object[] {(jui.ui.View) d::view, d.files, d.query};
    }

    void view(Context c) {
        Theme t = c.theme();
        workspace.view(c);
        modal(c, paletteOpen, () -> palette(c, t)).width(520).label("palette");
        if (infoButton != null) {
            popover(c, infoButton, infoOpen, () -> info(c, t)).label("info");
        }
    }

    void sidebar(Context c, Theme t) {
        var colors = workspace.colors;
        column(c).fill().padding(0, 10).children(() -> {
            row(c).height(44).shrink(0).gap(8).children(() -> {
                text(c, workspace.heading()).grow(1).fontSize(13).singleLine();
                if (workspace.iconButton(c, "list-collapse", "sheets-toggle").clicked()) sheetList = false;
            });
            row(c).height(36).shrink(0).children(() -> {
                Element search = textInput(c, query).placeholder("搜索笔记").label("search").grow(1).bind(searchHandle);
                if (benchmarkRawKeys) search.inputMethod(false);
                if (focusSearch) { searchHandle.focus(); focusSearch = false; }
                if (search.changed()) files.scrollToTop();
            });
            List<FilesDemo.Note> found = shown();
            text(c, countLabel).fontSize(10).textColor(colors.muted()).height(24).shrink(0);
            if (found.isEmpty()) {
                column(c).grow(1).center().children(() -> text(c, "没有匹配的笔记").textColor(colors.muted()));
            } else {
                list(c, files, found.size(), i -> {
                    FilesDemo.Note note = found.get(i);
                    Element card = column(c.key(note.id)).height(96).padding(10).radius(7)
                            .focusable().cursor(jui.ui.Cursor.POINTER).label("note-row");
                    card.background(note == current() ? colors.card() : card.hovered() ? colors.well() : jui.ui.Color.TRANSPARENT);
                    if (card.clicked()) open(note);
                    card.children(() -> {
                        text(c, note.title).fontSize(13).bold().singleLine().height(24);
                        text(c, note.summary == null ? "打开笔记查看预览" : note.summary)
                                .fontSize(11).textColor(colors.muted()).height(36).clip();
                    });
                }).grow(1).label("notes-list");
            }
            row(c).height(44).shrink(0).children(() -> {
                if (workspace.dashedButton(c, "new-sheet", "New sheet").grow(1).clicked()) newNote();
            });
        });
    }

    void editor(Context c, Theme t) {
        column(c).fill().children(() -> {
            row(c).height(44).shrink(0).padding(0, 8).gap(6).children(() -> {
                if (workspace.iconButton(c, "toggle-navigation", "navigation").shrink(0).clicked()) navigation = !navigation;
                if (workspace.iconButton(c, "toggle-sheets", "sheets-toggle").shrink(0).clicked()) sheetList = !sheetList;
                if (!open.isEmpty()) {
                    String[] titles = new String[open.size()];
                    for (int i=0;i<titles.length;i++) {
                        String title = open.get(i).title;
                        titles[i] = title.substring(0, Math.min(12, title.length()));
                    }
                    tabs(c, tab, titles).grow(1).minWidth(0);
                    if (workspace.iconButton(c, "close-tab", "close").clicked()) closeTab();
                } else spacer(c).grow(1);
                infoButton = button(c, "信息");
                if (infoButton.clicked()) infoOpen.set(!infoOpen.get());
                if (workspace.iconButton(c, "replay", autoplay ? "stop" : "play").clicked()) {
                    autoplay = !autoplay; playbackStart = -1; playbackStep = 0;
                }
            });
            divider(c);
            if (current() == null) {
                column(c).grow(1).center().children(() -> text(c, "选择或搜索一篇笔记").textColor(t.textMuted()));
            } else workspace.document(c, previewBodies.get(current()));
        });
    }

    private void palette(Context c, Theme t) {
        column(c).gap(8).children(() -> {
            textInput(c, command).placeholder("输入笔记标题…").label("command").autoFocus();
            List<FilesDemo.Note> found = matching();
            for (FilesDemo.Note n : found) {
                Element r = row(c).height(32).padding(0, 10).focusable().label("palette-row");
                if (r.clicked()) {
                    open(n);
                    paletteOpen.set(false);
                    command.set("");
                }
                r.children(() -> text(c, n.title).singleLine());
            }
            if (found.isEmpty()) {
                text(c, "没有匹配的笔记").textColor(t.textMuted());
            }
        });
    }

    private void info(Context c, Theme t) {
        column(c).gap(4).padding(4).children(() -> {
            FilesDemo.Note n = current();
            if (n == null) {
                text(c, "没有打开的笔记").textColor(t.textMuted());
                return;
            }
            text(c, "编号 " + n.id);
            text(c, n.summary == null ? "无摘要" : n.summary).fontSize(12).textColor(t.textMuted()).singleLine();
            if (primaryButton(c, "在编辑器中打开").clicked()) {
                open(n);
                infoOpen.set(false);
            }
        });
    }

    /** The app's own menus, as App.menu declares them; {@code --print-window} prints the window's number. */
    public static void main(String[] args) {
        long start = jui.ffi.Libc.processStartMicros(), mainAt = jui.ffi.Libc.nowMicros();
        boolean quit = Arrays.asList(args).contains("--quit-after-first-frame") || Arrays.asList(args).contains("--bench");
        NotesVault vault = null;
        for (String arg : args) if (arg.startsWith("--vault="))
            vault = NotesVault.open(java.nio.file.Path.of(arg.substring(8)));
        NotesVault loaded = vault;
        NotesDemo demo = vault == null ? new NotesDemo() : new NotesDemo(vault);
        demo.autoplay = Arrays.asList(args).contains("--autoplay");
        demo.reducedMotion = Arrays.asList(args).contains("--reduced-motion");
        if (demo.current() == null && !demo.notes.isEmpty()) demo.open(demo.notes.getFirst());
        App.menu(demo::menus);
        App.run(() -> {
            long launched = jui.ffi.Libc.nowMicros();
            Window w = Window.open(WindowOptions.of("笔记").size(980, 640).content(demo::view));
            long ready = jui.ffi.Libc.nowMicros();
            if (Arrays.asList(args).contains("--print-window")) {
                System.out.println("window=" + w.windowNumber());
            }
            w.onFirstFrame(done -> {
                if (quit) {
                    System.out.printf(Locale.ROOT, "BENCH main=%.3f launched=%.3f ready=%.3f committed=%.3f frame=%.3f%n",
                            (mainAt - start) / 1000.0, (launched - start) / 1000.0, (ready - start) / 1000.0,
                            (w.firstCommitMicros() - start) / 1000.0, (done - start) / 1000.0);
                    System.exit(done > 0 ? 0 : 1);
                }
                if (loaded != null) Thread.startVirtualThread(() -> {
                    List<FilesDemo.Note> names = loaded.scan();
                    w.update(() -> {
                        demo.replaceNotes(names);
                        System.out.println("VAULT notes=" + names.size());
                    });
                });
            });
        });
    }

    void menus(Menu bar) {
        bar.submenu("文件", file -> {
            if (file.item("新建笔记").shortcut(Shortcut.cmd(Key.N)).chosen()) {
                newNote();
            }
            file.separator();
            if (file.item("关闭标签页").shortcut(Shortcut.cmd(Key.W)).disabled(open.isEmpty()).chosen()) closeTab();
        });
        bar.submenu("视图", view -> {
            if (view.item("命令面板").shortcut(Shortcut.cmd(Key.P)).chosen()) {
                paletteOpen.set(true);
            }
            if (view.item("显示笔记列表").shortcut(Shortcut.of(Mod.CMD, Key.BACKSLASH)).checked(sheetList).chosen())
                sheetList = !sheetList;
        });
    }
}
