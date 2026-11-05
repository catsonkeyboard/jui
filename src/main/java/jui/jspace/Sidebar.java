package jui.jspace;

import jui.ui.Color;
import jui.ui.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static jui.ui.Ui.*;

/** The window's sidebar: bookmarks, places, finder tags (shown only) and the workspace line. */
final class Sidebar {
    private final Workspace ws;
    private final jui.ui.TextRef filter = new jui.ui.TextRef("");
    private final jui.ui.ScrollState scrollState = new jui.ui.ScrollState();

    Sidebar(Workspace ws) { this.ws = ws; }

    private record Item(String icon, String label, Path path) {}

    void build(Context c) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        column(c).fill().background(pal.sidebar()).label("sidebar").children(() -> {
            scroll(c, scrollState).grow(1).padding(6, 8).children(() -> {
                group(c, "暂存架");
                note(c, "将文件拖到这里（演示）");
                group(c, "书签");
                for (Item i : matched(bookmarks())) item(c, i);
                List<Item> cloud = matched(cloud());
                if (!cloud.isEmpty()) {                           // the group shows only when its drive does
                    group(c, "iCloud");
                    for (Item i : cloud) item(c, i);
                }
                group(c, "服务器");
                for (Item i : matched(List.of(new Item("disk", "Macintosh HD", Path.of("/"))))) item(c, i);
                group(c, "访达标签");
                if (filter.get().isEmpty()) {
                    for (String[] tag : TAGS) tagRow(c, tag[0], Color.rgb(Integer.parseInt(tag[1], 16)));
                }
                group(c, "工作区");
                for (Item i : matched(List.of(new Item(null, "默认", null)))) item(c, i);
            });
            box(c).height(36).fillWidth().padding(4, 8).children(() -> {
                box(c).fillWidth().height(28).radius(6).background(pal.pill()).label("sidebar-filter")
                        .padding(0, 6).children(() -> {
                            jui.ui.Element in = textInput(c, filter).placeholder("过滤");
                            in.fontSize(12);
                        });
            });
        });
    }

    private static final String[][] TAGS = {
            {"蓝色", "0A84FF"}, {"重要", "FFD60A"}, {"灰色", "8E8E93"}, {"工作", "30D158"},
            {"红色", "FF453A"}, {"个人", "FF9F0A"}, {"绿色", "32D74B"}, {"橙色", "FF9F0A"},
            {"黄色", "FFD60A"}, {"紫色", "BF5AF2"},
    };

    private List<Item> bookmarks() {
        Path home = Path.of(System.getProperty("user.home"));
        return List.of(
                new Item("desktop", "桌面", home.resolve("Desktop")),
                new Item("downloads", "下载", home.resolve("Downloads")),
                new Item("docs", "文稿", home.resolve("Documents")),
                new Item("pictures", "图片", home.resolve("Pictures")),
                new Item("music", "音乐", home.resolve("Music")),
                new Item("movies", "影片", home.resolve("Movies")),
                new Item("home", "主目录", home),
                new Item("apps", "应用程序", Path.of("/Applications")),
                new Item("trash", "废纸篓", home.resolve(".Trash")));
    }

    private List<Item> cloud() {
        Path p = Path.of(System.getProperty("user.home"),
                "Library/Mobile Documents/com~apple~CloudDocs");
        return Files.isDirectory(p) ? List.of(new Item("cloud", "iCloud 云盘", p)) : List.of();
    }

    private List<Item> matched(List<Item> in) {
        String q = filter.get().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return in;
        List<Item> out = new ArrayList<>();
        for (Item i : in) if (i.label().toLowerCase(Locale.ROOT).contains(q)) out.add(i);
        return out;
    }

    private void group(Context c, String title) {
        text(c, title).fontSize(10.5f).bold().textColor(c.theme().textMuted())
                .padding(12, 6, 3, 6).label("group-" + title);
    }

    private void note(Context c, String s) {
        text(c, s).fontSize(11).textColor(c.theme().textMuted()).padding(2, 6);
    }

    private void item(Context c, Item it) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        jui.ui.Element row = buttonBase(c).fillWidth().height(27).radius(6).label("side-" + it.label())
                .padding(0, 6).justify(jui.ui.Justify.CENTER)
                .children(() -> row(c).gap(8).alignItems(jui.ui.Align.CENTER).children(() -> {
                    if (it.icon() != null) {
                        box(c).size(15, 15).draw(FileIcons.mini(it.icon(), c.theme().textMuted()));
                    } else {
                        box(c).size(15, 15);
                    }
                    text(c, it.label()).fontSize(12.5f).textColor(c.theme().text());
                }));
        if (row.hovered()) row.background(pal.sidebarHover());
        if (row.clicked() && it.path() != null) ws.navigate(ws.model().active, it.path());
    }

    private void tagRow(Context c, String name, Color dot) {
        row(c).height(25).fillWidth().padding(0, 14).gap(8).alignItems(jui.ui.Align.CENTER)
                .label("side-" + name)
                .children(() -> {
                    box(c).size(10, 10).radius(5).background(dot);
                    text(c, name).fontSize(13).textColor(c.theme().textMuted());
                });
    }
}
