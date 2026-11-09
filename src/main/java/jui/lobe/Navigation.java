package jui.lobe;

import java.util.function.IntConsumer;
import jui.ui.*;
import static jui.ui.Ui.*;

public final class Navigation {
    public static Element tabs(Context c,IntRef selected,String... titles) {
        Tokens t=Tokens.of(c);
        return row(c).gap(20).label("lobe-tabs").children(()->{
            for(int i=0;i<titles.length;i++) {
                int index=i; c.key(i); boolean on=selected.get()==i;
                column(c).gap(0).children(()->{
                    Element tab=buttonBase(c).height(40).padding(0,2).label("lobe-tab-"+titles[index])
                            .children(()->text(c,titles[index]).fontSize(14).fontWeight(on?600:400)
                                    .textColor(on?t.text:t.textTertiary));
                    if(tab.clicked()) selected.set(index);
                    box(c).fillWidth().height(2).radius(1).background(on?t.primary:Color.TRANSPARENT);
                });
            }
        });
    }
    public static Element breadcrumb(Context c,IntConsumer onNavigate,String... path) {
        return row(c).gap(4).alignItems(Align.CENTER).children(()->{
            for(int i=0;i<path.length;i++) {
                int index=i; c.key(i);
                if(i>0) Icon.icon(c,"chevron-right",12,Tokens.of(c).textTertiary);
                if(i==path.length-1) Display.body(c,path[i]);
                else Actions.button(c,path[i],Lobe.Variant.TEXT).onClick(()->onNavigate.accept(index));
            }
        });
    }
    /** Page numbers are one-based; render a bounded window for large page counts. */
    public static Element pagination(Context c,IntRef page,int count) {
        if(count<1) throw new IllegalArgumentException("Expected at least one page");
        int current=Math.max(1,Math.min(count,page.get()));
        return row(c).gap(4).children(()->{
            Element prev=Actions.actionIcon(c,"chevron-left","上一页").disabled(current==1);
            if(prev.clicked()) page.set(current-1);
            int start=Math.max(1,Math.min(current-2,count-4)),end=Math.min(count,start+4);
            for(int i=start;i<=end;i++) {
                int number=i; c.key(i);
                Element e=Actions.button(c,Integer.toString(i),i==current?Lobe.Variant.FILLED:Lobe.Variant.TEXT)
                        .width(32).label("lobe-page-"+i);
                if(e.clicked()) page.set(number);
            }
            Element next=Actions.actionIcon(c,"chevron-right","下一页").disabled(current==count);
            if(next.clicked()) page.set(current+1);
        });
    }
    public static Element steps(Context c,int current,String... labels) {
        Tokens t=Tokens.of(c);
        return row(c).gap(12).wrap().alignItems(Align.CENTER).children(()->{
            for(int i=0;i<labels.length;i++) {
                int index=i;
                if(i>0) box(c).size(24,1).background(t.border);
                row(c).gap(8).alignItems(Align.CENTER).children(()->{
                    box(c).size(24,24).radius(12).background(index<=current?t.primary:t.fillTertiary)
                            .center().children(()->{
                                if(index<current) Icon.icon(c,"check",14,t.dark?t.bgBase:t.white);
                                else text(c,Integer.toString(index+1)).fontSize(12)
                                        .textColor(index==current?(t.dark?t.bgBase:t.white):t.textTertiary);
                            });
                    text(c,labels[index]).fontSize(13).textColor(index<=current?t.text:t.textTertiary);
                });
            }
        });
    }
    public static Element burger(Context c,BoolRef open) {
        Element e=Actions.actionIcon(c,open.get()?"close":"menu","切换导航");
        if(e.clicked()) open.set(!open.get());
        return e;
    }
    private Navigation() {}
}
