package jui.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Key;
import jui.ui.TextRef;
import jui.ui.Theme;

import static jui.ui.Ui.column;
import static jui.ui.Ui.row;
import static jui.ui.Ui.text;
import static jui.ui.Ui.textInput;
import static jui.ui.Ui.toggle;

/**
 * The text input's options: a read-only field to select and copy from, a
 * password field with a switch that shows it, a message box where Enter
 * sends (onTextKey) and a long paste is refused (onPaste), and a field that
 * types without the input method.
 *
 * <pre>./build.sh demo Form</pre>
 */
public final class FormDemo {
    private static final int PASTE_LIMIT = 200;

    final TextRef account = new TextRef("jui-2026-1027");
    final TextRef password = new TextRef("hunter2");
    final BoolRef showPassword = new BoolRef(false);
    final TextRef draft = new TextRef("");
    final TextRef command = new TextRef("");
    final List<String> messages = new ArrayList<>();
    /** A paste the message box refused, to say so. */
    private String notice;

    /** Enter sends what the box holds and clears it; Shift+Enter and the rest go on. */
    private boolean sendKey(jui.ui.TextKey k) {
        if (k.key() != Key.ENTER || k.mods() != 0) {
            return false;
        }
        String t = k.text().strip();
        if (!t.isEmpty()) {
            messages.add(t);
        }
        k.setText("");
        return true;
    }

    /** A paste longer than the limit is refused, and said so. */
    private boolean refuseLong(String s) {
        if (s.length() > PASTE_LIMIT) {
            notice = "粘贴的内容超过 " + PASTE_LIMIT + " 个字符，没有粘贴";
            return true;
        }
        notice = null;
        return false;
    }

    void view(Context c) {
        Theme t = c.theme();
        column(c).fill().padding(24).gap(10).children(() -> {
            text(c, "表单").fontSize(22).bold();

            text(c, "账号 ID").textColor(t.textMuted());
            textInput(c, account).label("账号框").readOnly(true);

            text(c, "密码").textColor(t.textMuted());
            row(c).gap(12).children(() -> {
                Element in = textInput(c, password).placeholder("密码").label("密码框").grow(1);
                if (!showPassword.get()) {
                    in.password();
                }
                toggle(c, showPassword, "显示密码");
            });

            text(c, "消息").textColor(t.textMuted());
            textInput(c, draft).placeholder("输入消息，回车发送").label("消息框")
                    .onTextKey(this::sendKey).onPaste(this::refuseLong);
            if (notice != null) {
                text(c, notice).textColor(t.textMuted());
            }
            column(c).gap(4).children(() -> {
                for (int i = 0; i < messages.size(); i++) {
                    text(c.key(i), messages.get(i));
                }
            });

            text(c, "命令").textColor(t.textMuted());
            textInput(c, command).placeholder("这里打字不经过输入法").label("命令框").inputMethod(false);
        });
    }

    public static void main(String[] args) {
        FormDemo s = new FormDemo();
        App.run(() -> {
            Window w = Window.open(WindowOptions.of("表单").size(480, 600).content(s::view));
            if (Arrays.asList(args).contains("--print-window")) {
                System.out.println("window=" + w.windowNumber());
            }
        });
    }
}
