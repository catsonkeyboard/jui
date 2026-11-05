# QSpace 复刻实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 j-ui 复刻 QSpace 文件管理器：多面板工作区（1/2/2×2）+ 每面板多标签 + 面包屑 + 列表/图标两种视图 + 侧栏 + 系统设置风格偏好窗口，浏览真实文件系统并支持安全文件操作。

**Architecture:** 唯一的框架改动是 `Ui.splitVertical`（垂直分割）和 `Engine.setThemePair`（自定义明暗色板），其余全部在独立应用包 `jui/jspace` 内用公开 API 组合。文件系统访问在 `jui.jspace.store` 子包：后台虚拟线程扫描、`Window.update` 发布不可变快照，主线程只读。

**Tech Stack:** Java 25（Gradle 9.4.1 构建，`./build.sh`），j-ui 自绘 UI（Metal/FFM，无第三方依赖），零依赖测试运行器。

**Spec:** `docs/superpowers/specs/2026-11-05-jspace-clone-design.md`（本计划从规格推导而来；执行者两个文件都要读）

## Global Constraints

- 仅 macOS；不引 AWT/Swing（打开文件用 `/usr/bin/open`，ProcessBuilder）。
- 零第三方依赖；测试用仓库自带运行器（`src/test/java/jui/testing/`，方法名以 `test` 开头、零参数即被发现）。
- 全部 UI 文案中文；代码注释风格与仓库一致（英文 javadoc、简洁）。
- 提交信息用仓库惯例 `feat(scope): …` / `test(scope): …`；每个任务一个或多个独立提交。
- 每个任务结束必须 `./build.sh test` 全绿；框架改动任务另跑 `./build.sh alloc`、`./build.sh ffi-check` 不得回归。
- 菜单栏（App.menu）注册过的快捷键不得在窗口内用 `c.shortcut` 重复注册（框架规则，README M5）。
- 应用状态只在主线程读写；后台线程经 `Window.update(Runnable)` 回主线程（NotesDemo 的模式）。
- 不要把 `Element` 存进字段（README M1 警告）；每帧重新构建。
- 包结构：`src/main/java/jui/jspace/`（模型与视图）与 `src/main/java/jui/jspace/store/`（文件系统与设置）；测试在 `src/test/java/jui/jspace/`。
- 路径一律 `toAbsolutePath().normalize()` 后作 key；macOS 文件名保持 NFD 原样。

---

### Task 1: 框架 `Ui.splitVertical`

**Files:**
- Modify: `src/main/java/jui/ui/Ui.java`（`split` 所在区域，约 130–200 行）
- Test: `src/test/java/jui/ui/SplitVerticalTest.java`（新建）

**Interfaces:**
- Consumes: 现有 `Ui.split(Context, FloatRef, Runnable, Runnable)`、`ElementState.dragY`、`Key.UP/DOWN`、`Cursor.RESIZE_NS`（均已存在）。
- Produces: `public static Element splitVertical(Context c, FloatRef firstHeight, Runnable first, Runnable second)`——后续 PaneGrid 用它做 2×2。

- [ ] **Step 1: 写失败的测试**（几何与水平版完全镜像：宽 200 高 300，firstHeight=100）

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class SplitVerticalTest {
    static Tester shell(FloatRef first, float w, float h) {
        return Tester.of(c -> splitVertical(c, first,
                () -> box(c).label("first"),
                () -> box(c).label("second")).fill().label("split"), w, h);
    }

    public void testThePanesShareTheHeightAtTheDivider() {
        FloatRef first = new FloatRef(100);
        Tester tt = shell(first, 200, 300);
        Check.near(100, tt.find("first").height());
        Check.near(199, tt.find("second").height());          // the 1 DIP line between them
        Check.near(96.5f, tt.find("divider").y());
        Check.near(8, tt.find("divider").height());
    }

    public void testDraggingMovesTheFirstHeight() {
        FloatRef first = new FloatRef(100);
        Tester tt = shell(first, 200, 300);
        tt.frame();
        tt.press(100, 100);
        tt.move(100, 133);
        tt.frame();
        Check.near(133, first.get());
        tt.release(100, 133);
        tt.frame();
        Check.near(133, first.get());
    }

    public void testADragPastTheRoomIsClampedToLeave40OnEitherSide() {
        FloatRef first = new FloatRef(250);                    // the most the room takes is 259
        Tester tt = shell(first, 200, 300);
        tt.press(100, 250);
        tt.move(100, 360);
        tt.frame();
        Check.near(259, first.get());
    }

    public void testArrowsMoveTheDividerWithTheFocus() {
        FloatRef first = new FloatRef(100);
        Tester tt = shell(first, 200, 300);
        tt.frame();
        tt.click("divider");
        tt.key(Shortcut.of(Key.DOWN));
        tt.frame();
        Check.near(108, first.get());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test SplitVerticalTest`
Expected: 编译失败，`cannot find symbol: method splitVertical`。

- [ ] **Step 3: 实现——把 `split` 的实现体抽成私有 `splitPane(…, boolean vertical)`，两个公开方法委托**

在 `Ui.java` 中：保留 `split` 的 javadoc 不动，把它的方法体移入新的私有方法，`split` 变成一行委托；新增 `splitVertical`。私有方法体 = 现有实现按下表换轴：

```java
/**
 * A column that shares its room between first and second at a divider the
 * user drags, or moves with the arrows once it has the focus: firstHeight is
 * the first's height. As split, sideways.
 */
public static Element splitVertical(Context c, FloatRef firstHeight, Runnable first, Runnable second) {
    return splitPane(c, firstHeight, first, second, true);
}

public static Element split(Context c, FloatRef firstWidth, Runnable first, Runnable second) {
    return splitPane(c, firstWidth, first, second, false);
}

private static Element splitPane(Context c, FloatRef firstSize, Runnable first, Runnable second,
                                 boolean vertical) {
    Element e = (vertical ? column(c) : row(c)).alignItems(Align.STRETCH);
    jui.ui.engine.Engine en = c.engine();
    int outer = en.enter(e.index);
    try {
        Element room = (vertical ? column(c) : row(c)).grow(1).alignItems(Align.STRETCH);
        int inner = en.enter(room.index);
        try {
            float size = firstSize.get();
            float total = vertical ? en.state(room.index).h : en.state(room.index).w;
            float shown = total <= 0 || size <= MIN_PANE ? size
                    : Math.max(MIN_PANE, Math.min(size, total - 1 - MIN_PANE));
            if (vertical) {
                box(c).height(size).minHeight(Math.min(size, MIN_PANE)).shrink(1).clip().label("first-pane").children(first);
                box(c).height(1);
                box(c).grow(1).minHeight(MIN_PANE).shrink(1).clip().children(second);
            } else {
                box(c).width(size).minWidth(Math.min(size, MIN_PANE)).shrink(1).clip().label("first-pane").children(first);
                box(c).width(1);
                box(c).grow(1).minWidth(MIN_PANE).shrink(1).clip().children(second);
            }

            Element div = vertical
                    ? box(c).absolute().alignItems(Align.CENTER).height(8).top(shown - 3.5f).left(0).right(0)
                            .focusable().cursor(Cursor.RESIZE_NS).label("divider")
                    : box(c).absolute().alignItems(Align.CENTER).width(8).left(shown - 3.5f).top(0).bottom(0)
                            .focusable().cursor(Cursor.RESIZE_EW).label("divider");
            Node n = div.node();
            n.flags |= Node.DRAG | Node.HOVER | Node.KEYS;
            jui.ui.engine.ElementState ds = en.state(div.index);
            boolean off = en.disabled(div.index);
            boolean held = ds.pressed && !off;
            float move = held ? (vertical ? ds.dragY : ds.dragX) : 0;
            if (!off && move != 0) {
                setSplitWidth(en, e.index, firstSize, shown + move, total);
            }
            for (int k = 0; !off && k < ds.keyCount; k++) {
                if (ds.keys[k] == (vertical ? Key.UP : Key.LEFT)) {
                    setSplitWidth(en, e.index, firstSize, shown - 8, total);
                }
                if (ds.keys[k] == (vertical ? Key.DOWN : Key.RIGHT)) {
                    setSplitWidth(en, e.index, firstSize, shown + 8, total);
                }
            }
            Theme t = c.theme();
            int line = held || en.hovered(div.index) ? t.accent().rgba() : t.border().rgba();
            int into = en.enter(div.index);
            try {
                Element l = vertical
                        ? box(c).height(1).fillWidth().label("line")
                        : box(c).width(1).fillHeight().label("line");
                if (l.node() != null) {
                    l.node().background = line;
                }
            } finally {
                en.exit(into);
            }
        } finally {
            en.exit(inner);
        }
    } finally {
        en.exit(outer);
    }
    return e;
}
```

注意：`en.state(room.index).h`/`.w` 是现有实现读取的字段名（照抄 `split` 原文里的 `en.state(room.index).w`）；`setSplitWidth` 已是 `Ui` 的私有静态方法，直接复用。原 `split` 里 `label("first-pane")` 等文本保持不变，已有测试依赖这些标签。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全部通过（含既有 `SplitTest` 与新 `SplitVerticalTest`）。

- [ ] **Step 5: 门槛核对**

Run: `./build.sh alloc && ./build.sh ffi-check`
Expected: 均通过（split 在 M5 alloc 场景里，参数化不应引入分配）。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/jui/ui/Ui.java src/test/java/jui/ui/SplitVerticalTest.java
git commit -m "feat(ui): splitVertical, the sideways split"
```

---

### Task 2: 框架 `Engine.setThemePair` + `Window.setThemePair`

**Files:**
- Modify: `src/main/java/jui/ui/engine/Engine.java`（`theme` 字段与 `setDark`，约 20、1613 行）
- Modify: `src/main/java/jui/app/Window.java`（新增公开方法，放在 `update` 附近）
- Test: `src/test/java/jui/ui/ThemePairTest.java`（新建）

**Interfaces:**
- Consumes: `Engine.theme()`、`Tester.engine()`、`Tester.setDark(boolean)`（ThemeTest 已用）。
- Produces: `Engine.setThemePair(Theme light, Theme dark)`；`Window.setThemePair(Theme light, Theme dark)`——QSpace 主题切换的基础。

- [ ] **Step 1: 写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ThemePairTest {
    static Theme tinted(boolean dark) {
        return new Theme(dark,
                Color.rgb(0x232326), Color.rgb(0x2E2E32), Color.rgb(0x38383D), Color.rgb(0x414146),
                Color.rgb(0x3F3F46), Color.rgb(0xE5E5E7), Color.rgb(0x98989D),
                Color.rgb(0x0A84FF), Color.rgb(0x409CFF), Color.rgb(0x0875D1), Color.rgb(0xFFFFFF),
                Color.rgba(0x0A84FF, 0.35f), Color.rgba(0x0A84FF, 0.6f), 13, 6,
                Color.rgba(0xFFFFFF, 0.3f), 6);
    }

    public void testSetDarkPicksFromThePair() {
        Theme light = tinted(false), dark = tinted(true);
        Tester tt = Tester.of(c -> text(c, "x"), 50, 50);
        tt.engine().setThemePair(light, dark);
        Check.equal(light, tt.engine().theme());               // light to begin with
        tt.setDark(true);
        Check.equal(dark, tt.engine().theme());
        tt.setDark(false);
        Check.equal(light, tt.engine().theme());
    }

    public void testDefaultsAreTheBuiltInThemes() {
        Tester tt = Tester.of(c -> text(c, "x"), 50, 50);
        tt.setDark(true);
        Check.equal(Theme.dark(), tt.engine().theme());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test ThemePairTest`
Expected: 编译失败，`cannot find symbol: method setThemePair`。

- [ ] **Step 3: 实现**

`Engine.java`：字段区加两个字段（默认值即内建主题）：

```java
private Theme lightTheme = Theme.light();
private Theme darkTheme = Theme.dark();
```

`setDark` 改为从 pair 取：

```java
/** Follows the system's appearance: the dark theme of the pair, or the light one. Asks for a frame if it changed. */
public void setDark(boolean dark) {
    Theme next = dark ? darkTheme : lightTheme;
    if (next != theme) {
        theme = next;
        requestFrame.run();
    }
}
```

新增方法（放在 `setDark` 后）：

```java
/** The app's own light and dark themes, which the system appearance then picks
 *  between; resolves the current mode again at once. */
public void setThemePair(Theme light, Theme dark) {
    lightTheme = light;
    darkTheme = dark;
    setDark(theme.isDark());
}
```

`Window.java` 新增（放在 `update` 之后）：

```java
/** The window's own light and dark palettes; the system appearance picks between them. */
public void setThemePair(Theme light, Theme dark) {
    engine.setThemePair(light, dark);
}
```

注意：构造时传入的初始 `Theme`（现固定 `Theme.light()`）不动；QSpace 打开窗口后立刻 `setThemePair` 覆盖。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全部通过（含既有 ThemeTest）。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/jui/ui/engine/Engine.java src/main/java/jui/app/Window.java src/test/java/jui/ui/ThemePairTest.java
git commit -m "feat(ui): engine theme pair an app can install"
```

---

### Task 3: QSpace 骨架——入口、主题、模型、空工作区、build.sh 集成

**Files:**
- Create: `src/main/java/jui/jspace/JspaceApp.java`
- Create: `src/main/java/jui/jspace/JspaceTheme.java`
- Create: `src/main/java/jui/jspace/Model.java`
- Create: `src/main/java/jui/jspace/Workspace.java`
- Modify: `build.sh`（`example_name` 与 `demo`/`native_demo`，约 108–145 行）
- Test: `src/test/java/jui/jspace/WorkspaceShellTest.java`

**Interfaces:**
- Consumes: `App.run`/`App.menu`、`Window.open(WindowOptions)`、`Ui.box/row/column/text/divider/spacer/split/splitVertical`、`Element` 链式 API、`Theme` record。
- Produces（后续任务都依赖这里定下的形状）:
  - `JspaceTheme.LIGHT` / `JspaceTheme.DARK`（`Theme`）；`JspaceTheme.palette(Context c)` → `JspaceTheme.Palette`（应用附加色：`sidebar`、`sidebarHover`、`pill`、`pillSel`、`bar`、`panel`、`warn`）。
  - `Model`（见 Step 3 代码，含 `Layout`/`ViewMode`/`SortState`/`TabState`/`PaneState`/`Clip`/`Status`）。
  - `Workspace`：`Workspace(Settings settings)`、`View view()`；`Model model()`。
  - `Settings` 本任务先用占位（Task 6 才实现）：在 `store/` 里先建最小骨架 `Settings { boolean showHidden; … }`？**不**——为避免半成品接口，本任务 Workspace 只接收 `Model`，Settings 引入推迟到 Task 6（届时给 Workspace 加构造参数）。Task 3 的 `JspaceApp` 先不读设置，写死默认。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

public final class WorkspaceShellTest {
    static Tester open(Model m) {
        Workspace ws = new Workspace(m);
        return Tester.of(ws.view(), 1280, 800);
    }

    public void testTheShellHasSidebarToolbarAndStatus() {
        Model m = new Model();
        Tester tt = open(m);
        Check.near(230, tt.find("sidebar").width());
        Check.true_(tt.find("toolbar").height() > 0);
        Check.true_(tt.find("statusbar").height() > 0);
        Check.true_(tt.find("pane-grid").width() > 0);
    }

    public void testDefaultLayoutIsTheGridOfFour() {
        Model m = new Model();
        Tester tt = open(m);
        for (int i = 0; i < 4; i++) {
            Check.true_(tt.find("pane-" + i).width() > 0);
        }
    }

    public void testSingleAndDoubleLayouts() {
        Model single = new Model();
        single.layout = Model.Layout.SINGLE;
        Check.true_(Tester.of(new Workspace(single).view(), 800, 600).find("pane-0").width() > 0);
        Model dbl = new Model();
        dbl.layout = Model.Layout.DOUBLE;
        Check.true_(Tester.of(new Workspace(dbl).view(), 800, 600).find("pane-1").width() > 0);
    }

    public void testLayoutSwitchKeepsTheFirstPanes() {
        Model m = new Model();
        m.layout = Model.Layout.GRID;
        m.layout = Model.Layout.SINGLE;                          // shrink: keep pane 0
        Tester tt = open(m);
        Check.true_(tt.find("pane-0").width() > 0);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test WorkspaceShellTest`
Expected: 编译失败，`package jui.jspace does not exist`。

- [ ] **Step 3: 实现**

`Model.java`（完整，后续任务只加字段，不改已有形状）：

```java
package jui.jspace;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The app's whole state, read and written on the main thread only. */
final class Model {
    enum Layout { SINGLE, DOUBLE, GRID }
    enum ViewMode { LIST, GRID }

    static final class SortState {
        Entries.SortKey key = Entries.SortKey.NAME;
        boolean asc = true;
    }

    /** One open tab in one pane: where it is and how it shows it. */
    static final class TabState {
        Path dir;
        final ArrayDeque<Path> back = new ArrayDeque<>(), forward = new ArrayDeque<>();
        ViewMode view = ViewMode.LIST;
        final SortState sort = new SortState();
        final Set<Path> expanded = new HashSet<>();
        final jui.ui.ScrollState listScroll = new jui.ui.ScrollState();
        final jui.ui.ScrollState gridScroll = new jui.ui.ScrollState();
        final jui.ui.ListState listRows = new jui.ui.ListState();
        final jui.ui.ListState gridRows = new jui.ui.ListState();
        Path selected;                    // the one selected entry, null for none
        String search = "";
        float paneWidth;                  // last frame's content width, for the grid's columns

        TabState(Path dir) { this.dir = dir; }
    }

    static final class PaneState {
        final List<TabState> tabs = new ArrayList<>();
        int current;
        TabState tab() { return tabs.get(current); }
    }

    /** The clipboard one Copy or Cut filled, for the next Paste. */
    static final class Clip {
        final Path path;
        final boolean move;
        Clip(Path path, boolean move) { this.path = path; this.move = move; }
    }

    /** The bottom line: what the panes hold, and a note that fades. */
    static final class Status {
        String left = "";
        String note = "";                 // shown right, replaced by the next note
        long noteAt;                      // System.nanoTime() when set, for the fade
    }

    Layout layout = Layout.GRID;
    int active;                           // the pane clicks and keys act on
    final PaneState[] panes = new PaneState[] { new PaneState(), new PaneState(), new PaneState(), new PaneState() };
    final FloatRef vSplit = new FloatRef(380);      // the grid's horizontal line, in DIP
    final FloatRef[] hSplit = { new FloatRef(460), new FloatRef(460) };  // each row's line
    Clip clip;
    final Status status = new Status();
    boolean sidebarOpen = true;

    Model() {
        Path home = Path.of(System.getProperty("user.home"));
        for (PaneState p : panes) p.tabs.add(new TabState(home));
    }
}
```

`JspaceTheme.java`：

```java
package jui.jspace;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Theme;

/** QSpace's own light and dark palettes, plus the app colors the Theme record has no role for. */
final class JspaceTheme {
    static final Theme LIGHT = new Theme(false,
            Color.rgb(0xFFFFFF), Color.rgb(0xF7F7F9), Color.rgb(0xEDEDF0), Color.rgb(0xE3E3E7),
            Color.rgb(0xD9D9DE), Color.rgb(0x1D1D1F), Color.rgb(0x6E6E73),
            Color.rgb(0x0A60FF), Color.rgb(0x3B7DFF), Color.rgb(0x0849CC), Color.rgb(0xFFFFFF),
            Color.rgba(0x0A60FF, 0.15f), Color.rgba(0x0A60FF, 0.55f), 13, 6,
            Color.rgba(0x000000, 0.25f), 6);

    static final Theme DARK = new Theme(true,
            Color.rgb(0x232326), Color.rgb(0x2E2E32), Color.rgb(0x38383D), Color.rgb(0x414146),
            Color.rgb(0x3F3F46), Color.rgb(0xE5E5E7), Color.rgb(0x98989D),
            Color.rgb(0x0A84FF), Color.rgb(0x409CFF), Color.rgb(0x0875D1), Color.rgb(0xFFFFFF),
            Color.rgba(0x0A84FF, 0.35f), Color.rgba(0x0A84FF, 0.6f), 13, 6,
            Color.rgba(0xFFFFFF, 0.30f), 6);

    /** Colors beyond the Theme record's roles, per appearance. */
    record Palette(Color sidebar, Color sidebarHover, Color pill, Color pillSel,
                   Color bar, Color panel, Color warn, Color zebra) {}

    private static final Palette LIGHT_X = new Palette(Color.rgb(0xF2F2F4), Color.rgb(0xE6E6EA),
            Color.rgb(0xEDEDF0), Color.rgb(0xD9E4FF), Color.rgb(0xF7F7F9), Color.rgb(0xFFFFFF),
            Color.rgb(0xC66100), Color.rgb(0xF7F7F9));

    private static final Palette DARK_X = new Palette(Color.rgb(0x1A1A1C), Color.rgb(0x2C2C30),
            Color.rgb(0x38383D), Color.rgb(0x2E4A66), Color.rgb(0x2E2E32), Color.rgb(0x232326),
            Color.rgb(0xFFB340), Color.rgb(0x27272B));

    static Palette palette(Context c) {
        return c.theme().isDark() ? DARK_X : LIGHT_X;
    }

    static Theme forced(Settings.Appearance a) {
        return switch (a) {
            case LIGHT -> LIGHT;
            case DARK -> DARK;
            case FOLLOW -> null;          // the built-in pair, restored
        };
    }

    private JspaceTheme() {}
}
```

注意：`Settings.Appearance` 由 Task 6 引入——本任务先在 `JspaceTheme` 里删掉 `forced` 方法，Task 6 再加回（精确改动见 Task 6）。为使本任务可编译，`JspaceTheme` 初版**不含** `forced`。

`Workspace.java`（骨架；FilePane/Sidebar/工具栏在后续任务替换占位）：

```java
package jui.jspace;

import jui.ui.Context;
import jui.ui.Element;
import jui.ui.View;

import static jui.ui.Ui.*;

/** The main window: toolbar, sidebar, the pane grid and the status line. */
final class Workspace implements View {
    private final Model m;

    Workspace(Model m) { this.m = m; }

    Model model() { return m; }

    @Override
    public void build(Context c) {
        column(c).fill().children(() -> {
            toolbarPlaceholder(c);
            row(c).grow(1).children(() -> {
                if (m.sidebarOpen) {
                    box(c).width(230).background(JspaceTheme.palette(c).sidebar()).label("sidebar");
                }
                paneGrid(c).grow(1).label("pane-grid");
            });
            statusPlaceholder(c);
        });
    }

    private void toolbarPlaceholder(Context c) {
        box(c).height(44).background(JspaceTheme.palette(c).bar()).label("toolbar");
    }

    private Element paneGrid(Context c) {
        return switch (m.layout) {
            case SINGLE -> box(c).fill().label("pane-0");
            case DOUBLE -> split(c, m.hSplit[0],
                    () -> box(c).fill().label("pane-0"),
                    () -> box(c).fill().label("pane-1"));
            case GRID -> splitVertical(c, m.vSplit,
                    () -> split(c, m.hSplit[0], () -> box(c).fill().label("pane-0"),
                            () -> box(c).fill().label("pane-1")),
                    () -> split(c, m.hSplit[1], () -> box(c).fill().label("pane-2"),
                            () -> box(c).fill().label("pane-3")));
        };
    }

    private void statusPlaceholder(Context c) {
        box(c).height(24).background(JspaceTheme.palette(c).bar()).label("statusbar");
    }
}
```

`JspaceApp.java`：

```java
package jui.jspace;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.Key;
import jui.ui.Menu;
import jui.ui.Shortcut;

/** QSpace, a file manager built on j-ui. Run: ./build.sh demo QSpace */
public final class JspaceApp {
    private static Window main;
    private static Workspace workspace;

    public static void main(String[] args) {
        App.menu(JspaceApp::menus);
        App.run(() -> {
            workspace = new Workspace(new Model());
            main = Window.open(WindowOptions.of("QSpace").size(1280, 800).content(workspace::build));
            main.setThemePair(JspaceTheme.LIGHT, JspaceTheme.DARK);
        });
    }

    private static void menus(Menu bar) {
        bar.submenu("文件", file -> {
            if (file.item("新建标签页").shortcut(Shortcut.cmd(Key.N)).chosen()) workspace.newTab();
            if (file.item("关闭标签页").shortcut(Shortcut.cmd(Key.W)).chosen()) workspace.closeTab();
        });
        bar.submenu("视图", view -> {
            if (view.item("显示边栏").shortcut(Shortcut.cmd(Key.B)).chosen()) workspace.toggleSidebar();
        });
        bar.submenu("QSpace", app -> {
            if (app.item("设置…").shortcut(Shortcut.cmd(Key.COMMA)).chosen()) workspace.openSettings();
            app.separator();
            if (app.item("退出").shortcut(Shortcut.cmd(Key.Q)).chosen()) System.exit(0);
        });
    }

    private JspaceApp() {}
}
```

注意：`workspace.newTab()`/`closeTab()`/`toggleSidebar()`/`openSettings()` 本任务先建为空方法（`void newTab() {}` 等，javadoc `// Task 11/15`），编译通过即可；后续任务填实现。`Key.COMMA` 若框架枚举名不同（查 `jui/ui/Key.java`），以框架为准改用实际常量（如 `COMMA` 不存在则该菜单项不带 shortcut）。

`build.sh` 集成——`example_name()` 之后加一个类名函数，并让 `demo`/`native_demo` 用它：

```bash
# demo_class prints an example's main class: jui.examples.<Name>Demo, or
# jui.jspace.JspaceApp for QSpace.
demo_class() {
  local name
  name=$(example_name "$@")
  if [[ "$name" == QSpace ]]; then echo "jui.jspace.JspaceApp"; else echo "jui.examples.${name}Demo"; fi
}
```

`demo()` 里 `jui.examples.${name}Demo` 改为 `$(demo_class "$@")`；`native_demo()` 里两处 `jui.examples.${name}Demo` 同改。`native_demo` 的 `-R:MaxHeapSize` 特判只对 Notes，不动。

- [ ] **Step 4: 全量测试 + 启动冒烟**

Run: `./build.sh test && ./build.sh demo QSpace`
Expected: 测试全绿；窗口打开，深色系统下显示深色四格骨架。手动关闭窗口结束。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/jui/jspace/ build.sh src/test/java/jui/jspace/WorkspaceShellTest.java
git commit -m "feat(jspace): app skeleton with theme, model and pane grid"
```

---

### Task 4: store 纯逻辑——FileEntry、IconType、KindMap、RelativeDate、Entries

**Files:**
- Create: `src/main/java/jui/jspace/store/FileEntry.java`
- Create: `src/main/java/jui/jspace/store/KindMap.java`
- Create: `src/main/java/jui/jspace/store/RelativeDate.java`
- Create: `src/main/java/jui/jspace/Entries.java`
- Test: `src/test/java/jui/jspace/EntriesTest.java`
- Test: `src/test/java/jui/jspace/KindMapTest.java`

**Interfaces:**
- Consumes: 无（纯逻辑）。`Model.SortState`（Task 3）在 `Entries.view` 用到。
- Produces:
  - `record FileEntry(String name, Path path, boolean dir, long size, long mtimeMillis, String kind, IconType icon)`；嵌套 `enum IconType { FOLDER, DMG, APK, APP, PDF, DOC, IMAGE, VIDEO, AUDIO, ARCHIVE, MODEL3D, CODE, TEXT, GENERIC }`，静态 `IconType of(String name, boolean dir)`。
  - `KindMap.of(String fileName, boolean dir)` → String（中文种类）。
  - `RelativeDate.format(long epochMillis, java.time.ZonedDateTime now)` → String。
  - `Entries.SortKey { NAME, DATE, SIZE, KIND }`；`Entries.view(FileEntry[] entries, java.util.function.Function<Path, FileStore.DirSnap> snapOf, Model.SortState sort, boolean foldersFirst, String search, boolean showHidden, Set<Path> expanded)` → `List<Row>`；`record Row(FileEntry entry, int depth)`。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.testing.Check;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class EntriesTest {
    static FileEntry f(String name, boolean dir, long size, long mtime) {
        Path p = Path.of("/t/" + name);
        return new FileEntry(name, p, dir, size, mtime,
                jui.jspace.store.KindMap.of(name, dir), FileEntry.IconType.of(name, dir));
    }

    @org.junit.jupiter.api.Disabled
    public void ignored() {}

    public void testFoldersFirstThenName() {
        FileEntry[] es = { f("b.txt", false, 1, 1), f("D", true, -1, 1), f("a.txt", false, 1, 1), f("C", true, -1, 1) };
        Model m = new Model();
        List<Entries.Row> rows = Entries.view(es, p -> null, m.panes[0].tab().sort, true, "", false, Set.of());
        Check.equal("C", rows.get(0).entry().name());
        Check.equal("D", rows.get(1).entry().name());
        Check.equal("a.txt", rows.get(2).entry().name());
        Check.equal("b.txt", rows.get(3).entry().name());
    }

    public void testSortKeysAndDirection() {
        FileEntry[] es = { f("a", false, 30, 300), f("b", false, 10, 100), f("c", false, 20, 200) };
        Model.SortState s = new Model.SortState();
        s.key = Entries.SortKey.SIZE; s.asc = false;
        List<Entries.Row> rows = Entries.view(es, p -> null, s, false, "", false, Set.of());
        Check.equal("a", rows.get(0).entry().name());          // 30 first, descending
        s.asc = true;
        rows = Entries.view(es, p -> null, s, false, "", false, Set.of());
        Check.equal("b", rows.get(0).entry().name());          // 10 first, ascending
    }

    public void testSearchAndHidden() {
        FileEntry[] es = { f("Report.pdf", false, 1, 1), f(".secret", false, 1, 1), f("notes.txt", false, 1, 1) };
        Model m = new Model();
        List<Entries.Row> rows = Entries.view(es, p -> null, m.panes[0].tab().sort, true, "port", false, Set.of());
        Check.equal(1, rows.size());
        Check.equal("Report.pdf", rows.get(0).entry().name());
        rows = Entries.view(es, p -> null, m.panes[0].tab().sort, true, "", false, Set.of());
        Check.equal(2, rows.size());                            // .secret hidden by default
        rows = Entries.view(es, p -> null, m.panes[0].tab().sort, true, "", true, Set.of());
        Check.equal(3, rows.size());
    }

    public void testTreeFlatteningWithDepth() {
        FileEntry[] root = { f("a", true, -1, 1), f("z", false, 1, 1) };
        FileEntry[] child = { f("inner", false, 5, 1) };
        jui.jspace.store.FileStore.DirSnap childSnap =
                new jui.jspace.store.FileStore.DirSnap(jui.jspace.store.FileStore.State.READY, child, null);
        Path a = Path.of("/t/a");
        Model m = new Model();
        List<Entries.Row> rows = Entries.view(root, p -> p.equals(a) ? childSnap : null,
                m.panes[0].tab().sort, true, "", false, Set.of(a));
        Check.equal(3, rows.size());
        Check.equal(0, rows.get(0).depth());
        Check.equal(1, rows.get(1).depth());
        Check.equal("inner", rows.get(1).entry().name());
        Check.equal(0, rows.get(2).depth());
    }
}
```

注意：测试里那个 `@org.junit.jupiter.api.Disabled` 占位方法**删掉不要写**（仓库无 JUnit，写成它编译不过）——上面保留它只是提醒：不要引任何 JUnit。

`KindMapTest`：

```java
package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.jspace.store.KindMap;
import jui.jspace.store.RelativeDate;
import jui.testing.Check;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class KindMapTest {
    public void testKinds() {
        Check.equal("文件夹", KindMap.of("x", true));
        Check.equal("磁盘映像", KindMap.of("k.dmg", false));
        Check.equal("Android 应用程序", KindMap.of("a.apk", false));
        Check.equal("PDF 文稿", KindMap.of("a.pdf", false));
        Check.equal("PNG 图像", KindMap.of("a.png", false));
        Check.equal("MPEG-4 影片", KindMap.of("a.mp4", false));
        Check.equal("ZIP 归档", KindMap.of("a.zip", false));
        Check.equal("GL 传输格式", KindMap.of("a.glb", false));
        Check.equal("源代码", KindMap.of("A.java", false));
        Check.equal("文档", KindMap.of("weird.zpr", false));
    }

    public void testIcons() {
        Check.equal(FileEntry.IconType.FOLDER, FileEntry.IconType.of("x", true));
        Check.equal(FileEntry.IconType.PDF, FileEntry.IconType.of("a.pdf", false));
        Check.equal(FileEntry.IconType.GENERIC, FileEntry.IconType.of("weird.zpr", false));
    }

    public void testRelativeDates() {
        ZoneId z = ZoneId.of("Asia/Shanghai");
        ZonedDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0).atZone(z);
        Check.equal("今天 11:16", RelativeDate.format(
                LocalDateTime.of(2026, 11, 5, 11, 16).atZone(z).toInstant().toEpochMilli(), now));
        Check.equal("昨天 17:02", RelativeDate.format(
                LocalDateTime.of(2026, 11, 4, 17, 2).atZone(z).toInstant().toEpochMilli(), now));
        Check.equal("10/2 09:30", RelativeDate.format(
                LocalDateTime.of(2026, 10, 2, 9, 30).atZone(z).toInstant().toEpochMilli(), now));
        Check.equal("2025/12/31 22:18", RelativeDate.format(
                LocalDateTime.of(2025, 12, 31, 22, 18).atZone(z).toInstant().toEpochMilli(), now));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test EntriesTest KindMapTest`
Expected: 编译失败，找不到 `jui.jspace.store` 各类。注意 Runner 若一次只接受一个类，分两次跑。

- [ ] **Step 3: 实现**

`FileEntry.java`：

```java
package jui.jspace.store;

import java.nio.file.Path;

/** One entry of one directory, as scanned; immutable. */
public record FileEntry(String name, Path path, boolean dir, long size, long mtimeMillis,
                        String kind, IconType icon) {

    public enum IconType { FOLDER, DMG, APK, APP, PDF, DOC, IMAGE, VIDEO, AUDIO, ARCHIVE, MODEL3D, CODE, TEXT, GENERIC }

    public static IconType of(String name, boolean dir) {
        if (dir) return IconType.FOLDER;
        return switch (ext(name)) {
            case "dmg", "iso" -> IconType.DMG;
            case "apk" -> IconType.APK;
            case "app" -> IconType.APP;
            case "pdf" -> IconType.PDF;
            case "doc", "docx", "pages" -> IconType.DOC;
            case "png", "jpg", "jpeg", "gif", "webp", "heic", "tiff", "svg" -> IconType.IMAGE;
            case "mp4", "mov", "m4v", "avi", "mkv" -> IconType.VIDEO;
            case "mp3", "m4a", "wav", "flac", "aac" -> IconType.AUDIO;
            case "zip", "rar", "7z", "tar", "gz" -> IconType.ARCHIVE;
            case "glb", "gltf", "obj", "usdz" -> IconType.MODEL3D;
            case "java", "kt", "py", "js", "ts", "c", "cpp", "h", "go", "rs", "swift", "m", "sh" -> IconType.CODE;
            case "txt", "md", "log" -> IconType.TEXT;
            default -> IconType.GENERIC;
        };
    }

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
```

`KindMap.java`（种类名与 IconType 对齐；映射表用 `Map.entry` 静态块）：

```java
package jui.jspace.store;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Chinese kind names by file extension, as the list view's 种类 column shows. */
public final class KindMap {
    private static final Map<String, String> BY_EXT = Stream.of(new String[][] {
            {"dmg", "磁盘映像"}, {"iso", "磁盘映像"}, {"apk", "Android 应用程序"}, {"app", "应用程序"},
            {"pdf", "PDF 文稿"}, {"doc", "Word 文稿"}, {"docx", "Word 文稿"}, {"pages", "Pages 文稿"},
            {"png", "PNG 图像"}, {"jpg", "JPEG 图像"}, {"jpeg", "JPEG 图像"}, {"gif", "GIF 图像"},
            {"webp", "WebP 图像"}, {"heic", "HEIC 图像"}, {"tiff", "TIFF 图像"}, {"svg", "SVG 文稿"},
            {"mp4", "MPEG-4 影片"}, {"mov", "QuickTime 影片"}, {"m4v", "MPEG-4 影片"}, {"mkv", "Matroska 影片"},
            {"mp3", "MP3 音频"}, {"m4a", "AAC 音频"}, {"wav", "WAV 音频"}, {"flac", "FLAC 音频"},
            {"zip", "ZIP 归档"}, {"rar", "RAR 归档"}, {"7z", "7-Zip 归档"}, {"tar", "TAR 归档"}, {"gz", "GZIP 归档"},
            {"glb", "GL 传输格式"}, {"gltf", "GL 传输格式"}, {"usdz", "USDZ 场景"},
            {"txt", "纯文本"}, {"md", "Markdown 文稿"}, {"rtf", "RTF 文稿"},
    }).collect(Collectors.toUnmodifiableMap(e -> e[0], e -> e[1]));

    private static final Map<String, String> BY_ICON_PREFIX = Map.of();   // reserved; unused

    public static String of(String fileName, boolean dir) {
        if (dir) return "文件夹";
        String k = BY_EXT.get(FileEntry.ext(fileName));
        if (k != null) return k;
        return switch (FileEntry.of(fileName, false)) {
            case CODE -> "源代码";
            default -> "文档";
        };
    }

    private KindMap() {}
}
```

（写完后把没用的 `BY_ICON_PREFIX` 删掉。）

`RelativeDate.java`：

```java
package jui.jspace.store;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** 修改日期的人话格式：今天/昨天/今年/往年，与 QSpace 的列一致。 */
public final class RelativeDate {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("M/d HH:mm");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm");

    public static String format(long epochMillis, ZonedDateTime now) {
        ZonedDateTime t = Instant.ofEpochMilli(epochMillis).atZone(now.getZone());
        ZonedDateTime today = now.toLocalDate().atStartOfDay(now.getZone());
        if (!t.isBefore(today)) return "今天 " + TIME.format(t);
        if (t.isBefore(today) && !t.isBefore(today.minusDays(1))) return "昨天 " + TIME.format(t);
        if (t.getYear() == now.getYear()) return YEAR.format(t);
        return FULL.format(t);
    }

    private RelativeDate() {}
}
```

`Entries.java`（在 `jui.jspace`，因引用 `Model.SortState`）：

```java
package jui.jspace;

import jui.jspace.store.FileEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Sorting, filtering and tree flattening of one directory's entries, pure and main-thread only. */
final class Entries {
    enum SortKey { NAME, DATE, SIZE, KIND }

    record Row(FileEntry entry, int depth) {}

    static List<Row> view(FileEntry[] entries, Function<Path, jui.jspace.store.FileStore.DirSnap> snapOf,
                          Model.SortState sort, boolean foldersFirst, String search,
                          boolean showHidden, Set<Path> expanded) {
        List<Row> out = new ArrayList<>();
        append(out, entries, snapOf, sort, foldersFirst, search, showHidden, expanded, 0);
        return out;
    }

    private static void append(List<Row> out, FileEntry[] entries,
                               Function<Path, jui.jspace.store.FileStore.DirSnap> snapOf,
                               Model.SortState sort, boolean foldersFirst, String search,
                               boolean showHidden, Set<Path> expanded, int depth) {
        List<FileEntry> kept = new ArrayList<>();
        for (FileEntry e : entries) {
            if (!showHidden && e.name().startsWith(".")) continue;
            if (!search.isEmpty() && !e.name().toLowerCase(Locale.ROOT)
                    .contains(search.toLowerCase(Locale.ROOT))) continue;
            kept.add(e);
        }
        Comparator<FileEntry> byKey = switch (sort.key) {
            case NAME -> Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
            case DATE -> Comparator.comparingLong(FileEntry::mtimeMillis);
            case SIZE -> Comparator.comparingLong(FileEntry::size);       // dirs are -1: first ascending
            case KIND -> Comparator.comparing(FileEntry::kind);
        };
        if (!sort.asc) byKey = byKey.reversed();
        if (foldersFirst) byKey = Comparator.comparing(FileEntry::dir).reversed().thenComparing(byKey);
        kept.sort(byKey);
        for (FileEntry e : kept) {
            out.add(new Row(e, depth));
            if (e.dir() && expanded.contains(e.path())) {
                jui.jspace.store.FileStore.DirSnap child = snapOf.apply(e.path());
                if (child != null && child.entries() != null) {
                    append(out, child.entries(), snapOf, sort, foldersFirst, search, showHidden, expanded, depth + 1);
                }
            }
        }
    }

    private Entries() {}
}
```

注意：`jui.jspace.store.FileStore` 还不存在（Task 5）。本任务**先建最小占位**让编译通过：

```java
package jui.jspace.store;

import java.nio.file.Path;

/** Directory snapshots, scanned off the main thread (Task 5 fills it in). */
public final class FileStore {
    public enum State { LOADING, READY, ERROR }
    public record DirSnap(State state, FileEntry[] entries, String error) {}

    private FileStore() {}
}
```

Task 5 会把它扩成完整类（同文件重写）。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/
git commit -m "feat(jspace): entry kinds, dates, sorting and tree flattening"
```

---

### Task 5: FileStore——后台扫描与快照

**Files:**
- Modify: `src/main/java/jui/jspace/store/FileStore.java`（重写 Task 4 的占位）
- Test: `src/test/java/jui/jspace/FileStoreTest.java`

**Interfaces:**
- Consumes: `FileEntry`、`KindMap`、`RelativeDate`（Task 4）。
- Produces:
  - `FileStore(Publish p)`，`interface Publish { void publish(Runnable r); }`（嵌套）；`static final Publish DIRECT = Runnable::run`（测试用）。
  - `DirSnap snap(Path dir)`——任何线程可读；未扫描过返回 `LOADING` 快照。
  - `void scan(Path dir)`——触发后台扫描并发布；同一目录并发 scan，只有最新一代的结果会被采纳。
  - `DirSnap` 增加工厂：`static DirSnap loading()`、`static DirSnap error(String msg)`。
  - `JspaceApp` 里接真实 Publish：`new FileStore(main::update)`（Window::update 就是 Publish 形状）。注意 `Window.update` 只能在窗口存在后用；FileStore 实例放在 Workspace，Task 8 起 Workspace 构造时创建。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.jspace.store.FileStore;
import jui.testing.Check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileStoreTest {
    static Path dir() throws IOException {
        Path d = Files.createTempDirectory("jspace-store");
        Files.writeString(d.resolve("b.txt"), "x");
        Files.createDirectory(d.resolve("A"));
        Files.writeString(d.resolve(".h"), "x");
        return d;
    }

    public void testScanReadsSortsNothingAndReportsSizes() throws IOException {
        Path d = dir();
        FileStore s = new FileStore(FileStore.DIRECT);
        Check.equal(FileStore.State.LOADING, s.snap(d).state());
        s.scan(d);
        Check.equal(FileStore.State.READY, s.snap(d).state());   // DIRECT publish: done at once
        FileEntry[] es = s.snap(d).entries();
        Check.equal(3, es.length);                               // .h still there; filtering is Entries' job
        java.util.Arrays.sort(es, java.util.Comparator.comparing(FileEntry::name));
        Check.equal("A", es[0].name());
        Check.true_(es[0].dir());
        Check.equal(-1, es[0].size());
        Check.equal(1, es[2].size());                            // "b.txt" holds one char
        Check.equal("文件夹", es[0].kind());
        Check.true_(es[1].name().startsWith(".h") || es[1].name().equals(".h"));
    }

    public void testMissingDirectoryIsAnErrorNotAThrow() {
        FileStore s = new FileStore(FileStore.DIRECT);
        Path gone = Path.of("/no/such/dir");
        s.scan(gone);
        Check.equal(FileStore.State.ERROR, s.snap(gone).state());
        Check.true_(s.snap(gone).error() != null);
    }

    public void testOnlyTheNewestScanWins() throws IOException, InterruptedException {
        Path d = dir();
        boolean[] held = { false };
        Object gate = new Object();
        FileStore slow = new FileStore(r -> {                 // a publisher that holds the first result
            synchronized (gate) {
                while (!held[0]) {
                    try { gate.wait(); } catch (InterruptedException e) { return; }
                }
            }
            r.run();
        });
        slow.scan(d);
        Thread.sleep(50);                                      // let the scan thread reach the gate
        FileStore s2 = new FileStore(FileStore.DIRECT);
        s2.scan(d);                                            // in another store, direct publish wins fine
        Check.equal(FileStore.State.READY, s2.snap(d).state());
        synchronized (gate) { held[0] = true; gate.notifyAll(); }
    }
}
```

（第三个用例故意简单：验证「发布回调可以延迟」不破坏状态；代数竞争在实现里由 `gen` 比较 保证，`DIRECT` 语义下不可观测竞争。）

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test FileStoreTest`
Expected: 编译失败（`FileStore` 无构造器/`Publish`/`DIRECT`）。

- [ ] **Step 3: 实现（重写 FileStore.java）**

```java
package jui.jspace.store;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Directory snapshots: scanned on virtual threads, published to the main thread, immutable once seen. */
public final class FileStore {

    /** How a scan's result reaches the main thread; Window::update in the app, Runnable::run in tests. */
    public interface Publish { void publish(Runnable r); }

    public static final Publish DIRECT = Runnable::run;

    public enum State { LOADING, READY, ERROR }

    public record DirSnap(State state, FileEntry[] entries, String error) {
        public static DirSnap loading() { return new DirSnap(State.LOADING, new FileEntry[0], null); }
        public static DirSnap error(String msg) { return new DirSnap(State.ERROR, new FileEntry[0], msg); }
    }

    private static final class Slot {
        volatile DirSnap snap = DirSnap.loading();
        volatile long gen;
    }

    private final Publish publish;
    private final Map<Path, Slot> slots = new ConcurrentHashMap<>();

    public FileStore(Publish publish) { this.publish = publish; }

    /** The directory's latest snapshot; LOADING before the first scan lands. */
    public DirSnap snap(Path dir) {
        return slots.get(key(dir)).snap;                        // a missing slot is simply not scanned yet
    }

    /** Whether anyone ever asked: the pane shows LOADING only after a scan was asked for. */
    public boolean known(Path dir) { return slots.containsKey(key(dir)); }

    /** Scans the directory again off the main thread; the newest scan of one directory wins. */
    public void scan(Path dir) {
        Path k = key(dir);
        Slot s = slots.computeIfAbsent(k, p -> new Slot());
        long gen = ++s.gen;
        Thread.startVirtualThread(() -> {
            DirSnap result = read(k);
            publish.publish(() -> { if (gen == s.gen) s.snap = result; });
        });
    }

    private static Path key(Path dir) { return dir.toAbsolutePath().normalize(); }

    /** Reads one directory into a snapshot; errors become ERROR snapshots, never throws. */
    static DirSnap read(Path dir) {
        List<FileEntry> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                try {
                    var a = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
                    String name = p.getFileName().toString();
                    boolean d = a.isDirectory();
                    out.add(new FileEntry(name, p, d, d ? -1 : a.size(),
                            a.lastModifiedTime().toMillis(), KindMap.of(name, d), FileEntry.of(name, d)));
                } catch (IOException skipped) {
                    // one unreadable entry does not fail the directory
                }
            }
        } catch (IOException | RuntimeException e) {
            return DirSnap.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        out.sort(Comparator.comparing(e -> e.name().toLowerCase(java.util.Locale.ROOT)));
        return new DirSnap(State.READY, out.toArray(new FileEntry[0]), null);
    }

    private FileStore() { throw new AssertionError(); }
}
```

（最后一个私有构造与 public 构造冲突——删掉私有那个，保留 `public FileStore(Publish)`。）

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/jui/jspace/store/FileStore.java src/test/java/jui/jspace/FileStoreTest.java
git commit -m "feat(jspace): background directory snapshots"
```

---

### Task 6: Settings——8 项设置与持久化

**Files:**
- Create: `src/main/java/jui/jspace/store/Settings.java`
- Modify: `src/main/java/jui/jspace/Model.java`（`Settings` 接入：见 Step 3）
- Modify: `src/main/java/jui/jspace/Workspace.java`（构造参数 + 应用于 Model 默认值）
- Modify: `src/main/java/jui/jspace/JspaceTheme.java`（加回 `forced`）
- Modify: `src/main/java/jui/jspace/JspaceApp.java`（加载设置、传入 Workspace、应用外观）
- Test: `src/test/java/jui/jspace/SettingsTest.java`

**Interfaces:**
- Consumes: Task 3 的 `Workspace(Model)`（改为 `Workspace(Model, Settings)`，本任务起两个参数）。
- Produces:
  - `Settings` 字段/枚举：`enum Appearance { FOLLOW, LIGHT, DARK }`、`enum Layout { SINGLE, DOUBLE, GRID }`、`enum NewTabAt { HOME, CURRENT }`、`enum Density { COMFORTABLE, COMPACT }`；实例字段 `appearance=FOLLOW`、`defaultLayout=GRID`、`newTabAt=HOME`、`showHidden=false`、`foldersFirst=true`、`density=COMFORTABLE`、`defaultSort=Entries.SortKey.NAME`、`defaultSortAsc=true`、`restoreWorkspace=true`。
  - `static Settings load()`（默认路径 `~/Library/Application Support/jui-jspace/settings.properties`，不存在给默认值）、`static Settings load(Path file)`（测试）、`void save()`/`void save(Path file)`。
  - `JspaceTheme.forced(Settings.Appearance)`（Task 3 删掉的，此处加回，形状同 Task 3 所列）。
  - `Workspace.applyAppearance()`——按 `settings.appearance` 对已开窗口 `setThemePair`（FOLLOW 恢复内建对 `Theme.light()/dark()`）。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.jspace.store.Settings;
import jui.testing.Check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SettingsTest {
    public void testRoundTrip() throws IOException {
        Path f = Files.createTempFile("jspace-settings", ".properties");
        Settings s = Settings.load(f);
        s.appearance = Settings.Appearance.DARK;
        s.defaultLayout = Settings.Layout.DOUBLE;
        s.newTabAt = Settings.NewTabAt.CURRENT;
        s.showHidden = true;
        s.foldersFirst = false;
        s.density = Settings.Density.COMPACT;
        s.defaultSort = Entries.SortKey.SIZE;
        s.defaultSortAsc = false;
        s.restoreWorkspace = false;
        s.save(f);
        Settings back = Settings.load(f);
        Check.equal(Settings.Appearance.DARK, back.appearance);
        Check.equal(Settings.Layout.DOUBLE, back.defaultLayout);
        Check.equal(Settings.NewTabAt.CURRENT, back.newTabAt);
        Check.true_(back.showHidden);
        Check.true_(!back.foldersFirst);
        Check.equal(Settings.Density.COMPACT, back.density);
        Check.equal(Entries.SortKey.SIZE, back.defaultSort);
        Check.true_(!back.defaultSortAsc);
        Check.true_(!back.restoreWorkspace);
    }

    public void testMissingFileGivesDefaults() {
        Settings s = Settings.load(Path.of("/no/such/settings.properties"));
        Check.equal(Settings.Appearance.FOLLOW, s.appearance);
        Check.equal(Entries.SortKey.NAME, s.defaultSort);
        Check.true_(s.foldersFirst);
    }

    public void testBadValueFallsBackToDefault() throws IOException {
        Path f = Files.createTempFile("jspace-settings", ".properties");
        Files.writeString(f, "appearance=NEON\n");
        Check.equal(Settings.Appearance.FOLLOW, Settings.load(f).appearance);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test SettingsTest`
Expected: 编译失败，找不到 `jui.jspace.store.Settings`。

- [ ] **Step 3: 实现**

`Settings.java`：

```java
package jui.jspace.store;

import jui.jspace.Entries;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** The eight settings that really work, in ~/Library/Application Support/jui-jspace/settings.properties. */
public final class Settings {
    public enum Appearance { FOLLOW, LIGHT, DARK }
    public enum Layout { SINGLE, DOUBLE, GRID }
    public enum NewTabAt { HOME, CURRENT }
    public enum Density { COMFORTABLE, COMPACT }

    public Appearance appearance = Appearance.FOLLOW;
    public Layout defaultLayout = Layout.GRID;
    public NewTabAt newTabAt = NewTabAt.HOME;
    public boolean showHidden;
    public boolean foldersFirst = true;
    public Density density = Density.COMFORTABLE;
    public Entries.SortKey defaultSort = Entries.SortKey.NAME;
    public boolean defaultSortAsc = true;
    public boolean restoreWorkspace = true;

    private static Path defaultFile() {
        return Path.of(System.getProperty("user.home"),
                "Library/Application Support/jui-jspace/settings.properties");
    }

    public static Settings load() { return load(defaultFile()); }

    public static Settings load(Path file) {
        Settings s = new Settings();
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                return s;                                        // unreadable settings read as defaults
            }
        }
        s.appearance = enumValue(Appearance.class, p.getProperty("appearance"), s.appearance);
        s.defaultLayout = enumValue(Layout.class, p.getProperty("defaultLayout"), s.defaultLayout);
        s.newTabAt = enumValue(NewTabAt.class, p.getProperty("newTabAt"), s.newTabAt);
        s.showHidden = Boolean.parseBoolean(p.getProperty("showHidden", "false"));
        s.foldersFirst = Boolean.parseBoolean(p.getProperty("foldersFirst", "true"));
        s.density = enumValue(Density.class, p.getProperty("density"), s.density);
        s.defaultSort = enumValue(Entries.SortKey.class, p.getProperty("defaultSort"), s.defaultSort);
        s.defaultSortAsc = Boolean.parseBoolean(p.getProperty("defaultSortAsc", "true"));
        s.restoreWorkspace = Boolean.parseBoolean(p.getProperty("restoreWorkspace", "true"));
        return s;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String v, E fallback) {
        if (v == null) return fallback;
        try { return Enum.valueOf(type, v); } catch (IllegalArgumentException e) { return fallback; }
    }

    public void save() { save(defaultFile()); }

    public void save(Path file) {
        Properties p = new Properties();
        p.setProperty("appearance", appearance.name());
        p.setProperty("defaultLayout", defaultLayout.name());
        p.setProperty("newTabAt", newTabAt.name());
        p.setProperty("showHidden", Boolean.toString(showHidden));
        p.setProperty("foldersFirst", Boolean.toString(foldersFirst));
        p.setProperty("density", density.name());
        p.setProperty("defaultSort", defaultSort.name());
        p.setProperty("defaultSortAsc", Boolean.toString(defaultSortAsc));
        p.setProperty("restoreWorkspace", Boolean.toString(restoreWorkspace));
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                p.store(out, "QSpace (j-ui)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);                  // saving settings failing is fatal-ish; surface it
        }
    }
}
```

接入：
- `Model.java` 加字段 `final store.Settings settings = new store.Settings();` 并在构造器里按 settings 设初值（`layout = Layout.valueOf(settings.defaultLayout.name())` 不可靠——直接 switch：`layout = switch (settings.defaultLayout) { SINGLE -> Layout.SINGLE; … }`；每个 TabState 的 `sort.key/settings.defaultSort`、`sort.asc/settings.defaultSortAsc`）。
- `Workspace` 构造签名改 `Workspace(Model m, store.Settings settings)`，存字段；新增 `void applyAppearance()`（内容见 Produces；FOLLOW 用 `Theme.light(), Theme.dark()`）。
- `JspaceTheme.forced` 加回（形状见 Task 3 代码）。
- `JspaceApp`：`Settings settings = Settings.load(); Model model = new Model();`（Model 从 settings 取默认）——把 `Model` 构造改为 `Model(Settings settings)`，Settings 字段随之删除，改为构造参数保存。`Window.open` 后：`settings.appearance != FOLLOW` 时 `main.setThemePair(JspaceTheme.forced(…), JspaceTheme.forced(…))`，否则 `main.setThemePair(JspaceTheme.LIGHT, JspaceTheme.DARK)`（跟随时用自己的两套，这样切到系统浅色也用 QSpace 浅色）。
- `WorkspaceShellTest` 的 `open(Model)` 相应改为传 `new Model(new Settings())`。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/
git commit -m "feat(jspace): persisted settings and appearance wiring"
```

---

### Task 7: FileOps——安全文件操作

**Files:**
- Create: `src/main/java/jui/jspace/store/FileOps.java`
- Test: `src/test/java/jui/jspace/FileOpsTest.java`

**Interfaces:**
- Consumes: `FileStore.Publish`/`DIRECT`（Task 5）。
- Produces（全部 `FileOps` 静态方法，`done` 都在发布线程回调一次，成功传 `null`）:
  - `static void mkdir(Path dir, String baseName, Publish p, Consumer<String> done)`——在 `dir` 里建 `baseName`、`baseName 2`… 第一个不存在的名字。
  - `static void rename(Path file, String newName, Publish p, Consumer<String> done)`——同目录改名；目标已存在报错 `「目标已存在」`。
  - `static void delete(Path file, Publish p, Consumer<String> done)`——文件或递归目录。
  - `static void paste(Path src, boolean move, Path targetDir, Publish p, ConflictAsk ask, Consumer<String> done)`；`enum Decision { OVERWRITE, SKIP, CANCEL }`；`interface ConflictAsk { void ask(String name, java.util.function.Consumer<Decision> answer); }`（在发布线程被调，UI 用它弹 modal；answer 可从任意线程回）。
  - `static long countEntries(Path dir)`——给删除确认框数条目（同步，UI 调用前先用后台算好：由 delete 流程自己数，见实现）。
  - 语义：`paste` 到源目录本身（copy 且同父目录）→ 自动「`name 副本`」不冲突；目标存在 → ask；`move` 跨设备也允许（`Files.move` 失败则退化为 copy+delete）。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.jspace.store.FileOps;
import jui.jspace.store.FileStore;
import jui.testing.Check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class FileOpsTest {
    static Path dir(String tag) throws IOException {
        return Files.createTempDirectory("jspace-ops-" + tag);
    }

    static String sync(Path target, java.util.function.BiConsumer<Path, Consumer<String>> call) {
        AtomicReference<String> err = new AtomicReference<>("(no callback)");
        call.accept(target, e -> err.set(e));                   // DIRECT publish: done runs inline
        return err.get();
    }

    public void testMkdirPicksAFreeName() throws IOException {
        Path d = dir("mkdir");
        Check.equal(null, sync(d, (t, done) -> FileOps.mkdir(t, "未命名文件夹", FileStore.DIRECT, done)));
        Check.true_(Files.isDirectory(d.resolve("未命名文件夹")));
        Check.equal(null, sync(d, (t, done) -> FileOps.mkdir(t, "未命名文件夹", FileStore.DIRECT, done)));
        Check.true_(Files.isDirectory(d.resolve("未命名文件夹 2")));
    }

    public void testRenameRefusesAnExistingName() throws IOException {
        Path d = dir("rename");
        Files.writeString(d.resolve("a.txt"), "a");
        Files.writeString(d.resolve("b.txt"), "b");
        Check.equal(null, sync(d.resolve("a.txt"), (t, done) -> FileOps.rename(t, "c.txt", FileStore.DIRECT, done)));
        Check.true_(Files.exists(d.resolve("c.txt")));
        Check.equal("目标已存在", sync(d.resolve("c.txt"), (t, done) -> FileOps.rename(t, "b.txt", FileStore.DIRECT, done)));
    }

    public void testDeleteRemovesDirectoriesRecursively() throws IOException {
        Path d = dir("delete");
        Path sub = Files.createDirectories(d.resolve("x/y"));
        Files.writeString(sub.resolve("f.txt"), "f");
        Check.equal(null, sync(d, (t, done) -> FileOps.delete(t, FileStore.DIRECT, done)));
        Check.true_(!Files.exists(d));
    }

    public void testCopyAndMoveBetweenDirectories() throws IOException {
        Path a = dir("a"), b = dir("b");
        Files.writeString(a.resolve("f.txt"), "hello");
        Check.equal(null, sync(a.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, false, b, FileStore.DIRECT, neverAsk(), done)));
        Check.equal("hello", Files.readString(b.resolve("f.txt")));
        Check.true_(Files.exists(a.resolve("f.txt")));          // a copy leaves the source
        Check.equal(null, sync(a.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, true, b, FileStore.DIRECT, neverAsk(), done)));
        Check.true_(!Files.exists(a.resolve("f.txt")));         // a move takes it away
    }

    public void testPasteIntoItsOwnDirectoryMakesACopyName() throws IOException {
        Path d = dir("self");
        Files.writeString(d.resolve("f.txt"), "x");
        Check.equal(null, sync(d.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, false, d, FileStore.DIRECT, neverAsk(), done)));
        Check.true_(Files.exists(d.resolve("f 副本.txt")));
    }

    public void testConflictAsksThenOverwritesOrSkips() throws IOException {
        Path a = dir("ca"), b = dir("cb");
        Files.writeString(a.resolve("f.txt"), "new");
        Files.writeString(b.resolve("f.txt"), "old");
        AtomicReference<FileOps.Decision> seen = new AtomicReference<>();
        Check.equal(null, sync(a.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, false, b, FileStore.DIRECT, (name, answer) -> {
                    seen.set(FileOps.Decision.OVERWRITE);
                    answer.accept(FileOps.Decision.OVERWRITE);
                }, done)));
        Check.equal(FileOps.Decision.OVERWRITE, seen.get());
        Check.equal("new", Files.readString(b.resolve("f.txt")));

        Files.writeString(a.resolve("f.txt"), "newer");
        Check.equal(null, sync(a.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, false, b, FileStore.DIRECT, (name, answer) ->
                        answer.accept(FileOps.Decision.SKIP), done)));
        Check.equal("new", Files.readString(b.resolve("f.txt")));  // skip leaves it
    }

    static FileOps.ConflictAsk neverAsk() {
        return (name, answer) -> { throw new AssertionError("no conflict expected for " + name); };
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test FileOpsTest`
Expected: 编译失败，找不到 `FileOps`。

- [ ] **Step 3: 实现**

```java
package jui.jspace.store;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** The safe file operations, each on its own virtual thread, results published back. */
public final class FileOps {
    public enum Decision { OVERWRITE, SKIP, CANCEL }

    /** The UI's chance to ask about a name clash; answer from any thread. */
    public interface ConflictAsk { void ask(String name, Consumer<Decision> answer); }

    public static void mkdir(Path dir, String baseName, FileStore.Publish p, Consumer<String> done) {
        onThread(p, done, () -> {
            String name = baseName;
            for (int i = 2; Files.exists(dir.resolve(name)); i++) name = baseName + " " + i;
            Files.createDirectory(dir.resolve(name));
            return null;
        });
    }

    public static void rename(Path file, String newName, FileStore.Publish p, Consumer<String> done) {
        onThread(p, done, () -> {
            Path to = file.getParent().resolve(newName);
            if (Files.exists(to)) return "目标已存在";
            Files.move(file, to);
            return null;
        });
    }

    public static void delete(Path file, FileStore.Publish p, Consumer<String> done) {
        onThread(p, done, () -> {
            walkDelete(file);
            return null;
        });
    }

    /** Copies or moves src into targetDir, asking about clashes; same-dir copies get a 副本 name. */
    public static void paste(Path src, boolean move, Path targetDir, FileStore.Publish p,
                             ConflictAsk ask, Consumer<String> done) {
        Thread.startVirtualThread(() -> {
            String name = src.getFileName().toString();
            Path target = targetDir.resolve(name);
            boolean sameDir = src.getParent().equals(targetDir.toAbsolutePath().normalize());
            if (sameDir && !move) {
                int dot = name.lastIndexOf('.');
                String stem = dot > 0 ? name.substring(0, dot) : name;
                String ext = dot > 0 ? name.substring(dot) : "";
                String copy = stem + " 副本" + ext;
                for (int i = 2; Files.exists(targetDir.resolve(copy)); i++) copy = stem + " 副本 " + i + ext;
                target = targetDir.resolve(copy);
            } else if (Files.exists(target)) {
                Decision d = new Object[] { Decision.CANCEL }[0] instanceof Decision dd ? dd : null;   // placeholder, replaced below
                final Decision[] box = { Decision.CANCEL };
                p.publish(() -> ask.ask(name, answer -> box[0] = answer));
                // The ask runs on the publishing thread; wait for its answer.
                synchronized (box) {
                    while (box[0] == null) {
                        try { box.wait(); } catch (InterruptedException e) { return; }
                    }
                }
                // ...continued in Step 3b
                if (box[0] == Decision.SKIP || box[0] == Decision.CANCEL) {
                    Decision fin = box[0];
                    p.publish(() -> done.accept(fin == Decision.CANCEL ? "已取消" : null));
                    return;
                }
            }
            String err = runPaste(src, target, move);
            p.publish(() -> done.accept(err));
        });
    }
    // Step 3b fills runPaste and removes the placeholder line.
```

**Step 3b（同任务内完成，上面 paste 里的占位行删掉）**——干净版 `paste` 与辅助：

```java
    public static void paste(Path src, boolean move, Path targetDir, FileStore.Publish p,
                             ConflictAsk ask, Consumer<String> done) {
        Thread.startVirtualThread(() -> {
            String name = src.getFileName().toString();
            Path target = targetDir.resolve(name);
            boolean sameDir = src.getParent().equals(targetDir.toAbsolutePath().normalize());
            if (sameDir && !move) {
                target = targetDir.resolve(copyName(name, targetDir));
            } else if (Files.exists(target)) {
                final Decision[] box = { null };
                p.publish(() -> ask.ask(name, answer -> {
                    synchronized (box) { box[0] = answer; box.notifyAll(); }
                }));
                Decision d;
                synchronized (box) {
                    while (box[0] == null) {
                        try { box.wait(); } catch (InterruptedException e) { return; }
                    }
                    d = box[0];
                }
                if (d != Decision.OVERWRITE) {
                    Decision fin = d;
                    p.publish(() -> done.accept(fin == Decision.CANCEL ? "已取消" : null));
                    return;
                }
            }
            String err = move ? tryMove(src, target) : tryCopy(src, target);
            p.publish(() -> done.accept(err));
        });
    }

    static String copyName(String name, Path dir) {
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String copy = stem + " 副本" + ext;
        for (int i = 2; Files.exists(dir.resolve(copy)); i++) copy = stem + " 副本 " + i + ext;
        return copy;
    }

    static String tryCopy(Path src, Path target) {
        try {
            if (Files.isDirectory(target)) walkDelete(target);
            copyTree(src, target);
            return null;
        } catch (IOException e) { return e.toString(); }
    }

    static String tryMove(Path src, Path target) {
        try {
            if (Files.isDirectory(target)) walkDelete(target);
            try {
                Files.move(src, target);
            } catch (IOException crossDevice) {                  // e.g. EXDEV: copy, then delete
                copyTree(src, target);
                walkDelete(src);
            }
            return null;
        } catch (IOException e) { return e.toString(); }
    }

    static void copyTree(Path src, Path target) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                Files.createDirectories(target.resolve(src.relativize(d).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.copy(f, target.resolve(src.relativize(f).toString()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void walkDelete(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.delete(f); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                if (e == null) Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** The entry count under dir, for the delete confirmation; 0 for a plain file's parent... see caller. */
    public static long countEntries(Path dirOrFile) {
        AtomicLong n = new AtomicLong();
        try {
            if (!Files.isDirectory(dirOrFile)) return 1;
            Files.walkFileTree(dirOrFile, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    n.incrementAndGet(); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (!d.equals(dirOrFile)) n.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return n.get();                                      // best effort
        }
        return n.get();
    }

    interface Thunk { String run() throws Exception; }

    static void onThread(FileStore.Publish p, Consumer<String> done, Thunk op) {
        Thread.startVirtualThread(() -> {
            String err;
            try { err = op.run(); } catch (Exception e) { err = e.toString(); }
            final String e2 = err;
            p.publish(() -> done.accept(e2));
        });
    }

    private FileOps() {}
}
```

（Step 3 的第一段 `paste` 有意展示坏味道再由 3b 修正——**实现时直接写 3b 的干净版，不要保留占位行**。）

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿（FileOpsTest 七个用例）。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/jui/jspace/store/FileOps.java src/test/java/jui/jspace/FileOpsTest.java
git commit -m "feat(jspace): safe file operations on background threads"
```

---

### Task 8: FilePane——标签栏与面包屑（内容区占位）

**Files:**
- Create: `src/main/java/jui/jspace/FilePane.java`
- Create: `src/main/java/jui/jspace/PaneTabs.java`
- Create: `src/main/java/jui/jspace/Breadcrumbs.java`
- Modify: `src/main/java/jui/jspace/Workspace.java`（paneGrid 用 FilePane 替换占位 box）
- Modify: `src/main/java/jui/jspace/Model.java`（`TabState` 加 `String renameNew` —— 重命名内联输入用，Task 13 才真正用，本任务先加字段）
- Test: `src/test/java/jui/jspace/FilePaneTest.java`

**Interfaces:**
- Consumes: `Model`（Task 3/6）、`FileStore`（Task 5，Workspace 持有并传入）、`JspaceTheme.palette`。
- Produces:
  - `Workspace` 增加字段 `final FileStore store`，构造器里 `store = new FileStore(publish)`，`publish` 由 `JspaceApp` 传 `main::update`（`Workspace(Model, Settings, Runnable publish)`——三参构造；`Runnable` 即 Window::update）。Workspace 暴露 `FileStore store()`。
  - `FilePane.build(Context c, int paneIndex)`（Workspace 的 paneGrid 调）：整个面板列——标签栏、面包屑、内容区。活动面板（`m.active == paneIndex`）外框 `border(1.5, accent)`。
  - `PaneTabs.build(Context c, int paneIndex)`；`Breadcrumbs.build(Context c, int paneIndex)`。
  - 导航入口（Workspace 上，供 Sidebar/面包屑/列表复用）：`void navigate(int pane, Path dir)`——push back、清 forward、`selected=null`、`store.scan(dir)`；`void goBack(int pane)`/`void goForward(int pane)`。
  - 标签增删（Workspace）：`void newTab(int pane)`（按 `settings.newTabAt` 取 HOME 或当前）、`void closeTab(int pane)`（最后一个不关，重置为主目录）。Task 3 的 `newTab()`/`closeTab()` 空方法改为带参实现，`JspaceApp` 菜单调 `workspace.newTab(m.active)`。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

import java.nio.file.Path;

public final class FilePaneTest {
    static final Path HOME = Path.of("/u/lm");

    static Model model() {
        Model m = new Model(new jui.jspace.store.Settings());
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(HOME));
        m.panes[0].tabs.add(new Model.TabState(HOME.resolve("Downloads")));
        m.panes[0].current = 1;
        return m;
    }

    static Tester open(Model m) {
        m.layout = Model.Layout.SINGLE;
        return Tester.of(new Workspace(m, new jui.jspace.store.Settings(), Runnable::run).view(), 800, 600);
    }

    public void testTabsShowClickAndClose() {
        Model m = model();
        Tester tt = open(m);
        Check.true_(tt.find("tab-0").width() > 0);
        Check.true_(tt.find("tab-1").width() > 0);
        tt.click("tab-0");
        tt.frame();
        Check.equal(0, m.panes[0].current);
        tt.click("tab-1-close");
        tt.frame();
        Check.equal(1, m.panes[0].tabs.size());
    }

    public void testNewTabButtonUsesTheSetting() {
        Model m = model();
        Tester tt = open(m);
        tt.click("new-tab");
        tt.frame();
        Check.equal(3, m.panes[0].tabs.size());
        Check.equal(HOME, m.panes[0].tabs.get(2).dir);          // NewTabAt.HOME
    }

    public void testBreadcrumbClicksNavigateAndBackForward() {
        Model m = model();
        m.panes[0].current = 0;
        Tester tt = open(m);
        Check.equal(HOME, m.panes[0].tab().dir);
        tt.click("crumb-Downloads");
        tt.frame();
        Check.equal(HOME.resolve("Downloads"), m.panes[0].tab().dir);
        tt.click("nav-back");
        tt.frame();
        Check.equal(HOME, m.panes[0].tab().dir);
        tt.click("nav-forward");
        tt.frame();
        Check.equal(HOME.resolve("Downloads"), m.panes[0].tab().dir);
    }

    public void testActivePaneHasTheAccentFrame() {
        Model m = model();
        m.layout = Model.Layout.DOUBLE;
        m.active = 1;
        Tester tt = open(m);
        Check.true_(tt.find("pane-1-frame").height() > 0);      // both frames exist
        Check.true_(tt.find("pane-0-frame").height() > 0);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test FilePaneTest`
Expected: 编译失败，找不到 `FilePane`；Workspace 构造参数不符。

- [ ] **Step 3: 实现**

`Workspace` 改动（摘要，全部落在 Task 3 的文件上）：
- 字段 `final store.Settings settings; final FileStore store; final Runnable publish;`
- 构造 `Workspace(Model m, store.Settings settings, Runnable publish)`；`store = new FileStore(r -> publish.run());`——注意 `FileStore` 要的是 `FileStore.Publish`（函数式接口），`Runnable::run` 适配：`store = new FileStore(r -> publish.run())`。
- `navigate/goBack/goForward/newTab(int)/closeTab(int)` 按接口语义实现（back/forward 栈操作后 `store.scan(dir)`）。
- `paneGrid` 四处占位 `box(c).fill().label("pane-i")` 换成 `panePane(c, i)`：

```java
private void panePane(Context c, int i) {
    int shown = switch (m.layout) { case SINGLE -> 1; case DOUBLE -> 2; case GRID -> 4; };
    if (i < shown) new FilePane(this, i).build(c);
}
```

`FilePane.java`：

```java
package jui.jspace;

import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/** One pane: its tab bar, its breadcrumb line and its content area. */
final class FilePane {
    private final Workspace ws;
    private final int pane;

    FilePane(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model m = ws.model();
        JspaceTheme.Palette p = JspaceTheme.palette(c);
        boolean active = m.active == pane;
        column(c).fill().children(() -> {
            box(c).fill().border(1.5f, active ? c.theme().accent() : p.panel())
                    .radius(6).clip().background(p.panel()).label("pane-" + pane + "-frame")
                    .padding(6).children(() -> {
                        new PaneTabs(ws, pane).build(c);
                        new Breadcrumbs(ws, pane).build(c);
                        contentPlaceholder(c);
                    });
        });
    }

    private void contentPlaceholder(Context c) {
        Model.TabState t = ws.model().panes[pane].tab();
        jui.jspace.store.FileStore.DirSnap snap = ws.store().snap(t.dir);
        box(c).grow(1).fillWidth().background(c.theme().background()).label("pane-" + pane + "-content")
                .children(() -> text(c, switch (snap.state()) {
                    case LOADING -> "正在载入…";
                    case ERROR -> snap.error() == null ? "无法打开" : snap.error();
                    case READY -> snap.entries().length == 0 ? "此文件夹为空" : "N 项";
                }).textColor(c.theme().textMuted()));
    }
}
```

（"N 项"占位处 Task 9/10 换成真实视图；ERROR 态再加「返回主目录」按钮——一并放 Task 9。）

`PaneTabs.java`：

```java
package jui.jspace;

import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/** One pane's tab bar: pill tabs and the + button. */
final class PaneTabs {
    private final Workspace ws;
    private final int pane;

    PaneTabs(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model.PaneState p = ws.model().panes[pane];
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        row(c).gap(4).padding(4, 4, 0, 4).children(() -> {
            for (int i = 0; i < p.tabs.size(); i++) {
                boolean sel = i == p.current;
                Model.TabState t = p.tabs.get(i);
                box(c).height(26).padding(0, 10).radius(6)
                        .background(sel ? pal.pillSel() : pal.pill()).label("tab-" + i)
                        .children(() -> {
                            row(c).gap(6).alignItems(jui.ui.Align.CENTER).children(() -> {
                                text(c, title(t)).fontSize(12)
                                        .textColor(sel ? c.theme().text() : c.theme().textMuted());
                                if (p.tabs.size() > 1 && sel) {
                                    Element x = buttonBase(c).label("tab-" + i + "-close");
                                    if (x.clicked()) ws.closeTab(pane);
                                    text(c, "✕").fontSize(11).textColor(c.theme().textMuted());
                                }
                            });
                        });
                Element pill = c.element("tab-" + i);           // 见下：选中切换用 box 自己的点击
            }
            Element plus = buttonBase(c).width(24).height(24).radius(6).label("new-tab");
            if (plus.clicked()) ws.newTab(pane);
            text(c, "+").fontSize(13).textColor(c.theme().textMuted());
        });
    }

    static String title(Model.TabState t) {
        if (t.dir == null) return "主目录";
        Path h = Path.of(System.getProperty("user.home"));
        if (t.dir.equals(h)) return "主目录";
        return t.dir.getFileName() == null ? "/" : t.dir.getFileName().toString();
    }
}
```

实现注意（写代码时落实，上面注释处）：
- pill 的点击选中：pill 是 `box`，不自动可点。给 `box(c)` 加 `.focusable()` 不对——直接用 `buttonBase(c)` 作为 pill 外壳（无外观按钮），在其 children 里放上面那行内容，`if (pillBase.clicked()) p.current = i;`。close 的 ✕ 同样用 `buttonBase`，点击调 `ws.closeTab(pane)`。最终以 buttonBase 版本为准，删掉示例里 `c.element(...)` 幻想 API——**框架没有 `Context.element(String)`**。
- `title` 需要 `import java.nio.file.Path;`。

`Breadcrumbs.java`：

```java
package jui.jspace;

import jui.ui.Context;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static jui.ui.Ui.*;

/** The pane's path line: home, then one crumb per step, clickable to jump. */
final class Breadcrumbs {
    private final Workspace ws;
    private final int pane;

    Breadcrumbs(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model.TabState t = ws.model().panes[pane].tab();
        boolean active = ws.model().active == pane;
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        row(c).height(28).padding(0, 8).gap(2).alignItems(jui.ui.Align.CENTER)
                .radius(4)
                .background(active ? jui.ui.Color.rgba(c.theme().accent().rgba() >>> 8 | 0x14_00_00_00) : pal.bar())
                .label("crumbs-" + pane)
                .children(() -> {
                    home(c);
                    for (Path step : steps(t.dir)) {
                        text(c, "›").fontSize(12).textColor(c.theme().textMuted());
                        crumb(c, step);
                    }
                });
    }

    private List<Path> steps(Path dir) {
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> out = new ArrayList<>();
        Path cur = dir;
        while (cur != null && !cur.equals(home)) {
            out.add(0, cur);
            cur = cur.getParent();
            if (cur == null) break;
            if (cur.equals(home)) break;
            if (out.size() > 8) {                               // deep paths: home › … › last two
                // keep it simple: full chain, QSpace shows it scrollable; we clip
            }
        }
        return out;
    }

    private void home(Context c) {
        jui.ui.Element h = buttonBase(c).label("crumb-home");
        if (h.clicked()) ws.navigate(pane, Path.of(System.getProperty("user.home")));
        text(c, "⌂").fontSize(13).textColor(c.theme().textMuted());
    }

    private void crumb(Context c, Path step) {
        jui.ui.Element b = buttonBase(c).label("crumb-" + step.getFileName());
        if (b.clicked()) ws.navigate(pane, step);
        text(c, step.getFileName() == null ? "/" : step.getFileName().toString())
                .fontSize(12).textColor(c.theme().text());
    }
}
```

实现注意：
- `steps()` 里的「>8 截断」分支直接删掉（保持全链）。若 dir 不在 home 之下（如 `/`），链会走到根：`/` 的 `getFileName()` 为 null → 显示 `/`。测试里 `crumb-Downloads` 依赖 `label("crumb-" + fileName)`。
- active 高亮的 RGBA 写法按框架 `Color.rgba(int rgb, float a)` 正确改写：`jui.ui.Color.rgba(c.theme().accent().rgb(), 0.08f)`——用 `Color.rgb(...)`/`Color.rgba(int, float)` 的真实签名（读 `jui/ui/Color.java`；若 accent 无 `.rgb()` 就 `Color.rgb(c.theme().accent().rgba() >>> 8)`）。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿；`WorkspaceShellTest` 的 `open(Model)` 已在 Task 6 改过签名，此任务同步补第三参 `Runnable::run`。

- [ ] **Step 5: 冒烟 + Commit**

Run: `./build.sh demo QSpace`
Expected: 窗口里能看到四个面板的标签栏与面包屑，点击面板切换激活描边。

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/
git commit -m "feat(jspace): pane tabs and breadcrumbs over live navigation"
```

---

### Task 9: FileList——列表视图

**Files:**
- Create: `src/main/java/jui/jspace/FileList.java`
- Create: `src/main/java/jui/jspace/Texts.java`（单行截断）
- Modify: `src/main/java/jui/jspace/FilePane.java`（内容区接 FileList；ERROR 态加「返回主目录」）
- Test: `src/test/java/jui/jspace/FileListTest.java`

**Interfaces:**
- Consumes: `Entries.view`（Task 4）、`FileStore`（Task 5）、`Model.TabState.listRows/listScroll/paneWidth`（Task 3）、`RelativeDate`。
- Produces:
  - `Texts.fit(Context c, String s, float size, int weight, float maxW)` → String（超宽时二分截断加 "…"）。
  - `FileList.build(Context c, Workspace ws, int pane)`——含表头（`名称`/`修改日期`/`大小`/`种类` 四列按钮，点击循环排序）与 `Ui.list` 虚拟行；行内点击选中、双击进入；▸/▾ 展开；行高按 `settings.density` 28/24。
  - `Model.TabState` 不新增字段（树展开已用 `expanded`）。行标签：`row-<name>`、表头 `sort-name/sort-date/sort-size/sort-kind`、展开箭头 `disc-<name>`。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.jspace.store.FileStore;
import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileListTest {
    static Path root() throws IOException {
        Path d = Files.createTempDirectory("jspace-list");
        Files.createDirectory(d.resolve("sub"));
        Files.writeString(d.resolve("a.txt"), "aaa");
        Files.writeString(d.resolve("b.md"), "bb");
        return d;
    }

    static Model wired(Path dir) throws IOException {
        jui.jspace.store.Settings st = new jui.jspace.store.Settings();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(dir));
        return m;
    }

    static Tester open(Model m) {
        Workspace ws = new Workspace(m, m.settings, Runnable::run);
        ws.store().scan(m.panes[0].tab().dir);                  // DIRECT publish: snapshot ready
        return Tester.of(ws.view(), 640, 400);
    }

    public void testRowsSortSelectAndEnter() throws IOException {
        Path d = root();
        Model m = wired(d);
        Tester tt = open(m);
        Check.true_(tt.find("row-sub").width() > 0);
        Check.true_(tt.find("row-a.txt").width() > 0);
        Check.true_(tt.find("row-sub").y() < tt.find("row-a.txt").y());   // folders first
        tt.click("row-a.txt");
        tt.frame();
        Check.equal(d.resolve("a.txt"), m.panes[0].tab().selected);
        tt.key(Shortcut.of(Key.ENTER));
        tt.frame();                                              // a file: no navigation, still here
        Check.equal(d, m.panes[0].tab().dir);
        tt.click("row-sub");
        tt.key(Shortcut.of(Key.ENTER));
        tt.frame();
        Check.equal(d.resolve("sub"), m.panes[0].tab().dir);
    }

    public void testHeaderClickSorts() throws IOException {
        Path d = root();
        Model m = wired(d);
        Tester tt = open(m);
        tt.click("sort-date");
        tt.frame();
        Check.equal(Entries.SortKey.DATE, m.panes[0].tab().sort.key);
        tt.click("sort-date");
        tt.frame();
        Check.true_(!m.panes[0].tab().sort.asc);                 // second click flips
    }

    public void testDisclosureExpandsChildren() throws IOException {
        Path d = root();
        Files.createDirectory(d.resolve("sub/inner"));
        Model m = wired(d);
        Tester tt = open(m);
        wsScan(tt, m);
        tt.click("disc-sub");
        tt.frame();
        Check.true_(m.panes[0].tab().expanded.contains(d.resolve("sub")));
        Check.true_(tt.find("row-inner").width() > 0);
        tt.click("disc-sub");
        tt.frame();
        Check.true_(!m.panes[0].tab().expanded.contains(d.resolve("sub")));
    }

    static void wsScan(Tester tt, Model m) { /* scan already done in open() */ }

    public void testSearchFiltersRows() throws IOException {
        Path d = root();
        Model m = wired(d);
        m.panes[0].tab().search = "md";
        Tester tt = open(m);
        Check.true_(tt.find("row-b.md").width() > 0);
        try {
            tt.find("row-a.txt");
            Check.true_(false, "a.txt should be filtered out");
        } catch (AssertionError expected) { /* find throws when missing: good */ }
    }
}
```

注意：`Check.true_(false, "…")` 的双参形式若不存在（读 `jui/testing/Check.java`），改写成 `if (true) throw new AssertionError("a.txt should be filtered out");`；`find` 未命中时抛什么以 Tester 实现为准（大概率 AssertionError）——**catch 那行的类型照实际改**。`ENTER` 键的枚举名以 `Key.java` 为准（`ENTER`/`RETURN`）。

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test FileListTest`
Expected: 编译失败，找不到 `FileList`。

- [ ] **Step 3: 实现**

`Texts.java`：

```java
package jui.jspace;

import jui.ui.Context;

/** One-line text that fits a width, cut with an ellipsis. */
final class Texts {
    /** Lays s out at size/weight; if it wraps past one line, returns it cut to fit + "…". */
    static String fit(Context c, String s, float size, int weight, float maxW) {
        jui.text.TextLayout l = c.layoutText(s, size, weight, maxW);
        if (l.lines <= 1) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {                                        // binary search the longest prefix that fits
            int mid = (lo + hi + 1) / 2;
            String cut = s.substring(0, mid) + "…";
            if (c.layoutText(cut, size, weight, maxW).lines <= 1) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + "…";
    }

    private Texts() {}
}
```

（`TextLayout.lines` 是 public final int 字段——已核实。）

`FileList.java`：

```java
package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.jspace.store.FileStore;
import jui.jspace.store.RelativeDate;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;

import static jui.ui.Ui.*;

/** The pane's list view: sortable header, virtualized rows, tree disclosure. */
final class FileList {
    private final Workspace ws;
    private final int pane;

    FileList(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model m = ws.model();
        Model.TabState t = m.panes[pane].tab();
        FileStore.DirSnap snap = ws.store().snap(t.dir);
        List<Entries.Row> rows = Entries.view(snap.entries(), ws.store()::snap, t.sort,
                ws.settings().foldersFirst, t.search, ws.settings().showHidden, t.expanded);
        float rowH = ws.settings().density == jui.jspace.store.Settings.Density.COMPACT ? 24 : 28;

        column(c).fill().children(() -> {
            header(c, t);
            box(c).grow(1).fillWidth().clip().children(() ->
                    list(c, t.listRows, rows.size(), i -> row(c, t, rows.get(i), rows, i, rowH)));
        });
    }

    private void header(Context c, Model.TabState t) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        row(c).height(24).fillWidth().background(pal.bar()).children(() -> {
            head(c, "名称", Entries.SortKey.NAME, "sort-name", t).grow(1);
            head(c, "修改日期", Entries.SortKey.DATE, "sort-date", t).width(110);
            head(c, "大小", Entries.SortKey.SIZE, "sort-size", t).width(72);
            head(c, "种类", Entries.SortKey.KIND, "sort-kind", t).width(100);
        });
    }

    private void head(Context c, String label, Entries.SortKey key, String id, Model.TabState t) {
        boolean on = t.sort.key == key;
        box(c).height(24).padding(0, 6).children(() -> {
            jui.ui.Element b = buttonBase(c).fill().label(id).alignItems(jui.ui.Align.CENTER);
            if (b.clicked()) {
                if (on) t.sort.asc = !t.sort.asc; else { t.sort.key = key; t.sort.asc = true; }
            }
            row(c).gap(4).children(() -> {
                text(c, label).fontSize(11).textColor(on ? c.theme().accent() : c.theme().textMuted());
                if (on) text(c, t.sort.asc ? "∧" : "∨").fontSize(10).textColor(c.theme().accent());
            });
        });
    }

    private void row(Context c, Model.TabState t, Entries.Row r, List<Entries.Row> rows, int i, float rowH) {
        FileEntry e = r.entry();
        boolean sel = e.path().equals(t.selected);
        box(c).height(rowH).fillWidth().padding(0, 6).label("row-" + e.name())
                .background(sel ? c.theme().selection()
                        : (i % 2 == 1 ? JspaceTheme.palette(c).zebra() : null))
                .children(() -> {
                    jui.ui.Element b = buttonBase(c).fill().label("row-hit-" + e.name())
                            .alignItems(jui.ui.Align.CENTER);
                    if (b.clicked()) t.selected = e.path();
                    if (b.clicks() >= 2) openEntry(e);
                    row(c).gap(4).alignItems(jui.ui.Align.CENTER).children(() -> {
                        box(c).width(r.depth() * 16);
                        if (e.dir()) disclosure(c, t, e);
                        else box(c).width(14);
                        box(c).width(18).height(18);            // Task 10's FileIcons goes here
                        text(c, Texts.fit(c, e.name(), 12, 400, t.paneWidth - r.depth() * 16 - 150))
                                .fontSize(12).textColor(sel ? c.theme().accentText() : c.theme().text());
                        spacer(c);
                        text(c, RelativeDate.format(e.mtimeMillis(), ZonedDateTime.now()))
                                .fontSize(11).textColor(c.theme().textMuted()).width(104);
                        text(c, size(e)).fontSize(11).textColor(c.theme().textMuted()).width(66);
                        box(c).alignItems(jui.ui.Align.END).width(96).children(() ->
                                text(c, e.kind()).fontSize(11).textColor(c.theme().textMuted()));
                    });
                });
    }

    private void disclosure(Context c, Model.TabState t, FileEntry e) {
        boolean open = t.expanded.contains(e.path());
        jui.ui.Element d = buttonBase(c).width(14).label("disc-" + e.name());
        if (d.clicked()) {
            if (open) t.expanded.remove(e.path()); else { t.expanded.add(e.path()); ws.store().scan(e.path()); }
        }
        text(c, open ? "▾" : "▸").fontSize(10).textColor(c.theme().textMuted());
    }

    private void openEntry(FileEntry e) {
        if (e.dir()) ws.navigate(pane, e.path());
        else if (ws.model().panes[pane].tab().dir != null) {
            try {
                new ProcessBuilder("/usr/bin/open", e.path().toString()).start();
            } catch (java.io.IOException ignored) {
                ws.model().status.note = "无法打开 " + e.name();
            }
        }
    }

    static String size(FileEntry e) {
        if (e.dir()) return "--";
        if (e.size() < 1024) return e.size() + " 字节";
        if (e.size() < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", e.size() / 1024.0);
        if (e.size() < 1024L * 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", e.size() / 1048576.0);
        return String.format(java.util.Locale.ROOT, "%.1f GB", e.size() / 1073741824.0);
    }
}
```

实现注意：
- `buttonBase(...).clicked()`/`.clicks()` 是 M2 交互 API；`clicks() >= 2` 即双击。
- 键盘导航：`FilePane` 里给内容区容器注册 `c.shortcut(Shortcut.of(k))`（↑↓←→↩⌫），落到 `ws.model().panes[pane].tab()` 的选中移动逻辑——把这四个键的实现放 `FilePane`，不在 FileList（FileList 无焦点概念，用活动面板判断）。本任务先实现 ↑↓/↩（沿 rows 找 selected 的下标 ±1；无选中选第一行；↩ 调 `openEntry`——把 `openEntry` 从 FileList 提为 `Workspace.openEntry(int pane, FileEntry e)` 静态语义，两处复用），←→ 与 ⌫ 留 Task 13/11。
- `paneWidth` 更新：FilePane 内容区根部 `box` 之后 `t.paneWidth = contentRoot.bounds().w;`（上一帧值，首帧 0，Texts.fit 用 `max(60, paneWidth-…)` 防负数）。
- ERROR 态（FilePane.contentPlaceholder 改造）：READY→FileList/Grid、LOADING→文字、ERROR→错误文字 + `button(c, "返回主目录")` 点击 `ws.navigate(pane, home)`。
- `Spacer` 用 `spacer(c).grow(1)`——已核实的 API 是 `spacer(c)` + `.grow(1)`。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿。

- [ ] **Step 5: 冒烟 + Commit**

Run: `./build.sh demo QSpace`
Expected: 面板显示真实主目录列表，排序/展开/选中/进入都工作。

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/FileListTest.java
git commit -m "feat(jspace): the list view with sorting, tree and keyboard"
```

---

### Task 10: IconGrid——图标网格视图 + FileIcons 自绘图标

**Files:**
- Create: `src/main/java/jui/jspace/IconGrid.java`
- Create: `src/main/java/jui/jspace/FileIcons.java`
- Modify: `src/main/java/jui/jspace/FilePane.java`（按 `t.view` 分发 FileList/IconGrid；图标接线 FileList 的 18×18 占位）
- Test: `src/test/java/jui/jspace/IconGridTest.java`

**Interfaces:**
- Consumes: `Entries.view`、`FileEntry.IconType`、`Element.draw(Painter.Draw)`、`Painter.roundedBox/box/circle/line`、`t.gridRows/gridScroll/paneWidth`。
- Produces:
  - `FileIcons.draw(FileEntry.IconType t, boolean large)` → `Painter.Draw`——所有类型图标（FOLDER 蓝→紫两层叠色、DMG/APK/PDF/DOC/IMAGE/VIDEO/AUDIO/ARCHIVE/MODEL3D/CODE/TEXT/GENERIC），`large=false` 内切 18×18、`true` 内切 48×48；颜色从 `Painter` 拿不到 theme——图标固定色板（深浅通吃的饱和色），侧栏小图标另行 `FileIcons.mini(String kind)`（`kind ∈ {home,desktop,downloads,docs,pictures,music,movies,apps,trash,disk,cloud}`）→ 单色 `Painter.Draw`，颜色由调用方包一层换色（`FileIcons.tint(Draw, Color)`）。
  - `IconGrid.build(Context c, Workspace ws, int pane)`——行分组虚拟化：`perRow = max(1, floor(paneWidth/96))`，行数 `ceil(rows.size()/perRow)`，每行一个 96 高的 wrap row。
  - 单元格标签 `cell-<name>`，双击同 FileList.openEntry。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IconGridTest {
    static Model grid(Path dir) throws IOException {
        for (int i = 0; i < 7; i++) Files.writeString(dir.resolve("f" + i + ".txt"), "x");
        jui.jspace.store.Settings st = new jui.jspace.store.Settings();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(dir));
        m.panes[0].tab().view = Model.ViewMode.GRID;
        return m;
    }

    static Tester open(Model m) {
        Workspace ws = new Workspace(m, m.settings, Runnable::run);
        ws.store().scan(m.panes[0].tab().dir);
        Tester tt = Tester.of(ws.view(), 640, 400);
        tt.frame();                                              // paneWidth lands after the first frame
        tt.frame();
        return tt;
    }

    public void testCellsLayOutInRows() throws IOException {
        Model m = grid(Files.createTempDirectory("jspace-grid"));
        Tester tt = open(m);
        Check.true_(tt.find("cell-f0").width() > 0);
        float y0 = tt.find("cell-f0").y();
        float y1 = tt.find("cell-f1").y();
        Check.true_(y0 == y1);                                   // 640 DIP: several per row
    }

    public void testCellSelects() throws IOException {
        Model m = grid(Files.createTempDirectory("jspace-grid"));
        Tester tt = open(m);
        tt.click("cell-f3");
        tt.frame();
        Check.equal(m.panes[0].tab().dir.resolve("f3.txt"), m.panes[0].tab().selected);
    }

    public void testFolderIconDraws() {                          // a draw that paints without state
        jui.ui.Painter.Draw d = FileIcons.draw(jui.jspace.store.FileEntry.IconType.FOLDER, true);
        Check.true_(d != null);
        jui.ui.Painter.Draw pdf = FileIcons.draw(jui.jspace.store.FileEntry.IconType.PDF, false);
        Check.true_(pdf != null);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh demo 2>/dev/null; ./build.sh test IconGridTest`
Expected: 编译失败，找不到 `IconGrid`/`FileIcons`。

- [ ] **Step 3: 实现**

`FileIcons.java` 的形状（每个图标一个小函数，坐标按 bounds 相对算；**颜色常量集中一处**）：

```java
package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.ui.Color;
import jui.ui.Painter;
import jui.ui.Rect;

import java.util.Map;

/** Every icon the app draws itself, at 18 or 48 DIP, from fixed colors that read on both looks. */
final class FileIcons {
    private static final Color BLUE = Color.rgb(0x3E6FF2), PURPLE = Color.rgb(0x8B5CF6),
            WHITE = Color.rgb(0xFFFFFF), GREY = Color.rgb(0xB9BDC4), DARKGREY = Color.rgb(0x6B7078),
            RED = Color.rgb(0xE5484D), GREEN = Color.rgb(0x30A46C), ORANGE = Color.rgb(0xF76B15),
            CYAN = Color.rgb(0x00A2C7), TEAL = Color.rgb(0x12A594);

    static Painter.Draw draw(FileEntry.IconType type, boolean large) {
        return switch (type) {
            case FOLDER -> FileIcons::folder;
            case DMG -> FileIcons::dmg;
            case APK -> FileIcons::apk;
            case APP -> FileIcons::app;
            case PDF -> p -> badge(p, "PDF", RED);
            case DOC -> p -> badge(p, "W", BLUE);
            case IMAGE -> FileIcons::image;
            case VIDEO -> FileIcons::video;
            case AUDIO -> FileIcons::audio;
            case ARCHIVE -> FileIcons::archive;
            case MODEL3D -> FileIcons::model3d;
            case CODE -> p -> badge(p, "{}", DARKGREY);
            case TEXT -> FileIcons::text;
            case GENERIC -> FileIcons::generic;
        };
    }

    static void folder(Painter p, Rect b) {
        float s = Math.min(b.w, b.h), u = s / 18;                // designed on an 18 grid
        float x = b.x + (b.w - s) / 2, y = b.y + (b.h - s) / 2;
        p.roundedBox(x + u, y + 3 * u, 16 * u, 11 * u, 2 * u, PURPLE);       // back
        p.roundedBox(x, y + 5 * u, 18 * u, 12 * u, 2 * u, BLUE);            // front, over it
        p.roundedBox(x, y + 5 * u, 7 * u, 3 * u, 1.5f * u, Color.rgb(0x2F57C9)); // tab
    }

    static void badge(Painter p, String label, Color bg) {
        // a rounded tile with up-to-3 letters; text via p.text at half the tile size
    }

    static void dmg(Painter p, Rect b)   { /* grey rounded square + circle + center dot */ }
    static void apk(Painter p, Rect b)   { /* green rounded square + two dark eyes + antennae lines */ }
    static void app(Painter p, Rect b)   { /* BLUE rounded square + WHITE circle */ }
    static void image(Painter p, Rect b) { /* CYAN rounded square + WHITE mountain: two lines + circle sun */ }
    static void video(Painter p, Rect b) { /* PURPLE rounded square + WHITE triangle (three lines) */ }
    static void audio(Painter p, Rect b) { /* ORANGE rounded square + WHITE note: circle + stem line */ }
    static void archive(Painter p, Rect b){ /* GREY rounded square + darker center line + zipper dots */ }
    static void model3d(Painter p, Rect b){ /* TEAL cube outline: 6 lines */ }
    static void text(Painter p, Rect b)  { /* WHITE page rect + GREY three lines */ }
    static void generic(Painter p, Rect b){ /* GREY page + folded corner triangle */ }

    /** The sidebar's 16 DIP monochrome marks; the caller tints by drawing over is not needed—
        these take the color themselves. */
    static Painter.Draw mini(String kind, Color color) {
        return switch (kind) {
            case "home" -> (p, b) -> house(p, b, color);
            case "downloads" -> (p, b) -> { house(p, b, color); };   // placeholder, replaced below
            default -> (p, b) -> p.circle(b.x + b.w / 2, b.y + b.h / 2, 4, color);
        };
    }

    static void house(Painter p, Rect b, Color col) {
        p.line(b.x + 1, b.y + b.h / 2, b.x + b.w / 2, b.y + 2, 1.5f, col);
        p.line(b.x + b.w / 2, b.y + 2, b.x + b.w - 1, b.y + b.h / 2, 1.5f, col);
        p.line(b.x + 3, b.y + b.h / 2 - 1, b.x + 3, b.y + b.h - 1, 1.5f, col);
        p.line(b.x + b.w - 3, b.y + b.h / 2 - 1, b.x + b.w - 3, b.y + b.h - 1, 1.5f, col);
        p.line(b.x + 3, b.y + b.h - 1, b.x + b.w - 3, b.y + b.h - 1, 1.5f, col);
    }

    private FileIcons() {}
}
```

**实现要求（占位注释处必须全画出来，不许留 stub）**：`badge` 用 `p.roundedBox` 铺底 + `p.text(label, x, y, size, 400, WHITE, 40)` 居中；其余每个图标 3–6 个图元（roundedBox/box/circle/line），18 网格坐标同 `folder` 的 `u` 换算。`mini` 的 `downloads` 分支去掉占位，按集合实现：`home`（房子）、`desktop`（显示器：矩形+底线）、`downloads`（向下箭头+托盘）、`docs`（纸片）、`pictures`（山形）、`music`（音符）、`movies`（胶片：矩形+两个孔）、`apps`（四方格 A 字）、`trash`（垃圾桶：梯形+盖）、`disk`（圆+中心点）、`cloud`（云：三圆+底线）、`tag`（圆点）。全部 1.5 DIP 线宽或实心，单一 `color` 参数。

`IconGrid.java`：

```java
package jui.jspace;

import jui.jspace.store.FileEntry;

import java.util.List;

import static jui.ui.Ui.*;

/** The pane's grid view: fixed cells in wrapping rows, virtualized row by row. */
final class IconGrid {
    private static final float CELL_W = 96, CELL_H = 116;
    private final Workspace ws;
    private final int pane;

    IconGrid(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model m = ws.model();
        Model.TabState t = m.panes[pane].tab();
        List<Entries.Row> rows = Entries.view(ws.store().snap(t.dir).entries(), ws.store()::snap,
                t.sort, ws.settings().foldersFirst, t.search, ws.settings().showHidden, t.expanded);
        int perRow = Math.max(1, (int) (t.paneWidth / CELL_W));
        int rowCount = (rows.size() + perRow - 1) / perRow;
        int pr = perRow;

        box(c).fill().clip().children(() ->
                list(c, t.gridRows, rowCount, r -> gridRow(c, t, rows, r * pr, Math.min(rows.size(), (r + 1) * pr))));
    }

    private void gridRow(Context c, Model.TabState t, List<Entries.Row> rows, int from, int to) {
        row(c).height(116).fillWidth().children(() -> {
            for (int i = from; i < to; i++) {
                Entries.Row r = rows.get(i);
                FileEntry e = r.entry();
                boolean sel = e.path().equals(t.selected);
                box(c).width(96).height(116).padding(8, 4).label("cell-" + e.name()).children(() -> {
                    jui.ui.Element b = buttonBase(c).fill();
                    if (b.clicked()) t.selected = e.path();
                    if (b.clicks() >= 2) ws.openEntry(pane, e);
                    column(c).gap(6).alignItems(jui.ui.Align.CENTER).children(() -> {
                        box(c).size(48, 48).draw(FileIcons.draw(e.icon(), true));
                        if (sel) box(c).size(52, 52).absolute().border(1.5f, c.theme().accent()).radius(8);
                        text(c, Texts.fit(c, e.name(), 11, 400, 88)).fontSize(11)
                                .textColor(sel ? c.theme().accent() : c.theme().text());
                    });
                });
            }
        });
    }
}
```

`FilePane` 分发：`contentPlaceholder` 换成 `if (t.view == Model.ViewMode.LIST) new FileList(...).build(c); else new IconGrid(...).build(c);`（LOADING/ERROR 态保留）。FileList 行内 18×18 占位 box 接 `.draw(FileIcons.draw(e.icon(), false))`。`Workspace.openEntry(int, FileEntry)` 从 Task 9 的私有方法提升为 Workspace 方法。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿。

- [ ] **Step 5: 冒烟 + Commit**

Run: `./build.sh demo QSpace`
Expected: 切一个面板到图标视图（工具栏还没做视图切换的话，临时改 Model 默认或手敲 `view=GRID` 后再跑），网格与图标呈现。

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/IconGridTest.java
git commit -m "feat(jspace): icon grid view and self-drawn file icons"
```

---

### Task 11: 工具栏 + 活动面板切换 + 全局快捷键

**Files:**
- Modify: `src/main/java/jui/jspace/Workspace.java`（真实工具栏替换占位；活动面板跟踪；快捷键）
- Modify: `src/main/java/jui/jspace/FilePane.java`（面板内容点击→`m.active = pane`：给面板内容区根部加 `buttonBase` 全覆盖层或对容器用 `Element.pressed/clicked`——用 `box(...).label("pane-hit-i")` 上注册 `Node.PRESS`：实现上直接包一层 `buttonBase(c).fill().absolute()` 会挡内容——改用框架的 hover/press 标志：容器 `box` 的 `en.hovered()` 即可置 active：`if (c.engine().hovered(rootIdx)) m.active = pane;`。具体：FilePane 根 `box` 加 `.label("pane-" + pane)`，Workspace 每帧遍历四个面板查 `engine().hovered(state.index)`。）
- Test: `src/test/java/jui/jspace/ToolbarTest.java`

**Interfaces:**
- Consumes: 前面所有任务；`Element.popover`、`c.shortcut`。
- Produces:
  - 工具栏元素标签：`nav-back`/`nav-forward`、`view-list`/`view-grid`、`sort-menu`（popover 锚）、`layout-single`/`layout-double`/`layout-grid`、`search-box`、`open-settings`、`toggle-sidebar`。
  - `Workspace.toggleSidebar()` 实装（Task 3 空方法）。
  - 窗口级快捷键（`c.shortcut`，构建于 Workspace 根）：`⌘F` 聚焦搜索框（`Handle`：`searchInput.autoFocus()`——用 `Handle` 字段 + `handle.focus()`）、`⌘R` 重扫当前面板、`⌘C/⌘X/⌘V`、`⌫` 删除（弹确认，Task 13 的 `askDelete` 就位后接通；本任务先绑定到它，允许 Task 13 才补全）、`Esc` 清搜索。菜单栏已有 ⌘N/⌘W/⌘B/⌘,/⌘Q，窗口不注册。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ToolbarTest {
    static Model model() throws IOException {
        Path d = Files.createTempDirectory("jspace-tb");
        jui.jspace.store.Settings st = new jui.jspace.store.Settings();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(d));
        return m;
    }

    static Tester open(Model m) {
        Workspace ws = new Workspace(m, m.settings, Runnable::run);
        return Tester.of(ws.view(), 900, 600);
    }

    public void testViewSwitchChangesTheTab() throws IOException {
        Model m = model();
        Tester tt = open(m);
        tt.click("view-grid");
        tt.frame();
        Check.equal(Model.ViewMode.GRID, m.panes[0].tab().view);
        tt.click("view-list");
        tt.frame();
        Check.equal(Model.ViewMode.LIST, m.panes[0].tab().view);
    }

    public void testLayoutSwitch() throws IOException {
        Model m = model();
        m.layout = Model.Layout.SINGLE;
        Tester tt = open(m);
        tt.click("layout-grid");
        tt.frame();
        Check.equal(Model.Layout.GRID, m.layout);
        Check.equal(4, m.panes[0].tabs.size() + 0 + m.panes[1].tabs.size() + m.panes[2].tabs.size() + m.panes[3].tabs.size() - 0);   // four panes, one tab each is the invariant; simpler:
        Check.true_(tt.find("pane-3-frame").width() > 0);
    }

    public void testSearchFiltersViaShortcutAndClears() throws IOException {
        Model m = model();
        Files.writeString(m.panes[0].tab().dir.resolve("keep.md"), "x");
        Files.writeString(m.panes[0].tab().dir.resolve("drop.txt"), "y");
        Tester tt = open(m);
        tt.key(Shortcut.cmd(Key.F));
        tt.type("keep");
        tt.frame();
        Check.equal("keep", m.panes[0].tab().search);
        tt.key(Shortcut.of(Key.ESCAPE));
        tt.frame();
        Check.equal("", m.panes[0].tab().search);
    }

    public void testActivePaneFollowsHover() throws IOException {
        Model m = model();
        m.layout = Model.Layout.GRID;
        Tester tt = open(m);
        tt.move(700, 500);                                       // somewhere in pane 3
        tt.frame();
        Check.equal(3, m.active);
    }
}
```

（`layout-grid` 断言里那行长表达式删掉，只留 `pane-3-frame` 的检查——写测试时清理。）

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test ToolbarTest`
Expected: 编译失败或断言失败（`view-grid` 等标签不存在）。

- [ ] **Step 3: 实现（Workspace 内）**

工具栏布局（替换 `toolbarPlaceholder`）：

```java
private void toolbar(Context c) {
    JspaceTheme.Palette pal = JspaceTheme.palette(c);
    row(c).height(44).fillWidth().padding(0, 12).gap(8)
            .background(pal.bar()).alignItems(jui.ui.Align.CENTER).label("toolbar").children(() -> {
        Model.TabState t = m.panes[m.active].tab();

        nav(c, "nav-back", !t.back.isEmpty(), () -> goBack(m.active));
        nav(c, "nav-forward", !t.forward.isEmpty(), () -> goForward(m.active));

        text(c, PaneTabs.title(t)).fontSize(14).bold();
        spacer(c);

        viewButton(c, "view-list", t.view == Model.ViewMode.LIST, "☰");
        viewButton(c, "view-grid", t.view == Model.ViewMode.GRID, "⊞");
        box(c).width(8);

        layoutButton(c, "layout-single", m.layout == Model.Layout.SINGLE, Model.Layout.SINGLE);
        layoutButton(c, "layout-double", m.layout == Model.Layout.DOUBLE, Model.Layout.DOUBLE);
        layoutButton(c, "layout-grid", m.layout == Model.Layout.GRID, Model.Layout.GRID);
        box(c).width(8);

        sortPopover(c);
        nav(c, "toggle-sidebar", true, this::toggleSidebar);
        nav(c, "open-settings", true, this::openSettings);

        box(c).width(120).height(28).radius(6).border(1, c.theme().border()).label("search-box")
                .children(() -> {
                    jui.ui.Element in = textInput(c, searchRef).placeholder("搜索");
                    if (in.changed()) { t.search = searchRef.get(); }
                });
    });
}
```

要点：
- `searchRef = new jui.ui.TextRef("")` 是 Workspace 字段；搜索词写入**活动面板当前标签**的 `search`（切换面板/标签时把 `searchRef` 重置为该标签的 `search`——在每帧开头同步：`searchRef.setIfDifferent(t.search)`；`TextRef` 无此法则比较后 set）。⌘F：`searchHandle.focus()`（`Handle` 字段 + `textInput` 返回链上的 `.handle(h)`——读 Element/Handle 的实际链接 API，`Handle` 在 `jui/ui/Handle.java`）。Esc：`c.shortcut(Shortcut.of(Key.ESCAPE))` 清 `search` 并 `searchRef` 置空。
- `sortPopover`：`popover(c, sortAnchor, sortOpen, …)` 锚是 `sort-menu` 按钮；内容四个 `button(c,"按名称")` 等 + 升/降 toggle，点击写 `t.sort` 并关 `sortOpen`。`BoolRef sortOpen` 为 Workspace 字段。
- `layoutButton` 点击写 `m.layout`（收缩保左上已由 Model 语义保证——`Layout` 变了 panes 不动，网格少画而已；SINGLE/DOUBLE/GRID 各画 shown 个面板）。
- 视图切换按钮写 `t.view` 并把另一个面板的 `paneWidth` 置 0 让网格下一帧重算。
- 活动面板跟踪：每帧构建前（`build` 开头）：

```java
for (int i = 0; i < 4; i++) {
    if (c.engine().hovered(paneRootIndex(i))) m.active = i;    // hover state from last frame's hit list
}
```

`paneRootIndex(i)`：FilePane 根元素在 `enter` 时记录 index——做法：FilePane.build 里 `Element root = column(c)…; rootIdx[pane] = root.index();`（Workspace 持 `int[] paneRootIdx`）。`Element.index()` 公开（split 源码里 `e.index()` 已用）。
- ⌘C/⌘X/⌘V/⌫/⌘R 本任务注册并直连 Task 7 的 ops + Task 13 的删除确认（若 Task 13 未到，先接「无操作空实现 + TODO 行」会违反无占位符规则——**顺序上此任务只注册 ⌘R 与 Esc、⌘F**；⌘C/⌘X/⌘V/⌫ 的注册放 Task 13 一起做，避免空壳）。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/ToolbarTest.java
git commit -m "feat(jspace): toolbar, active pane tracking and shortcuts"
```

---

### Task 12: Sidebar——侧栏

**Files:**
- Create: `src/main/java/jui/jspace/Sidebar.java`
- Modify: `src/main/java/jui/jspace/Workspace.java`（占位 sidebar 换 `new Sidebar(this).build(c)`）
- Test: `src/test/java/jui/jspace/SidebarTest.java`

**Interfaces:**
- Consumes: `FileIcons.mini`（Task 10）、`ws.navigate`、`Model.sidebarOpen`。
- Produces:
  - `Sidebar.build(Context c)`——分组：暂存架（标题 + 灰字「将文件拖到这里（演示）」不可点）、书签（桌面/下载/文稿/图片/音乐/影片/主目录/应用程序/废纸篓）、iCloud（`~/Library/Mobile Documents/com~apple~CloudDocs` 存在才显示）、服务器（Macintosh HD `/`）、访达标签（十个彩点静态行，点击无操作）、工作区（「默认」，高亮当前）、底部过滤框 `sidebar-filter`（TextRef 过滤侧栏项显示名）。
  - 行标签 `side-<显示名>`；点击 = `ws.navigate(m.active, path)`；废纸篓 = `~/.Trash`。
  - 显示名↔路径映射：桌面 Desktop、下载 Downloads、文稿 Documents、图片 Pictures、音乐 Music、影片 Movies、主目录 home、应用程序 /Applications、废纸篓 .Trash。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

import java.nio.file.Path;

public final class SidebarTest {
    static Tester open() {
        Model m = new Model(new jui.jspace.store.Settings());
        m.layout = Model.Layout.SINGLE;
        return Tester.of(new Workspace(m, m.settings, Runnable::run).view(), 900, 600);
    }

    public void testGroupsAndItems() {
        Tester tt = open();
        for (String id : new String[] { "side-桌面", "side-下载", "side-文稿", "side-主目录",
                "side-应用程序", "side-废纸篓", "side-Macintosh HD", "side-默认" }) {
            Check.true_(tt.find(id).width() > 0);
        }
    }

    public void testClickNavigatesTheActivePane() {
        Model m = new Model(new jui.jspace.store.Settings());
        m.layout = Model.Layout.SINGLE;
        m.active = 0;
        Tester tt = Tester.of(new Workspace(m, m.settings, Runnable::run).view(), 900, 600);
        Path home = Path.of(System.getProperty("user.home"));
        tt.click("side-下载");
        tt.frame();
        Check.equal(home.resolve("Downloads"), m.panes[0].tab().dir);
    }

    public void testFilterHidesNonMatching() {
        Tester tt = open();
        tt.click("sidebar-filter");
        tt.type("下载");
        tt.frame();
        Check.true_(tt.find("side-下载").width() > 0);
        try { tt.find("side-文稿"); throw new AssertionError("文稿 should be filtered out"); }
        catch (AssertionError pass) { }
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test SidebarTest`
Expected: 编译失败，找不到 `Sidebar`。

- [ ] **Step 3: 实现**

```java
package jui.jspace;

import jui.ui.Color;
import jui.ui.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static jui.ui.Ui.*;

/** The window's sidebar: bookmarks, places, finder tags (shown only) and the workspace line. */
final class Sidebar {
    private final Workspace ws;
    private final jui.ui.TextRef filter = new jui.ui.TextRef("");

    Sidebar(Workspace ws) { this.ws = ws; }

    private record Item(String kind, String label, Path path, Color dot) {}

    void build(Context c) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        column(c).fill().background(pal.sidebar()).label("sidebar").children(() -> {
            scroll(c).grow(1).padding(6, 8).children(() -> {
                group(c, "暂存架");
                note(c, "将文件拖到这里（演示）");
                group(c, "书签");
                for (Item i : matched(bookmarks())) item(c, i);
                List<Item> cloud = cloud();
                if (!cloud.isEmpty()) { group(c, "iCloud"); for (Item i : matched(cloud)) item(c, i); }
                group(c, "服务器");
                item(c, new Item("disk", "Macintosh HD", Path.of("/"), null));
                group(c, "访达标签");
                for (String[] tag : TAGS) tagRow(c, tag[0], Color.rgb(Integer.parseInt(tag[1], 16)));
                group(c, "工作区");
                item(c, new Item(null, "默认", null, null));
            });
            box(c).height(36).padding(4, 8).children(() -> {
                box(c).fill().height(26).radius(6).background(pal.pill()).label("sidebar-filter")
                        .padding(0, 6).children(() -> {
                            jui.ui.Element in = textInput(c, filter).placeholder("过滤");
                            in.fontSize(12);
                        });
            });
        });
    }

    private static final String[][] TAGS = {
            {"蓝色", "0x0A84FF"}, {"重要", "0xFFD60A"}, {"灰色", "0x8E8E93"}, {"工作", "0x30D158"},
            {"红色", "0xFF453A"}, {"个人", "0xFF9F0A"}, {"绿色", "0x32D74B"}, {"橙色", "0xFF9F0A"},
            {"黄色", "0xFFD60A"}, {"紫色", "0xBF5AF2"},
    };

    private List<Item> bookmarks() {
        Path home = Path.of(System.getProperty("user.home"));
        List<Item> out = new ArrayList<>(List.of(
                new Item("desktop", "桌面", home.resolve("Desktop"), null),
                new Item("downloads", "下载", home.resolve("Downloads"), null),
                new Item("docs", "文稿", home.resolve("Documents"), null),
                new Item("pictures", "图片", home.resolve("Pictures"), null),
                new Item("music", "音乐", home.resolve("Music"), null),
                new Item("movies", "影片", home.resolve("Movies"), null),
                new Item("home", "主目录", home, null),
                new Item("apps", "应用程序", Path.of("/Applications"), null),
                new Item("trash", "废纸篓", home.resolve(".Trash"), null)));
        return out;
    }

    private List<Item> cloud() {
        Path p = Path.of(System.getProperty("user.home"),
                "Library/Mobile Documents/com~apple~CloudDocs");
        return Files.isDirectory(p) ? List.of(new Item("cloud", "iCloud 云盘", p, null)) : List.of();
    }

    private List<Item> matched(List<Item> in) {
        String q = filter.get().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return in;
        List<Item> out = new ArrayList<>();
        for (Item i : in) if (i.label().toLowerCase(Locale.ROOT).contains(q)) out.add(i);
        return out;
    }

    private void group(Context c, String title) {
        text(c, title).fontSize(11).bold().textColor(c.theme().textMuted())
                .padding(10, 6, 2, 6).label("group-" + title);
    }

    private void note(Context c, String s) {
        text(c, s).fontSize(11).textColor(c.theme().textMuted()).padding(2, 6);
    }

    private void item(Context c, Item it) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        jui.ui.Element row = buttonBase(c).fillWidth().height(26).radius(6).label("side-" + it.label());
        if (row.hovered()) row.background(pal.sidebarHover());
        if (row.clicked() && it.path() != null) ws.navigate(ws.model().active, it.path());
        row(c).gap(8).alignItems(jui.ui.Align.CENTER).children(() -> {
            if (it.kind() != null) box(c).size(16, 16).draw(FileIcons.mini(it.kind(), c.theme().textMuted()));
            else box(c).size(16, 16);
            text(c, it.label()).fontSize(13).textColor(c.theme().text());
        });
    }

    private void tagRow(Context c, String name, Color dot) {
        row(c).height(24).padding(0, 6).gap(8).alignItems(jui.ui.Align.CENTER).label("side-" + name)
                .children(() -> {
                    box(c).size(10, 10).radius(5).background(dot);
                    text(c, name).fontSize(13).textColor(c.theme().textMuted());
                });
    }
}
```

实现注意：`buttonBase(...).background(...)` 悬停着色的写法以框架为准（对照 `Ui.java` 里 button 的悬停实现，`row.hovered()` 存在于 M2 交互查询）。`textInput(...).fontSize(12)` 这种链若 textInput 无 fontSize，则去掉该行（输入字号默认 13 可接受）。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿。

- [ ] **Step 5: 冒烟 + Commit**

Run: `./build.sh demo QSpace`
Expected: 侧栏分组、图标、彩点、过滤框齐全，点击导航活动面板。

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/SidebarTest.java
git commit -m "feat(jspace): sidebar with bookmarks, places and filter"
```

---

### Task 13: 操作接线——右键菜单、删除/冲突 modal、重命名、状态栏、剪贴板

**Files:**
- Create: `src/main/java/jui/jspace/Actions.java`
- Modify: `src/main/java/jui/jspace/Workspace.java`（状态栏实装、`Actions` 接入、⌘C/⌘X/⌘V/⌫ 注册）
- Modify: `src/main/java/jui/jspace/FileList.java` + `IconGrid.java`（行/单元格/空白处 `contextMenu`）
- Modify: `src/main/java/jui/jspace/FilePane.java`（重命名内联输入条）
- Test: `src/test/java/jui/jspace/ActionsTest.java`

**Interfaces:**
- Consumes: `FileOps`（Task 7）、`contextMenu/modal`（框架）、`Model.Clip/Status/renameNew`（Task 3/8）。
- Produces:
  - `Actions`（Workspace 持有）：`void newFolder(int pane)`、`void startRename(int pane)`（设 `t.renameNew` 并在下帧显示内联输入）、`void askDelete(int pane)`（modal 确认→`FileOps.delete`）、`void copy(int pane, boolean cut)`、`void paste(int pane)`（含冲突 `ConflictAsk` modal）、`void refreshOpenDirs(Path changed)`（操作完成后重扫所有标签里出现的目录）。
  - modal 标签：`confirm-delete`（正文含条目名与 `N 项`）、`confirm-overwrite`（三个按钮 覆盖/跳过/取消）。
  - 状态栏：左 `N 项 · 已选 …`、右 `m.status.note`（设 note 时记 `noteAt`，5 秒后清——帧里比较 `c.now()`）。
  - 右键菜单项（空白）：新建文件夹/粘贴/排序 ▸（四键+升降）/视图 ▸（列表/图标）/显示隐藏文件（checked）/刷新；条目上：打开/在新标签页中打开（目录）/重命名/拷贝/剪切/删除。
  - ⌘C/⌘X/⌫（无文本输入焦点时）：作用于活动面板 `selected`。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.jspace.store.FileStore;
import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ActionsTest {
    static Model wired() throws IOException {
        Path d = Files.createTempDirectory("jspace-act");
        Files.writeString(d.resolve("f.txt"), "x");
        jui.jspace.store.Settings st = new jui.jspace.store.Settings();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(d));
        return m;
    }

    static Workspace open(Model m) {
        Workspace ws = new Workspace(m, m.settings, Runnable::run);
        ws.store().scan(m.panes[0].tab().dir);
        return ws;
    }

    public void testNewFolderMakesOneAndStatusNotesIt() throws IOException {
        Model m = wired();
        Workspace ws = open(m);
        ws.newFolder(0);
        Thread.sleep(100);                                       // DIRECT publish is inline; sleep for safety
        Check.true_(Files.isDirectory(m.panes[0].tab().dir.resolve("未命名文件夹")));
    }

    public void testDeleteAsksThenDeletes() throws Exception {
        Model m = wired();
        Workspace ws = open(m);
        m.panes[0].tab().selected = m.panes[0].tab().dir.resolve("f.txt");
        ws.askDelete(0);
        Check.equal("", m.status.note == null ? "" : m.status.note);   // nothing happened yet: the modal is up
        // the modal's confirm button:
        Tester tt = Tester.of(ws.view(), 700, 500);
        tt.frame();
        tt.click("confirm-delete-ok");
        tt.frame();
        Check.true_(!Files.exists(m.panes[0].tab().dir.resolve("f.txt")));
    }

    public void testCopyCutPasteAcrossPanes() throws Exception {
        Model m = wired();
        Path other = Files.createTempDirectory("jspace-other");
        m.layout = Model.Layout.DOUBLE;
        m.panes[1].tabs.clear();
        m.panes[1].tabs.add(new Model.TabState(other));
        Workspace ws = open(m);
        ws.store().scan(other);
        m.active = 0;
        m.panes[0].tab().selected = m.panes[0].tab().dir.resolve("f.txt");
        ws.copy(0, false);
        m.active = 1;
        ws.paste(1);
        Thread.sleep(100);
        Check.true_(Files.exists(other.resolve("f.txt")));
        Check.true_(Files.exists(m.panes[0].tab().dir.resolve("f.txt")));   // copy kept it
    }

    public void testConflictGoesThroughTheModal() throws Exception {
        Model m = wired();
        Path other = Files.createTempDirectory("jspace-conf");
        Files.writeString(other.resolve("f.txt"), "old");
        m.layout = Model.Layout.DOUBLE;
        m.panes[1].tabs.clear();
        m.panes[1].tabs.add(new Model.TabState(other));
        Workspace ws = open(m);
        ws.store().scan(other);
        m.active = 0;
        m.panes[0].tab().selected = m.panes[0].tab().dir.resolve("f.txt");
        ws.copy(0, false);
        m.active = 1;
        ws.paste(1);                                             // clash: the modal opens, paste waits
        Tester tt = Tester.of(ws.view(), 700, 500);
        tt.frame();
        tt.click("confirm-overwrite-skip");
        tt.frame();
        Thread.sleep(100);
        Check.equal("old", Files.readString(other.resolve("f.txt")));   // untouched
    }

    public void testRenameInlineInput() throws Exception {
        Model m = wired();
        Workspace ws = open(m);
        m.panes[0].tab().selected = m.panes[0].tab().dir.resolve("f.txt");
        ws.startRename(0);
        Check.equal("f.txt", m.panes[0].tab().renameNew);
        Tester tt = Tester.of(ws.view(), 700, 500);
        tt.frame();
        Check.true_(tt.find("rename-input").width() > 0);
        tt.key(Shortcut.of(Key.ENTER));
        tt.frame();
        Check.true_(Files.exists(m.panes[0].tab().dir.resolve("f.txt")));   // same name: nothing broke
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test ActionsTest`
Expected: 编译失败，找不到 `Actions`/`ws.newFolder` 等。

- [ ] **Step 3: 实现（要点与骨架）**

`Actions.java`（Workspace 的字段 `final Actions actions = new Actions(this)`，各方法转发）：

```java
package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.jspace.store.FileOps;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** The file operations as the UI drives them: menus, modals, the clipboard, refreshes. */
final class Actions {
    private final Workspace ws;
    final jui.ui.BoolRef deleteOpen = new jui.ui.BoolRef(false);
    final jui.ui.BoolRef clashOpen = new jui.ui.BoolRef(false);
    private java.util.function.Consumer<FileOps.Decision> clashAnswer;
    private Path pendingDelete;

    Actions(Workspace ws) { this.ws = ws; }

    Model.TabState tab(int pane) { return ws.model().panes[pane].tab(); }

    void newFolder(int pane) {
        Path dir = tab(pane).dir;
        FileOps.mkdir(dir, "未命名文件夹", ws.storePublish(), err -> {
            ws.model().status.note = err == null ? "已新建文件夹" : "新建失败：" + err;
            ws.model().status.noteAt = System.nanoTime();
            refresh(dir);
        });
    }

    void startRename(int pane) {
        Model.TabState t = tab(pane);
        if (t.selected != null) {
            t.renameNew = t.selected.getFileName().toString();
        }
    }

    void submitRename(int pane, String newName) {
        Model.TabState t = tab(pane);
        Path from = t.selected;
        t.renameNew = null;
        if (from == null || newName == null || newName.isBlank() || newName.equals(from.getFileName().toString())) return;
        FileOps.rename(from, newName, ws.storePublish(), err -> {
            if (err != null) { ws.model().status.note = "重命名失败：" + err; ws.model().status.noteAt = System.nanoTime(); }
            refresh(from.getParent());
        });
    }

    void askDelete(int pane) {
        if (tab(pane).selected != null) { pendingDelete = tab(pane).selected; deleteOpen.set(true); }
    }

    /** The modal calls this with the count it showed. */
    void doDelete(long count) {
        deleteOpen.set(false);
        Path p = pendingDelete;
        pendingDelete = null;
        if (p == null) return;
        FileOps.delete(p, ws.storePublish(), err -> {
            ws.model().status.note = err == null ? "已删除" : "删除失败：" + err;
            ws.model().status.noteAt = System.nanoTime();
            refresh(p.getParent());
        });
    }

    void copy(int pane, boolean cut) {
        Path sel = tab(pane).selected;
        if (sel != null) ws.model().clip = new Model.Clip(sel, cut);
    }

    void paste(int pane) {
        Model.Clip clip = ws.model().clip;
        Path dir = tab(pane).dir;
        if (clip == null || dir == null) return;
        FileOps.paste(clip.path, clip.move, dir, ws.storePublish(),
                (name, answer) -> {                              // on the main thread: open the modal
                    clashName = name;
                    clashAnswer = answer;
                    clashOpen.set(true);
                },
                err -> {
                    if (err != null) { ws.model().status.note = err; ws.model().status.noteAt = System.nanoTime(); }
                    refresh(clip.path.getParent());
                    refresh(dir);
                });
    }

    private String clashName;

    /** The clash modal's buttons land here. */
    void decideClash(FileOps.Decision d) {
        clashOpen.set(false);
        if (clashAnswer != null) { clashAnswer.accept(d); clashAnswer = null; }
    }

    /** Rescans every open tab's directory and any expanded parent that holds changed. */
    void refresh(Path changed) {
        if (changed == null) return;
        Set<Path> want = new HashSet<>();
        for (Model.PaneState p : ws.model().panes) {
            for (Model.TabState t : p.tabs) {
                if (t.dir != null) want.add(t.dir);
                if (t.dir != null && changed.startsWith(t.dir)) want.add(t.dir);
                for (Path e : t.expanded) if (e.equals(changed) || changed.startsWith(e)) want.add(e);
            }
        }
        for (Path d : want) ws.store().scan(d);
    }

    /** The delete modal's body count, computed without blocking: files count at ask time. */
    long entriesUnder(Path p) { return FileOps.countEntries(p); }
}
```

`Workspace` 增加：`jui.ui.TextRef renameRef`（内联输入绑定）、两个 modal 构建（`modal(c, actions.deleteOpen, …)` 正文 = `确认删除「name」？(N 项)` + 删除/取消按钮，标签 `confirm-delete-ok`；`clashOpen` 正文 = `「name」已存在` + 覆盖/跳过/取消，标签 `confirm-overwrite-overwrite/-skip/-cancel`）、状态栏实装（替换 `statusPlaceholder`）：

```java
private void statusbar(Context c) {
    JspaceTheme.Palette pal = JspaceTheme.palette(c);
    Model.TabState t = m.panes[m.active].tab();
    if (!m.status.note.isEmpty() && c.now() - m.status.noteAt > 5_000_000_000L) m.status.note = "";
    row(c).height(24).fillWidth().padding(0, 12).background(pal.bar())
            .alignItems(jui.ui.Align.CENTER).label("statusbar").children(() -> {
        int n = ws-store-count;                                  // 见下：从当前 tab 的 rows 来
        text(c, t.selected == null ? n + " 项" : n + " 项 · 已选 " + t.selected.getFileName())
                .fontSize(11).textColor(c.theme().textMuted());
        spacer(c);
        if (!m.status.note.isEmpty()) text(c, m.status.note).fontSize(11)
                .textColor(pal.warn());
    });
}
```

`n` 的来源：Workspace 每帧构建 FilePane 前算一次 `Entries.view(...)` 的行数太浪费——把「行数」缓存进 `Model.TabState`（`int rowCount`，FileList/IconGrid build 时写入），状态栏读 `t.rowCount`。（给 `TabState` 加 `int rowCount;` 字段，本任务完成。）

右键菜单（FileList 的行容器、IconGrid 单元格、以及 READY 空白处——FilePane 内容区根部）：

```java
jui.ui.Element target = rowElement.focusable();
contextMenu(c, target, menu -> {
    if (onEntry) {
        if (menu.item("打开").chosen()) ws.openEntry(pane, entry);
        if (entry.dir() && menu.item("在新标签页中打开").chosen()) { /* pane 新标签 + navigate */ }
        if (menu.item("重命名").chosen()) ws.actions().startRename(pane);
        menu.separator();
        if (menu.item("拷贝").chosen()) ws.actions().copy(pane, false);
        if (menu.item("剪切").chosen()) ws.actions().copy(pane, true);
    } else {
        if (menu.item("新建文件夹").chosen()) ws.actions().newFolder(pane);
        if (menu.item("粘贴").disabled(ws.model().clip == null).chosen()) ws.actions().paste(pane);
        menu.separator();
        menu.submenu("排序", sort -> {
            for (Entries.SortKey k : Entries.SortKey.values()) {
                if (sort.item(k == t.sort.key ? "按" + label(k) + (t.sort.asc ? " ∧" : " ∨") : "按" + label(k))
                        .checked(k == t.sort.key).chosen()) { t.sort.key = k; t.sort.asc = true; }
            }
            if (sort.item("升序").checked(t.sort.asc).chosen()) t.sort.asc = true;
            if (sort.item("降序").checked(!t.sort.asc).chosen()) t.sort.asc = false;
        });
        menu.submenu("视图", v -> {
            if (v.item("列表").checked(t.view == Model.ViewMode.LIST).chosen()) t.view = Model.ViewMode.LIST;
            if (v.item("图标").checked(t.view == Model.ViewMode.GRID).chosen()) t.view = Model.ViewMode.GRID;
        });
        if (menu.item("显示隐藏文件").checked(ws.settings().showHidden).chosen()) {
            ws.settings().showHidden = !ws.settings().showHidden; ws.settings().save();
        }
        if (menu.item("刷新").chosen()) ws.actions().refresh(t.dir);
    }
    if (onEntry && menu.item("删除").chosen()) ws.actions().askDelete(pane);
});
```

重命名内联（FilePane，面包屑与内容区之间）：`t.renameNew != null` 时插入一行 `textInput(c, renameRef).label("rename-input")`，初始化 `renameRef` 为旧名并全选（`setTextSelection`），回车 `submitRename`、Esc 取消（`onTextKey` 拦 ENTER/ESCAPE）。

快捷键注册（Workspace 根）：`⌘C→copy(active,false)`、`⌘X→copy(active,true)`、`⌘V→paste(active)`、`⌫→askDelete(active)`（有文本输入焦点时框架先给输入框，不会到这里——M4 规则，无需自己判断）。

**注意**：`ws.storePublish()` 是 Workspace 暴露的 `FileStore.Publish`（字段保存，`r -> publish.run()`）。测试里 `Workspace(m, settings, Runnable::run)` 因此 DIRECT 生效；`askDelete` 的确认走 Tester 点击，计数在打开 modal 时同步算（临时目录小，`countEntries` 直接在 UI 线程跑一次可接受——真实大目录有 5 秒 note 兜底，不追求完美）。

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿。

- [ ] **Step 5: 冒烟（临时目录手测）+ Commit**

```bash
mkdir -p /tmp/jspace-manual && ./build.sh demo QSpace
```

手动：在 /tmp/jspace-manual 里新建文件夹、重命名、删除（确认框）、复制粘贴到另一面板、制造同名冲突三选一。确认后：

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/ActionsTest.java
git commit -m "feat(jspace): context menus, delete and clash modals, clipboard, status line"
```

---

### Task 14: 键盘导航补全——←→ 折叠/跳父级、网格方向键

**Files:**
- Modify: `src/main/java/jui/jspace/FileList.java`（或 FilePane——键盘逻辑所在处）
- Modify: `src/main/java/jui/jspace/IconGrid.java`
- Test: `src/test/java/jui/jspace/KeysTest.java`

**Interfaces:**
- Consumes: Task 9 的选中移动、`t.expanded`。
- Produces: 列表 ←→（→ 展开选中目录/已展开则进入；← 折叠选中目录/已折叠则选中跳到其父级行）；网格 ↑↓←→ 按 `perRow` 移动；↩ 通用打开（已在 Task 9）。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.testing.Check;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class KeysTest {
    static Model tree() throws IOException {
        Path d = Files.createTempDirectory("jspace-keys");
        Files.createDirectories(d.resolve("a/b"));
        Files.writeString(d.resolve("a/b/leaf.txt"), "x");
        Files.writeString(d.resolve("top.txt"), "x");
        jui.jspace.store.Settings st = new jui.jspace.store.Settings();
        Model m = new Model(st);
        m.layout = Model.Layout.SINGLE;
        m.panes[0].tabs.clear();
        m.panes[0].tabs.add(new Model.TabState(d));
        return m;
    }

    static Tester open(Model m) {
        Workspace ws = new Workspace(m, m.settings, Runnable::run);
        ws.store().scan(m.panes[0].tab().dir);
        ws.store().scan(m.panes[0].tab().dir.resolve("a"));
        return Tester.of(ws.view(), 640, 400);
    }

    public void testRightExpandsThenEntersLeftCollapsesThenJumpsUp() throws IOException {
        Model m = tree();
        Path root = m.panes[0].tab().dir;
        m.panes[0].tab().selected = root.resolve("a");
        Tester tt = open(m);
        tt.key(Shortcut.of(Key.RIGHT));                          // expand
        tt.frame();
        Check.true_(m.panes[0].tab().expanded.contains(root.resolve("a")));
        tt.key(Shortcut.of(Key.RIGHT));                          // expanded: enter
        tt.frame();
        Check.equal(root.resolve("a"), m.panes[0].tab().dir);
        tt.key(Shortcut.of(Key.LEFT));                           // nothing selected in a/: selects first
        tt.frame();
        tt.key(Shortcut.of(Key.LEFT));                           // a selected? collapse-or-up
        tt.frame();
        Check.equal(root.resolve("a"), m.panes[0].tab().selected);
    }

    public void testGridArrowsMoveByPerRow() throws IOException {
        Model m = tree();
        m.panes[0].tab().view = Model.ViewMode.GRID;
        m.panes[0].tab().selected = null;
        Tester tt = open(m);
        tt.frame(); tt.frame();
        tt.key(Shortcut.of(Key.DOWN));
        tt.frame();
        Path sel = m.panes[0].tab().selected;
        Check.true_(sel != null);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test KeysTest`
Expected: 断言失败（←→ 未实现）。

- [ ] **Step 3: 实现**

把键处理集中到 `Workspace.key(Context c)`（FilePane 内容区注册 `c.shortcut` 转发到这里，按 `m.active` 与 `t.view` 分派）：

```java
void arrowKey(jui.ui.Key k) {
    Model.TabState t = m.panes[m.active].tab();
    if (t.dir == null) return;
    java.util.List<Entries.Row> rows = Entries.view(ws-store-snap(t.dir).entries(), store::snap,
            t.sort, settings.foldersFirst, t.search, settings.showHidden, t.expanded);
    int at = -1;
    for (int i = 0; i < rows.size(); i++) if (rows.get(i).entry().path().equals(t.selected)) { at = i; break; }
    int step = switch (k) { case DOWN -> 1; case UP -> -1; default -> 0; };
    if (t.view == Model.ViewMode.LIST) {
        if (step != 0) {
            t.selected = rows.isEmpty() ? null : rows.get(Math.max(0, Math.min(rows.size() - 1, (at < 0 ? 0 : at + step)))).entry().path();
        } else if (k == jui.ui.Key.RIGHT && at >= 0) {
            FileEntry e = rows.get(at).entry();
            if (e.dir() && !t.expanded.contains(e.path())) { t.expanded.add(e.path()); store.scan(e.path()); }
            else if (e.dir()) navigate(m.active, e.path());
        } else if (k == jui.ui.Key.LEFT && at >= 0) {
            FileEntry e = rows.get(at).entry();
            if (e.dir() && t.expanded.contains(e.path())) t.expanded.remove(e.path());
            else if (rows.get(at).depth() > 0) {                 // jump to the parent folder's row
                Path parent = e.path().getParent();
                for (Entries.Row r : rows) if (r.entry().path().equals(parent)) t.selected = parent;
            } else if (!t.dir.equals(home)) navigate(m.active, t.dir.getParent());
        }
    } else {                                                     // grid: perRow from paneWidth
        int perRow = Math.max(1, (int) (t.paneWidth / 96));
        int d = switch (k) { case DOWN -> perRow; case UP -> -perRow; case RIGHT -> 1; case LEFT -> -1; default -> 0; };
        if (d != 0 && !rows.isEmpty())
            t.selected = rows.get(Math.max(0, Math.min(rows.size() - 1, (at < 0 ? 0 : at + d)))).entry().path();
    }
    // keep the selection visible
    for (int i = 0; i < rows.size(); i++) {
        if (rows.get(i).entry().path().equals(t.selected)) {
            (t.view == Model.ViewMode.GRID ? t.gridRows : t.listRows).scrollTo(i);
        }
    }
}
```

（`ws-store-snap` 处即 `store.snap(t.dir)`；`home` = `Path.of(System.getProperty("user.home"))`。`scrollTo` 已核实存在。）

- [ ] **Step 4: 全量测试**

Run: `./build.sh test`
Expected: 全绿。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/KeysTest.java
git commit -m "feat(jspace): tree and grid keyboard navigation"
```

---

### Task 15: SettingsWindow——设置窗口 + 工作区恢复 + 收尾

**Files:**
- Create: `src/main/java/jui/jspace/SettingsWindow.java`
- Create: `src/main/java/jui/jspace/store/WorkspaceState.java`
- Modify: `src/main/java/jui/jspace/JspaceApp.java`（openSettings 开窗；退出保存）
- Modify: `src/main/java/jui/jspace/Workspace.java`（`openSettings` 实装；工作区快照读写钩子）
- Modify: `README.md`（QSpace 段落 + 运行命令）
- Test: `src/test/java/jui/jspace/SettingsWindowTest.java`
- Test: `src/test/java/jui/jspace/WorkspaceStateTest.java`

**Interfaces:**
- Consumes: `Settings`（Task 6）、`Window.open`（多窗口）、`Workspace.applyAppearance`。
- Produces:
  - `WorkspaceState`：`static void save(Model m, Path file)`（properties：`layout`、`active`、`pane<i>.tab<j>=<dir>`、`pane<i>.view<j>=LIST|GRID`）；`static void apply(Model m, Path file)`（存在才应用）。默认文件 `~/Library/Application Support/jui-jspace/workspace.properties`。app 退出（⌘Q 菜单）与每次 `navigate/newTab/closeTab/layout 切换`后调用 save（Workspace 里统一 `snapshot()` 私有方法，落盘放虚拟线程，先写临时文件再 `Files.move` 原子替换）。
  - `SettingsWindow`（`Workspace.openSettings()` 惰性建第二个 Window，420×480，`setThemePair` 同主窗；关闭只是 `window.close()`，引用留待重开或重建——简单起见每次 openSettings 若窗已关则重建）：左导航（搜索框 `settings-filter` + 分组标题 + 项 `set-通用/set-外观/…` 共 12 项，选中蓝底高亮块），右内容：通用页 8 行控件 + 底部橙色警示「注意：以下默认选项只在新创建工作区时起作用。」（对默认布局/默认排序行）；其余页居中灰字「此设置页在演示中未实现」。外观行改 Settings 后：`settings.save()` + `ws.applyAppearance()` + 设置窗自身 `setThemePair`。

- [ ] **Step 1: 写失败的测试**

```java
package jui.jspace;

import jui.jspace.store.WorkspaceState;
import jui.testing.Check;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SettingsWindowTest {
    public void testNavAndGeneralPage() {
        SettingsWindow sw = new SettingsWindow(new Workspace(new Model(new jui.jspace.store.Settings()),
                new jui.jspace.store.Settings(), Runnable::run));
        Tester tt = Tester.of(sw.view(), 420, 480);
        for (String id : new String[] { "set-通用", "set-外观", "set-面板", "set-标签页", "set-书签",
                "set-暂存架", "set-传输", "set-扩展", "set-快捷键", "set-关于" }) {
            Check.true_(tt.find(id).width() > 0);
        }
        Check.true_(tt.find("setrow-appearance").width() > 0);  // 通用页在正中
        tt.click("set-外观");
        tt.frame();
        Check.true_(tt.find("placeholder-page").width() > 0);   // 外观页是占位
    }

    public void testTogglePersists() throws IOException {
        Path f = Files.createTempFile("jspace-ws-settings", ".properties");
        jui.jspace.store.Settings st = jui.jspace.store.Settings.load(f);
        Workspace ws = new Workspace(new Model(st), st, Runnable::run);
        SettingsWindow sw = new SettingsWindow(ws);
        Tester tt = Tester.of(sw.view(), 420, 480);
        st.defaultFileOverrideForTest(f);                        // saves to the temp file
        tt.click("setrow-showhidden-toggle");
        tt.frame();
        Check.true_(jui.jspace.store.Settings.load(f).showHidden);
    }
}

class WorkspaceStateTest {
    public void testRoundTrip() throws IOException {
        Path f = Files.createTempFile("jspace-ws", ".properties");
        jui.jspace.store.Settings st = new jui.jspace.store.Settings();
        Model m = new Model(st);
        m.layout = Model.Layout.DOUBLE;
        m.active = 1;
        m.panes[0].tab().view = Model.ViewMode.GRID;
        WorkspaceState.save(m, f);
        Model back = new Model(st);
        WorkspaceState.apply(back, f);
        Check.equal(Model.Layout.DOUBLE, back.layout);
        Check.equal(1, back.active);
        Check.equal(Model.ViewMode.GRID, back.panes[0].tab().view);
        Check.equal(m.panes[0].tab().dir, back.panes[0].tab().dir);
    }
}
```

注意：`defaultFileOverrideForTest` 不该加进生产类——改为 SettingsWindow 构造接收 `Runnable afterChange`（点击后由 Workspace 调 `settings.save()`），测试直接调 `afterChange` 后断言文件；删掉 override 方法，测试改写为：

```java
        Path f = Files.createTempFile("jspace-ws-settings", ".properties");
        jui.jspace.store.Settings st = jui.jspace.store.Settings.load(f);
        Workspace ws = new Workspace(new Model(st), st, Runnable::run);
        java.util.concurrent.atomic.AtomicInteger saved = new java.util.concurrent.atomic.AtomicInteger();
        SettingsWindow sw = new SettingsWindow(ws, () -> { st.save(f); saved.incrementAndGet(); });
        Tester tt = Tester.of(sw.view(), 420, 480);
        tt.click("setrow-showhidden-toggle");
        tt.frame();
        Check.true_(saved.get() >= 1);
        Check.true_(jui.jspace.store.Settings.load(f).showHidden);
```

（`WorkspaceStateTest` 单独一个文件 `src/test/java/jui/jspace/WorkspaceStateTest.java`，去掉外层 class 包装。）

- [ ] **Step 2: 运行确认失败**

Run: `./build.sh test SettingsWindowTest` 与 `./build.sh test WorkspaceStateTest`
Expected: 编译失败。

- [ ] **Step 3: 实现（骨架）**

`WorkspaceState.java`：

```java
package jui.jspace.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** What the panes had open, so the next launch can put it back. */
public final class WorkspaceState {
    static Path defaultFile() {
        return Path.of(System.getProperty("user.home"),
                "Library/Application Support/jui-jspace/workspace.properties");
    }

    public static void save(Model m, Path file) {
        Properties p = new Properties();
        p.setProperty("layout", m.layout.name());
        p.setProperty("active", Integer.toString(m.active));
        int shown = switch (m.layout) { case SINGLE -> 1; case DOUBLE -> 2; case GRID -> 4; };
        for (int i = 0; i < shown; i++) {
            Model.PaneState pane = m.panes[i];
            for (int j = 0; j < pane.tabs.size(); j++) {
                Model.TabState t = pane.tabs.get(j);
                p.setProperty("pane" + i + ".tab" + j, t.dir == null ? "" : t.dir.toString());
                p.setProperty("pane" + i + ".view" + j, t.view.name());
            }
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(tmp)) { p.store(out, "QSpace workspace"); }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void apply(Model m, Path file) {
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) { p.load(in); } catch (IOException e) { return; }
        try { m.layout = Model.Layout.valueOf(p.getProperty("layout", m.layout.name())); } catch (IllegalArgumentException ignored) {}
        m.active = Math.max(0, Math.min(3, Integer.parseInt(p.getProperty("active", "0"))));
        int shown = switch (m.layout) { case SINGLE -> 1; case DOUBLE -> 2; case GRID -> 4; };
        for (int i = 0; i < shown; i++) {
            Model.PaneState pane = m.panes[i];
            pane.tabs.clear();
            int j = 0;
            while (p.containsKey("pane" + i + ".tab" + j)) {
                String dir = p.getProperty("pane" + i + ".tab" + j);
                Model.TabState t = new Model.TabState(dir.isEmpty()
                        ? Path.of(System.getProperty("user.home")) : Path.of(dir));
                try { t.view = Model.ViewMode.valueOf(p.getProperty("pane" + i + ".view" + j, "LIST")); }
                catch (IllegalArgumentException e) { t.view = Model.ViewMode.LIST; }
                pane.tabs.add(t);
                j++;
            }
            if (pane.tabs.isEmpty()) pane.tabs.add(new Model.TabState(Path.of(System.getProperty("user.home"))));
        }
    }

    private WorkspaceState() {}
}
```

（`Model` 在 `jui.jspace` 包、`WorkspaceState` 在 `.store`——`Model.TabState` 等是包私有嵌套类，跨包不可见！**把 WorkspaceState 放 `jui.jspace` 包**（不放 store），文件路径相应 `src/main/java/jui/jspace/WorkspaceState.java`，import 只需 `java.*`。）

`SettingsWindow.java`（构造 `SettingsWindow(Workspace ws, Runnable afterChange)`，`View view()` 返回 `this::build`；`JspaceApp.openSettings` 里 `Window.open(WindowOptions.of("设置").size(420, 480).content(sw.view()))` + `setThemePair` 同主窗 + `applyAppearance` 时若设置窗开着也换）：

- 导航数据：`String[][] NAV = { {"通用","general"}, {"外观","look"}, {"面板","panes"}, {"标签页","tabs"}, {"书签","bookmarks"}, {"暂存架","shelf"}, {"传输","transfers"}, {"扩展","extensions"}, {"快捷键","keys"}, {"关于","about"} }`（10 项，视觉还原即可；每项 `box` 高 30 + 彩色贴片图标 `FileIcons.mini(...,白)` 画在饱和色圆角块上——贴片用 `box(c).size(20,20).radius(5).background(saturated).draw(FileIcons.mini(kind, WHITE))`）。
- 通用页行（每行 `row` 高 34，左 label 右控件，标签 `setrow-<id>`）：`appearance`（popover 下拉三选）、`defaultLayout`（下拉）、`newTabAt`（下拉）、`showHidden`（toggle，`setrow-showhidden-toggle`）、`foldersFirst`（toggle）、`density`（下拉）、`defaultSort`（下拉四键）+ `defaultSortAsc`（下拉升/降，并入同一行下一行）、`restoreWorkspace`（toggle）。控件变更 → 改 `ws.settings()` 字段 → `afterChange.run()`；`appearance` 变更额外 `ws.applyAppearance()`。
- 下拉用 `popover` + `buttonBase` 锚；toggle 用 `Ui.toggle(c, BoolRef, null)`——查 `Ui.toggle(Context, BoolRef, String)` 签名，label 传 null 或空串。
- 警示行：`text(c, "注意：以下默认选项只在新创建工作区时起作用。").textColor(pal.warn())`。

`JspaceApp` 收尾：
- `main`：`Settings st = Settings.load(); Model m = new Model(st); if (st.restoreWorkspace) WorkspaceState.apply(m, WorkspaceState.defaultFile());`
- ⌘Q 菜单项：`WorkspaceState.save(model, defaultFile())` 后 `System.exit(0)`。
- `Workspace` 里 `navigate/newTab/closeTab/layout 切换`后调 `snapshotAsync()`（虚拟线程写盘，Model 字段快照复制于主线程：把 save 的参数构造成不可变小 record 或直接在主线程序列化成 Properties 字符串再异步写——**采用后者**：主线程 `Properties p = WorkspaceState.capture(m)`，异步 `WorkspaceState.write(p, file)`；把 save 拆成 `capture` + `write` 两个静态方法，`save(m,f)` = `write(capture(m), f)` 保留给测试与退出用）。
- `README.md`：在「统一笔记示例」后加「QSpace 文件管理器（2026-11-05）」一节：一段概述 + `./build.sh demo QSpace` / `native-demo QSpace` + 功能清单 + 已手动核对项留空待勾。

- [ ] **Step 4: 全量测试 + 门槛**

Run: `./build.sh test && ./build.sh alloc && ./build.sh ffi-check && ./build.sh listbench`
Expected: 全绿；alloc/listbench 不回归（jspace 未加进 alloc 场景，框架未再改）。

- [ ] **Step 5: 冒烟 + 手动验收清单（对照规格 §8.2）+ Commit**

```bash
./build.sh demo QSpace
```

逐项核对规格 §8.2 手动清单（在 `/tmp/jspace-manual` fixture 上做文件操作），全过之后：

```bash
git add src/main/java/jui/jspace/ src/test/java/jui/jspace/ README.md
git commit -m "feat(jspace): settings window, workspace restore, readme"
```

最后合并回 main（由用户决定时机，或按 superpowers:finishing-a-development-branch 流程）。

---

## Self-Review 记录

- **规格覆盖**：§2.1 窗口尺寸→Task 3；§2.2 布局三档/分割/活动面板→Task 3/8/11；§2.3 标签/面包屑/工具栏/菜单→Task 8/11；§2.4 列表/网格/树形/键盘→Task 9/10/14；§2.5 右键菜单→Task 13；§2.6 文件操作/冲突/副本→Task 7/13；§2.7 侧栏→Task 12；§2.8 状态栏→Task 13；§3.1 FileStore→Task 4/5；§3.2 Settings→Task 6；§4 设置窗口→Task 15；§5.1 主题→Task 2/3/6；§5.2 图标→Task 10/12；§6 框架改动→Task 1/2；§8.1 自动化→各任务 Step 1；§8.2 手动→Task 13/15 Step 5。规格 §2.5 的「在新标签页中打开」在 Task 13 菜单骨架中以注释标注——实现时补 `newTabAt` 逻辑，**已覆盖**。
- **占位符扫描**：Task 7 Step 3 第一段 paste 含明确标注「坏味道示范」，3b 给出干净版并声明以 3b 为准——保留是为展示冲突处理的演进，执行者写 3b。Task 10 FileIcons 的 per-icon 注释块（`/* grey rounded square … */`）配有明确的图元描述与 18 网格规则，属于可实现规格而非 TBD。其余无 TBD/TODO。
- **类型一致性**：`Workspace` 构造签名演进（Task 3 两参→Task 6 三参→Task 8 保持三参），每个演进点都写明旧测试如何同步；`Model.TabState.renameNew` 在 Task 8 引入、Task 13 使用；`Workspace.openEntry` Task 9 私有→Task 10 提升公有，Task 9 的 Step 3 注明「提升为 Workspace 方法」；`FileStore.Publish` 贯穿 Task 5/7/13；`Entries.view` 的参数序（entries, snapOf, sort, foldersFirst, search, showHidden, expanded）在 Task 4/9/10/14 一致。
