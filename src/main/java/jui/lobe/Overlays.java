package jui.lobe;

import java.util.function.IntConsumer;
import java.util.function.Consumer;
import jui.ui.*;
import static jui.ui.Ui.*;

/** Engine-managed overlays retain outside-click, Escape and anchor placement behavior. */
public final class Overlays {
    public static Element popover(Context c,Element anchor,BoolRef open,Runnable content) {
        Tokens t=Tokens.of(c);
        return jui.ui.Ui.popover(c,anchor,open,content).padding(6).radius(10)
                .background(t.bgElevated).border(1,t.borderSecondary)
                .shadow(0,6,24,0,Color.rgba(0,t.dark?0.4f:0.1f));
    }
    public static Element tooltip(Context c,Element anchor,String message) {
        return popover(c,anchor,new BoolRef(anchor.hovered()),()->
                text(c,message).fontSize(12).textColor(Tokens.of(c).text).padding(4,8));
    }
    public static Element dropdownMenu(Context c,Element anchor,BoolRef open,IntConsumer onSelect,String... options) {
        return popover(c,anchor,open,()->column(c).minWidth(180).gap(2).children(()->{
            for(int i=0;i<options.length;i++) {
                int index=i; c.key(i);
                Fields.menuItem(c,options[i],false).onClick(()->{open.set(false); onSelect.accept(index);});
            }
        }));
    }
    /** Native context menu using the platform menu host. */
    public static void contextMenu(Context c,Element target,Consumer<Menu> content) {
        jui.ui.Ui.contextMenu(c,target,content);
    }
    private Overlays() {}
}
