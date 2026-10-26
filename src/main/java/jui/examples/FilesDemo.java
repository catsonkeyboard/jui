package jui.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.ListState;
import jui.ui.TextRef;
import jui.ui.Theme;

import static jui.ui.Ui.button;
import static jui.ui.Ui.column;
import static jui.ui.Ui.divider;
import static jui.ui.Ui.list;
import static jui.ui.Ui.row;
import static jui.ui.Ui.spacer;
import static jui.ui.Ui.text;
import static jui.ui.Ui.textInput;

/**
 * A list of 10000 notes, as an Obsidian-like file list: only the rows in
 * view are built, some rows are taller (a summary line), a click selects a
 * note, and a search box filters the titles as you type (in Chinese too,
 * through the input method). Titles come from a small vocabulary, so their
 * glyphs fit the atlas until it can grow (M6).
 *
 * <pre>./build.sh demo Files</pre>
 */
public final class FilesDemo {
    static final class Note {
        final long id;
        final String title;
        final String summary;
        /** The title in lower case, for the search. */
        final String lower;

        Note(long id, String title, String summary) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.lower = title.toLowerCase(Locale.ROOT);
        }
    }

    private static final String[] WORDS = {
        "会议纪要", "产品规划", "读书笔记", "周报", "日记", "灵感", "待办", "旅行计划", "健身记录", "菜谱",
        "学习清单", "项目复盘", "需求文档", "设计草稿", "面试准备", "年度总结", "月度目标", "预算", "购物清单", "电影推荐",
        "技术方案", "架构设计", "接口说明", "测试用例", "发布记录", "问题排查", "性能分析", "代码评审", "知识卡片", "名词解释",
        "季度回顾", "客户反馈", "竞品调研", "用户访谈", "数据报告", "市场分析", "合同要点", "法务问答", "招聘计划", "团队建设",
        "Java", "Rust", "Go", "GraalVM", "Metal", "Core Text", "flexbox", "SQLite", "Markdown", "Obsidian",
        "春天", "夏天", "秋天", "冬天", "北京", "上海", "杭州", "深圳", "成都", "西安",
    };

    final List<Note> notes = new ArrayList<>();
    final ListState files = new ListState();
    final TextRef query = new TextRef("");
    Note selected;
    /** The notes the query matches, made again only when the query changes, and their count. */
    private String filteredFor;
    private List<Note> shown;
    private String countText;

    FilesDemo() {
        Random r = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            String title = String.format("笔记 %04d · %s %s", i + 1, WORDS[r.nextInt(WORDS.length)],
                    WORDS[r.nextInt(WORDS.length)]);
            String summary = r.nextInt(7) == 0
                    ? "摘要：" + WORDS[r.nextInt(WORDS.length)] + "、" + WORDS[r.nextInt(WORDS.length)] + "和"
                            + WORDS[r.nextInt(WORDS.length)]
                    : null;
            notes.add(new Note(i + 1, title, summary));
        }
    }

    /** The notes whose titles hold the query, case aside. */
    private List<Note> shown() {
        String q = query.get();
        if (!q.equals(filteredFor)) {
            filteredFor = q;
            if (q.isEmpty()) {
                shown = notes;
            } else {
                String k = q.toLowerCase(Locale.ROOT);
                shown = new ArrayList<>();
                for (Note n : notes) {
                    if (n.lower.contains(k)) {
                        shown.add(n);
                    }
                }
            }
            countText = shown.size() + " 篇笔记";
        }
        return shown;
    }

    /** A new demo's view and its list's state, for tools that build it without a window. */
    public static Object[] forTools() {
        FilesDemo s = new FilesDemo();
        return new Object[] {(jui.ui.View) s::view, s.files, s.query};
    }

    void view(Context c) {
        Theme t = c.theme();
        column(c).fill().children(() -> {
            row(c).padding(12, 16, 0, 16).children(() -> {
                if (textInput(c, query).placeholder("搜索笔记").grow(1).changed()) {
                    files.scrollToTop();
                }
            });
            List<Note> found = shown();
            row(c).padding(12, 16).gap(8).children(() -> {
                text(c, countText).bold();
                spacer(c);
                if (files.first() >= 0) {
                    text(c, "第 " + (files.first() + 1) + "–" + (files.last() + 1) + " 篇").textColor(t.textMuted());
                }
            });
            divider(c);
            if (found.isEmpty()) {
                column(c).grow(1).center().children(() -> text(c, "没有匹配的笔记").textColor(t.textMuted()));
            } else {
                list(c, files, found.size(), i -> noteRow(c, found.get(i), t)).grow(1);
            }
            divider(c);
            row(c).padding(8, 16).children(() -> {
                if (button(c, "回到顶部").clicked()) {
                    files.scrollToTop();
                }
            });
        });
    }

    private void noteRow(Context c, Note n, Theme t) {
        Element r = column(c.key(n.id)).padding(8, 16).gap(2);
        if (r.clicked()) {
            selected = n;
        }
        boolean chosen = n == selected;
        if (chosen) {
            r.background(t.accent()).textColor(t.accentText());
        } else if (r.hovered()) {
            r.background(t.surfaceHover());
        }
        r.children(() -> {
            text(c, n.title).singleLine();
            if (n.summary != null) {
                text(c, n.summary).fontSize(12).textColor(chosen ? t.accentText() : t.textMuted()).singleLine();
            }
        });
    }

    public static void main(String[] args) {
        FilesDemo s = new FilesDemo();
        App.run(() -> {
            Window w = Window.open(WindowOptions.of("笔记").size(360, 640).content(s::view));
            if (Arrays.asList(args).contains("--print-window")) {
                System.out.println("window=" + w.windowNumber());
            }
        });
    }
}
