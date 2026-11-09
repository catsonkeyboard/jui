package jui.lobe;

import jui.ui.*;
import static jui.ui.Ui.*;

/** Plain source snippet, horizontally scrollable, with an actual clipboard action. */
public final class Code {
    public static Element snippet(Context c,String language,String source) {
        Tokens t=Tokens.of(c);
        return column(c).fillWidth().radius(10).border(1,t.border).background(t.bgContainerSecondary)
                .clip().label("lobe-snippet").children(()->{
                    row(c).fillWidth().padding(6,12).children(()->{
                        Display.secondary(c,language); spacer(c); Actions.copyButton(c,source);
                    });
                    Layout.divider(c);
                    scrollHorizontal(c).fillWidth().padding(16).children(()->
                            text(c,source).fontSize(13).textColor(t.text));
                });
    }
    private Code() {}
}
