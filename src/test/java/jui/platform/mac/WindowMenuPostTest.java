package jui.platform.mac;

import java.util.List;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ffi.ObjC;
import jui.testing.Check;
import jui.ui.Menu;

public final class WindowMenuPostTest {
    public void testPostingDoesNotTrackInsideTheCallerAndCloseInvalidatesIt() {
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        Window window = Window.open(WindowOptions.of("posted menu").size(200, 150));
        int[] callbacks = {0}, choice = {-1};
        MacApp.post(window::close);
        try {
            window.popupMenu(Menu.build(menu -> menu.item("one"), List.of(), null).model(), 20, 20, id -> {
                callbacks[0]++;
                choice[0] = id;
            });
            Check.equal(0, callbacks[0]);
            MacWindowFrameTest.pump(1, () -> callbacks[0] != 0);
            Check.equal(1, callbacks[0]);
            Check.equal(0, choice[0]);
        } finally {
            window.close();
        }
    }
}
