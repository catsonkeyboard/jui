package jui.ui;

import java.util.List;
import jui.platform.MenuModel;
import jui.testing.Check;

public final class MenuTest {
    public void testTreeStatesAndFrozenItems() {
        Menu.Result result = Menu.build(menu -> {
            Check.isTrue(!menu.item("one").checked(true).shortcut(Shortcut.cmd(Key.A)).chosen(), "collect never chooses");
            menu.separator();
            menu.submenu("sub", child -> child.item("leaf")).disabled(true);
            menu.editItems();
        }, List.of(), null);
        Check.equal(3, result.model().items().size());
        Check.isTrue(result.model().items().get(0).checked(), "checked");
        Check.isTrue(!result.model().items().get(2).children().get(0).enabled(), "late disabled parent applies");
        Check.fails(UnsupportedOperationException.class, () -> result.model().items().clear());
    }

    public void testChoiceIsOnceAndMatchesTheWholePath() {
        int[] count = {0};
        java.util.function.Consumer<Menu> build = menu -> menu.submenu("sub", child -> {
            MenuItem item = child.item("leaf");
            if (item.chosen()) count[0]++;
            if (item.chosen()) count[0]++;
        });
        MenuModel.Item choice = Menu.build(build, List.of(), null).model().items().get(0).children().get(0);
        Check.isTrue(Menu.build(build, List.of(), choice).chosen(), "choice delivered");
        Check.equal(1, count[0]);
        Check.isTrue(!Menu.build(menu -> menu.submenu("other", child -> {
            if (child.item("leaf").chosen()) count[0]++;
        }), List.of(), choice).chosen(), "ancestor label matters");
        Check.equal(1, count[0]);
        Check.isTrue(!Menu.build(menu -> menu.submenu("sub", child -> {
            if (child.item("leaf").chosen()) count[0]++;
        }).disabled(true), List.of(), choice).chosen(), "disabled ancestor blocks replay");
    }

    public void testEveryHandleExpiresAfterItsCallback() {
        Menu[] savedMenu = new Menu[1];
        MenuItem[] savedItem = new MenuItem[1];
        Menu.build(menu -> { savedMenu[0] = menu; savedItem[0] = menu.item("one"); }, List.of(), null);
        Check.fails(IllegalStateException.class, () -> savedMenu[0].separator());
        Check.fails(IllegalStateException.class, () -> savedItem[0].chosen());
        Menu.build(menu -> Check.fails(IllegalStateException.class, () -> savedItem[0].checked(true)), List.of(), null);
    }
}
