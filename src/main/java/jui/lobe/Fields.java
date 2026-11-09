package jui.lobe;

import java.util.Locale;
import jui.ui.*;
import static jui.ui.Ui.*;

/** Text fields use j-ui's native text stack, including IME, selection and undo. */
public final class Fields {
    public static Element input(Context c,TextRef value,String placeholder) {
        return input(c,value,placeholder,Lobe.Size.MIDDLE);
    }
    public static Element input(Context c,TextRef value,String placeholder,Lobe.Size size) {
        return style(c,textInputBase(c,value),size).height(size.height)
                .placeholder(placeholder).label("lobe-input");
    }
    private static Element style(Context c,Element e,Lobe.Size size) {
        Tokens t=Tokens.of(c);
        return e.fillWidth().padding(6,size.padding).fontSize(size.font).textColor(t.text)
                .background(t.bgContainer).radius(size.radius)
                .border(1,e.focused()?t.primary:e.hovered()?t.primaryBorder:t.border);
    }
    public static Element textArea(Context c,TextRef value,String placeholder,float height) {
        return style(c,jui.ui.Ui.textArea(c,value),Lobe.Size.MIDDLE).height(height)
                .placeholder(placeholder).label("lobe-textarea");
    }
    public static Element password(Context c,TextRef value,String placeholder) {
        return input(c,value,placeholder).password().label("lobe-password");
    }
    public static Element searchBar(Context c,TextRef query,String placeholder) {
        Tokens t=Tokens.of(c);
        return row(c).fillWidth().height(36).gap(8).padding(0,10).radius(8)
                .background(t.fillTertiary).label("lobe-search").children(()->{
                    Icon.icon(c,"search",16,t.textTertiary);
                    textInputBase(c,query).fillWidth().height(32).fontSize(13)
                            .placeholder(placeholder).label("lobe-search-input");
                    if(!query.get().isEmpty()) {
                        Element clear=Actions.actionIcon(c,"close","清除搜索",Lobe.Size.SMALL);
                        if(clear.clicked()) query.set("");
                    }
                });
    }
    /** Label, optional error, and an application-supplied control. */
    public static Element formItem(Context c,String label,String error,Runnable control) {
        Tokens t=Tokens.of(c);
        return column(c).fillWidth().gap(8).label("lobe-field-"+label).children(()->{
            text(c,label).fontSize(13).fontWeight(500).textColor(t.text);
            control.run();
            if(error!=null && !error.isBlank()) text(c,error).fontSize(12).textColor(t.errorText);
        });
    }
    public static Element select(Context c,IntRef selected,BoolRef open,String... options) {
        Tokens t=Tokens.of(c);
        String current=selected.get()>=0 && selected.get()<options.length?options[selected.get()]:"请选择";
        Element trigger=buttonBase(c).fillWidth().height(32).padding(0,12).radius(8)
                .border(1,open.get()?t.primary:t.border).background(t.bgContainer)
                .justify(Justify.START).gap(8).label("lobe-select").children(()->{
                    text(c,current).singleLine().fontSize(14).textColor(t.text);
                    spacer(c);
                    Icon.icon(c,"chevron-down",16,t.textTertiary);
                });
        if(trigger.clicked()) open.set(!open.get());
        Overlays.popover(c,trigger,open,()->column(c).minWidth(180).gap(2).children(()->{
            for(int i=0;i<options.length;i++) {
                int index=i; c.key(i);
                Element item=menuItem(c,options[i],selected.get()==i);
                if(item.clicked()) { selected.set(index); open.set(false); }
            }
        }));
        return trigger;
    }
    static Element menuItem(Context c,String label,boolean selected) {
        Tokens t=Tokens.of(c);
        Element e=buttonBase(c).fillWidth().height(32).padding(0,10).radius(6).gap(12)
                .justify(Justify.START).label("lobe-option-"+label).children(()->{
                    text(c,label).fontSize(13).textColor(t.text); spacer(c);
                    if(selected) Icon.icon(c,"check",14,t.text);
                });
        if(selected || e.hovered()) e.background(t.bgTextHover);
        return e;
    }
    /** Suggestions filter case-insensitively; caller owns popup state. */
    public static Element autoComplete(Context c,TextRef value,BoolRef open,String... options) {
        Element e=input(c,value,"输入以筛选").label("lobe-autocomplete");
        if(e.changed()) open.set(true);
        Overlays.popover(c,e,open,()->column(c).minWidth(180).children(()->{
            int matches=0;
            for(String option:options) {
                if(!option.toLowerCase(Locale.ROOT).contains(value.get().toLowerCase(Locale.ROOT))) continue;
                matches++; c.key(option);
                if(menuItem(c,option,false).clicked()) { value.set(option); open.set(false); }
            }
            if(matches==0) text(c,"没有匹配项").padding(8).fontSize(12).textColor(Tokens.of(c).textTertiary);
        }));
        return e;
    }
    /** Text remains editable while incomplete; submit or blur validation belongs to the form. */
    public static Element inputNumber(Context c,DoubleRef value,double min,double max,double step) {
        if(!Double.isFinite(min)||!Double.isFinite(max)||min>max||!Double.isFinite(step)||step<=0)
            throw new IllegalArgumentException("Expected finite min <= max and positive step");
        Element root=row(c).gap(8).alignItems(Align.CENTER).label("lobe-number");
        NumberState state=local(root,"number",()->new NumberState(value.get())).get();
        if(Double.compare(state.last,value.get())!=0) {
            state.text.set(Double.toString(value.get())); state.last=value.get();
        }
        return root.children(()->{
            Element down=Actions.actionIcon(c,"minus","减少");
            down.disabled(value.get()<=min);
            if(down.clicked()) {
                value.set(Math.max(min,value.get()-step)); state.text.set(Double.toString(value.get()));
            }
            Element field=input(c,state.text,"0").width(88).label("lobe-number-input");
            if(field.changed()) {
                try {
                    double next=Double.parseDouble(state.text.get());
                    if(Double.isFinite(next)) value.set(Math.max(min,Math.min(max,next)));
                } catch(NumberFormatException ignored) { /* retain incomplete numeric input */ }
            }
            if(field.submitted()) state.text.set(Double.toString(value.get()));
            Element up=Actions.actionIcon(c,"plus","增加");
            up.disabled(value.get()>=max);
            if(up.clicked()) {
                value.set(Math.min(max,value.get()+step)); state.text.set(Double.toString(value.get()));
            }
            state.last=value.get();
        });
    }
    private static final class NumberState {
        final TextRef text; double last;
        NumberState(double value) { text=new TextRef(Double.toString(value)); last=value; }
    }
    /** Display/edit mode, Enter commits and Escape cancels without changing the original. */
    public static Element editableText(Context c,TextRef value,TextRef draft,BoolRef editing) {
        if(!editing.get()) {
            Element e=Actions.button(c,value.get(),Lobe.Variant.TEXT).label("lobe-editable");
            if(e.clicked()) { draft.set(value.get()); editing.set(true); }
            return e;
        }
        Element e=input(c,draft,"输入文本").autoFocus().label("lobe-editable-input");
        if(e.submitted()) { value.set(draft.get()); editing.set(false); }
        if(e.shortcut(Shortcut.of(Key.ESCAPE))) editing.set(false);
        return e;
    }
    private Fields() {}
}
