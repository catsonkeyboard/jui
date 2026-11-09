package jui.examples;

import java.util.Arrays;
import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.lobe.*;
import jui.ui.*;
import static jui.ui.Ui.*;

/** Native, interactive lobe-jui catalog. Run ./build.sh demo Lobe (or lobe-jui). */
public final class LobeDemo implements View {
    public static final String[] PAGES={"概览","操作 Actions","表单 Fields","选择 Choices","展示 Display",
            "反馈 Feedback","浮层 Overlays","导航 Navigation","布局 Layout","主题 Tokens"};
    private final IntRef page=new IntRef(0), segment=new IntRef(0), selected=new IntRef(0), radio=new IntRef(0),
            rating=new IntRef(3), swatch=new IntRef(0), tab=new IntRef(0), pagination=new IntRef(1), steps=new IntRef(1);
    private final BoolRef checked=new BoolRef(true), enabled=new BoolRef(true), disclosure=new BoolRef(false),
            selectOpen=new BoolRef(false), suggestions=new BoolRef(false), editing=new BoolRef(false),
            modalOpen=new BoolRef(false), drawerOpen=new BoolRef(false), popupOpen=new BoolRef(false),
            menuOpen=new BoolRef(false), navOpen=new BoolRef(true);
    private final TextRef name=new TextRef(""), notes=new TextRef(""), password=new TextRef(""),
            search=new TextRef(""), autocomplete=new TextRef(""), editable=new TextRef("点击修改这个标题"),
            draft=new TextRef(""), menuChoice=new TextRef("尚未选择");
    private final DoubleRef amount=new DoubleRef(3), progress=new DoubleRef(0.64);
    private final FloatRef panelWidth=new FloatRef(180);
    private final ScrollState scroll=new ScrollState();
    private final Feedback.Toasts toasts=new Feedback.Toasts();
    private int appearance, clicks, previousPage=-1;

    @Override public void build(Context c) {
        if(appearance==0) Lobe.apply(c);
        else c.setThemePair(appearance==1?Lobe.light():Lobe.dark(),appearance==1?Lobe.light():Lobe.dark());
        Tokens t=Tokens.of(c);
        if(previousPage!=page.get()) { scroll.scrollToTop(); previousPage=page.get(); }
        column(c).fill().background(t.bgLayout).children(()->{
            row(c).fillWidth().height(64).padding(0,24).gap(12).shrink(0).children(()->{
                Navigation.burger(c,navOpen);
                brand(c);
                spacer(c);
                Actions.button(c,switch(appearance){case 1->"外观：浅色";case 2->"外观：深色";default->"外观：跟随系统";},
                        new Actions.ButtonProps(Lobe.Variant.TEXT,Lobe.Size.SMALL,t.dark?"moon":"sun",false,false))
                        .label("lobe-theme-toggle").onClick(()->appearance=(appearance+1)%3);
                Display.tag(c,"Java 25",Lobe.Status.NEUTRAL);
            });
            Layout.divider(c);
            row(c).fillWidth().grow(1).clip().children(()->{
                if(navOpen.get()) column(c).width(216).shrink(0).fillHeight().padding(20,12).gap(16).children(()->{
                    text(c,"组件目录").fontSize(12).textColor(t.textTertiary).padding(0,12);
                    Layout.sideNav(c,page,PAGES);
                    spacer(c);
                    column(c).padding(12).gap(6).children(()->{
                        Display.secondary(c,"GPU 绘制 · 原生交互");
                        text(c,"基于 j-ui / FFM").fontSize(11).textColor(t.textTertiary);
                    });
                });
                column(c).grow(1).basis(0).minWidth(0).fillHeight().background(t.bgContainer).children(()->{
                    row(c).fillWidth().height(48).padding(0,28).gap(8).shrink(0).children(()->{
                        text(c,"lobe-jui").fontSize(12).textColor(t.textTertiary);
                        Icon.icon(c,"chevron-right",12,t.textTertiary);
                        text(c,PAGES[page.get()]).fontSize(12).textColor(t.text);
                    });
                    Layout.divider(c);
                    box(c).fillWidth().grow(1).clip().children(()->Layout.scrollArea(c,scroll,()->
                        column(c).fillWidth().padding(28).gap(24).children(()->{
                            switch(page.get()) {
                                case 0->overview(c); case 1->actions(c); case 2->fields(c);
                                case 3->choices(c); case 4->display(c); case 5->feedback(c);
                                case 6->overlays(c); case 7->navigation(c); case 8->layout(c); case 9->tokens(c);
                                default->throw new IllegalStateException("Invalid catalog page");
                            }
                            Layout.divider(c);
                            Display.secondary(c,"lobe-jui · 为 j-ui 构建的 Lobe 风格原生组件");
                        })));
                });
            });
            Feedback.modal(c,modalOpen,"新建工作空间",()->{
                Display.secondary(c,"输入名称，创建属于你的工作空间。");
                Fields.input(c,name,"工作空间名称").label("workspace-name");
            },()->{
                Actions.button(c,"取消").onClick(()->modalOpen.set(false));
                Actions.button(c,"创建",Lobe.Variant.PRIMARY).disabled(name.get().isBlank()).onClick(()->{
                    modalOpen.set(false); toasts.show(c,"已创建工作空间："+name.get(),Lobe.Status.SUCCESS);
                });
            });
            Feedback.drawer(c,drawerOpen,"偏好设置",360,()->column(c).fillWidth().gap(20).children(()->{
                Display.secondary(c,"调整工作空间的显示与行为。");
                Choices.switchControl(c,enabled,"开启通知");
                Choices.checkbox(c,checked,"保存最近打开的项目");
                Actions.button(c,"完成",Lobe.Variant.PRIMARY).onClick(()->drawerOpen.set(false));
            }));
            toasts.host(c);
        });
    }
    private void brand(Context c) {
        row(c).gap(3).alignItems(Align.CENTER).children(()->{
            int[] colors={0xF06A75,0xEEA54C,0xD8C659,0x74BC92,0x6AA5D5,0xAB86CB};
            for(int color:colors) box(c).size(3,18).radius(1.5f).background(Color.rgb(color));
        });
        text(c,"lobe-jui").fontSize(18).fontWeight(600).textColor(Tokens.of(c).text);
    }
    private void title(Context c,String title,String description) {
        column(c).fillWidth().gap(10).children(()->{
            Display.heading(c,title,2); Display.secondary(c,description);
        });
    }
    private void sample(Context c,String title,String caption,Runnable content) {
        column(c).fillWidth().gap(10).children(()->{
            Display.heading(c,title,5);
            if(caption!=null) Display.secondary(c,caption);
            Layout.block(c,content);
        });
    }
    private void overview(Context c) {
        title(c,"原生界面，Lobe 的细节。","熟悉的设计语言，直接绘制在 GPU 上。为 Java 桌面应用准备的组件库。");
        row(c).gap(8).wrap().children(()->{
            Display.tag(c,"明暗主题",Lobe.Status.NEUTRAL); Display.tag(c,"即时模式 API",Lobe.Status.NEUTRAL);
            Display.tag(c,"原生输入法",Lobe.Status.NEUTRAL);
        });
        sample(c,"从一个工作空间开始","按钮、表单、导航与反馈，在同一个界面里协作。",()->{
            row(c).fillWidth().gap(12).wrap().children(()->{
                Display.heading(c,"我的工作空间",4); spacer(c);
                Actions.actionIcon(c,"panel","工作空间设置").onClick(()->drawerOpen.set(true));
                Actions.button(c,"新建工作空间",new Actions.ButtonProps(Lobe.Variant.PRIMARY,Lobe.Size.MIDDLE,"plus",false,false))
                        .onClick(()->modalOpen.set(true));
            });
            Layout.divider(c);
            Actions.segmented(c,segment,"全部项目","最近使用","已收藏");
            Layout.grid(c,16,()->{
                Layout.cell(c,210,()->Layout.guideCard(c,"code","组件实验室","探索可组合的原生界面与交互。",()->
                        Actions.button(c,"浏览组件",Lobe.Variant.TEXT).onClick(()->page.set(1))));
                Layout.cell(c,210,()->Layout.guideCard(c,"sparkles","设计令牌","在两套语义配色之间自然切换。",()->
                        Actions.button(c,"查看主题",Lobe.Variant.TEXT).onClick(()->page.set(9))));
            });
            row(c).fillWidth().gap(12).wrap().children(()->{
                Display.groupAvatar(c,28,"Li Ming","Chen Yu","Alex"); spacer(c);
                Feedback.badge(c,"所有更改已保存",Lobe.Status.SUCCESS);
            });
        });
        Code.snippet(c,"Java","Lobe.apply(c);\nif (Actions.button(c, \"Hello Lobe\", Lobe.Variant.PRIMARY).clicked()) {\n    count++;\n}");
        Feedback.alert(c,"与现有 j-ui API 组合","组件返回 Element，可继续设置宽度、禁用状态、快捷键和点击回调。",Lobe.Status.INFO);
    }
    private void actions(Context c) {
        title(c,"操作 Actions","六种按钮变体、三档尺寸，以及紧凑的图标操作。");
        sample(c,"Button · Variants",null,()->{
            row(c).gap(10).wrap().children(()->{
                for(Lobe.Variant v:Lobe.Variant.values()) Actions.button(c,v.name(),v).onClick(()->clicks++);
            });
            Display.secondary(c,"按钮已点击 "+clicks+" 次");
        });
        sample(c,"Size & state",null,()->{
            row(c).gap(12).wrap().children(()->{
                for(Lobe.Size s:Lobe.Size.values()) Actions.button(c,s.name(),
                        new Actions.ButtonProps(Lobe.Variant.DEFAULT,s,null,false,false));
                Actions.button(c,"加载中",new Actions.ButtonProps(Lobe.Variant.PRIMARY,Lobe.Size.MIDDLE,null,true,false));
                Actions.button(c,"不可用",new Actions.ButtonProps(Lobe.Variant.DEFAULT,Lobe.Size.MIDDLE,null,false,true));
            });
        });
        sample(c,"ActionIcon · CopyButton",null,()->{
            Actions.actionIconGroup(c,()->{
                Actions.actionIcon(c,"plus","新增").onClick(()->toasts.show(c,"已新增",Lobe.Status.SUCCESS));
                Actions.copyButton(c,"Hello from lobe-jui");
                Element help=Actions.actionIcon(c,"info","帮助"); Overlays.tooltip(c,help,"复制按钮将文本写入剪贴板");
            });
            Actions.segmented(c,segment,"设计","开发","预览");
        });
    }
    private void fields(Context c) {
        title(c,"表单 Fields","单行、多行、密码、搜索与选择，沿用 j-ui 的原生编辑能力。");
        sample(c,"Input · TextArea · Form",null,()->{
            Fields.formItem(c,"项目名称",null,()->Fields.input(c,name,"例如：我的组件库"));
            Fields.formItem(c,"访问密码",null,()->Fields.password(c,password,"输入密码"));
            Fields.formItem(c,"项目说明",null,()->Fields.textArea(c,notes,"写下项目的用途…",88));
            Actions.button(c,"保存项目",Lobe.Variant.PRIMARY).onClick(()->toasts.show(c,"项目已保存",Lobe.Status.SUCCESS));
        });
        sample(c,"SearchBar · Select · AutoComplete",null,()->{
            Fields.searchBar(c,search,"搜索组件");
            Fields.select(c,selected,selectOpen,"个人空间","团队空间","公开空间");
            Fields.autoComplete(c,autocomplete,suggestions,"Button","Input","Popover","Progress","Tabs");
        });
        sample(c,"InputNumber · EditableText",null,()->{
            Fields.inputNumber(c,amount,0,10,1);
            Fields.editableText(c,editable,draft,editing);
        });
    }
    private void choices(Context c) {
        title(c,"选择 Choices","状态由应用持有，组件负责清晰的输入与反馈。");
        sample(c,"Checkbox · Switch · Radio",null,()->{
            Choices.checkbox(c,checked,"我已阅读使用说明");
            Choices.switchControl(c,enabled,"开启通知");
            Choices.radioGroup(c,radio,"跟随系统","始终浅色","始终深色");
        });
        sample(c,"Slider · Rate · ColorSwatches",null,()->{
            Choices.slider(c,progress,0,1,0.01);
            Display.secondary(c,"进度："+Math.round(progress.get()*100)+"%");
            Choices.rate(c,rating,5);
            Choices.colorSwatches(c,swatch,Color.rgb(0x222222),Color.rgb(0x0076E3),Color.rgb(0x00A03F),
                    Color.rgb(0xBD54C6),Color.rgb(0xF35837));
        });
    }
    private void display(Context c) {
        title(c,"展示 Display","文字、身份、标签和内容状态。");
        sample(c,"Text · Tag · Hotkey",null,()->{
            Display.heading(c,"让内容成为主角",3); Display.body(c,"14 DIP 正文与 600 字重标题，共用系统字体与中文回退。");
            row(c).gap(8).wrap().children(()->{
                for(Lobe.Status s:Lobe.Status.values()) Display.tag(c,s.name(),s);
            });
            Display.hotkey(c,"⌘","K");
        });
        sample(c,"Avatar · GroupAvatar · Skeleton",null,()->{
            Display.groupAvatar(c,40,"Li Ming","Chen Yu","Alex");
            Display.skeleton(c,160,14); Display.skeleton(c,240,14);
        });
        Display.accordion(c,disclosure,"组件如何保存状态？",()->
                Display.body(c,"状态保存在应用自己的 Ref 中。组件每帧重新构建，焦点和编辑状态由 j-ui 引擎维护。"));
        Layout.block(c,()->Display.empty(c,"还没有项目","创建第一个项目，开始使用工作空间。",()->
                Actions.button(c,"创建项目",Lobe.Variant.PRIMARY).onClick(()->modalOpen.set(true))));
    }
    private void feedback(Context c) {
        title(c,"反馈 Feedback","操作结果、进度以及具有焦点管理的弹窗与抽屉。");
        for(Lobe.Status s:new Lobe.Status[]{Lobe.Status.INFO,Lobe.Status.SUCCESS,Lobe.Status.WARNING,Lobe.Status.ERROR}) {
            Feedback.alert(c,switch(s){case INFO->"提示信息";case SUCCESS->"保存成功";case WARNING->"请检查输入";default->"连接失败";},
                    switch(s){case INFO->"更改会自动保存到当前工作空间。";case SUCCESS->"项目设置已更新。";case WARNING->"项目名称不能为空。";default->"检查网络连接后重试。";},s);
        }
        sample(c,"Progress · Spin · Badge",null,()->{
            Feedback.progress(c,progress.get(),Lobe.Status.NEUTRAL);
            Choices.slider(c,progress,0,1,0.01);
            row(c).gap(16).children(()->{Feedback.spin(c,18,Tokens.of(c).text); Feedback.badge(c,"正在同步",Lobe.Status.INFO);});
        });
        sample(c,"Modal · Drawer · Toast",null,()->row(c).gap(10).wrap().children(()->{
            Actions.button(c,"打开弹窗",Lobe.Variant.PRIMARY).onClick(()->modalOpen.set(true));
            Actions.button(c,"打开抽屉").onClick(()->drawerOpen.set(true));
            Actions.button(c,"显示通知").onClick(()->toasts.show(c,"设置已保存",Lobe.Status.SUCCESS));
        }));
        Feedback.result(c,"准备就绪","组件库已可以在你的下一个原生应用中使用。",Lobe.Status.SUCCESS,null);
    }
    private void overlays(Context c) {
        title(c,"浮层 Overlays","浮层随锚点定位，支持点击外部与 Escape 关闭。");
        sample(c,"Tooltip · Popover · DropdownMenu",null,()->row(c).gap(12).wrap().children(()->{
            Element tooltip=Actions.button(c,"悬停查看提示"); Overlays.tooltip(c,tooltip,"这是一个 lobe-jui 提示");
            Element popup=Actions.button(c,"打开浮层"); if(popup.clicked()) popupOpen.set(!popupOpen.get());
            Overlays.popover(c,popup,popupOpen,()->column(c).width(220).padding(12).gap(8).children(()->{
                Display.heading(c,"关于工作空间",5); Display.secondary(c,"点击外部或按 Escape 关闭。");
            }));
            Element menu=Actions.button(c,"更多操作"); if(menu.clicked()) menuOpen.set(!menuOpen.get());
            Overlays.dropdownMenu(c,menu,menuOpen,i->menuChoice.set(new String[]{"复制链接","添加收藏","移出列表"}[i]),
                    "复制链接","添加收藏","移出列表");
        }));
        sample(c,"ContextMenu","右键点击下面的区域，打开平台原生菜单。",()->{
            Element target=box(c).fillWidth().height(100).radius(8).background(Tokens.of(c).fillTertiary).center()
                    .label("lobe-context-target").children(()->Display.secondary(c,"在此处右键"));
            Overlays.contextMenu(c,target,m->{
                if(m.item("复制名称").chosen()) { c.copyText("lobe-jui"); menuChoice.set("已复制名称"); }
                if(m.item("查看详情").chosen()) menuChoice.set("lobe-jui 原生组件库");
            });
            Display.secondary(c,menuChoice.get());
        });
    }
    private void navigation(Context c) {
        title(c,"导航 Navigation","标签、路径、分页与步骤。");
        sample(c,"Tabs",null,()->{
            Navigation.tabs(c,tab,"概览","活动","设置");
            Display.body(c,new String[]{"工作空间概览","这里显示近期活动","在这里调整偏好设置"}[tab.get()]);
        });
        sample(c,"Breadcrumb · Pagination",null,()->{
            Navigation.breadcrumb(c,i->toasts.show(c,"导航到第 "+(i+1)+" 层",Lobe.Status.INFO),"工作空间","组件库","Button");
            Navigation.pagination(c,pagination,12); Display.secondary(c,"第 "+pagination.get()+" 页，共 12 页");
        });
        sample(c,"Steps",null,()->{
            Navigation.steps(c,steps.get(),"创建项目","选择组件","完成");
            Actions.button(c,"下一步").onClick(()->steps.set((steps.get()+1)%3));
        });
    }
    private void layout(Context c) {
        title(c,"布局 Layout","结构化容器、可换行网格和可拖动的双栏。");
        Layout.header(c,"项目概览",()->Actions.button(c,"偏好设置").onClick(()->drawerOpen.set(true)));
        Layout.grid(c,16,()->{
            Layout.cell(c,200,()->Layout.guideCard(c,"panel","Block / Grid","可组合的容器与自动换行布局。",null));
            Layout.cell(c,200,()->Layout.guideCard(c,"file","GuideCard","为新用户提供下一步操作。",null));
        });
        sample(c,"DraggablePanel · SideNav · ScrollArea","拖动分割线改变面板宽度。",()->
                box(c).fillWidth().height(220).clip().children(()->Layout.draggablePanel(c,panelWidth,
                        ()->column(c).fill().padding(12).children(()->Layout.sideNav(c,selected,"文档","组件","设置")),
                        ()->scroll(c).fill().padding(20).children(()->column(c).gap(12).children(()->{
                            for(int i=0;i<12;i++) Display.body(c,"原生滚动内容 "+(i+1));
                        })))));
    }
    private void tokens(Context c) {
        title(c,"主题 Tokens","来自 Lobe UI 的语义令牌，包含浅色与深色两套配色。");
        sample(c,"Semantic colors",null,()->Layout.grid(c,16,()->{
            String[] names={"BgContainer","BgLayout","BgElevated","Border","Text","TextSecondary","Primary","Info","Success","Warning","Error"};
            for(String name:names) Layout.cell(c,148,()->row(c).gap(10).alignItems(Align.CENTER).children(()->{
                Color color=Tokens.of(c).color(name);
                box(c).size(32,32).radius(8).background(color).border(1,Tokens.of(c).border);
                column(c).gap(3).children(()->{
                    text(c,name).fontSize(12).textColor(Tokens.of(c).text);
                    text(c,String.format("#%06X",color.rgba()>>>8)).fontSize(11).textColor(Tokens.of(c).textTertiary);
                });
            }));
        }));
        sample(c,"尺寸与圆角",null,()->row(c).gap(12).wrap().children(()->{
            for(Lobe.Size s:Lobe.Size.values()) Actions.button(c,(int)s.height+" DIP / R"+(int)s.radius,
                    new Actions.ButtonProps(Lobe.Variant.DEFAULT,s,null,false,false));
        }));
        Code.snippet(c,"Java","Lobe.apply(c); // 跟随系统外观\nTokens t = Tokens.of(c);\nDisplay.heading(c, \"Hello Lobe\", 2);\nLayout.block(c, () -> Actions.button(c, \"开始\", Lobe.Variant.PRIMARY));");
    }
    public static void main(String[] args) {
        LobeDemo demo=new LobeDemo();
        if(Arrays.asList(args).contains("--dark")) demo.appearance=2;
        if(Arrays.asList(args).contains("--light")) demo.appearance=1;
        App.run(()->{
            Window w=Window.open(WindowOptions.of("lobe-jui · 原生组件库").size(1160,820).content(demo));
            w.setThemePair(Lobe.light(),Lobe.dark());
            if(Arrays.asList(args).contains("--print-window")) System.out.println("window="+w.windowNumber());
        });
    }
}
