package jui.lobe;

import java.time.Duration;
import jui.examples.LobeDemo;
import jui.testing.Check;
import jui.ui.*;
import static jui.ui.Ui.*;

public final class LobeTest {
    private static Tester tester(View view) {
        return Tester.of(c->{Lobe.apply(c);column(c).fill().padding(20).gap(12).children(()->view.build(c));},640,600);
    }
    public void testThemeFollowsSystemAndTokensAreStable() {
        Tester t=tester(c->Actions.button(c,"theme"));
        try {
            t.frame(); Check.equal(Lobe.light(),t.engine().theme());
            t.setDark(true); Check.equal(Lobe.dark(),t.engine().theme());
            Check.equal(Color.rgb(0xEEEEEE),Tokens.of(t.engine().context()).primary);
            t.setDark(false); Check.equal(Lobe.light(),t.engine().theme());
            Check.equal(Color.rgb(0xE3E3E3),Tokens.light().color("Border"));
            Check.fails(IllegalArgumentException.class,()->Tokens.light().color("Typo"));
        } finally {t.close();}
    }
    public void testButtonsDisableAndKeyboardActivation() {
        int[] count={0};
        Tester t=tester(c->{
            Actions.button(c,"enabled").onClick(()->count[0]++);
            Actions.button(c,"disabled",new Actions.ButtonProps(Lobe.Variant.PRIMARY,Lobe.Size.MIDDLE,null,false,true))
                    .onClick(()->count[0]+=100);
            Actions.button(c,"loading",new Actions.ButtonProps(Lobe.Variant.PRIMARY,Lobe.Size.MIDDLE,null,true,false))
                    .onClick(()->count[0]+=100);
        });
        try {
            t.frame();t.click("lobe-button-disabled");t.click("lobe-button-loading");Check.equal(0,count[0]);
            t.click("lobe-button-enabled");Check.equal(1,count[0]);
            t.key(Shortcut.of(Key.SPACE));Check.equal(2,count[0]);
            Check.near(32,t.find("lobe-button-enabled").height());
        } finally {t.close();}
    }
    public void testAllVariantsAndSizesPaintOnBothThemes() {
        Tester t=tester(c->{
            for(Lobe.Variant v:Lobe.Variant.values()) Actions.button(c,v.name(),v);
            for(Lobe.Size s:Lobe.Size.values()) Actions.button(c,s.name(),
                    new Actions.ButtonProps(Lobe.Variant.DEFAULT,s,"plus",false,false));
        });
        try {
            t.frame(); Check.near(24,t.find("lobe-button-SMALL").height());
            Check.near(40,t.find("lobe-button-LARGE").height());
            t.setDark(true);Check.isTrue(t.instances()>100,"dark frame paints");
        } finally {t.close();}
    }
    public void testInputImePasswordAndTextAreaEditing() {
        TextRef input=new TextRef(""),password=new TextRef("secret"),area=new TextRef("");
        Tester t=tester(c->{Fields.input(c,input,"Name");Fields.password(c,password,"Password");Fields.textArea(c,area,"Notes",100);});
        try {
            t.frame();t.click("lobe-input");t.compose("中文",2);t.type("中文");Check.equal("中文",input.get());
            Check.isTrue(!t.shownText("lobe-password").contains("secret"),"password masked");
            t.click("lobe-textarea");t.type("first\nsecond");Check.equal("first\nsecond",area.get());
        } finally {t.close();}
    }
    public void testSelectAndEscape() {
        IntRef selected=new IntRef(-1); BoolRef open=new BoolRef(false);
        Tester t=tester(c->Fields.select(c,selected,open,"One","Two","Three"));
        try {
            t.frame();t.click("lobe-select");Check.isTrue(open.get(),"select opened");
            t.click("lobe-option-Two");Check.equal(1,selected.get());Check.isTrue(!open.get(),"selection closes");
            t.click("lobe-select");t.key(Shortcut.of(Key.ESCAPE));Check.isTrue(!open.get(),"Escape closes");
        } finally {t.close();}
    }
    public void testAutocompleteFiltersAndSelects() {
        TextRef value=new TextRef("");BoolRef open=new BoolRef(false);
        Tester t=tester(c->Fields.autoComplete(c,value,open,"Button","Badge","Input"));
        try {
            t.frame();t.click("lobe-autocomplete");t.type("bu");Check.isTrue(open.get(),"editing opens suggestions");
            Check.isTrue(t.hasText("Button")&&!t.hasText("Input"),"suggestions filtered");
            t.click("lobe-option-Button");Check.equal("Button",value.get());Check.isTrue(!open.get(),"closed after choice");
        } finally {t.close();}
    }
    public void testNumberTypingClampsAndStepperBounds() {
        DoubleRef value=new DoubleRef(1);
        Tester t=tester(c->Fields.inputNumber(c,value,0,2,1));
        try {
            t.frame();t.click("lobe-action-增加");t.click("lobe-action-增加");Check.near(2,value.get());
            t.click("lobe-action-减少");Check.near(1,value.get());
            t.click("lobe-number-input");t.key(Shortcut.cmd(Key.A));t.type("99");Check.near(2,value.get());
            t.key(Shortcut.of(Key.ENTER));Check.equal("2.0",t.shownText("lobe-number-input"));
        } finally {t.close();}
    }
    public void testEditableTextEnterCommitsEscapeCancels() {
        TextRef value=new TextRef("Original"),draft=new TextRef("");BoolRef editing=new BoolRef(false);
        Tester t=tester(c->Fields.editableText(c,value,draft,editing));
        try {
            t.frame();t.click("lobe-editable");t.key(Shortcut.cmd(Key.A));t.type("Changed");
            t.key(Shortcut.of(Key.ESCAPE));Check.equal("Original",value.get());Check.isTrue(!editing.get(),"canceled");
            t.click("lobe-editable");t.key(Shortcut.cmd(Key.A));t.type("Changed");
            t.key(Shortcut.of(Key.ENTER));Check.equal("Changed",value.get());Check.isTrue(!editing.get(),"committed");
        } finally {t.close();}
    }
    public void testChoiceControlsAndDisabledParent() {
        BoolRef checked=new BoolRef(false),enabled=new BoolRef(false);IntRef radio=new IntRef(0),rating=new IntRef(0),color=new IntRef(0);
        Tester t=tester(c->{
            Choices.checkbox(c,checked,"check");Choices.switchControl(c,enabled,"switch");
            Choices.radioGroup(c,radio,"A","B");Choices.rate(c,rating,5);
            Choices.colorSwatches(c,color,Color.rgb(0),Color.rgb(0xFF0000));
            column(c).disabled(true).children(()->Choices.checkbox(c,checked,"disabled"));
        });
        try {
            t.frame();t.click("lobe-checkbox-disabled");Check.isTrue(!checked.get(),"inherited disabled");
            t.click("lobe-checkbox-check");Check.isTrue(checked.get(),"checked");
            t.click("lobe-switch-switch");Check.isTrue(enabled.get(),"switched");
            t.click("lobe-radio-B");Check.equal(1,radio.get());t.click("lobe-rate-4");Check.equal(4,rating.get());
            t.click("lobe-rate-4");Check.equal(0,rating.get());t.click("lobe-swatch-1");Check.equal(1,color.get());
        } finally {t.close();}
    }
    public void testSliderKeyboardAndSegments() {
        DoubleRef value=new DoubleRef(0);IntRef selected=new IntRef(0);
        Tester t=tester(c->{Choices.slider(c,value,0,10,1);Actions.segmented(c,selected,"Day","Week");});
        try {
            t.frame();t.click("lobe-slider");t.key(Shortcut.of(Key.HOME));t.key(Shortcut.of(Key.RIGHT));Check.near(1,value.get());
            t.key(Shortcut.of(Key.END));Check.near(10,value.get());t.click("lobe-segment-Week");Check.equal(1,selected.get());
        } finally {t.close();}
    }
    public void testCopyAndContextMenu() {
        Tester t=tester(c->{
            Actions.copyButton(c,"clipboard text");
            Element target=Actions.button(c,"target");
            Overlays.contextMenu(c,target,m->{if(m.item("Copy").chosen()) c.copyText("menu text");});
        });
        try {
            t.frame();t.click("lobe-action-复制");Check.equal("clipboard text",t.clipboard());
            t.rightClick("lobe-button-target");Check.isTrue(t.menu()!=null,"native menu requested");
            t.chooseMenuItem("Copy");Check.equal("menu text",t.clipboard());
        } finally {t.close();}
    }
    public void testModalFocusTrapAndRestore() {
        BoolRef open=new BoolRef(false);TextRef value=new TextRef("");
        Tester t=Tester.of(c->{
            Lobe.apply(c);
            Actions.button(c,"Open").onClick(()->open.set(true));
            Feedback.modal(c,open,"Title",()->Fields.input(c,value,"Type"),()->Actions.button(c,"Done"));
        },640,480);
        try {
            t.frame();t.click("lobe-button-Open");t.key(Shortcut.of(Key.TAB));
            for(int i=0;i<6;i++) {t.key(Shortcut.of(Key.TAB));Check.isTrue(!t.focused("lobe-button-Open"),"focus trapped");}
            t.key(Shortcut.of(Key.ESCAPE));Check.isTrue(!open.get(),"closed");
            Check.isTrue(t.focused("lobe-button-Open"),"focus restored");
        } finally {t.close();}
    }
    public void testDrawerIsRightAlignedAndBlocksBackground() {
        BoolRef open=new BoolRef(true);int[] clicks={0};
        Tester t=Tester.of(c->{
            Lobe.apply(c);Actions.button(c,"Below").onClick(()->clicks[0]++);
            Feedback.drawer(c,open,"Settings",260,()->Choices.checkbox(c,new BoolRef(false),"inside"));
        },640,480);
        try {
            t.frame();Rect drawer=t.find("lobe-drawer");Check.near(380,drawer.x());Check.near(480,drawer.height());
            t.click("lobe-button-Below");Check.equal(0,clicks[0]);Check.isTrue(!open.get(),"backdrop closes");
        } finally {t.close();}
    }
    public void testToastsExpireAndAreWindowLocal() {
        Feedback.Toasts a=new Feedback.Toasts(),b=new Feedback.Toasts();
        Tester t=tester(c->{Actions.button(c,"Notify").onClick(()->a.show(c,"Saved",Lobe.Status.SUCCESS));a.host(c);});
        try {
            t.frame();t.click("lobe-button-Notify");Check.equal(1,a.size());Check.equal(0,b.size());Check.isTrue(t.hasText("Saved"),"visible toast");
            t.advance(Duration.ofSeconds(4));Check.equal(0,a.size());Check.isTrue(!t.hasText("Saved"),"toast removed on scheduled frame");
        } finally {t.close();}
    }
    public void testTabsPaginationAndDisclosure() {
        IntRef tab=new IntRef(0),page=new IntRef(1);BoolRef open=new BoolRef(false);
        Tester t=tester(c->{Navigation.tabs(c,tab,"Overview","Settings");Navigation.pagination(c,page,3);
            Display.accordion(c,open,"Details",()->Display.body(c,"Expanded content"));});
        try {
            t.frame();t.click("lobe-tab-Settings");Check.equal(1,tab.get());
            t.click("lobe-action-上一页");Check.equal(1,page.get());t.click("lobe-page-3");Check.equal(3,page.get());
            t.click("lobe-action-下一页");Check.equal(3,page.get());
            t.click("lobe-accordion-Details");Check.isTrue(t.hasText("Expanded content"),"disclosure opens");
        } finally {t.close();}
    }
    public void testCatalogAllPagesLightAndDark() {
        Tester t=Tester.of(new LobeDemo(),1160,820);
        try {
            t.frame();
            for(boolean dark:new boolean[]{false,true}) {
                t.setDark(dark);
                for(String page:LobeDemo.PAGES) {
                    t.click("lobe-nav-"+page);t.frame();
                    Check.isTrue(t.instances()>100,"catalog page paints: "+page);
                    Rect nav=t.find("lobe-nav-"+page);
                    Check.isTrue(nav.y()+nav.height()<820,"navigation remains visible");
                }
            }
        } finally {t.close();}
    }
    public void testCatalogOverviewLayoutAndNarrowWidth() {
        Tester t=Tester.of(new LobeDemo(),1160,820);
        try {
            t.frame();Rect start=t.find("lobe-button-新建工作空间");
            Check.isTrue(start.x()>216 && start.x()+start.width()<=1160,"primary action stays inside content");
            t.resize(760,700);t.frame();
            Check.isTrue(t.find("lobe-button-新建工作空间").x()<760,"narrow action reachable");
            t.click("lobe-action-切换导航");t.frame();
            Check.isTrue(t.find("lobe-button-新建工作空间").x()+t.find("lobe-button-新建工作空间").width()<=760,"collapsed navigation fits");
        } finally {t.close();}
    }
}
