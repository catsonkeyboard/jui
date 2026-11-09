package jui.lobe;

import jui.ui.*;
import static jui.ui.Ui.*;
import static jui.lobe.Lobe.*;

/** Buttons, icon actions, clipboard action and segmented selection. */
public final class Actions {
    public record ButtonProps(Variant variant, Size size, String icon, boolean loading, boolean disabled) {
        public static ButtonProps defaults() { return new ButtonProps(Variant.DEFAULT, Size.MIDDLE, null, false, false); }
    }
    public static Element button(Context c, String label) { return button(c,label,Variant.DEFAULT); }
    public static Element button(Context c, String label, Variant variant) {
        return button(c,label,new ButtonProps(variant,Size.MIDDLE,null,false,false));
    }
    public static Element button(Context c, String label, ButtonProps props) {
        Tokens t=Tokens.of(c);
        Size s=props.size(); Variant v=props.variant();
        boolean solid=v==Variant.PRIMARY;
        Color ink=solid ? (t.dark?t.bgBase:t.white) : v==Variant.LINK?t.link:t.text;
        Element e=buttonBase(c).height(s.height).padding(0,s.padding).gap(8).radius(s.radius)
                .disabled(props.disabled()||props.loading()).label("lobe-button-"+label);
        boolean hot=e.hovered(), pressed=e.pressed();
        Color fill=solid?(pressed?t.primaryActive:hot?t.primaryHover:t.primary)
                : v==Variant.FILLED?t.fillTertiary
                : v==Variant.DEFAULT||v==Variant.DASHED?t.bgContainer:Color.TRANSPARENT;
        if(!solid && hot) fill=pressed?t.bgTextActive:t.bgTextHover;
        e.background(fill).textColor(ink).fontSize(s.font);
        if(v==Variant.DEFAULT) e.border(1,hot?t.primaryBorder:t.border);
        if(v==Variant.DASHED) e.draw((p,b)->{
            Color border=hot?t.primaryBorder:t.border;
            float w=b.width(), h=b.height();
            for(float x=6;x<w-5;x+=7) {
                p.line(x,1,Math.min(x+4,w-5),1,1,border);
                p.line(x,h-1,Math.min(x+4,w-5),h-1,1,border);
            }
            for(float y=6;y<h-5;y+=7) {
                p.line(1,y,1,Math.min(y+4,h-5),1,border);
                p.line(w-1,y,w-1,Math.min(y+4,h-5),1,border);
            }
        });
        e.children(()->{
            if(props.loading()) Feedback.spin(c,s.font,ink);
            else if(props.icon()!=null) Icon.icon(c,props.icon(),s.font+2,ink);
            text(c,label).singleLine();
        });
        if(props.disabled()) e.opacity(0.4f);
        return e;
    }
    public static Element actionIcon(Context c,String icon,String accessibleLabel) {
        return actionIcon(c,icon,accessibleLabel,Size.MIDDLE);
    }
    public static Element actionIcon(Context c,String icon,String accessibleLabel,Size size) {
        Tokens t=Tokens.of(c);
        Element e=buttonBase(c).size(size.height,size.height).radius(size.radius)
                .label("lobe-action-"+accessibleLabel);
        if(e.hovered()) e.background(e.pressed()?t.bgTextActive:t.bgTextHover);
        return e.children(()->Icon.icon(c,icon,size.font+4,t.textSecondary));
    }
    public static Element actionIconGroup(Context c,Runnable content) {
        return row(c).gap(2).padding(4).radius(10).border(1,Tokens.of(c).border).children(content);
    }
    public static Element copyButton(Context c,String value) {
        Element e=actionIcon(c,"copy","复制");
        if(e.clicked()) c.copyText(value);
        return e;
    }
    public static Element segmented(Context c,IntRef selected,String... options) {
        Tokens t=Tokens.of(c);
        return row(c).gap(2).padding(3).radius(10).background(t.fillTertiary).children(()->{
            for(int i=0;i<options.length;i++) {
                int index=i; boolean active=selected.get()==i;
                c.key(i);
                Element e=buttonBase(c).height(28).padding(0,14).radius(7)
                        .label("lobe-segment-"+options[i]).children(()->
                            text(c,options[index]).fontSize(13).textColor(active?t.text:t.textSecondary));
                if(active) e.background(t.bgContainer).border(1,t.borderSecondary)
                        .shadow(0,1,3,0,Color.rgba(0,0.06f));
                else if(e.hovered()) e.background(t.bgTextHover);
                if(e.clicked()) selected.set(index);
            }
        });
    }
    private Actions() {}
}
