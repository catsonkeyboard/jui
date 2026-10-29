package jui.examples;

import jui.testing.Check;
import jui.ui.*;

public final class EditorPrimitivesDemoTest {
    public void testApplicationScrollUsesFrameworkDirection() {
        EditorPrimitivesDemo demo = new EditorPrimitivesDemo();
        Tester tester = Tester.of(demo::view, 800, 650);
        tester.frame(); demo.editor.reset("line\n".repeat(100)); tester.frame();
        float previous = demo.editor.scrollY();
        tester.scroll("自有编辑面", 0, -40);
        Check.isTrue(demo.editor.scrollY() < previous, "negative scroll moves toward document start");
    }

    public void testApplicationEditorTypingCompositionAndUndo() {
        EditorPrimitivesDemo demo = new EditorPrimitivesDemo();
        Tester tester = Tester.of(demo::view, 800, 650);
        tester.frame();
        tester.key(Shortcut.cmd(Key.A)); tester.type("hello\n中😀");
        Check.equal("hello\n中😀", demo.editor.value());
        tester.compose("ni", 2); tester.compose("nih", 3); tester.type("你");
        Check.equal("hello\n中😀你", demo.editor.value());
        tester.command(EditCommand.UNDO);
        Check.equal("hello\n中😀", demo.editor.value());
        tester.command(EditCommand.REDO);
        Check.equal("hello\n中😀你", demo.editor.value());
        tester.key(Shortcut.of(Key.ENTER)); tester.type("line");
        Check.isTrue(demo.editor.value().endsWith("\nline"), "application inserts newline");
        tester.key(Shortcut.cmd(Key.A)); tester.command(EditCommand.COPY);
        Check.equal(demo.editor.value(), tester.clipboard());
    }
}
