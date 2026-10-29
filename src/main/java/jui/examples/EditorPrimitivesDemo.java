package jui.examples;

import jui.app.*;
import jui.ui.*;
import static jui.ui.Ui.*;

public final class EditorPrimitivesDemo {
    final EditorSurface editor = new EditorSurface("应用自有文档、选区、组字与撤销。\n试试中文输入、跨行拖选、⌘C / ⌘V / ⌘Z。\n");
    private final BoolRef takeJ = new BoolRef(false), ime = new BoolRef(true);
    private static final Spans PREVIEW = Spans.of(Span.text("Rich text 共同排版\n").fontSize(24).bold(),
            Span.text("系统字体与 ").color(Color.rgb(0x3366CC)), Span.text("monospace italic").monospace().italic().background(Color.rgb(0xE2E8F0)).radius(3),
            Span.text("\n下划线 ").underline(), Span.text("删除线").strikethrough(), Span.text(" · 大字号").fontSize(28));

    public void view(Context context) {
        editor.attach(context);
        column(context).fill().padding(24).gap(12).children(() -> {
            text(context, "编辑器底层能力").fontSize(24).bold();
            checkbox(context, takeJ, "取走 J（不再漏入系统文字阶段）");
            checkbox(context, ime, "使用输入法");
            editor.takeJ = takeJ.get();
            Element surface = canvas(context.key("editor")).height(280).background(Color.rgb(0xF8FAFC)).border(1, Color.rgb(0xCBD5E1)).radius(8)
                    .label("自有编辑面").handleTextInput(editor).handleInput(editor.handler).inputMethod(ime.get()).autoFocus().draw(editor.drawing);
            editor.focused = surface.focused();
            contextMenu(context, surface, menu -> {
                if (menu.item("Copy").chosen()) editor.command(EditCommand.COPY);
                if (menu.item("Paste").chosen()) editor.command(EditCommand.PASTE);
                if (menu.item("Undo").chosen()) editor.command(EditCommand.UNDO);
            });
            richText(context, PREVIEW);
            text(context, "这是公开 API 示例，不是完整编辑器或 Markdown 产品。").fontSize(12);
        });
    }
    public static void main(String[] arguments) {
        EditorPrimitivesDemo demo = new EditorPrimitivesDemo();
        App.run(() -> {
            Window window = Window.open(WindowOptions.of("编辑器底层能力").size(800, 680).content(demo::view));
            if (java.util.Arrays.asList(arguments).contains("--print-window")) System.out.println("window=" + window.windowNumber());
        });
    }
}
