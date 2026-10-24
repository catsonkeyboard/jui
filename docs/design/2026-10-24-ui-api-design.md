# j-ui 即时模式 UI API 设计

- 状态：已定稿（2026-10-24）
- 日期：2026-10-24
- 依据：
  - MyGO `ui` 包（egoist/mygo，commit `8049993`）；
  - 本仓库的 macOS 验证原型（FFM + Metal + Core Text，见 README）；
  - API 写法的对比与选择（[ui-api-styles.md](ui-api-styles.md)）。

---

## 1. 为什么做

要做一个类似 Obsidian 的笔记应用。Obsidian 基于 Electron，启动慢、内存占用高。我们要的是**启动快、内存占用低**的同类应用。

验证原型已经证明，Java + FFM + GraalVM Native Image 能用接近原生的成本把界面画到 GPU 上：
- 首帧 133 ms（纯 Objective-C 是 114 ms）；
- 空闲内存 45 MB。

现在缺的是上层的 UI 框架：一套即时模式的 API，让应用代码能简洁地描述界面、处理交互。这份文档设计这套 API，以及支撑它的引擎。

**成功的标准**：用这套 API 写出 Obsidian 式的应用外壳，包括文件列表、搜索、分栏、命令面板和编辑区占位，并且第 8 节的启动、内存和帧耗时目标全部达标。这些目标是硬性门槛。

## 2. 目标、不做的、绝不能出现的

**目标**
- 采用 MyGO 风格的即时模式 API（写法 A）：
  - 控件是传入显式 `Context c` 的静态函数，返回 `Element`；
  - 链式设置样式，子元素放在 lambda 里；
  - 事件既可以就地查询，也可以用回调。
- 状态绑定用**引用对象**（`BoolRef`、`TextRef` 等），控件自己的状态用**控件局部状态**。
- 用 Java 自己实现的 flexbox 布局。
- 指针、键盘焦点、Tab 切换、快捷键、文本输入（含中文输入法）。
- 滚动、虚拟化长列表、分栏、弹出层，满足 Obsidian 外壳的第一批控件需求（6.10）。
- 不开窗口也能测试视图（`Tester`）。
- 对 native image 友好，并且能测量（第 8 节）。

**这次不做**（以后单独设计）
- 多行 Markdown 编辑器（rope、实时预览、语法高亮）。这里只定义它要依赖的底层能力（6.12、7.7）。
- Windows 和 Linux 后端。平台层先抽象好，只实现 macOS。
- 无障碍（读屏）、从右到左的排版、grid 布局、复杂动画、插件系统、Web 视图。

**绝不能出现**（就算功能都对，出现了也算失败）
- 每帧产生和元素数量成正比的垃圾。
- 运行时反射、运行时生成代码，或者其他需要大量 native image 配置的做法。
- 隐藏的全局上下文：控件函数必须显式传入 `c`。
- 应用开发者需要写 C 或 Objective-C。
- 没有理由地偏离 MyGO：行为和命名能和 MyGO 对应的，就和它对应。

## 3. 约束

- Java 25，GraalVM CE 25（native image），只支持 Apple Silicon 上的 macOS 14 及以上（原因见第 4 节第 7 条）。
- 所有原生调用都走 FFM，并沿用 `jui.ffi.Native` 的做法：句柄不绑定函数地址，所在的类在构建期初始化。原生签名统一登记在 `Sigs` 里。
- AppKit 只能在主线程使用。视图的构建、布局和绘制都在主线程进行。
- 复用已有模块：
  - `jui.ffi`：FFM 和 Objective-C 运行时；
  - `jui.mac`：窗口和视图；
  - `jui.metal`：渲染器和 `Scene`；
  - `jui.text`：Core Text 排版和字形图集。

## 4. 已经定下的决定

**你定的**
1. API 写法采用 A：MyGO 风格。
2. 状态绑定用引用对象加控件局部状态。
3. 第一个真实应用是类似 Obsidian 的笔记应用，优先级是启动快、内存低。
4. 第 8 节的性能目标是硬性门槛：M6 必须全部达标才算完成。

**我定的**（读的时候请重点检查）
1. **只有一个 `Element` 类**，所有链式方法都在它上面，和 MyGO 的 `ui.Element` 一致。
   - 原因：Java 的继承加链式调用，需要“自引用泛型”（`Element<E extends Element<E>>`）才能在子类上继续链式调用，写起来很啰嗦。
   - 对某种元素没有意义的方法（比如在按钮上调用 `placeholder`），在开发模式下报错，在正式版里忽略。
2. **`Element` 是轻量句柄对象**（记录节点编号和代数）。只在当场使用时，靠 Graal 的逃逸分析消除分配。
   - 如果实测发现消除不掉，就退回“直接返回池里的节点对象”的方案（7.1）。
3. **长度单位是 DIP**（设备无关像素，等于 macOS 的点），在 API 里用 `float`；绘制时再对齐到物理像素。
4. **颜色是 `Color` record**（内部是一个 `int` 0xRRGGBBAA）。主题里的颜色和应用里的常量都只创建一次，每帧不分配。
5. **重建规则和 MyGO 一致**：一次构建里，如果有控件报告了输入（点击、修改、提交）或者执行了回调，就立刻再构建一遍，一帧最多 3 遍。
6. **列表行高可以各不相同**：测量可见的行，其余行按已测行的平均高度估计（同 MyGO）。
7. **最低支持 macOS 14**（验证原型的构建脚本目前写的是 13，实现时一并改掉）。
   - 原因：按显示器刷新率出帧要用 `NSView` 的 `displayLink(target:selector:)`，它从 macOS 14 才有。
   - 支持 13 就要再写一条退路（MyGO 用的是按刷新率触发的 `NSTimer`），而且需要一台 macOS 13 的机器来测试。
   - 以后要降到 13，按 MyGO 的办法补上 `NSTimer` 退路即可。
8. **内存分配的规则分两层**，详见第 8 节：
   - 框架内部：内容不变的帧里，不能产生和元素数量成正比的分配，这是硬性要求；
   - 加上应用代码的整帧分配（以 6.15 的外壳为准）：内容不变时每帧不超过 32 KB。
9. **基准外壳的范围**，详见第 8 节：
   - 用脚本生成 1 万篇 Markdown 文件作为测试笔记库；
   - 启动时只列文件名，搜索只匹配标题，不建全文索引；
   - 首帧必须显示上次打开的笔记，文件列表可以在后台扫描完成后再填进来。

---

## 5. 总体架构

```
 应用代码  view(Context c)
     │  调用 Ui.row / text / button …（静态函数）
     ▼
 jui.ui          公开 API：Ui、Context、Element、引用对象、Theme、Tester …
     │
     ▼
 jui.ui.engine   每帧：输入分发 → 构建（最多 3 遍）→ 清理不再出现的状态 → 布局（flexbox）
     │                 → 提交（命中测试列表、焦点顺序、滚动范围、输入法光标）→ 绘制
     ▼
 jui.render      Scene（方框、阴影、字形、裁剪、线段）→ MetalRenderer
 jui.text        Core Text 排版、字形图集
     ▲
 jui.platform    平台接口：窗口、事件、输入法、剪贴板、菜单、光标、外观
 jui.platform.mac  AppKit 实现（由现有的 MacApp / MacWindow 演变而来）
```

**一帧的流程**
1. 平台事件（指针、滚轮、按键、文本、组字、焦点、尺寸）先进入队列。只有收到事件、调用了 `invalidate`/`update`、有定时器到期或有动画时，才会请求新的一帧。
2. **分发输入**：用上一帧提交的命中测试列表，找出事件落在哪个元素（按身份识别），记到该元素的状态里。
3. **构建**：调用视图函数。每个控件构造时，按身份找到自己的状态，读取输入，把用户的修改写回引用对象。如果这一遍消耗了输入或执行了回调，就再构建一遍（最多 3 遍）。
4. **清理**：这一帧没有出现的元素，它的状态被回收。
5. **布局**：flexbox，文本通过 `TextEngine` 测量尺寸。
6. **提交**：把每个元素的位置和尺寸写回它的状态，生成命中测试列表（按绘制顺序）、焦点顺序、滚动范围和输入法光标位置。
7. **绘制**：遍历元素树生成 `Scene`，交给 `MetalRenderer`。

---

## 6. 公开 API

以下代码都属于 `jui.ui` / `jui.app` 包，应用里用 `import static jui.ui.Ui.*;` 引入控件函数。签名是设计目标，具体命名的细节可以在实现时微调（第 11 节）。

### 6.1 应用与窗口

```java
public final class App {
    /** 在主线程运行应用，阻塞直到退出；whenReady 在应用启动完成后执行。 */
    public static void run(Runnable whenReady);
    public static void quit();
}

public final class Window {
    public static Window open(WindowOptions options);
    /** 任何线程都可以调用：在主线程执行 change，然后重画。 */
    public void update(Runnable change);
    /** 任何线程都可以调用：只请求重画。 */
    public void invalidate();
    public void close();
}

WindowOptions.of("笔记").size(960, 640).minSize(480, 320).content(app::view);
```

对应 MyGO 的 `mygo.App.WhenReady`、`NewWindow(WindowOptions{Content: ui.View(app.view)})`、`Window.Update`。

### 6.2 视图、Context、Element

```java
@FunctionalInterface
public interface View {
    void build(Context c);
}
```

- **视图**：一个以应用状态为输入的方法，每帧调用，一帧内可能调用多次。应用状态放在你自己的对象里（比如 `NotesApp` 的字段）。
- **`Context`**：每个窗口一个，生命周期和窗口相同。容器在执行子元素的 lambda 期间，临时把自己设为“当前父元素”，所以子元素的控件函数照样传同一个 `c`。

  `Context` 提供：
  - `theme()`、`scale()`；
  - `key(Object)`：见 6.3；
  - `shortcut(Shortcut)`：窗口级快捷键，见 6.7；
  - `invalidate()`、`after(Duration)`：一段时间后再画一帧；
  - `now()`、`modifiers()`、`window()`。
- **`Element`**：控件函数的返回值，只在本次构建期间有效。上面的方法分四类：
  - **布局**：见 6.8。
  - **样式**：见 6.9。
  - **行为**：`focusable()`、`autoFocus()`、`disabled(boolean)`、`cursor(Cursor)`、`label(String)`（给测试和以后的无障碍用）。
  - **查询**（读的是“上一帧以来发生的事”）：
    - `hovered()`、`pressed()`、`clicked()`、`doubleClicked()`、`rightClicked()`；
    - `focused()`、`focusWithin()`；
    - `changed()`、`submitted()`；
    - `dragX()`、`dragY()`：按住时指针自上一帧移动的距离；
    - `bounds()`：上一帧的位置和尺寸。
  - **回调**：`onClick(Runnable)`、`onChange(Runnable)`、`onSubmit(Runnable)`。在这一遍构建结束后执行，然后重建。
  - **子元素**：`children(Runnable)`。
- 把 `Element` 存起来、到后面的构建里再用，属于错误用法：开发模式抛 `IllegalStateException`，正式版忽略或返回空结果（同 MyGO）。

### 6.3 身份与 key

- 元素的身份由“父元素的身份 + 它在兄弟中的位置”算出。焦点、滚动位置、输入框的状态、局部状态都跟着身份走，跨帧保留。
- 循环生成的元素要指定 key，让状态跟着数据项走，而不是跟着位置走：

  ```java
  for (Note n : notes) {
      noteRow(c.key(n.id()), n, n == selected);
  }
  ```

- `c.key(k)` 给**下一个**构造的元素指定 key，然后返回 `c` 本身。
  - 提供 `key(long)`、`key(int)`、`key(String)`、`key(Object)` 四个重载，用数字 ID 作 key 时就不会产生装箱对象。
- key 在兄弟之间必须唯一，按 `equals`/`hashCode` 比较。重复的 key 在开发模式下报错，在正式版里记日志（同 MyGO）。

### 6.4 状态绑定：引用对象

Java 没有指针，控件通过引用对象读写应用状态：

```java
public final class BoolRef  { public BoolRef(boolean v); public boolean get(); public void set(boolean v); }
public final class IntRef   { ... }
public final class FloatRef { ... }
public final class DoubleRef{ ... }
public final class TextRef  { public TextRef(String v); public String get(); public void set(String v); }
public final class Ref<T>   { public Ref(T v); public T get(); public void set(T v); }
```

规则（同 MyGO 的指针绑定）：
- 控件**在返回前**就把用户的修改写回引用对象，所以下一行代码读到的就是新值。
- `changed()` 报告这一遍构建里值被用户改过；`submitted()` 报告单行输入框里按了回车。
- 数据不在引用对象里时（比如存在 Map 里），先放进一个**临时引用对象**，改了再写回：

  ```java
  BoolRef viewed = new BoolRef(app.isViewed(file));
  if (checkbox(c, viewed, "已读").changed()) {
      app.setViewed(file, viewed.get());
  }
  ```

  这个临时对象只在当场使用，预期会被逃逸分析消除。

### 6.5 控件局部状态

控件自己需要的、跨帧保留的状态（比如是否展开、悬停动画的进度），不放进应用状态，而是挂在元素的身份上：

```java
public static <T> Ref<T> local(Element e, String name, Supplier<T> initial);
public static BoolRef localBool(Element e, String name, boolean initial);
public static FloatRef localFloat(Element e, String name, float initial);
```

- 第一次构建时创建；元素不再出现时，和元素状态一起回收（同 MyGO 的 `ui.Local`）。
- `name` 用来区分同一个元素上的多份局部状态。

### 6.6 事件：就地查询与回调

- **就地查询**：`if (button(c, "保存").clicked()) save();`，处理代码写在控件旁边。
- **回调**：`.onClick(this::save)`。适合要修改正在遍历的集合的场景：等这一遍构建结束后再改，循环就不会出问题。
- **每次输入只处理一次**：被查询或回调消耗掉的输入，不会在重建时再次报告。
- **指针规则**（同 MyGO）：
  - 指针交给最内层的、会接收指针的元素：交互控件、`focusable()` 的元素，以及上一帧查询过指针状态的元素。
  - 按下期间，按下开始时所在的那些元素保持悬停状态，拖到别处不会让其他元素亮起来。
  - `clicked()` 在指针于同一元素内按下并松开时报告，也会因为键盘激活（回车或空格）而报告。
  - 读取到“开始按下”时，会在绘制前重建一次，让按下时做出的选择和焦点颜色在同一帧出现。

### 6.7 键盘：焦点、Tab、快捷键

- 点击 `focusable()` 的元素会让它获得焦点。Tab 和 Shift+Tab 按构建顺序切换焦点；用键盘切换时显示焦点环。
- `autoFocus()`：元素出现时获得焦点，比如对话框里的第一个输入框。
- 持久的焦点身份 `Handle`：

  ```java
  final Handle search = new Handle();                       // 放在应用状态里
  textInput(c.key("search"), query).bind(search);           // 每次构建都绑定
  search.focus();                                           // 在任意动作里请求焦点
  ```

- **快捷键**：
  - `element.shortcut(Shortcut)`：焦点在该元素内部时按下才报告；
  - `c.shortcut(Shortcut)`：窗口级。

  例如 `Shortcut.cmd(Key.P)`、`Shortcut.of(Mod.CMD | Mod.SHIFT, Key.F)`。
- **菜单**：macOS 的应用菜单自带标准的“编辑”菜单（撤销、剪切、复制、粘贴、全选），这些命令交给当前获得焦点的文本控件处理。应用自己的菜单项在第 10 节的 M5 加入。

### 6.8 布局（flexbox）

属性名和语义照搬 CSS flexbox，与 MyGO 一致。

| 类别 | 方法 |
|---|---|
| 容器 | `box(c)`、`column(c)`（子元素从上到下排列，横向拉伸）、`row(c)`（从左到右排列，纵向居中）、`reverse()`、`spacer(c)`（占满剩余空间）、`divider(c)` |
| 尺寸 | `width`、`height`、`size(w, h)`、`widthPercent`、`heightPercent`、`minWidth`/`maxWidth`/`minHeight`/`maxHeight`、`fill()`、`fillWidth()`、`fillHeight()` |
| 伸缩 | `grow(float)`、`shrink(float)`、`basis(float)` |
| 间距 | `gap`、`gapX`、`gapY`、`padding(全部)`、`padding(上下, 左右)`、`padding(上, 右, 下, 左)`、`margin(...)`（同样三种写法；`Ui.AUTO` 表示自动外边距） |
| 对齐 | `justify(Justify)`、`alignItems(Align)`、`alignSelf(Align)`、`center()`、`wrap()`、`alignContent(...)` |
| 定位 | `absolute()`，配合 `top`/`right`/`bottom`/`left` |
| 裁剪和可见性 | `clip()`、`invisible()` |

- 文本元素的尺寸由 `TextEngine` 测量：先给定可用宽度，再得到换行后的高度。
- 本期不做：`Baseline` 对齐（放到编辑器设计里一起做）、grid、宽高比。

### 6.9 样式与主题

- 盒子样式：`background(Color)`、`border(float, Color)`、`radius(float)` 或 `radius(左上, 右上, 右下, 左下)`、`shadow(x, y, blur, spread, Color)`、`opacity(float)`。
- 文字样式：`textColor`、`fontSize`、`fontWeight`、`bold()`、`singleLine()`、`maxLines(int)`。
  - 文字样式可以设在任何元素上，由里面的文字继承（同 MyGO）。
- `Theme` 是一个 record，字段包括：
  - 颜色：`background`、`surface`、`surfaceHover`、`border`、`text`、`textMuted`、`accent`、`accentText`、`selection`、`focusRing`；
  - 字号、圆角等。

  提供 `Theme.light()` 和 `Theme.dark()`，默认跟随系统的明暗外观，系统切换时自动重画。
- `Color.rgb(0x2563EB)`、`Color.rgba(0x2563EB, 0.5f)`。

### 6.10 第一批控件（按 Obsidian 外壳的需要）

| 类别 | 控件 | 对应 MyGO | 里程碑 |
|---|---|---|---|
| 布局 | `box`、`row`、`column`、`spacer`、`divider` | 同名 | M1 |
| 文字 | `text(c, String)` | `ui.Text` | M1 |
| 动作 | `button(c, label)`、`primaryButton(c, label)` | 同名 | M2 |
| 选择 | `checkbox(c, BoolRef, label)`、`toggle(c, BoolRef, label)`（开关）、`slider(c, DoubleRef, min, max)` | `Checkbox`/`Switch`/`Slider` | M2 |
| 滚动 | `scroll(c)`、`scroll(c, ScrollState)` | `ui.Scroll` | M3 |
| 长列表 | `list(c, ListState, int count, IntConsumer row)`，`state` 可以为 null | `ui.List` | M3 |
| 文本输入 | `textInput(c, TextRef)`（单行，支持输入法、选区、剪贴板、撤销） | `ui.TextInput` | M4 |
| 分栏 | `split(c, FloatRef firstWidth, Runnable first, Runnable second)` | `ui.Split` | M5 |
| 弹窗 | `modal(c, BoolRef open, Runnable content)`（点击外部或按 Esc 关闭） | `ui.Modal` | M5 |
| 弹出面板 | `popover(c, Element anchor, BoolRef open, Runnable content)` | `ui.Popover` | M5 |
| 右键菜单 | `contextMenu(c, Element target, Consumer<Menu> items)`（使用系统原生菜单） | `ui.ContextMenu` | M5 |
| 标签页 | `tabs(c, IntRef selected, String... titles)` | `ui.Tabs` | M5 |
| 自定义绘制 | `element.draw(Painter.Draw)`，见 6.12 | `Draw` | M6 |

**无外观的基础控件**（同 MyGO 的 Base）：`buttonBase`、`toggleBase`、`textInputBase` 等只处理指针、键盘、焦点和数据绑定，外观完全交给返回的元素。带样式的内置控件都建立在它们之上，第三方也可以基于它们做自己的设计。

### 6.11 自定义控件

自定义控件就是一个静态函数，写法和内置控件完全一样，这也是选择写法 A 的主要原因：

```java
static Element noteRow(Context c, Note n, boolean active) {
    Theme t = c.theme();
    Element row = row(c).padding(6, 10).radius(6);
    if (active) {
        row.background(t.accent()).textColor(t.accentText());
    } else if (row.hovered()) {
        row.background(t.surfaceHover());
    }
    row.children(() -> text(c, n.title()).singleLine());
    return row;
}

// 带局部状态的控件：点击才显示的剧透文字（同 MyGO 文档里的 Spoiler）
static void spoiler(Context c, String s) {
    Theme t = c.theme();
    Element box = box(c).padding(2, 6).radius(4).focusable();
    BoolRef shown = localBool(box, "shown", false);
    if (box.clicked()) {
        shown.set(!shown.get());
    }
    box.background(shown.get() ? t.surface() : t.text());
    box.children(() -> text(c, s));
}
```

### 6.12 自定义绘制（给图谱视图用）

```java
canvas(c).fill().draw((Painter p, Rect r) -> {
    for (Edge e : graph.edges()) p.line(e.x1(), e.y1(), e.x2(), e.y2(), 1, t.border());
    for (Node n : graph.nodes())  p.circle(n.x(), n.y(), 6, t.accent());
    p.text("中心笔记", cx, cy, 12, t.text());
});
```

- 绘制函数在布局之后调用，坐标相对于元素自身。
- 可用的绘制操作：矩形和圆角矩形、圆、线段（新增的着色器图元）、文字、裁剪。
- 需要动画时调用 `p.animationFrame()` 请求下一帧。

**给编辑器预留的底层能力**（编辑器以后单独设计，这里先定接口方向）：
- 元素可以接管文本输入（`handleTextInput`），收到插入文字、组字（输入法的拼音阶段）、编辑命令；
- 元素要报告光标位置，供输入法定位候选框；
- `TextEngine` 要增加：按坐标找字符位置、按字符位置求光标矩形、带多种样式的富文本片段。

### 6.13 多线程

- 视图、回调、查询都在主线程执行。
- 其他线程用 `window.update(() -> ...)` 修改状态，修改在主线程上执行，然后重画。
- 建索引、扫描文件、全文搜索这类工作用虚拟线程做，完成后再 `update`。

### 6.14 Tester：不开窗口测试视图

```java
var tt = Tester.of(app::view, 960, 640);
tt.click("新建笔记");
tt.type("会议记录");
tt.key(Shortcut.cmd(Key.P));
assertTrue(tt.hasText("命令面板"));
tt.advance(Duration.ofMillis(300));
```

- 使用真实的布局和 `TextEngine`（Core Text 不需要窗口），但不用 Metal。
- 元素按文字或 `label` 查找。
- 有自己的时钟，调用 `advance` 才会前进。
- 能检查当前焦点、剪贴板内容，以及有没有还在请求的帧。

### 6.15 完整示例：Obsidian 式外壳

```java
import static jui.ui.Ui.*;

public final class NotesApp {
    final Vault vault;                                    // 笔记库：扫描和索引在虚拟线程里做
    final TextRef query = new TextRef("");
    final FloatRef sidebarWidth = new FloatRef(260);
    final BoolRef paletteOpen = new BoolRef(false);
    final TextRef command = new TextRef("");
    final ListState files = new ListState();
    Note selected;

    void view(Context c) {
        if (c.shortcut(Shortcut.cmd(Key.P))) {
            paletteOpen.set(true);
        }
        split(c, sidebarWidth, () -> sidebar(c), () -> editor(c)).fill();
        modal(c, paletteOpen, () -> palette(c)).width(560);
    }

    void sidebar(Context c) {
        Theme t = c.theme();
        column(c).fill().padding(8).gap(6).background(t.surface()).children(() -> {
            if (textInput(c, query).placeholder("搜索笔记").changed()) {
                files.scrollToTop();
            }
            List<Note> notes = vault.filter(query.get());   // Vault 自己按查询缓存结果
            list(c, files, notes.size(), i -> {
                Note n = notes.get(i);
                if (noteRow(c.key(n.id()), n, n == selected).clicked()) {
                    selected = n;
                }
            }).grow(1);
            if (primaryButton(c, "新建笔记").clicked()) {
                selected = vault.create();
            }
        });
    }

    void editor(Context c) {
        Theme t = c.theme();
        column(c).fill().padding(24).gap(12).children(() -> {
            if (selected == null) {
                text(c, "按 ⌘P 打开命令面板").textColor(t.textMuted()).center();
                return;
            }
            text(c, selected.title()).fontSize(24).bold();
            scroll(c).grow(1).children(() -> text(c, selected.body()));  // 编辑器另行设计，先只读显示
        });
    }

    void palette(Context c) {
        Theme t = c.theme();
        textInput(c, command).placeholder("输入命令").autoFocus();
        for (Command cmd : Commands.matching(command.get())) {
            Element item = row(c.key(cmd.id())).padding(8, 12).radius(6);
            if (item.hovered()) item.background(t.surfaceHover());
            item.children(() -> text(c, cmd.title()));
            if (item.clicked()) {
                cmd.run();
                paletteOpen.set(false);
            }
        }
    }

    public static void main(String[] args) {
        var app = new NotesApp(Vault.open(Path.of(args[0])));
        App.run(() -> Window.open(WindowOptions.of("笔记").size(1100, 720).content(app::view)));
    }
}
```

---

## 7. 内部设计

### 7.1 元素池与 `Element` 句柄

- 每个窗口有一个**节点池**：按块分配节点对象（参考 MyGO，每块 32 个），每帧从头开始复用，帧结束后多出来的块会被释放，只留一块空闲的备用。
- 节点的字段尽量都是基本类型：身份（`long`）、父节点、第一个子节点、下一个兄弟节点（都是池内编号），以及种类、布局属性、视觉属性、标志位。引用类型的字段只有文字、回调和局部状态。
- 子节点用“第一个子节点 + 下一个兄弟节点”链接，不用 `List`。
- `Element` 句柄记录 `(context, 节点编号, 代数)`，每次使用都会校验代数，过期的句柄就能被发现。
- **退路**：如果实测（第 8 节）发现句柄的分配没有被消除，就改成直接返回池里的节点对象，用“构建遍次的编号”做校验。代价是只能发现一部分误用。

### 7.2 元素状态表

- 跨帧保留的数据按身份（`long` 哈希）存在每个窗口的状态表里：上一帧的位置和尺寸、悬停和按下标志、焦点、滚动位置、文本输入状态（内容、光标、选区、组字）、局部状态、布局缓存。
- 一帧里没有出现的身份，它的状态会被回收，放进空闲列表给新元素复用。
- 身份的算法：`id = mix(父元素的 id, 有 key 时用 key.hashCode() 并加上区分标记，否则用位置)`。开发模式下检测兄弟之间的哈希冲突。

### 7.3 输入处理与命中测试

- 事件先进入队列。帧开始时，用上一帧的命中测试列表（按绘制顺序，带裁剪信息）找到目标身份，再更新悬停集合、按下的元素、点击、双击（用平台提供的连击次数）、拖动距离。
- 控件构造时读取自己的输入，被查询或回调消耗后清除。
- 键盘事件发给焦点所在的元素，再沿祖先向上冒泡：先看文本输入，再看元素的快捷键，最后是窗口快捷键。

### 7.4 flexbox 引擎与文本测量

- 自己用 Java 实现 CSS flexbox 的一个子集（第 6.8 节列出的属性），按 CSS 规范的算法：确定主轴尺寸、伸缩、换行、交叉轴对齐、自动外边距、绝对定位。
- 文本节点通过测量回调排版：给定可用宽度，得到排版结果（`TextLayout`），排版结果缓存在 `TextEngine` 里。
- **布局缓存**（M6）：同 MyGO，比较元素的布局输入和子元素是否和上一帧相同；相同且得到的空间也相同，就直接复用上一帧的结果。数据结构从 M1 起就要为此留好位置。

### 7.5 绘制：扩展 `Scene`

需要在现有的方框、阴影、字形之外增加：
- **裁剪**：矩形裁剪栈，通过 Metal 的 `setScissorRect:` 实现，给滚动区域和弹出层用；圆角裁剪以后再做。
- **线段**：给图谱视图用，在着色器里新增一种距离场图元。
- **焦点环**和选区高亮：用现有的方框图元就能画。
- **字形图集**：增加扩容和回收（参照 MyGO：第一次画的字形先放进临时区，第二帧还在用才移到长期区）。

### 7.6 帧调度

- 只有收到事件、调用 `invalidate`/`update`、`after` 定时器到期、或有动画时，才请求新的一帧；空闲时什么都不画。
- 动画和滚动期间按显示器的刷新率出帧，用 `NSView` 的 `displayLink(target:selector:)`（macOS 14 起提供），和 MyGO 一样。
  - 验证原型里还没有这部分：原型只在 AppKit 调用 `updateLayer` 时画一帧。
  - 不需要出帧时暂停 display link，空闲时不唤醒任何线程。
- 窗口被遮挡时暂停动画（参照 MyGO 的做法，本期可以只做简化版）。

### 7.7 平台层接口

`jui.platform` 定义接口，`jui.platform.mac` 实现：

| 能力 | macOS 实现 |
|---|---|
| 应用循环、窗口、尺寸和缩放、请求帧 | 现有的 `MacApp` / `MacWindow` |
| 指针、滚轮（触控板的精确增量和惯性）、按键、修饰键 | `NSView` 的鼠标、滚轮、键盘事件方法（用 `ObjCClass` 实现） |
| 输入法 | `JUIView` 实现 `NSTextInputClient`：插入文字、设置组字文字、结束组字、报告光标矩形（`firstRectForCharacterRange`）、编辑命令（`doCommandBySelector:`） |
| 剪贴板 | `NSPasteboard`（本期只支持纯文本） |
| 右键菜单、应用菜单 | `NSMenu` |
| 光标形状 | `NSCursor` |
| 明暗外观 | `NSApp.effectiveAppearance`，变化时通知 |

新增的原生签名（NSRange、NSPoint 参数，NSRect 返回值，MTLScissorRect）照例登记在 `Sigs` 里，调用走 `Native`。

### 7.8 开发模式的检查

在 JVM 上运行，或设置了 `-Djui.dev=true` 时开启。检查内容：
- 过期的 `Element`；
- 兄弟之间重复的 key；
- 设置了却没被下一个元素用掉的 key；
- 对不适用的元素调用的方法；
- 在非主线程调用 UI 函数。

正式版里这些检查都关闭，或只记日志。

---

## 8. 性能与内存预算

测量环境和原型一致：Apple Silicon，macOS 26，native image，Retina 屏。笔记库按 1 万篇笔记计。

**下表的目标都是硬性门槛**：M6 必须全部达标才算完成（第 4 节）。达不到的项目要继续优化，不能只说明差距就结束。

| 指标 | 目标 |
|---|---|
| 冷启动到首帧（显示上次打开的笔记） | < 200 ms |
| 空闲内存（物理占用，空闲 3 秒后） | < 80 MB |
| 内容不变时的一帧（构建 + 布局 + 绘制，以 6.15 的外壳为准） | < 4 ms，整帧分配不超过 32 KB |
| 打字延迟（按键到画面更新） | 不超过一帧 |
| 1 万行的列表滚动 | 只构建可见行，滚动时稳定在显示器刷新率 |

**内存分配的规则**

- **框架内部（硬性要求）**：内容不变的帧里，节点池、状态表、布局、命中测试、绘制，都不能产生和元素数量成正比的分配。
- **整帧（含应用代码）**：以 6.15 的外壳为准，内容不变时每帧不超过 32 KB。

整帧的预算包含了文档自己推荐的几种应用写法。在 GraalVM 社区版上，下面这些写法可能消除不掉：
- 传给 `children(...)` 等方法的、捕获了外部变量的 lambda：只有被调用的方法内联了，分配才能消除；
- 6.4 里先放进临时引用对象再传给控件的写法：控件函数较大，不一定会被内联；
- 用对象作 key：数字 ID 用 `key(long)` 重载就能避免装箱。

**M1 先做分配实测**，在开始写控件之前完成：
- 在 native image 上分别测量 `Element` 句柄、`children` 的 lambda、临时引用对象、带 key 的行各自产生多少分配；
- 如果超出预算，就在 M1 里调整公开 API，因为这时改代价最小。可选的调整有：不捕获变量的重载形式、去掉临时引用对象的推荐写法、启用 7.1 的退路；
- 换用 Oracle GraalVM 不在本次考虑范围内。

**基准外壳和测试数据**

- **测试笔记库**：用脚本生成 1 万个 Markdown 文件，每篇有标题和几段正文，放在磁盘上。
- **启动**：
  - 首帧必须显示上次打开的那篇笔记（路径记在一个小的状态文件里，只读取这一篇的正文）；
  - 文件列表在虚拟线程里扫描目录，扫完后通过 `window.update` 填进列表，可以晚于首帧。
- **内存**：80 MB 的预算按“1 万个文件名加一篇打开的笔记”来测，不建全文索引，也不把所有正文读进内存。
- **搜索**：只在内存里按标题过滤。全文搜索（比如用 Lucene）属于应用功能，不在这份设计的范围内。
- `Vault` 和 `Commands` 是示例应用里的类，只实现基准测试需要的最小功能，不属于框架。

**如何测量**
- `JUI_FRAME_STATS=1` 时，记录每一帧构建（含遍数）、布局、绘制、提交的耗时，以及这一帧分配的内存（参照 MyGO 的 `MYGO_FRAME_STATS`）。
  - JVM 上用 `ThreadMXBean` 统计当前线程分配的字节数；
  - native image 上连续跑几千帧内容不变的帧，用堆使用量和 GC 计数换算出每帧的平均分配。具体做法由实现者决定，但结果必须是“每帧多少字节”。
- `build.sh bench` 增加基准外壳的启动和内存测量。

---

## 9. 包结构与现有代码的去向

| 包 | 内容 | 来源 |
|---|---|---|
| `jui.app` | `App`、`Window`、`WindowOptions` | 新增 |
| `jui.ui` | `Ui`、`Context`、`Element`、`View`、引用对象、`Theme`、`Color`、`Handle`、`Shortcut`、`Key`、`ListState`、`ScrollState`、`Painter`、`Tester` | 新增 |
| `jui.ui.engine` | 节点池、状态表、输入分发、构建、flexbox、绘制、帧调度 | 新增 |
| `jui.platform`、`jui.platform.mac` | 平台接口和 AppKit 实现 | 由 `jui.mac` 演变而来 |
| `jui.render`、`jui.render.metal` | `Scene` 和 `MetalRenderer` | 由 `jui.metal` 迁入 |
| `jui.text` | 不变，增加 6.12 列出的能力 | 已有 |
| `jui.ffi` | 不变 | 已有 |
| `jui.spike` | 保留作为启动基准，或改写成基于新 API 的示例 | 已有 |

构建：继续用 `build.sh`，或者在 M1 时换成 Gradle，由实现者决定（第 11 节）。

## 10. 分阶段交付

每个里程碑都要有一个能运行的示例，测试通过，并在 native image 下跑通。

| 里程碑 | 内容 | 示例与验收 |
|---|---|---|
| M1 引擎骨架 | 先做第 8 节的分配实测，必要时调整 API；然后是 `Context`、`Element`、节点池、身份和 key、`box`/`row`/`column`/`text`/`spacer`、flexbox（不含换行和绝对定位）、绘制到 `Scene`、按需出帧、`Tester` 的基本功能 | 用新 API 重写原型的卡片示例，画面和原型一致；分配实测有结论 |
| M2 交互 | 指针（悬停、按下、点击、双击）、`button`、`checkbox`、`toggle`、`slider`、回调、重建规则、焦点和 Tab、快捷键、局部状态、主题和明暗切换 | 计数器和待办清单；Tester 覆盖点击、Tab 和快捷键 |
| M3 滚动和列表 | `scroll`（裁剪、触控板和滚轮、惯性）、`list`（虚拟化、行高可变）、`ListState`/`ScrollState`、flexbox 补齐换行和绝对定位 | 1 万篇笔记的文件列表，滚动流畅，帧耗时在预算内 |
| M4 文本输入 | `textInput`：输入法（`NSTextInputClient`）、光标、选区、剪贴板、撤销、编辑菜单 | 搜索框过滤列表，中文拼音输入时候选框位置正确 |
| M5 外壳 | `split`、`modal`、`popover`、`contextMenu`（原生菜单）、`tabs`、应用菜单 | 6.15 的 Obsidian 式外壳 |
| M6 绘制和性能 | `Painter`（线段、圆、文字）、布局缓存、字形图集扩容和回收、帧统计、对照第 8 节逐项测量和优化 | 简单的图谱视图；第 8 节的指标**全部达标**（硬性门槛，达标才算完成） |

## 11. 留给实现者决定的

- 第 6 节以外的方法命名和重载；只要和 MyGO 的语义对应即可。
- 节点池、状态表、命中测试列表的具体数据结构。
- flexbox 算法的实现细节，只要和 CSS 规范的结果一致即可。
- 主题的具体配色。
- 是否换成 Gradle，以及测试框架选 JUnit 5 还是别的。
- 开发模式的开关方式。

## 12. 以后单独设计的

- **多行 Markdown 编辑器**：rope 存储、实时预览、语法高亮、大文件。依赖 6.12 列出的底层能力。
- **Windows（Direct3D 11）和 Linux（GTK 3 + OpenGL）后端**。
- **无障碍**：`label` 和角色信息已经在 `Element` 上预留，以后生成系统的无障碍树（MyGO 映射到 `NSAccessibility`）。
- **插件系统**：写法 A 已经保证插件写的控件和内置控件写法一致。
- **彩色字形**（emoji）、grid 布局、动画和过渡、从右到左排版。
