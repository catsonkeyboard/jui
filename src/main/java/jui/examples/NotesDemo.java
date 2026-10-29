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
import static jui.ui.Ui.richText;
import static jui.ui.Ui.row;
import static jui.ui.Ui.spacer;
import static jui.ui.Ui.split;
import static jui.ui.Ui.tabs;
import static jui.ui.Ui.text;
import static jui.ui.Ui.textInput;

/**
 * The Obsidian-like shell of design §6.15, as the M5 acceptance: a sidebar
 * of 10000 notes beside an editor, split at a draggable divider; tabs of the
 * open notes over a read-only preview; a command palette as a modal, ⌘P in
 * the app's own menu bar; a popover of the selected note's facts off a
 * button in the editor. The editor itself is later work (a real editor is
 * not M5).
 *
 * <pre>./build.sh demo Notes</pre>
 */
public final class NotesDemo {
    /** The one the menu bar's builders talk to; made in main, or by a test. */
    static NotesDemo demo;

    final List<FilesDemo.Note> notes = new FilesDemo().notes;
    final ListState files = new ListState();
    final TextRef query = new TextRef("");
    final FloatRef sidebarWidth = new FloatRef(240);
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
        if (!q.equals(filteredFor)) {
            filteredFor = q;
            shown = new ArrayList<>();
            String k = q.isEmpty() ? null : q.toLowerCase(Locale.ROOT);
            for (FilesDemo.Note n : notes) {
                if (k == null || n.lower.contains(k)) {
                    shown.add(n);
                }
            }
        }
        return shown;
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

    void view(Context c) {
        Theme t = c.theme();
        split(c, sidebarWidth, () -> sidebar(c, t), () -> editor(c, t)).grow(1);
        modal(c, paletteOpen, () -> palette(c, t)).width(520).label("palette");
        if (infoButton != null) {
            popover(c, infoButton, infoOpen, () -> info(c, t)).label("info");
        }
    }

    private void sidebar(Context c, Theme t) {
        column(c).fill().background(t.surface()).children(() -> {
            row(c).padding(8).children(() -> {
                if (textInput(c, query).placeholder("搜索笔记").grow(1).changed()) {
                    files.scrollToTop();
                }
            });
            divider(c);
            List<FilesDemo.Note> found = shown();
            if (found.isEmpty()) {
                column(c).grow(1).center().children(() -> text(c, "没有匹配的笔记").textColor(t.textMuted()));
            } else {
                list(c, files, found.size(), i -> {
                    FilesDemo.Note n = found.get(i);
                    boolean chosen = n == current();
                    Element r = row(c.key(n.id)).height(32).padding(0, 10).focusable().label("note-row");
                    if (chosen) {
                        r.background(t.accent()).textColor(t.accentText());
                    }
                    if (r.clicked()) {
                        open(n);
                    }
                    r.children(() -> text(c, n.title).singleLine());
                }).grow(1);
            }
        });
    }

    private void editor(Context c, Theme t) {
        column(c).fill().children(() -> {
            row(c).padding(6, 8).gap(8).children(() -> {
                if (!open.isEmpty()) {
                    String[] titles = new String[open.size()];
                    for (int i = 0; i < titles.length; i++) {
                        String title = open.get(i).title;
                        titles[i] = title.substring(0, Math.min(12, title.length()));
                    }
                    tabs(c, tab, titles).grow(1);
                } else {
                    spacer(c).grow(1);
                }
                infoButton = button(c, "信息");
                if (infoButton.clicked()) {
                    infoOpen.set(!infoOpen.get());
                }
            });
            divider(c);
            FilesDemo.Note n = current();
            if (n == null) {
                column(c).grow(1).center().children(() -> text(c, "选择或搜索一篇笔记").textColor(t.textMuted()));
            } else {
                column(c).grow(1).padding(16).clip().children(() -> {
                    text(c, n.title).bold().fontSize(18);
                    richText(c, PREVIEW);
                });
            }
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
        demo = new NotesDemo();
        App.menu(NotesDemo::menus);
        App.run(() -> {
            Window w = Window.open(WindowOptions.of("笔记").size(980, 640).content(demo::view));
            if (Arrays.asList(args).contains("--print-window")) {
                System.out.println("window=" + w.windowNumber());
            }
        });
    }

    private static void menus(Menu bar) {
        bar.submenu("文件", file -> {
            if (file.item("新建笔记").shortcut(Shortcut.cmd(Key.N)).chosen()) {
                demo.open(new FilesDemo.Note(System.nanoTime(), "新建笔记", null));
            }
            file.separator();
            file.item("关闭窗口").shortcut(Shortcut.cmd(Key.W));
        });
        bar.submenu("视图", view -> {
            if (view.item("命令面板").shortcut(Shortcut.cmd(Key.P)).chosen()) {
                demo.paletteOpen.set(true);
            }
            view.item("显示侧栏").shortcut(Shortcut.of(Mod.CMD, Key.BACKSLASH)).checked(true);
        });
    }
}
