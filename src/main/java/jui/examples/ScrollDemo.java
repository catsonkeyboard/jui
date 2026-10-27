package jui.examples;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.ListState;
import jui.ui.ScrollState;
import jui.ui.Theme;

import static jui.ui.Ui.box;
import static jui.ui.Ui.button;
import static jui.ui.Ui.checkbox;
import static jui.ui.Ui.column;
import static jui.ui.Ui.list;
import static jui.ui.Ui.row;
import static jui.ui.Ui.scrollHorizontal;
import static jui.ui.Ui.spacer;
import static jui.ui.Ui.text;

/**
 * The scroll, list and layout follow-ups: a strip of cards that scrolls
 * across in a rounded, clipped container, moved by buttons with an
 * animation; a log that follows its end, its rows keyed; check boxes whose
 * marks are strokes.
 *
 * <pre>./build.sh demo Scroll</pre>
 */
public final class ScrollDemo {
    private static final Duration GLIDE = Duration.ofMillis(300);
    private static final Color[] CARDS = {
        Color.rgb(0x2563EB), Color.rgb(0x7C3AED), Color.rgb(0xDB2777), Color.rgb(0xEA580C), Color.rgb(0x16A34A),
    };
    private static final Color WHITE = Color.rgb(0xFFFFFF);

    /** A log entry: its number, which keys its row, and its line. */
    record Entry(long n, String line) {}

    final ScrollState cards = new ScrollState();
    final List<Entry> entries = new ArrayList<>();
    final ListState log = new ListState().followEnd(true).keys(i -> entries.get(i).n());
    final BoolRef[] checks = {new BoolRef(true), new BoolRef(true), new BoolRef(false)};
    private final String[] cardTitles = new String[20];

    ScrollDemo() {
        for (int i = 0; i < 30; i++) {
            append();
        }
        for (int i = 0; i < cardTitles.length; i++) {
            cardTitles[i] = "卡片 " + (i + 1);
        }
    }

    private void append() {
        long n = entries.size() + 1;
        entries.add(new Entry(n, "第 " + n + " 条日志"));
    }

    void view(Context c) {
        Theme t = c.theme();
        column(c).fill().padding(20).gap(12).children(() -> {
            text(c, "横向卡片").fontSize(16).bold();
            // The cards as high as the strip: its rounded corners cut those passing its ends.
            scrollHorizontal(c, cards).height(124).radius(16).border(1, t.border()).clip().padding(0, 12).gap(12)
                    .label("卡片").children(() -> {
                for (int i = 0; i < cardTitles.length; i++) {
                    String title = cardTitles[i];
                    box(c).size(140, 122).background(CARDS[i % CARDS.length]).padding(12).focusable()
                            .label(title).children(() -> text(c, title).textColor(WHITE).bold());
                }
            });
            row(c).gap(8).children(() -> {
                if (button(c, "往左（动画）").clicked()) {
                    cards.scrollTo(cards.x() - 400, 0, GLIDE);
                }
                if (button(c, "往右（动画）").clicked()) {
                    cards.scrollTo(cards.x() + 400, 0, GLIDE);
                }
            });

            row(c).gap(8).children(() -> {
                text(c, "日志").fontSize(16).bold();
                spacer(c);
                text(c, log.atEnd() ? "跟随末尾" : "已离开末尾").textColor(t.textMuted());
            });
            // Striped rows: the list's rounded corners cut the stripes at its top and bottom.
            list(c, log, entries.size(), i -> {
                Element r = row(c).padding(5, 12);
                if (entries.get(i).n() % 2 == 0) {
                    r.background(t.surface());
                }
                r.children(() -> text(c, entries.get(i).line()));
            }).height(200).radius(12).border(1, t.border()).label("日志");
            row(c).gap(8).children(() -> {
                if (button(c, "追加 5 行").clicked()) {
                    for (int k = 0; k < 5; k++) {
                        append();
                    }
                }
                if (button(c, "回到末尾").clicked()) {
                    log.scrollToEnd();
                }
            });

            text(c, "复选框").fontSize(16).bold();
            row(c).gap(20).children(() -> {
                checkbox(c, checks[0], "描边的勾号");
                checkbox(c, checks[1], "两段折线");
                checkbox(c, checks[2], "圆头圆角");
            });
        });
    }

    public static void main(String[] args) {
        ScrollDemo s = new ScrollDemo();
        App.run(() -> {
            Window w = Window.open(WindowOptions.of("滚动和列表").size(560, 640).content(s::view));
            if (Arrays.asList(args).contains("--print-window")) {
                System.out.println("window=" + w.windowNumber());
            }
        });
    }
}
