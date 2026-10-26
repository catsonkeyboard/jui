package jui.platform.mac;

import jui.testing.Check;

public final class MacPasteboardTest {
    public void testTextRoundTripsThroughAPasteboard() {
        MacPasteboard p = MacPasteboard.unique();    // not the user's clipboard
        try {
            Check.equal(null, p.read());
            p.write("剪贴板 ✓");
            Check.equal("剪贴板 ✓", p.read());
        } finally {
            p.release();
        }
    }
}
