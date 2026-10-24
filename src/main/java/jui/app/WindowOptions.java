package jui.app;

import jui.ui.View;

/** What a window shows and how large it opens, in DIPs. */
public record WindowOptions(String title, float width, float height, View content) {

    /** A window of 800 by 600 DIPs, without content yet. */
    public static WindowOptions of(String title) {
        return new WindowOptions(title, 800, 600, null);
    }

    public WindowOptions size(float width, float height) {
        return new WindowOptions(title, width, height, content);
    }

    public WindowOptions content(View content) {
        return new WindowOptions(title, width, height, content);
    }
}
