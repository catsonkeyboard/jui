package jui.lobe;

import jui.ui.*;
import static jui.ui.Ui.*;

/** Quiet surfaces, wrapping grids and application chrome. */
public final class Layout {
    public static Element block(Context c,Runnable content) {
        Tokens t=Tokens.of(c);
        return column(c).fillWidth().padding(20).gap(16).radius(12).border(1,t.border)
                .background(t.bgContainer).label("lobe-block").children(content);
    }
    public static Element divider(Context c) {
        return box(c).fillWidth().height(1).shrink(0).background(Tokens.of(c).borderSecondary);
    }
    public static Element grid(Context c,float gap,Runnable content) {
        return row(c).fillWidth().wrap().gap(gap).alignItems(Align.STRETCH).children(content);
    }
    /** Use inside grid; fills available row width while maintaining the minimum. */
    public static Element cell(Context c,float minWidth,Runnable content) {
        return column(c).minWidth(minWidth).basis(minWidth).grow(1).children(content);
    }
    public static Element scrollArea(Context c,ScrollState state,Runnable content) {
        return scroll(c,state).fill().clip().label("lobe-scroll").children(content);
    }
    public static Element header(Context c,String title,Runnable actions) {
        return row(c).fillWidth().height(64).padding(0,24).gap(16).shrink(0)
                .background(Tokens.of(c).bgContainer).children(()->{
                    Display.heading(c,title,5); spacer(c);
                    if(actions!=null) actions.run();
                });
    }
    public static Element sideNav(Context c,IntRef selected,String... entries) {
        Tokens t=Tokens.of(c);
        return column(c).fillWidth().gap(4).children(()->{
            for(int i=0;i<entries.length;i++) {
                int index=i; boolean active=selected.get()==i; c.key(i);
                Element e=buttonBase(c).fillWidth().height(36).padding(0,12).radius(8)
                        .justify(Justify.START).label("lobe-nav-"+entries[i]).children(()->
                                text(c,entries[index]).fontSize(13).fontWeight(active?600:400)
                                        .textColor(active?t.text:t.textSecondary));
                if(active||e.hovered()) e.background(t.bgTextHover);
                if(e.clicked()) selected.set(index);
            }
        });
    }
    public static Element draggablePanel(Context c,FloatRef width,Runnable left,Runnable right) {
        return split(c,width,left,right);
    }
    public static Element guideCard(Context c,String icon,String title,String description,Runnable action) {
        return block(c,()->{
            Icon.icon(c,icon,24,Tokens.of(c).text);
            Display.heading(c,title,5); Display.secondary(c,description);
            if(action!=null) action.run();
        });
    }
    private Layout() {}
}
