package jui.ui;

import java.util.function.IntConsumer;
import jui.testing.Check;
import jui.ui.engine.Host;
import static jui.ui.Ui.*;

public final class ContextMenuTest {
    public void testOrdinaryMenusDoNotStealFocusAndNoMenuStillReportsRightClick() {
        int[] right = {0};
        Tester tester = Tester.of(context -> {
            button(context, "focus");
            contextMenu(context, button(context, "target"), menu -> menu.item("one"));
            if (button(context, "plain").rightClicked()) right[0]++;
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.rightClick("target");
        Check.isTrue(tester.focused("focus"), "ordinary menu leaves focus alone");
        tester.closeMenu();
        tester.rightClick("plain");
        Check.equal(1, right[0]);
        Check.isTrue(tester.menu() == null, "plain right click is not a menu");
    }

    public void testInvalidTargetsAndUnregistering() {
        Context[] original = new Context[1];
        Element[] saved = new Element[1];
        Tester first = Tester.of(context -> {
            original[0] = context;
            saved[0] = button(context, "first");
        }, 300, 240);
        first.frame();
        Check.fails(IllegalStateException.class, () -> Tester.of(context ->
                contextMenu(context, saved[0], menu -> menu.item("bad")), 300, 240).frame());
        Check.fails(IllegalStateException.class, () -> contextMenu(original[0], saved[0], menu -> menu.item("bad")));
        Tester tester = Tester.of(context -> {
            Element target = button(context, "target");
            contextMenu(context, target, menu -> menu.item("one"));
            contextMenu(context, target, null);
        }, 300, 240);
        tester.rightClick("target");
        Check.isTrue(tester.menu() == null, "last registration wins");
    }

    public void testOpensOnlyOnDemandAndChoosesOnce() {
        int[] builds = {0}, chosen = {0}, clicks = {0};
        Tester tester = Tester.of(context -> {
            Element target = button(context, "target");
            if (target.clicked() || target.rightClicked()) clicks[0]++;
            contextMenu(context, target, menu -> {
                builds[0]++;
                if (menu.item("one").chosen()) chosen[0]++;
            });
        }, 300, 240);
        tester.frame();
        Check.equal(0, builds[0]);
        tester.rightClick("target");
        Check.equal(1, builds[0]);
        tester.frame();
        Check.equal(1, builds[0]);
        tester.chooseMenuItem("one");
        tester.frame();
        Check.equal(2, builds[0]);
        Check.equal(1, chosen[0]);
        Check.equal(0, clicks[0]);
        tester.click("target");
        Check.equal(1, clicks[0]);
    }

    public void testInnermostMenuAndDisabledParent() {
        boolean[] disabled = {false};
        Tester tester = Tester.of(context -> {
            Element outer = column(context).children(() -> {
                Element inner = button(context, "inner");
                contextMenu(context, inner, menu -> menu.item("inside"));
            }).disabled(disabled[0]);
            contextMenu(context, outer, menu -> menu.item("outside"));
        }, 300, 240);
        tester.rightClick("inner");
        Check.equal("inside", tester.menu().items().get(0).label());
        tester.closeMenu();
        disabled[0] = true;
        tester.frame();
        tester.rightClick("inner");
        Check.isTrue(tester.menu() == null, "disabled ancestry has no menu");
    }

    public void testChangedPathsAndDisabledChoicesDoNotFire() {
        String[] label = {"sub"};
        int[] count = {0};
        Tester tester = Tester.of(context -> {
            Element target = button(context, "target");
            contextMenu(context, target, menu -> menu.submenu(label[0], child -> {
                if (child.item("one").chosen()) count[0]++;
            }));
        }, 300, 240);
        tester.rightClick("target");
        label[0] = "changed";
        tester.chooseMenuItem("sub", "one");
        Check.equal(0, count[0]);
    }

    public void testLateCallbackCannotReachAReusedIdentity() {
        boolean[] present = {true};
        int[] count = {0};
        Host.Memory host = new Host.Memory();
        Tester tester = Tester.of(context -> {
            if (present[0]) contextMenu(context, button(context.key("same"), "target"), menu -> {
                if (menu.item("one").chosen()) count[0]++;
            });
        }, 300, 240);
        tester.engine().setHost(host);
        tester.rightClick("target");
        IntConsumer old = host.menuResult;
        int item = host.menuModel.items().get(0).id();
        present[0] = false;
        tester.frame();
        present[0] = true;
        tester.frame();
        old.accept(item);
        old.accept(item);
        tester.frame();
        Check.equal(0, count[0]);
    }

    public void testKeyboardAndShortcutsHaveTheExpectedPriority() {
        int[] shortcuts = {0};
        boolean[] claim = {false};
        Tester tester = Tester.of(context -> {
            if (claim[0] && context.shortcut(Shortcut.of(Mod.SHIFT, Key.F10))) shortcuts[0]++;
            contextMenu(context, button(context, "target"), menu -> menu.item("one"));
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.SHIFT, Key.F10));
        Check.isTrue(tester.menu() != null, "Shift F10 opens");
        tester.closeMenu();
        claim[0] = true;
        tester.frame();
        tester.key(Shortcut.of(Mod.SHIFT, Key.F10));
        Check.isTrue(tester.menu() == null, "shortcut wins");
        Check.equal(1, shortcuts[0]);
        tester.key(Shortcut.of(Key.CONTEXT_MENU));
        Check.isTrue(tester.menu() != null, "menu key opens");
    }

    public void testEmptyCustomMenuReplacesDefaultAndExceptionsRecover() {
        boolean[] fail = {true};
        TextRef value = new TextRef("abc");
        Tester tester = Tester.of(context -> contextMenu(context,
                textInput(context, value).label("input"), menu -> {
                    if (fail[0]) throw new IllegalStateException("builder failed");
                }), 300, 240);
        Check.fails(IllegalStateException.class, () -> tester.rightClick("input"));
        fail[0] = false;
        tester.rightClick("input");
        Check.isTrue(tester.menu() == null, "empty explicit menu does not fall back");
    }
}
