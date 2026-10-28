package jui.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.Context;
import jui.ui.Key;
import jui.ui.TextKey;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

public final class TextAreaDemo {
    final TextRef notes = new TextRef(("这是一段可编辑的多行备注，支持中英文混排与 soft wrapping。\n"
            + "使用方向键移动光标，按住 Shift 选择，或者在这里试试拼音输入。\n\n").repeat(8));
    final TextRef draft = new TextRef("");
    final TextRef preview = new TextRef("只读多行预览。\n可以选择文字并复制，不能编辑。\n发送的消息会显示在这里。");
    final List<String> messages = new ArrayList<>();

    private boolean send(TextKey key) {
        if (key.key() != Key.ENTER || key.mods() != 0) return false;
        String message = key.text().strip();
        if (!message.isEmpty()) {
            messages.add(message);
            preview.set(message);
        }
        key.setText("");
        return true;
    }

    void view(Context context) {
        column(context).fill().padding(24).gap(12).children(() -> {
            text(context, "多行文本框").fontSize(22).bold();
            text(context, "备注 · 固定高度，框内滚动").textColor(context.theme().textMuted());
            textArea(context.key("notes"), notes).height(180).label("备注框");
            text(context, "消息 · 自动增高至四行").textColor(context.theme().textMuted());
            textArea(context.key("draft"), draft).lines(1, 4).placeholder("回车发送，Shift+回车换行")
                    .label("消息框").onTextKey(this::send);
            text(context, "只读预览 · 可选择和复制").textColor(context.theme().textMuted());
            textArea(context.key("preview"), preview).height(110).readOnly(true).label("预览框");
            text(context, "试试跨行拖选、⌘C/⌘V、⌘Z，以及滚动后等待光标闪烁。")
                    .fontSize(12).textColor(context.theme().textMuted());
        });
    }

    public static void main(String[] arguments) {
        TextAreaDemo demo = new TextAreaDemo();
        App.run(() -> {
            Window window = Window.open(WindowOptions.of("多行文本框").size(540, 720).content(demo::view));
            if (Arrays.asList(arguments).contains("--print-window")) {
                System.out.println("window=" + window.windowNumber());
            }
        });
    }
}
