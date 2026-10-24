package jui.examples;

import java.util.Arrays;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.box;
import static jui.ui.Ui.column;
import static jui.ui.Ui.row;
import static jui.ui.Ui.spacer;
import static jui.ui.Ui.text;

/** The prototype's card of text, rebuilt with the UI API: the M1 acceptance example. */
public final class CardDemo {
    private CardDemo() {}

    private static final Color PAGE = Color.rgb(0xF3F4F6);
    private static final Color WHITE = Color.rgb(0xFFFFFF);
    private static final Color CARD_BORDER = Color.rgb(0xE5E7EB);
    private static final Color CARD_SHADOW = Color.rgba(0x0F172A, 0x26 / 255f);
    private static final Color INK = Color.rgb(0x111827);
    private static final Color BODY = Color.rgb(0x1F2937);
    private static final Color MUTED = Color.rgb(0x6B7280);
    private static final Color ACCENT = Color.rgb(0x2563EB);
    private static final Color ACCENT_SHADOW = Color.rgba(0x2563EB, 0x55 / 255f);
    private static final Color BUTTON_BORDER = Color.rgb(0xD1D5DB);
    private static final Color ROSE = Color.rgb(0xF43F5E);
    private static final Color[] GREEN = {Color.rgb(0xDCFCE7), Color.rgb(0x86EFAC), Color.rgb(0x166534)};
    private static final Color[] BLUE = {Color.rgb(0xDBEAFE), Color.rgb(0x93C5FD), Color.rgb(0x1E40AF)};
    private static final Color[] AMBER = {Color.rgb(0xFEF3C7), Color.rgb(0xFCD34D), Color.rgb(0x92400E)};

    private static final String PARAGRAPH = "Java 通过 FFM 直接调用 Core Text：由它找字体，为缺字的字体回退"
            + "（中文落到苹方），再整形和断行。The quick brown fox jumps over the lazy dog. "
            + "中英文混排、标点挤压和自动换行，都交给系统处理，和 AppKit 画出来的一样。";
    private static final String[] WEIGHT_NAMES = {"Regular 常规", "Medium 中等", "Semibold 中粗", "Bold 粗体"};
    private static final int[] WEIGHTS = {400, 500, 600, 700};

    public static void view(Context c) {
        box(c).fill().background(PAGE).padding(32).children(() ->
            column(c).grow(1).background(WHITE).border(1, CARD_BORDER).radius(16)
                .shadow(0, 8, 24, 0, CARD_SHADOW).padding(26, 32, 32, 32).children(() -> {
                    text(c, "j-ui 文字渲染").fontSize(26).bold().textColor(INK);
                    text(c, "Core Text 排版 · Core Graphics 光栅化 · Metal 绘制").fontSize(13).textColor(MUTED)
                        .margin(2, 0, 0, 0);
                    text(c, PARAGRAPH).fontSize(16).textColor(BODY).margin(16, 0, 0, 0);
                    row(c).gap(24).margin(16, 0, 0, 0).children(() -> {
                        for (int i = 0; i < WEIGHT_NAMES.length; i++) {
                            text(c, WEIGHT_NAMES[i]).fontSize(15).fontWeight(WEIGHTS[i]).textColor(INK);
                        }
                    });
                    text(c, "11pt 小字号：永和九年，岁在癸丑，暮春之初，会于会稽山阴之兰亭。0123456789")
                        .fontSize(11).textColor(MUTED).margin(12, 0, 0, 0);
                    row(c).gap(10).margin(16, 0, 0, 0).children(() -> {
                        tag(c, "已完成", GREEN);
                        tag(c, "Metal", BLUE);
                        tag(c, "GraalVM 25", AMBER);
                    });
                    spacer(c);
                    row(c).gap(16).children(() -> {
                        button(c, "确定 OK", ACCENT, null, WHITE).shadow(0, 4, 12, 0, ACCENT_SHADOW);
                        button(c, "取消 Cancel", WHITE, BUTTON_BORDER, INK);
                        spacer(c);
                        box(c).size(44, 44).radius(22).background(ROSE);
                    });
                }));
    }

    /** A rounded tag around a label, in colors {fill, border, text}. */
    private static Element tag(Context c, String label, Color[] colors) {
        return row(c).height(28).padding(0, 12).radius(14).background(colors[0]).border(1, colors[1])
            .children(() -> text(c, label).fontSize(13).fontWeight(500).textColor(colors[2]));
    }

    private static Element button(Context c, String label, Color fill, Color border, Color color) {
        Element b = row(c).size(152, 44).radius(10).background(fill).center();
        if (border != null) {
            b.border(1, border);
        }
        return b.children(() -> text(c, label).fontSize(15).fontWeight(500).textColor(color));
    }

    public static void main(String[] args) {
        App.run(() -> {
            Window w = Window.open(WindowOptions.of("j-ui 卡片示例").size(760, 560).content(CardDemo::view));
            if (Arrays.asList(args).contains("--print-window")) {
                System.out.println("window=" + w.windowNumber());
            }
        });
    }
}
