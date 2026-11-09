package jui.lobe;

import java.time.Duration;
import jui.ui.*;
import static jui.ui.Ui.*;

/** Stateful choices; refs belong to the application and every control supports keyboard focus. */
public final class Choices {
    public static Element checkbox(Context c,BoolRef value,String label) {
        Tokens t=Tokens.of(c);
        Element e=toggleBase(c,value).gap(8).height(28).label("lobe-checkbox-"+label);
        return e.children(()->{
            box(c).size(16,16).radius(4).background(value.get()?t.primary:t.bgContainer)
                    .border(1,value.get()?t.primary:t.primaryBorder).center().children(()->{
                        if(value.get()) Icon.icon(c,"check",13,t.dark?t.bgBase:t.white);
                    });
            text(c,label).fontSize(14).textColor(t.text);
        });
    }
    public static Element switchControl(Context c,BoolRef value,String label) {
        Tokens t=Tokens.of(c);
        Element e=toggleBase(c,value).gap(10).height(28).label("lobe-switch-"+label);
        float pos=e.animate("lobe-switch",value.get()?1:0,Duration.ofMillis(140));
        return e.children(()->{
            row(c).size(36,22).padding(3).radius(11).background(value.get()?t.primary:t.fill)
                    .children(()->box(c).size(16,16).radius(8).margin(0,0,0,14*pos)
                            .background(value.get() && t.dark?t.bgBase:t.white));
            text(c,label).fontSize(14).textColor(t.text);
        });
    }
    public static Element radioGroup(Context c,IntRef selected,String... options) {
        Tokens t=Tokens.of(c);
        return column(c).gap(8).children(()->{
            for(int i=0;i<options.length;i++) {
                int index=i; c.key(i); boolean on=selected.get()==i;
                Element e=buttonBase(c).height(24).gap(8).justify(Justify.START)
                        .label("lobe-radio-"+options[i]).children(()->{
                            box(c).size(16,16).radius(8).border(1,on?t.primary:t.primaryBorder)
                                    .center().children(()->{
                                        if(on) box(c).size(8,8).radius(4).background(t.primary);
                                    });
                            text(c,options[index]).fontSize(14).textColor(t.text);
                        });
                if(e.clicked()) selected.set(index);
            }
        });
    }
    public static Element slider(Context c,DoubleRef value,double min,double max,double step) {
        if(!Double.isFinite(min)||!Double.isFinite(max)||min>=max || !Double.isFinite(step)||step<0)
            throw new IllegalArgumentException("Expected finite min < max and nonnegative step");
        return jui.ui.Ui.slider(c,value,min,max,step).fillWidth().label("lobe-slider");
    }
    public static Element rate(Context c,IntRef value,int count) {
        Tokens t=Tokens.of(c);
        return row(c).gap(4).children(()->{
            for(int i=1;i<=count;i++) {
                int n=i; c.key(i);
                Element e=buttonBase(c).size(26,28).label("lobe-rate-"+i).children(()->
                        text(c,"★").fontSize(24).textColor(value.get()>=n?t.warning:t.fill));
                if(e.clicked()) value.set(value.get()==n?0:n);
            }
        });
    }
    public static Element colorSwatches(Context c,IntRef selected,Color... colors) {
        return row(c).gap(8).wrap().children(()->{
            for(int i=0;i<colors.length;i++) {
                int index=i; c.key(i);
                Element e=buttonBase(c).size(28,28).radius(14).background(colors[i])
                        .border(selected.get()==i?3:1,Tokens.of(c).border)
                        .label("lobe-swatch-"+i);
                if(e.clicked()) selected.set(index);
            }
        });
    }
    private Choices() {}
}
