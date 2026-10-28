package jui.platform.mac;

import java.util.List;
import jui.ffi.ObjC;
import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Menu;
import jui.ui.Shortcut;

public final class MacMenuTest {
    public void testNativeTreeAndActionsHaveScopedOwnership() {
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        long pool = ObjC.poolPush();
        try {
            var model = Menu.build(menu -> {
                menu.item("one").checked(true).shortcut(Shortcut.cmd(Key.A));
                menu.separator();
                menu.submenu("sub", child -> child.item("leaf")).disabled(true);
            }, List.of(), null).model();
            for (int count = 0; count < 20; count++) {
                try (MacMenus.Popup popup = MacMenus.create(model)) {
                    Check.equal(3L, ObjC.send(popup.menu, "numberOfItems"));
                    Check.isTrue(!ObjC.sendBool(popup.menu, "autoenablesItems"), "manual enablement");
                    long item = ObjC.send(popup.menu, "itemAtIndex:", 0);
                    Check.equal(1L, ObjC.send(item, "state"));
                    Check.equal("a", ObjC.string(ObjC.send(item, "keyEquivalent")));
                    ObjC.send(popup.target, "juiContextMenuItem:", item);
                    Check.equal(model.items().get(0).id(), popup.chosen);
                    long sub = ObjC.send(ObjC.send(popup.menu, "itemAtIndex:", 2), "submenu");
                    Check.isTrue(!ObjC.sendBool(ObjC.send(sub, "itemAtIndex:", 0), "isEnabled"), "disabled parent propagates");
                }
                Check.equal(0, MacMenus.owners());
            }
        } finally {
            ObjC.poolPop(pool);
        }
    }
}
