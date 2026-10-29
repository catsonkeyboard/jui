package jui.ui.engine;

import jui.examples.EditorSurface;
import jui.render.Scene;
import jui.testing.Check;
import jui.ui.*;
import static jui.ui.Ui.*;

public final class EditorLifecycleTest {
    public void testOldAdaptersNeverReactivateAfterBlurSwapPruneOrClose() {
        EditorSurface first = new EditorSurface("one"), second = new EditorSurface("two");
        EditorSurface[] client = {first}; boolean[] visible = {true};
        Tester tester = Tester.of(context -> {
            client[0].attach(context);
            if (visible[0]) canvas(context.key("same")).size(120, 60).handleTextInput(client[0]).autoFocus();
        }, 300, 200);
        tester.frame();
        Engine engine = tester.engine();
        TextClientAdapter original = engine.activeTextConnection();
        engine.textMark("ni", 2, 0, -1, 0);
        engine.windowFocusChanged(false);
        Check.equal(1, ((Host.Memory) engine.host()).discards);
        engine.windowFocusChanged(true);
        Check.isTrue(original != engine.activeTextConnection(), "new connection on refocus");
        original.replace("bad", -1, 0); Check.equal("oneni", first.value());
        TextClientAdapter refocused = engine.activeTextConnection();
        client[0] = second; tester.frame();
        refocused.replace("bad", -1, 0); Check.equal("two", second.value());
        TextClientAdapter swapped = engine.activeTextConnection();
        visible[0] = false; tester.frame(); visible[0] = true; tester.frame();
        swapped.replace("bad", -1, 0); Check.equal("two", second.value());
        TextClientAdapter last = engine.activeTextConnection();
        tester.close(); last.replace("bad", -1, 0);
        Check.isTrue(!last.live(), "closed adapter inert"); Check.equal("two", second.value());
    }

    public void testCallbacksCannotReenterPaintingAndClosedEngineIsInert() {
        Tester[] holder = new Tester[1]; int[] drawn = {0};
        holder[0] = Tester.of(context -> canvas(context).size(100, 80).focusable().autoFocus().draw((painter, bounds) -> drawn[0]++)
                .handleInput(event -> {
                    if (event.kind() == InputEvent.Kind.KEY_DOWN)
                        Check.fails(IllegalStateException.class, () -> holder[0].engine().paint(new Scene()));
                    return true;
                }), 200, 200);
        holder[0].frame(); holder[0].key(Shortcut.of(Key.A));
        holder[0].close(); int before = drawn[0]; holder[0].frame();
        Check.equal(before, drawn[0]);
    }
}
