package jui.jspace;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.View;

import java.util.Locale;

import static jui.ui.Ui.*;

/** The preferences window: a settings-style nav on the left, the working page on the right. */
final class SettingsWindow implements View {
    /** One nav entry; only 通用's page does anything, the rest are placeholders. */
    private static final String[][] NAV = {
            {"通用", "home"}, {"外观", "pictures"}, {"面板", "grid"}, {"标签页", "docs"},
            {"书签", "tag"}, {"暂存架", "trash"}, {"传输", "disk"}, {"扩展", "cloud"},
            {"快捷键", "docs"}, {"关于", "apps"},
    };

    private final Workspace ws;
    private final Runnable afterChange;
    private final jui.ui.TextRef filter = new jui.ui.TextRef("");
    private final jui.ui.BoolRef showHiddenRef;
    private final jui.ui.BoolRef foldersFirstRef;
    private final jui.ui.BoolRef restoreRef;
    private String page = "通用";

    SettingsWindow(Workspace ws, Runnable afterChange) {
        this.ws = ws;
        this.afterChange = afterChange;
        this.showHiddenRef = new jui.ui.BoolRef(ws.settings().showHidden);
        this.foldersFirstRef = new jui.ui.BoolRef(ws.settings().foldersFirst);
        this.restoreRef = new jui.ui.BoolRef(ws.settings().restoreWorkspace);
    }

    View view() { return this; }

    @Override
    public void build(Context c) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        row(c).fill().children(() -> {
            column(c).width(200).fillHeight().background(pal.sidebar()).label("settings-nav").children(() -> {
                box(c).height(40).fillWidth().padding(4, 10).children(() -> {
                    box(c).fillWidth().height(26).radius(6).background(pal.pill())
                            .label("settings-filter").padding(0, 6)
                            .children(() -> textInput(c, filter).placeholder("搜索").fontSize(12));
                });
                scroll(c).grow(1).padding(4, 6).children(() -> {
                    for (String[] nav : NAV) {
                        if (!filter.get().isEmpty()
                                && !nav[0].toLowerCase(Locale.ROOT)
                                        .contains(filter.get().toLowerCase(Locale.ROOT))) continue;
                        navItem(c, nav[0], nav[1], pal);
                    }
                });
            });
            box(c).grow(1).fillHeight().background(c.theme().background()).children(() -> {
                if ("通用".equals(page)) generalPage(c);
                else placeholder(c);
            });
        });
    }

    private void navItem(Context c, String title, String icon, JspaceTheme.Palette pal) {
        boolean on = title.equals(page);
        jui.ui.Element b = buttonBase(c).fillWidth().height(30).radius(6)
                .label("set-" + title).alignItems(jui.ui.Align.CENTER)
                .children(() -> row(c).gap(8).alignItems(jui.ui.Align.CENTER).children(() -> {
                    box(c).size(20, 20).radius(5)
                            .background(on ? c.theme().accent() : tileOf(title))
                            .draw(FileIcons.mini(icon, Color.rgb(0xFFFFFF)));
                    text(c, title).fontSize(13)
                            .textColor(on ? c.theme().accentText() : c.theme().text());
                }));
        if (on) b.background(c.theme().accent());
        else if (b.hovered()) b.background(pal.sidebarHover());
        if (b.clicked()) page = title;
    }

    private static Color tileOf(String title) {
        return switch (title) {
            case "外观" -> Color.rgb(0x00A2C7);
            case "面板" -> Color.rgb(0x8B5CF6);
            case "标签页" -> Color.rgb(0x3E6FF2);
            case "书签" -> Color.rgb(0xFF9F0A);
            case "暂存架" -> Color.rgb(0x30A46C);
            case "传输" -> Color.rgb(0x12A594);
            case "扩展" -> Color.rgb(0x0A84FF);
            case "快捷键" -> Color.rgb(0x6B7078);
            case "关于" -> Color.rgb(0xE5484D);
            default -> Color.rgb(0x0A84FF);
        };
    }

    private void placeholder(Context c) {
        column(c).fill().alignItems(jui.ui.Align.CENTER).label("placeholder-page").children(() -> {
            spacer(c);
            text(c, "此设置页在演示中未实现").fontSize(13).textColor(c.theme().textMuted());
            spacer(c);
        });
    }

    private void generalPage(Context c) {
        jui.jspace.store.Settings s = ws.settings();
        column(c).fill().padding(18, 24).gap(12).children(() -> {
            text(c, "通用").fontSize(16).bold().textColor(c.theme().text());

            card(c, "外观", pal -> choice(c, pal, "setrow-appearance",
                    new String[] { "跟随系统", "浅色", "深色" },
                    switch (s.appearance) { case FOLLOW -> 0; case LIGHT -> 1; case DARK -> 2; },
                    pick -> {
                        s.appearance = switch (pick) {
                            case 1 -> jui.jspace.store.Settings.Appearance.LIGHT;
                            case 2 -> jui.jspace.store.Settings.Appearance.DARK;
                            default -> jui.jspace.store.Settings.Appearance.FOLLOW;
                        };
                        ws.applyAppearance();
                        afterChange.run();
                    }));
            card(c, "默认布局", pal -> choice(c, pal, "setrow-defaultlayout",
                    new String[] { "单面板", "双面板", "2×2 四面板" },
                    switch (s.defaultLayout) { case SINGLE -> 0; case DOUBLE -> 1; case GRID -> 2; },
                    pick -> {
                        s.defaultLayout = switch (pick) {
                            case 0 -> jui.jspace.store.Settings.Layout.SINGLE;
                            case 1 -> jui.jspace.store.Settings.Layout.DOUBLE;
                            default -> jui.jspace.store.Settings.Layout.GRID;
                        };
                        afterChange.run();
                    }));
            card(c, "新标签页打开位置", pal -> choice(c, pal, "setrow-newtabat",
                    new String[] { "主目录", "当前目录" },
                    s.newTabAt == jui.jspace.store.Settings.NewTabAt.HOME ? 0 : 1,
                    pick -> {
                        s.newTabAt = pick == 0
                                ? jui.jspace.store.Settings.NewTabAt.HOME
                                : jui.jspace.store.Settings.NewTabAt.CURRENT;
                        afterChange.run();
                    }));
            card(c, "列表密度", pal -> choice(c, pal, "setrow-density",
                    new String[] { "舒适", "紧凑" },
                    s.density == jui.jspace.store.Settings.Density.COMFORTABLE ? 0 : 1,
                    pick -> {
                        s.density = pick == 0
                                ? jui.jspace.store.Settings.Density.COMFORTABLE
                                : jui.jspace.store.Settings.Density.COMPACT;
                        afterChange.run();
                    }));
            card(c, "默认排序", pal -> choice(c, pal, "setrow-defaultsort",
                    new String[] { "名称", "修改日期", "大小", "种类" },
                    s.defaultSort.ordinal(),
                    pick -> {
                        s.defaultSort = Entries.SortKey.values()[pick];
                        afterChange.run();
                    }));

            card(c, null, pal -> {
                jui.ui.Element t = toggle(c, showHiddenRef, "显示隐藏文件")
                        .label("setrow-showhidden-toggle");
                if (t.changed()) {
                    s.showHidden = showHiddenRef.get();
                    afterChange.run();
                }
            });
            card(c, null, pal -> {
                jui.ui.Element t = toggle(c, foldersFirstRef, "文件夹置顶排序")
                        .label("setrow-foldersfirst-toggle");
                if (t.changed()) {
                    s.foldersFirst = foldersFirstRef.get();
                    afterChange.run();
                }
            });
            card(c, null, pal -> {
                jui.ui.Element t = toggle(c, restoreRef, "启动时恢复上次工作区")
                        .label("setrow-restore-toggle");
                if (t.changed()) {
                    s.restoreWorkspace = restoreRef.get();
                    afterChange.run();
                }
            });

            text(c, "注意：默认布局、新标签页位置和默认排序只在新创建工作区时起作用。")
                    .fontSize(11).textColor(JspaceTheme.palette(c).warn());
        });
    }

    /** One rounded card with a row: a label on the left, the control builder's output on the right. */
    private void card(Context c, String title, java.util.function.Consumer<JspaceTheme.Palette> control) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        column(c).fillWidth().radius(10).background(c.theme().surface()).padding(10, 14).gap(8)
                .children(() -> {
                    if (title != null) {
                        text(c, title).fontSize(11).bold().textColor(c.theme().textMuted());
                    }
                    control.accept(pal);
                });
    }

    /** A dropdown: a button showing the current option, a popover listing them all. */
    private void choice(Context c, JspaceTheme.Palette pal, String id, String[] options,
                        int current, java.util.function.IntConsumer pick) {
        jui.ui.BoolRef open = new jui.ui.BoolRef(false);
        jui.ui.Element anchor = buttonBase(c).height(26).padding(0, 10).radius(6)
                .border(1, c.theme().border()).label(id).alignItems(jui.ui.Align.CENTER)
                .children(() -> text(c, options[Math.max(0, Math.min(current, options.length - 1))] + " ▾")
                        .fontSize(12).textColor(c.theme().text()));
        popover(c, anchor, open, () -> column(c).padding(4).gap(2).children(() -> {
            for (int i = 0; i < options.length; i++) {
                final int idx = i;
                final int cur = current;
                jui.ui.Element b = buttonBase(c).height(26).padding(0, 10).radius(4)
                        .children(() -> text(c, (idx == cur ? "✓ " : "") + options[idx]).fontSize(12)
                                .textColor(c.theme().text()));
                if (b.clicked()) {
                    pick.accept(idx);
                    open.set(false);
                }
            }
        }));
    }
}
