package jui.lobe;

import java.util.Locale;
import jui.ui.*;
import static jui.ui.Ui.*;

/** Typography and compact data display. */
public final class Display {
    public static Element body(Context c,String value) {
        return text(c,value).fontSize(14).textColor(Tokens.of(c).text);
    }
    public static Element secondary(Context c,String value) {
        return text(c,value).fontSize(13).textColor(Tokens.of(c).textSecondary);
    }
    public static Element heading(Context c,String value,int level) {
        float size=switch(level) {case 1->38;case 2->30;case 3->24;case 4->20;default->16;};
        return text(c,value).fontSize(size).fontWeight(600).textColor(Tokens.of(c).textHeading);
    }
    public static Element tag(Context c,String label,Lobe.Status status) {
        return row(c).height(24).padding(0,8).radius(6).background(Lobe.background(c,status))
                .alignItems(Align.CENTER).label("lobe-tag-"+label).children(()->
                        text(c,label).fontSize(12).textColor(Lobe.color(c,status)));
    }
    public static Element hotkey(Context c,String... keys) {
        return row(c).gap(3).children(()->{
            for(String key:keys) box(c).minWidth(22).height(22).padding(0,5).radius(5)
                    .border(1,Tokens.of(c).border).background(Tokens.of(c).bgContainer)
                    .center().children(()->text(c,key).fontSize(11).textColor(Tokens.of(c).textTertiary));
        });
    }
    public static Element avatar(Context c,String name,float size) {
        String clean=name==null?"":name.strip();
        String initials=clean.isEmpty()?"?":new String(Character.toChars(clean.codePointAt(0)));
        String[] words=clean.split("\\s+");
        if(words.length>1) initials+=new String(Character.toChars(words[words.length-1].codePointAt(0)));
        final String value=initials.toUpperCase(Locale.ROOT);
        return box(c).size(size,size).radius(size*0.3f).background(Tokens.of(c).fillSecondary)
                .center().label("lobe-avatar-"+clean).children(()->
                        text(c,value).fontSize(size*0.36f).fontWeight(600).textColor(Tokens.of(c).text));
    }
    public static Element groupAvatar(Context c,float size,String... names) {
        return row(c).gap(4).children(()->{
            for(String name:names) avatar(c,name,size);
        });
    }
    public static Element empty(Context c,String title,String description,Runnable action) {
        Tokens t=Tokens.of(c);
        return column(c).fillWidth().padding(32).gap(12).center().label("lobe-empty").children(()->{
            box(c).size(48,48).radius(14).background(t.fillTertiary).center()
                    .children(()->Icon.icon(c,"file",24,t.textTertiary));
            heading(c,title,5); secondary(c,description);
            if(action!=null) action.run();
        });
    }
    public static Element skeleton(Context c,float width,float height) {
        return box(c).size(width,height).radius(6).background(Tokens.of(c).fillTertiary)
                .label("lobe-skeleton");
    }
    /** One disclosure. A caller-owned ref permits independent or exclusive groups. */
    public static Element accordion(Context c,BoolRef open,String title,Runnable content) {
        Tokens t=Tokens.of(c);
        return column(c).fillWidth().border(1,t.border).radius(10).children(()->{
            Element header=buttonBase(c).fillWidth().height(44).padding(0,16).gap(12)
                    .justify(Justify.START).label("lobe-accordion-"+title).children(()->{
                        body(c,title); spacer(c);
                        Icon.icon(c,open.get()?"chevron-down":"chevron-right",16,t.textTertiary);
                    });
            if(header.hovered()) header.background(t.bgTextHover);
            if(header.clicked()) open.set(!open.get());
            if(open.get()) column(c).fillWidth().padding(0,16,16,16).children(content);
        });
    }
    private Display() {}
}
