package jui.ui;

import java.util.ArrayList;
import java.util.List;
import jui.testing.Check;
import static jui.ui.Ui.*;

public final class RawInputTest {
    public void testTakenTabAndShortcutPriority() {
        List<Key> keys = new ArrayList<>();
        int[] shortcut = {0};
        Tester tester = Tester.of(context -> {
            if (context.shortcut(Shortcut.cmd(Key.A))) shortcut[0]++;
            canvas(context).size(100, 60).focusable().label("canvas").handleInput(event -> {
                if (event.kind() == InputEvent.Kind.KEY_DOWN) keys.add(event.key());
                return true;
            });
            button(context, "next");
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Key.TAB));
        Check.isTrue(tester.focused("canvas"), "taken Tab keeps focus");
        tester.key(Shortcut.cmd(Key.A));
        Check.equal(1, shortcut[0]);
        Check.equal(List.of(Key.TAB), keys);
    }

    public void testPointerCaptureAndCancellation() {
        List<InputEvent.Kind> kinds = new ArrayList<>();
        boolean[] disabled = {false};
        Tester tester = Tester.of(context -> canvas(context).size(100, 60).label("canvas")
                .disabled(disabled[0]).handleInput(event -> { kinds.add(event.kind()); return true; }), 300, 240);
        tester.press(20, 20);
        tester.move(250, 200);
        tester.release(250, 200);
        Check.isTrue(kinds.contains(InputEvent.Kind.POINTER_UP), "release outside is captured");
        kinds.clear();
        tester.press(20, 20);
        disabled[0] = true;
        tester.frame();
        Check.isTrue(kinds.contains(InputEvent.Kind.POINTER_CANCEL), "disabled cancels capture");
    }

    public void testEventExpiresAndRightClickCanBlockMenu() {
        InputEvent[] saved = new InputEvent[1];
        Tester tester = Tester.of(context -> {
            Element target = canvas(context).size(100, 60).label("canvas").handleInput(event -> {
                if (saved[0] != null) Check.fails(IllegalStateException.class, () -> saved[0].key());
                saved[0] = event;
                return true;
            });
            contextMenu(context, target, menu -> menu.item("one"));
        }, 300, 240);
        tester.rightClick("canvas");
        Check.isTrue(tester.menu() == null, "raw right click wins");
        Check.fails(IllegalStateException.class, () -> saved[0].clipboardText());
    }
}
