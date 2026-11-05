package jui.examples;

import jui.kit.Accordion;
import jui.kit.Alert;
import jui.kit.Attachment;
import jui.kit.Avatar;
import jui.kit.Badge;
import jui.kit.Bars;
import jui.kit.Button;
import jui.kit.Calendar;
import jui.kit.Carousel;
import jui.kit.Collapsible;
import jui.kit.Command;
import jui.kit.DataTable;
import jui.kit.DescriptionList;
import jui.kit.Dialog;
import jui.kit.Dock;
import jui.kit.DropdownButton;
import jui.kit.Form;
import jui.kit.Icon;
import jui.kit.Input;
import jui.kit.Kbd;
import jui.kit.Kit;
import jui.kit.Label;
import jui.kit.Marker;
import jui.kit.Message;
import jui.kit.Notifications;
import jui.kit.NumberInput;
import jui.kit.OtpInput;
import jui.kit.Pagination;
import jui.kit.Progress;
import jui.kit.Questionnaire;
import jui.kit.Radio;
import jui.kit.Rating;
import jui.kit.Select;
import jui.kit.Settings;
import jui.kit.Skeleton;
import jui.kit.Spinner;
import jui.kit.Tabs;
import jui.kit.Tag;
import jui.kit.TextView;
import jui.kit.Tree;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.DoubleRef;
import jui.ui.Element;
import jui.ui.IntRef;
import jui.ui.TextRef;
import jui.ui.View;

import static jui.ui.Ui.*;

import java.util.ArrayList;
import java.util.List;



/**
 * The kit's gallery: every jui-kit component live, grouped like the docs it
 * mirrors. Run: ./build.sh demo Kit
 */
public final class KitDemo implements View {
    private final DoubleRef progress = new DoubleRef(0.45);
    private final IntRef rating = new IntRef(3);
    private final IntRef radio = new IntRef(1);
    private final IntRef groupSel = new IntRef(0);
    private final IntRef tab = new IntRef(0);
    private final IntRef page = new IntRef(1);
    private final IntRef sel = new IntRef(0);
    private final IntRef dock = new IntRef(0);
    private final IntRef carousel = new IntRef(0);
    private final IntRef tableSort = new IntRef(-1);
    private final IntRef calMonth = Calendar.today()[0];
    private final IntRef calDay = Calendar.today()[1];
    private final BoolRef alertOpen = new BoolRef(true);
    private final BoolRef dialogOpen = new BoolRef(false);
    private final BoolRef sheetOpen = new BoolRef(false);
    private final BoolRef paletteOpen = new BoolRef(false);
    private final BoolRef selectOpen = new BoolRef(false);
    private final BoolRef ddOpen = new BoolRef(false);
    private final BoolRef comboOpen = new BoolRef(false);
    private final BoolRef timeH = new BoolRef(false);
    private final BoolRef timeM = new BoolRef(false);
    private final BoolRef collapse = new BoolRef(false);
    private final BoolRef showHidden = new BoolRef(true);
    private final IntRef accordionOpen = new IntRef(0);
    private final IntRef picked = new IntRef(-1);
    private final TextRef name = new TextRef("");
    private final TextRef bio = new TextRef("");
    private final TextRef number = new TextRef("5");
    private final TextRef query = new TextRef("");
    private final TextRef otp = new TextRef("");
    private final TextRef sidebarSel = new TextRef("常规/外观");
    private final TextRef treeOpen = new TextRef("");
    private final List<String> files = new ArrayList<>(List.of("报告.pdf", "设计稿.png"));

    /** One gallery section: a heading and the samples under it. */
    private record Section(String title, Runnable body) {}

    @Override
    public void build(Context c) {
        column(c).fill().background(c.theme().background()).children(() -> {
            Bars.toolbar(c,
                    () -> {
                        Label.label(c, "jui-kit 画廊", Kit.Size.LARGE, Label.Variant.DEFAULT);
                        Button.button(c, "通知", Kit.Variant.GHOST, Kit.Size.SMALL, "bell");
                    },
                    () -> {
                        Element n = Button.button(c, "发通知", Kit.Variant.PRIMARY, Kit.Size.SMALL);
                        if (n.clicked()) {
                            Notifications.notify("已发送一条通知", Kit.Variant.SUCCESS);
                        }
                        Element s = Button.button(c, "设置", Kit.Variant.DEFAULT, Kit.Size.SMALL, "gearshape");
                        if (s.clicked()) sheetOpen.set(true);
                    });
            row(c).grow(1).children(() -> {
                scroll(c).width(300).padding(12, 16).children(() ->
                        column(c).fillWidth().gap(18).children(() -> {
                            for (Section s : sections(c)) {
                                text(c, s.title()).fontSize(13).bold().textColor(c.theme().accent());
                                s.body().run();
                                divider(c);
                            }
                        }));
                box(c).grow(1).fillHeight().children(() -> Notifications.host(c));
            });
            Bars.statusBar(c, "jui-kit · " + sections(c).length + " 个组件分组 · 全部可交互",
                    () -> Label.label(c, "j-ui", Kit.Size.XSMALL, Label.Variant.MUTED));
        });
    }

    private Section[] sections(Context c) {
        return new Section[] {
            new Section("按钮与状态", () -> {
                row(c).gap(8).children(() -> {
                    Button.button(c, "主要", Kit.Variant.PRIMARY);
                    Button.button(c, "危险", Kit.Variant.DANGER);
                    Button.button(c, "成功", Kit.Variant.SUCCESS);
                    Button.button(c, "警告", Kit.Variant.WARNING);
                    Button.button(c, "信息", Kit.Variant.INFO);
                });
                row(c).gap(8).children(() -> {
                    Button.button(c, "默认");
                    Button.button(c, "幽灵", Kit.Variant.GHOST);
                    Button.button(c, "链接", Kit.Variant.LINK);
                    Button.button(c, "纯文字", Kit.Variant.TEXT);
                    Button.button(c, "描边", Kit.Variant.DEFAULT, Kit.Size.MEDIUM, null, true,
                            false, false, false, false);
                });
                row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                    Button.button(c, "超小", Kit.Variant.PRIMARY, Kit.Size.XSMALL);
                    Button.button(c, "小", Kit.Variant.PRIMARY, Kit.Size.SMALL);
                    Button.button(c, "中", Kit.Variant.PRIMARY, Kit.Size.MEDIUM);
                    Button.button(c, "大", Kit.Variant.PRIMARY, Kit.Size.LARGE);
                });
                row(c).gap(8).children(() -> {
                    Button.button(c, "加载中", Kit.Variant.PRIMARY, Kit.Size.MEDIUM, null,
                            false, true, false, false, false);
                    Button.button(c, "禁用", Kit.Variant.DEFAULT, Kit.Size.MEDIUM, null,
                            false, false, true, false, false);
                    Button.button(c, "选中", Kit.Variant.DEFAULT, Kit.Size.MEDIUM, null,
                            false, false, false, true, false);
                    Button.button(c, "菜单", Kit.Variant.DEFAULT, Kit.Size.MEDIUM, null,
                            false, false, false, false, true);
                });
                Button.buttonGroup(c, groupSel, "天", "周", "月");
            }),
            new Section("图标与标记", () -> {
                row(c).gap(10).alignItems(Align.CENTER).children(() -> {
                    for (String ic : new String[] { "house", "magnifyingglass", "gearshape", "trash",
                            "folder", "star", "heart", "bookmark", "clock", "calendar", "bell",
                            "download", "share", "bolt", "lock", "doc", "photo", "person" }) {
                        box(c).size(18, 18).draw(Icon.draw(ic, c.theme().textMuted()));
                    }
                });
                row(c).gap(10).alignItems(Align.CENTER).children(() -> {
                    Badge.badge(c, 3);
                    Badge.badge(c, 120);
                    Badge.badgeDot(c);                                       // dot-only
                    Tag.tag(c, "beta", Kit.Variant.INFO, Tag.Kind.SOFT, true, false, null);
                    Tag.tag(c, "稳定", Kit.Variant.SUCCESS, Tag.Kind.FILLED, false, false, null);
                    Tag.tag(c, "实验", Kit.Variant.WARNING, Tag.Kind.OUTLINE, true, false, null);
                    Kbd.kbd(c, "⌘", "K");
                    Marker.marker(c, Kit.Variant.DANGER, "7");
                    Marker.marker(c, Kit.Variant.SUCCESS);
                });
            }),
            new Section("头像与加载", () -> {
                row(c).gap(10).alignItems(Align.CENTER).children(() -> {
                    Avatar.avatar(c, "Li Ming", 36, true);
                    Avatar.avatar(c, "Wang", 28);
                    Avatar.avatar(c, "Chen Jing", 24);
                    Spinner.spinner(c, 20);
                    Progress.bar(c, progress, 140);
                    Progress.ring(c, progress, 28);
                    Label.label(c, "45%", Kit.Size.SMALL, Label.Variant.MUTED);
                });
                Skeleton.textLines(c, 220);
                Rating.rating(c, rating, 5, 20);
            }),
            new Section("文本与提示", () -> {
                TextView.textView(c, "支持 **加粗**、`code` 与换行\n第二行普通文字");
                Alert.alert(c, Kit.Variant.INFO, "提示", "这是一条信息提示。", false,
                        new BoolRef(true));
                Alert.alert(c, Kit.Variant.DANGER, "出错了", "操作失败，请重试。", true, alertOpen);
                Element tipAnchor = Button.button(c, "悬停我看", Kit.Variant.DEFAULT, Kit.Size.SMALL);
                TooltipOwner.attach(c, tipAnchor, "这是工具提示");
            }),
            new Section("表单", () -> {
                Input.field(c, "名字", name, "请输入名字");
                Input.areaField(c, "简介", bio, "一句话介绍", 48);
                row(c).gap(10).alignItems(Align.CENTER).children(() -> {
                    NumberInput.numberInput(c, number, new DoubleRef(5), 0, 100, 1);
                    Label.label(c, "数字", Kit.Size.SMALL, Label.Variant.MUTED);
                });
                row(c).gap(14).alignItems(Align.CENTER).children(() -> {
                    Select.select(c, sel, selectOpen, "北京", "上海", "广州");
                    jui.ui.Element combo = jui.kit.Combobox.combobox(c, query, comboOpen,
                            "Beijing", "Boston", "Berlin", "Bogota");
                    combo.width(150);
                });
                row(c).gap(10).children(() ->
                        OtpInput.otp(c, otp, 6, 30));
                row(c).gap(12).children(() -> {
                    for (String f : files) {
                        Attachment.attachment(c, f, "", () -> files.remove(f));
                    }
                });
            }),
            new Section("选择与开关", () -> {
                Radio.radio(c, radio, true, "左", "中", "右");
                row(c).gap(16).alignItems(Align.CENTER).children(() -> {
                    jui.ui.Ui.toggle(c, showHidden, "显示隐藏文件");
                    Label.label(c, showHidden.get() ? "开" : "关", Kit.Size.SMALL,
                            Label.Variant.MUTED);
                });
                row(c).gap(12).children(() -> {
                    DropdownButton.dropdownButton(c, "导出", Kit.Variant.DEFAULT, Kit.Size.MEDIUM,
                            ddOpen, picked::set, "PDF", "PNG", "SVG");
                    Label.label(c, picked.get() >= 0 ? "已选第 " + (picked.get() + 1) + " 项"
                            : "未选择", Kit.Size.SMALL, Label.Variant.MUTED);
                });
                StepperHost.show(c);
            }),
            new Section("日期与日历", () -> {
                row(c).gap(16).alignItems(Align.CENTER).children(() -> {
                    jui.kit.DatePicker.datePicker(c, calMonth, calDay, new BoolRef(false));
                    jui.kit.TimeField.timeField(c, new IntRef(9), new IntRef(41),
                            timeH, timeM);
                });
                Calendar.calendar(c, calMonth, calDay);
            }),
            new Section("折叠与手风琴", () -> {
                Collapsible.collapsible(c, collapse, "详细信息", true,
                        () -> text(c, "这里是折叠的内容。"));
                Accordion.accordion(c, accordionOpen, false, true,
                        Accordion.item("第一节", () -> text(c, "第一节的内容")),
                        Accordion.item("第二节", () -> text(c, "第二节的内容")));
                Questionnaire.questionnaire(c, "快速问卷",
                        Questionnaire.question("你最喜欢的颜色？",
                                () -> Radio.radio(c, radio, true, "蓝", "绿")),
                        Questionnaire.question("满意度？",
                                () -> Rating.rating(c, rating, 5, 16)));
            }),
            new Section("消息与对话", () -> {
                Message.message(c, Message.Side.LEFT, "李明", "10:24", "下午三点开会，记得带上数据。", Kit.Variant.DEFAULT);
                Message.message(c, Message.Side.RIGHT, "我", "10:25", "收到，准时到。", Kit.Variant.PRIMARY);
            }),
            new Section("分页与轮播", () -> {
                Pagination.pagination(c, page, 9);
                Carousel.carousel(c, carousel, 3, p -> {
                    box(c).width(220).height(64).radius(Kit.RADIUS)
                            .background(c.theme().surface())
                            .alignItems(Align.CENTER)
                            .children(() -> text(c, "第 " + (p + 1) + " 页").fontSize(13)
                                    .textColor(c.theme().textMuted()));
                });
            }),
            new Section("表格与树", () -> {
                DataTable.table(c, new DataTable.Column[] {
                        DataTable.col("名称", 110), DataTable.col("状态", 70),
                }, new String[][] { {"任务 A", "进行中"}, {"任务 B", "已完成"}, {"任务 C", "排队"} },
                        tableSort, new BoolRef(true));
                Tree.tree(c, new Tree.Node[] {
                        Tree.node("src",
                                Tree.node("main"),
                                Tree.leaf("README.md", () -> Label.label(c, "说明文件",
                                        Kit.Size.SMALL, Label.Variant.MUTED))),
                        Tree.node("docs"),
                }, treeOpen);
            }),
            new Section("对话框与命令", () -> {
                row(c).gap(8).children(() -> {
                    Element d = Button.button(c, "打开对话框", Kit.Variant.PRIMARY, Kit.Size.SMALL);
                    if (d.clicked()) dialogOpen.set(true);
                    Element p = Button.button(c, "命令面板", Kit.Variant.DEFAULT, Kit.Size.SMALL, "bolt");
                    if (p.clicked()) paletteOpen.set(true);
                });
                Dialog.dialog(c, dialogOpen, "删除确认",
                        () -> text(c, "该操作不可撤销，确定继续？").fontSize(12)
                                .textColor(c.theme().textMuted()),
                        Dialog.Buttons.CONFIRM_CANCEL, ok -> { });
                Command.palette(c, "命令", paletteOpen, query, picked::set,
                        Command.cmd("新建文件"), Command.cmd("关闭窗口"));
            }),
            new Section("侧栏与停靠", () -> {
                row(c).gap(16).alignItems(Align.CENTER).children(() -> {
                    jui.kit.Sidebar.sidebar(c, new jui.kit.Sidebar.Entry[] {
                            jui.kit.Sidebar.entry("常规", "外观", "photo"),
                            jui.kit.Sidebar.entry("常规", "面板", "square-grid"),
                    }, sidebarSel, 170);
                    Dock.dock(c, dock, "house", "magnifyingglass", "gearshape", "bell");
                });
                Tabs.tabs(c, tab, "全部", "进行中", "已完成");
            }),
            new Section("设置页", () -> {
                Settings.page(c, "", Settings.line("显示隐藏文件",
                                () -> jui.ui.Ui.toggle(c, showHidden, "")),
                        Settings.line("密度", () -> Label.label(c, "舒适", Kit.Size.SMALL)));
            }),
            new Section("布局基元", () -> {
                jui.kit.GroupBox.groupBox(c, "分组", () ->
                        DescriptionList.descriptionList(c,
                                DescriptionList.entry("版本", "1.0.0"),
                                DescriptionList.entry("许可", "Apache-2.0")));
            }),
        };
    }

    /** Helper wrapper so tooltip placement reads like the other components. */
    private static final class TooltipOwner {
        static void attach(Context c, Element anchor, String text) {
            jui.kit.Tooltip.over(c, anchor, text);
        }
    }

    /** A small host for the stepper, kept out of the sections array for clarity. */
    private static final class StepperHost {
        static void show(Context c) {
            DoubleRef v = new DoubleRef(2);
            jui.kit.Stepper.stepper(c, v, 0, 10, 1, "%.0f");
        }
    }

    public static void main(String[] args) {
        App.menu(bar -> { });
        App.run(() -> Window.open(WindowOptions.of("jui-kit 画廊").size(560, 760)
                .content(new KitDemo()::build)));
    }

    private KitDemo() {}
}
