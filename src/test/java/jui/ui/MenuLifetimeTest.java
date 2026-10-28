package jui.ui;

import java.util.function.IntConsumer;
import jui.testing.Check;
import jui.ui.engine.Host;
import static jui.ui.Ui.*;

public final class MenuLifetimeTest {
    public void testDuplicateCancellationCannotEraseAnAcceptedChoice() {
        Host.Memory host = new Host.Memory();
        int[] count = {0};
        Tester tester = Tester.of(context -> contextMenu(context, button(context, "target"), menu -> {
            if (menu.item("one").chosen()) count[0]++;
        }), 300, 240);
        tester.engine().setHost(host);
        tester.rightClick("target");
        IntConsumer result = host.menuResult;
        result.accept(host.menuModel.items().get(0).id());
        result.accept(0);
        tester.frame();
        Check.equal(1, count[0]);
    }

    public void testANewRequestDoesNotLoseAnAlreadyAcceptedChoice() {
        Host.Memory host = new Host.Memory();
        int[] count = {0};
        Tester tester = Tester.of(context -> contextMenu(context, button(context, "target"), menu -> {
            if (menu.item("one").chosen()) count[0]++;
        }), 300, 240);
        tester.engine().setHost(host);
        tester.rightClick("target");
        IntConsumer result = host.menuResult;
        result.accept(host.menuModel.items().get(0).id());
        Rect target = tester.find("target");
        tester.engine().pointerDown(target.x() + 5, target.y() + 5, 1, 1, 0);
        tester.frame();
        Check.equal(1, count[0]);
        Check.isTrue(host.menuModel != null, "the newer request still opens");
    }

    public void testNewerSessionRejectsOldCallbacksAndCloseRejectsAll() {
        Host.Memory host = new Host.Memory();
        int[] count = {0};
        Tester tester = Tester.of(context -> contextMenu(context, button(context, "target"), menu -> {
            if (menu.item("one").chosen()) count[0]++;
        }), 300, 240);
        tester.engine().setHost(host);
        tester.rightClick("target");
        IntConsumer old = host.menuResult;
        int id = host.menuModel.items().get(0).id();
        tester.rightClick("target");
        old.accept(id);
        tester.frame();
        Check.equal(0, count[0]);
        IntConsumer current = host.menuResult;
        tester.engine().closeMenus();
        current.accept(id);
        tester.frame();
        Check.equal(0, count[0]);
        Check.isTrue(host.menuModel == null, "close drops platform snapshot");
    }

    public void testSettlementDuringAnOpenRequestDoesNotLoseChanged() {
        TextRef value = new TextRef("a");
        boolean[] ime = {true};
        int[] changed = {0};
        Tester tester = Tester.of(context -> {
            if (textArea(context, value).label("input").inputMethod(ime[0]).changed()) changed[0]++;
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.CTRL, Key.END));
        tester.compose("ni", 2);
        ime[0] = false;
        tester.key(Shortcut.of(Key.CONTEXT_MENU));
        tester.settle();
        Check.equal("ani", value.get());
        Check.equal(1, changed[0]);
    }
}
