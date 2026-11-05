package jui.kit;

import jui.ui.Context;
import jui.ui.Element;

/**
 * The kit's tabs wrapper: the framework's tab list behind the kit facade,
 * after gpui-kit's Tabs.
 */
public final class Tabs {
    public static Element tabs(jui.ui.Context c, jui.ui.IntRef selected, String... titles) {
        return jui.ui.Ui.tabs(c, selected, titles);
    }

    private Tabs() {}
}
