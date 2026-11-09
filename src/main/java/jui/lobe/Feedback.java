package jui.lobe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import jui.ui.*;
import static jui.ui.Ui.*;

/** Feedback surfaces. Toast state belongs to one window, never a global queue. */
public final class Feedback {
    public static Element spin(Context c,float size,Color color) {
        double angle=(c.now()%1_000_000_000L)/1e9*Math.PI*2;
        c.after(Duration.ofMillis(33));
        return box(c).size(size,size).label("lobe-spin").draw((p,b)->{
            float r=size*0.36f, mid=size/2;
            for(int i=0;i<9;i++) {
                double a=angle+i*Math.PI/6,z=a+Math.PI/6;
                p.line(mid+(float)Math.cos(a)*r,mid+(float)Math.sin(a)*r,
                        mid+(float)Math.cos(z)*r,mid+(float)Math.sin(z)*r,1.8f,color);
            }
        });
    }
    public static Element progress(Context c,double value,Lobe.Status status) {
        float fraction=(float)(Double.isFinite(value)?Math.max(0,Math.min(1,value)):0);
        return box(c).fillWidth().height(6).radius(3).background(Tokens.of(c).fillTertiary)
                .label("lobe-progress").children(()->box(c).height(6).widthPercent(fraction*100)
                        .radius(3).background(status==Lobe.Status.NEUTRAL?Tokens.of(c).primary:Lobe.color(c,status)));
    }
    public static Element badge(Context c,String label,Lobe.Status status) {
        return row(c).gap(6).alignItems(Align.CENTER).label("lobe-badge-"+label).children(()->{
            box(c).size(6,6).radius(3).background(Lobe.color(c,status));
            Display.secondary(c,label);
        });
    }
    public static Element alert(Context c,String title,String description,Lobe.Status status) {
        return row(c).fillWidth().padding(14,16).gap(12).radius(10).alignItems(Align.START)
                .background(Lobe.background(c,status)).label("lobe-alert-"+title).children(()->{
                    Icon.icon(c,status==Lobe.Status.SUCCESS?"check":status==Lobe.Status.WARNING?"warning":"info",
                            18,Lobe.color(c,status));
                    column(c).grow(1).gap(4).children(()->{
                        text(c,title).fontSize(14).fontWeight(500).textColor(Lobe.color(c,status));
                        if(description!=null && !description.isBlank()) Display.secondary(c,description);
                    });
                });
    }
    public static Element result(Context c,String title,String description,Lobe.Status status,Runnable action) {
        return column(c).fillWidth().padding(28).gap(12).center().children(()->{
            box(c).size(48,48).radius(24).background(Lobe.background(c,status)).center()
                    .children(()->Icon.icon(c,status==Lobe.Status.SUCCESS?"check":"info",26,Lobe.color(c,status)));
            Display.heading(c,title,4); Display.secondary(c,description);
            if(action!=null) action.run();
        });
    }
    public static Element modal(Context c,BoolRef open,String title,Runnable body,Runnable footer) {
        Tokens t=Tokens.of(c);
        Element panel=jui.ui.Ui.modal(c,open,()->column(c).fillWidth().gap(20).children(()->{
            row(c).fillWidth().gap(12).children(()->{
                Display.heading(c,title,4); spacer(c);
                if(Actions.actionIcon(c,"close","关闭弹窗").clicked()) open.set(false);
            });
            body.run();
            if(footer!=null) row(c).fillWidth().gap(8).justify(Justify.END).children(footer);
        })).width(440).padding(24).radius(16).background(t.bgElevated).border(1,t.borderSecondary)
                .label("lobe-modal");
        if(panel.dismissed()) c.invalidate();
        return panel;
    }
    /** A modal side panel; uses the same focus trap and dismissal as modal. Build at the root. */
    public static Element drawer(Context c,BoolRef open,String title,float width,Runnable body) {
        Tokens t=Tokens.of(c);
        Element panel=jui.ui.Ui.modal(c,open,()->column(c).fill().gap(20).children(()->{
            row(c).fillWidth().children(()->{
                Display.heading(c,title,4); spacer(c);
                if(Actions.actionIcon(c,"close","关闭抽屉").clicked()) open.set(false);
            });
            scroll(c).grow(1).fillWidth().children(body);
        })).absolute().right(0).top(0).bottom(0).width(width).maxHeight(100000)
                .padding(24).radius(16,0,0,16).background(t.bgElevated).label("lobe-drawer");
        if(panel.dismissed()) c.invalidate();
        return panel;
    }
    public static final class Toasts {
        private record Item(long id,String text,Lobe.Status status,long expires) {}
        private final List<Item> items=new ArrayList<>();
        private long nextId;
        public void show(Context c,String message,Lobe.Status status) {
            items.add(new Item(++nextId,message,status,c.now()+Duration.ofSeconds(3).toNanos()));
            if(items.size()>4) items.removeFirst();
            c.invalidate();
        }
        public int size() { return items.size(); }
        public Element host(Context c) {
            items.removeIf(item->item.expires()<=c.now());
            return column(c).absolute().top(16).right(16).width(300).gap(8).children(()->{
                for(Item item:items) {
                    c.key(item.id());
                    c.after(Duration.ofNanos(Math.max(1,item.expires()-c.now())));
                    row(c).fillWidth().padding(12).radius(10).border(1,Tokens.of(c).border)
                            .background(Tokens.of(c).bgElevated).gap(8)
                            .shadow(0,4,16,0,Color.rgba(0,0.08f)).label("lobe-toast").children(()->{
                                Icon.icon(c,item.status()==Lobe.Status.SUCCESS?"check":"info",16,Lobe.color(c,item.status()));
                                text(c,item.text()).grow(1).fontSize(13).textColor(Tokens.of(c).text);
                                Actions.actionIcon(c,"close","关闭通知",Lobe.Size.SMALL)
                                        .onClick(()->items.remove(item));
                            });
                }
            });
        }
    }
    private Feedback() {}
}
