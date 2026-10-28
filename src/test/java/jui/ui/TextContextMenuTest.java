package jui.ui;

import java.util.Arrays;
import jui.testing.Check;
import jui.platform.MenuModel;
import static jui.ui.Ui.*;

public final class TextContextMenuTest {
    public void testDefaultMenuCopiesCutsPastesAndUndoes() {
        TextRef value = new TextRef("one\ntwo");
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 120).label("input"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Key.CONTEXT_MENU));
        Check.equal("[Cut, Copy, Paste, , Select All]", tester.menu().items().stream().map(MenuModel.Item::label).toList().toString());
        tester.chooseMenuItem("Copy");
        Check.equal(value.get(), tester.clipboard());
        tester.key(Shortcut.of(Key.CONTEXT_MENU));
        tester.chooseMenuItem("Cut");
        Check.equal("", value.get());
        tester.key(Shortcut.of(Key.CONTEXT_MENU));
        tester.chooseMenuItem("Paste");
        Check.equal("one\ntwo", value.get());
        tester.command(EditCommand.UNDO);
        Check.equal("", value.get());
    }

    public void testReadonlyAndPasswordPolicies() {
        for (boolean password : new boolean[] {false, true}) {
            TextRef value = new TextRef("secret");
            Tester tester = Tester.of(context -> {
                Element input = textInput(context, value).readOnly(!password).label("input");
                if (password) input.password();
            }, 300, 240);
            tester.key(Shortcut.of(Key.TAB));
            tester.key(Shortcut.of(Key.CONTEXT_MENU));
            Check.equal(password ? 5 : 3, tester.menu().items().size());
            if (password) {
                Check.isTrue(!tester.menu().items().get(0).enabled() && !tester.menu().items().get(1).enabled(), "password does not export");
            } else {
                tester.chooseMenuItem("Copy");
                Check.equal("secret", tester.clipboard());
            }
        }
    }

    public void testEditItemsAndPasteStayBoundToTheirOriginalTarget() {
        TextRef first = new TextRef("first"), second = new TextRef("second");
        int[] pastes = {0, 0};
        Tester tester = Tester.of(context -> {
            Element input = textInput(context, first).label("first").onPaste(text -> { pastes[0]++; return false; });
            contextMenu(context, input, menu -> menu.submenu("edit", Menu::editItems));
            textInput(context, second).label("second").onPaste(text -> { pastes[1]++; return false; });
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Key.CONTEXT_MENU));
        tester.click("second");
        tester.setClipboard("new\nvalue");
        tester.chooseMenuItem("edit", "Paste");
        Check.equal("new value", first.get());
        Check.equal("second", second.get());
        Check.equal("[1, 0]", Arrays.toString(pastes));
        Check.isTrue(tester.focused("second"), "menu command does not steal focus");
    }

    public void testChangedPolicyAndChangedEditorInvalidateUnsafeCommands() {
        TextRef value = new TextRef("secret");
        boolean[] password = {false}, multiline = {false};
        Tester tester = Tester.of(context -> {
            Element input = multiline[0] ? textArea(context, value) : textInput(context, value);
            input.label("input");
            if (password[0]) input.password();
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Key.CONTEXT_MENU));
        password[0] = true;
        tester.chooseMenuItem("Cut");
        Check.equal("secret", value.get());
        Check.isTrue(tester.clipboard() == null, "no secret in clipboard");
        tester.key(Shortcut.of(Key.CONTEXT_MENU));
        multiline[0] = true;
        tester.setClipboard("bad");
        tester.chooseMenuItem("Paste");
        Check.equal("secret", value.get());
    }

    public void testRightClickPreservesOrMovesAMultilineSelection() {
        TextRef value = new TextRef("ab\r\n😀中");
        Tester tester = Tester.of(context -> textArea(context, value).fontSize(13).size(220, 120).label("input"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        Rect input = tester.find("input");
        tester.rightClickAt(input.x() + 15, input.y() + 10);
        Check.equal("[0, 7]", Arrays.toString(tester.textSelection()));
        tester.closeMenu();
        tester.key(Shortcut.of(Mod.CTRL, Key.HOME));
        tester.rightClickAt(input.x() + 200, input.y() + 40);
        Check.equal("[7, 7]", Arrays.toString(tester.textSelection()));
    }

    public void testOnTextKeyCanTakeTheMenuKey() {
        TextRef value = new TextRef("abc");
        Tester tester = Tester.of(context -> textInput(context, value).label("input")
                .onTextKey(key -> key.key() == Key.CONTEXT_MENU), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Key.CONTEXT_MENU));
        Check.isTrue(tester.menu() == null, "onTextKey wins");
    }

    public void testOpeningAndClosingACompositionReportsOneChange() {
        TextRef value = new TextRef("a");
        int[] changes = {0};
        Tester tester = Tester.of(context -> {
            if (textArea(context, value).size(220, 120).label("input").changed()) changes[0]++;
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.CTRL, Key.END));
        tester.compose("ni", 2);
        tester.rightClick("input");
        Check.equal("ani", value.get());
        tester.closeMenu();
        tester.frame();
        Check.equal(1, changes[0]);
    }
}
