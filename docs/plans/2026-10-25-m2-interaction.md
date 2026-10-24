# M2 交互 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal**：实现设计文档里的 M2。包括：
- 指针：悬停、按下、点击、双击、右键、拖动；
- 控件：`button`/`primaryButton`、`checkbox`、`toggle`、`slider`，以及它们的无外观基础版；
- 回调、重建规则（一帧最多 3 遍）；
- 焦点和 Tab、快捷键、控件局部状态；
- 主题和明暗切换。

最后做计数器和待办清单两个示例，用 Tester 覆盖点击、Tab 和快捷键，并在 native image 下跑通。

**Architecture**：
- **元素状态表**：每个窗口一张，按身份保存跨帧数据：上一帧的位置、点击计数、按下状态、局部状态。一帧里没出现的身份，它的状态被回收。
- **事件处理**：平台事件一到，就用上一帧提交的命中测试列表处理，改的是状态表和引擎里的悬停、按下、焦点，然后请求一帧。这和 MyGO 的 `engine.event` 相同。
- **键盘**：Tab、回车/空格激活、滑块方向键当场处理；其余按键进队列，在下一帧开始时按快捷键路由。
- **每帧的流程**：路由按键 → 构建（最多 3 遍，某一遍消耗了输入或执行了回调才再来一遍）→ 布局 → 提交 → 回收状态 → 绘制。提交时写回状态里的位置，生成命中测试列表、焦点顺序和快捷键登记。

**Tech Stack**：
- Java 25、GraalVM CE 25（`graalvm-community-25.4.4.1.1+1.1`）；
- FFM（`jui.ffi`）、AppKit、Metal、Core Text；
- 零依赖：自己写的测试运行器，构建用 `build.sh`。

**Spec**：[docs/design/2026-10-24-ui-api-design.md](../design/2026-10-24-ui-api-design.md)，已定稿。本计划实现其中的 M2（第 10 节）：
- API 依据第 6.2–6.11、6.14 节；
- 内部设计依据第 7.2、7.3、7.5、7.7 节；
- 分配规则依据第 8 节。

**行为依据**：MyGO（egoist/mygo，commit `8049993`）。本地副本在 `/tmp/mygo`。下文写“同 MyGO”的地方，都以这个版本的 `ui/input.go`、`ui/runtime.go`、`ui/widgets.go`、`ui/base.go`、`internal/darwin/surface.go` 为准。

## Global Constraints

沿用 M1 的约束：
- **平台**：Apple Silicon、macOS 14 及以上，`MACOS_MIN=14.0`。
- **原生调用**：全部走 FFM，通过 `jui.ffi.Native` 不绑定地址的句柄调用；新的签名登记在 `jui.ffi.Sigs`。不写 C 或 Objective-C。
- **不用反射**：主代码在运行时不用反射，也不在运行时生成代码。只有测试运行器和测试代码可以用。
- **零第三方依赖**：Maven Central 当前拒绝本机出口 IP 的下载，也不要尝试绕过封禁。
- **线程**：UI 函数和事件处理都在主线程。JVM 下用 `-XstartOnFirstThread` 运行，测试也一样。
- **开发模式**：在 JVM 上运行，或设置了 `-Djui.dev=true` 时开启。开启时，误用 API 抛 `IllegalStateException`；关闭时忽略。
- **命名**：按设计第 6 节，和 MyGO 对应。
- **类型约定**：长度单位是 DIP，类型为 `float`；颜色是 `jui.ui.Color`（0xRRGGBBAA）。
- **注释和文档**：代码注释用英文，文档用中文。
- **提交信息**：每条提交信息末尾加上 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- **分配门槛**（设计第 8 节）：
  - 框架内部：内容不变的帧里，不能产生和元素数量成正比的分配。状态表、命中测试列表、焦点顺序、快捷键登记都用可复用的数组和 `LongMap`；
  - 整帧：以基准外壳为准，每帧不超过 32 KB。

M2 新增两条：
- **框架控件内部不用捕获变量的 lambda**：`button` 等函数要建子元素时，调用 `Engine.enter`/`exit`，不调 `Element.children(Runnable)`。在 native image 上，捕获变量的 lambda 每个元素都要分配一次。
- **每次输入只报告一次**：被查询或回调消耗掉的输入，不会在重建的遍次里再次报告（设计 6.6）。

## 本计划定下的细节

设计文档没写死、或者 M2 有意和设计文档不同的地方，实现者照做。审查时请重点检查：

1. **事件到达时立刻处理**，用的是上一帧提交的命中测试列表，同 MyGO。按键里只有 Tab、回车/空格激活、滑块的方向键当场处理，其余进队列。
   - 设计第 5 节写的是“先进入队列”。两种做法用的都是上一帧的命中测试列表，结果相同；照 MyGO 做，可以少存一份事件。
2. **每帧最多路由一个排队的按键**，其余的留到下一帧，并请求下一帧。这样重复按键（比如连按两次 ↑）不会在同一帧里丢掉一个，顺序也不乱。
3. **状态的回收放在提交之后、绘制之前**。MyGO 放在绘制之后；j-ui 的绘制只读节点、不读状态，所以两者等价。
4. **推迟到后面里程碑的**：
   - `cursor(Cursor)` 推到 M4：文本输入要 I 形光标，而 M2 的控件用默认光标（MyGO 的按钮也是）；
   - `c.now()`、`c.after(Duration)`、`Tester.advance` 推到 M3：和 display link、惯性滚动一起做；
   - `onSubmit`/`submitted()` 推到 M4：只有单行输入框会“提交”；
   - `c.scale()`、`c.window()`、`App.quit()` 等到第一个用到它们的里程碑再加，M2 的控件和示例都用不到。
5. **勾选标记先用文字 `✓`**：MyGO 用路径描边画勾，j-ui 要到 M6 才有线段图元。
6. **开关不做 140 ms 的滑动动画**：M2 没有按刷新率出帧的机制，M3 有了 display link 再补。
7. **滑块由 `Paint` 按新的节点种类 `SLIDER` 直接画出**轨道、已填充部分和滑块头。flexbox 要到 M3 才支持绝对定位，用不了“子元素叠在轨道上”的写法。
8. **明暗外观的变化**从 `JUIView` 覆写的 `viewDidChangeEffectiveAppearance` 得知。MyGO 是在 `NSApp` 上做 KVO，那需要一个四个参数的回调签名；窗口不单独设外观时，两者效果相同。
9. **Tester 的每次 `click` 都是单击**（连击次数为 1），双击用 `doubleClick`。MyGO 的 Tester 会把两次没有 `Advance` 隔开的 `Click` 当成双击；j-ui 在 M3 之前没有 Tester 时钟，所以分开写。
10. **`jui.platform.mac` 直接用 `jui.ui.Key` 枚举**，不再定义一份平台自己的按键枚举。以后加 Windows、Linux 后端时再拆。
11. **主题配色改用 MyGO 的 `LightTheme`/`DarkTheme`**（数值见 Task 1）。
12. **`local` 的名字是 `String`**，照设计 6.5；MyGO 的 `Local` 可以用任何可比较的值作 key。
13. **增加 `clicks()` 查询**，同 MyGO 的 `Clicks()`。一帧里排队了两次点击时，`clicked()` 只报告一次，`clicks()` 返回 2。
14. **`toggle(c, ref, label)` 是开关加文字**，照设计 6.10。MyGO 的 `Switch` 不带文字。
15. **焦点环**是元素外扩 3 DIP、圆角各加 3、宽 2 DIP 的描边，颜色 `theme.focusRing()`，画在子元素之后（同 MyGO 的 `paint.go`）。复选框、开关、滑块把焦点环画在框、轨道、滑块头上，不画在整行上。

## Review Focus

测试没有直接覆盖、但最容易让使用者踩坑的 5 种情况，以及大多数人会期望的行为：

1. **点击删除了指针下面的元素，下一行滑到指针下**（待办清单里点“删除”）：下一行不能因此收到点击，但要立刻显示悬停（提交后按新布局重新计算悬停）。测试放在 Task 3。
2. **在 A 上按下，拖到 B 上松开**：A 和 B 都不报告点击；按住期间 B 不亮，A 的 `pressed()` 在指针离开后为假。测试放在 Task 3。
3. **获得焦点的元素被删除了，然后按 Tab**：焦点落到第一个可聚焦元素，不出错。另外一个新元素不会因为碰巧用到同一个身份而“继承”焦点：待办按 id 作 key，新待办的身份不同。测试放在 Task 5。
4. **一帧之内排队了两次点击**（点得很快）：`clicked()` 这一遍报告一次，`clicks()` 返回 2，下一遍都清零。测试放在 Task 3。
5. **按下期间元素变成禁用，再松开**：不产生点击，也不留下按下状态。测试放在 Task 4。

---

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `src/main/java/jui/util/LongMap.java` | 由 `jui.text.LongMap` 迁来，改为公开类，新增 `remove(long)` | 1 |
| `src/main/java/jui/ui/{Key,Mod,Shortcut}.java` | 按键、修饰键、快捷键 | 1 |
| `src/main/java/jui/ui/{IntRef,FloatRef,DoubleRef,Ref}.java` | 引用对象（`BoolRef` 已有） | 1 |
| `src/main/java/jui/ui/{Color,Theme}.java` | `Color.mix`；主题加字段，增加暗色主题 | 1 |
| `src/main/java/jui/ui/engine/{ElementState,StateTable}.java` | 元素状态、按身份查找、回收和空闲列表 | 2 |
| `src/main/java/jui/ui/engine/HitList.java` | 提交出来的命中测试列表：身份、方框、父节点、标志位；命中链 | 3 |
| `src/main/java/jui/ui/engine/Input.java` | 指针和键盘的处理：悬停链、按下、焦点、按键队列、快捷键路由 | 3、5、6 |
| `src/main/java/jui/ui/engine/{Engine,Node,Paint}.java` | 多遍构建、回调、提交、回收；节点的新字段；绘制变体色、透明度、焦点环、滑块 | 2–8 |
| `src/main/java/jui/ui/{Element,Context,Ui,Handle}.java` | 公开 API：查询、行为、回调、控件、局部状态、焦点句柄 | 2–7 |
| `src/main/java/jui/ui/Tester.java` | 指针、按键、焦点查询、明暗切换 | 3、5、6、8 |
| `src/main/java/jui/platform/mac/{MacWindow,MacKeys}.java` | 鼠标、键盘、修饰键、窗口焦点、外观事件；按键映射 | 9 |
| `src/main/java/jui/ffi/{Sigs,ObjC}.java` | NSPoint 返回值、`convertPoint:fromView:`、`NSTrackingArea` 的初始化 | 9 |
| `src/main/java/jui/app/Window.java` | 把平台事件交给引擎，跟随外观，`update(Runnable)`、`close()` | 8、9 |
| `src/main/java/jui/examples/{CounterDemo,TodoDemo}.java` | 验收示例 | 10 |
| `src/main/java/jui/spike/AllocSpike.java` | 新增控件场景的分配实测 | 11 |
| `build.sh` | `demo`/`native-demo` 接受示例名 | 10 |

---

### Task 0：分支

- [ ] **Step 1**：从 `main` 建分支 `m2-interaction`，在分支上完成全部任务：`git checkout -b m2-interaction`。
- [ ] **Step 2**：在分支上提交本计划：`git add docs/plans/2026-10-25-m2-interaction.md`，提交信息 `docs: plan M2 (interaction)`。

---

### Task 1：值类型、引用对象、主题

**Files:**
- Create: `src/main/java/jui/util/LongMap.java`（从 `jui/text/LongMap.java` 移来，删除原文件，`TextEngine`、`GlyphAtlas` 改 import）
- Create: `src/main/java/jui/ui/{Key,Mod,Shortcut,IntRef,FloatRef,DoubleRef,Ref}.java`
- Modify: `src/main/java/jui/ui/{Color,Theme}.java`
- Test: `src/test/java/jui/util/LongMapTest.java`（从 `jui/text` 移来），`src/test/java/jui/ui/ValuesTest.java`

**Interfaces:**
- **Produces:**
  - `public final class LongMap<V>`：`V get(long)`、`void put(long, V)`、`V remove(long)`、`int size()`、`void clear()`。`remove` 用线性探测的回移删除，不留墓碑。
  - `public enum Key`：
    - 字母 `A`–`Z`、数字 `DIGIT0`–`DIGIT9`；
    - `ENTER, TAB, SPACE, BACKSPACE, ESCAPE, DELETE, INSERT, HOME, END, PAGE_UP, PAGE_DOWN, LEFT, RIGHT, UP, DOWN`；
    - `F1`–`F12`；
    - `MINUS, EQUAL, COMMA, PERIOD, SLASH, SEMICOLON, QUOTE, LEFT_BRACKET, RIGHT_BRACKET, BACKSLASH, BACKQUOTE`；
    - `UNKNOWN`。
  - `public final class Mod`：`SHIFT = 1, CTRL = 2, ALT = 4, CMD = 8`（同 MyGO 的位值；`CMD` 即 macOS 的 Command）。
  - `public record Shortcut(int mods, Key key)`：
    - 工厂方法：`static Shortcut of(Key key)`（不带修饰键）、`static Shortcut of(int mods, Key key)`、`static Shortcut cmd(Key key)`；
    - `boolean matches(int mods, Key key)`：修饰键完全相等才匹配。
  - `IntRef`、`FloatRef`、`DoubleRef`、`Ref<T>`：照 `BoolRef` 的写法，提供 `get()`/`set(v)`。
  - `Color mix(Color other, float t)`：sRGB 分量（含 alpha）的线性插值，`t` 先夹到 0–1。
  - `Theme`：
    - 字段，按顺序：`boolean dark, Color background, surface, surfaceHover, surfacePressed, border, text, textMuted, accent, accentHover, accentPressed, accentText, selection, focusRing, float fontSize, float radius`；
    - 工厂方法：`static Theme light()`、`static Theme dark()`、`static Theme of(boolean dark)`。

**主题数值**（MyGO `ui/theme.go:106-155`；`fontSize` 13，`radius` 6）：

| 字段 | light | dark |
|---|---|---|
| background | `#ffffff` | `#18181b` |
| surface | `#f4f4f5` | `#27272a` |
| surfaceHover | `#e9e9ec` | `#323236` |
| surfacePressed | `#dddde1` | `#3c3c41` |
| border | `#d9d9de` | `#3f3f46` |
| text | `#18181b` | `#f4f4f5` |
| textMuted | `#71717a` | `#a1a1aa` |
| accent | `#2563eb` | `#3b82f6` |
| accentHover | `#1d4ed8` | `#60a5fa` |
| accentPressed | `#1e40af` | `#2563eb` |
| accentText | `#ffffff` | `#ffffff` |
| selection | `Color.rgba(0x2563EB, 0.25f)` | `Color.rgba(0x3B82F6, 0.4f)` |
| focusRing | `Color.rgba(0x2563EB, 0.55f)` | `Color.rgba(0x60A5FA, 0.6f)` |

- [ ] **Step 1：写失败的测试**

```java
// LongMapTest（新增）
public void testRemoveKeepsTheOtherKeysFindable() {
    LongMap<String> m = new LongMap<>();
    for (long k = 0; k < 100; k++) m.put(k * 16, "v" + k);   // same low bits: long probe chains
    Check.equal("v50", m.remove(50 * 16));
    Check.equal(null, m.get(50 * 16));
    for (long k = 0; k < 100; k++) if (k != 50) Check.equal("v" + k, m.get(k * 16));
    Check.equal(99, m.size());
    Check.equal(null, m.remove(12345));
}

// ValuesTest（新增）
public void testShortcutMatchesExactModifiers() {
    Shortcut s = Shortcut.cmd(Key.P);
    Check.isTrue(s.matches(Mod.CMD, Key.P), "cmd-P");
    Check.isTrue(!s.matches(Mod.CMD | Mod.SHIFT, Key.P), "extra shift does not match");
    Check.isTrue(!s.matches(0, Key.P), "no cmd does not match");
    Check.equal(new Shortcut(0, Key.UP), Shortcut.of(Key.UP));
}

public void testColorMix() {
    Check.equal(Color.rgb(0x808080).rgba(), Color.rgb(0x000000).mix(Color.rgb(0xFFFFFF), 0.5f).rgba() & 0xFEFEFEFF);
    Check.equal(Color.rgb(0x123456), Color.rgb(0x123456).mix(Color.rgb(0xFFFFFF), 0));
    Check.equal(Color.rgb(0xFFFFFF), Color.rgb(0x123456).mix(Color.rgb(0xFFFFFF), 7));   // t is clamped
}

public void testThemesFollowMyGo() {
    Check.isTrue(!Theme.light().dark() && Theme.dark().dark(), "dark flag");
    Check.equal(Color.rgb(0x2563EB), Theme.light().accent());
    Check.equal(Color.rgb(0x18181B), Theme.dark().background());
    Check.equal(Theme.dark(), Theme.of(true));
}

public void testRefsHoldTheirValue() {
    DoubleRef d = new DoubleRef(1.5); d.set(2.5); Check.near(2.5, d.get());
    Ref<String> r = new Ref<>("a"); r.set("b"); Check.equal("b", r.get());
}
```

- [ ] **Step 2**：运行 `./build.sh test`。Expected：编译失败（`Key`、`Shortcut`、`LongMap.remove` 等不存在）。
- [ ] **Step 3**：实现上面 Produces 里的类型；移动 `LongMap` 并更新它的使用者。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过（M1 的 58 个加上新增的）。
- [ ] **Step 5**：提交 `feat(ui): keys, shortcuts, refs and MyGo's light and dark themes`。

---

### Task 2：元素状态表与局部状态

**Files:**
- Create: `src/main/java/jui/ui/engine/{ElementState,StateTable}.java`
- Modify: `Engine.java`、`Node.java`、`src/main/java/jui/ui/{Element,Ui}.java`
- Test: `src/test/java/jui/ui/StateTest.java`

**Interfaces:**
- **Consumes:** `LongMap`（Task 1）。
- **Produces:**
  - **`ElementState`**（`jui.ui.engine`）：
    - 字段：`long id`；`long born`（创建它的帧号）；`long seenFrame`、`int seenPass`；
    - 上一帧的方框 `float x, y, w, h` 和内容框 `cx, cy, cw, ch`；
    - 输入：`int clicks, rightClicks, doubleClicks`、`boolean pressed, pressPending, changed`、`float dragX, dragY`；
    - 按键：`Key[] keys` 加 `int keyCount`，保存给滑块的方向键；
    - 局部状态：`java.util.HashMap<String, Object> locals`，第一次用时才创建；
    - `void reset()`。
  - **`StateTable`**：
    - `ElementState get(long id)`：不存在就返回 null；
    - `ElementState create(long id, long frame)`：优先从空闲列表取，空闲列表最多保留 256 个；
    - `void prune(long frame, int lastPass)`：回收 `(seenFrame, seenPass)` 不等于 `(frame, lastPass)` 的状态；
    - `int size()`。
  - **`Node.state`**（`public ElementState`）：`Engine.open` 按身份查一次状态表。查到就挂到节点上，并记为本遍已见；查不到就留 null。
  - **`Engine.state(int index)`**：取节点的状态，没有就创建，记为本遍已见。
  - **`Engine` 的帧号**：`long frame()`，每次 `buildAndLayout` 开始时加 1。
  - **`Engine.commit()`**：在 `buildAndLayout` 里布局之后调用。把每个有状态的节点的方框和内容框写进状态，然后 `prune`。
  - **`Element.bounds()`**：返回上一帧的 `Rect`，第一次出现时是 `(0, 0, 0, 0)`。
  - **局部状态**（`Ui` 里的静态方法）：
    - `static <T> Ref<T> local(Element e, String name, Supplier<T> initial)`；
    - `static BoolRef localBool(Element e, String name, boolean initial)`；
    - `static FloatRef localFloat(Element e, String name, float initial)`。

    同名的局部状态类型不符（比如先 `localBool` 后 `local`）时，新建一个替换旧的，同 MyGO。

- [ ] **Step 1：写失败的测试**

```java
public final class StateTest {
    public void testLocalStateSurvivesFrames() {
        int[] created = {0};
        Object[] seen = new Object[3];
        int[] frame = {0};
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(10, 10);
            seen[frame[0]++] = local(e, "n", () -> { created[0]++; return new int[1]; }).get();
        }, 100, 100);
        tt.frame(); tt.frame(); tt.frame();
        Check.equal(1, created[0]);
        Check.isTrue(seen[0] == seen[2], "the same value, frame after frame");
    }

    public void testLocalStateGoesWithItsElement() {
        int[] created = {0};
        boolean[] show = {true};
        Tester tt = Tester.of(c -> {
            if (show[0]) local(box(c).size(10, 10), "n", () -> created[0]++);
        }, 100, 100);
        tt.frame();
        show[0] = false; tt.frame();      // absent: its state is pruned
        show[0] = true; tt.frame();
        Check.equal(2, created[0]);
    }

    public void testNamesKeepLocalsApart() {
        boolean[] got = new boolean[2];
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(10, 10);
            got[0] = localBool(e, "a", true).get();
            got[1] = localBool(e, "b", false).get();
        }, 100, 100);
        tt.frame();
        Check.isTrue(got[0] && !got[1], "a and b are separate");
    }

    public void testLocalFollowsTheKey() {
        java.util.List<String> order = new java.util.ArrayList<>(java.util.List.of("x", "y"));
        java.util.Map<String, Float> got = new java.util.HashMap<>();
        Tester tt = Tester.of(c -> {
            for (String k : order) {
                FloatRef f = localFloat(box(c.key(k)).size(10, 10), "v", k.equals("x") ? 1 : 2);
                got.put(k, f.get());
            }
        }, 100, 100);
        tt.frame();
        java.util.Collections.reverse(order); tt.frame();
        Check.near(1, got.get("x")); Check.near(2, got.get("y"));
    }

    public void testBoundsAreTheLastFramesBox() {
        Rect[] r = new Rect[1];
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> r[0] = box(c).size(100, 40).bounds()), 300, 200);
        tt.frame();
        Check.equal(new Rect(0, 0, 0, 0), r[0]);
        tt.frame();
        Check.equal(new Rect(10, 10, 100, 40), r[0]);
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test StateTest`。Expected：编译失败（`local`、`bounds` 不存在）。
- [ ] **Step 3**：实现 Produces 里的内容。`Engine.state(int)` 只在查询和局部状态里调用；普通元素不创建状态，内容不变的帧里也不分配。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): element state by identity, with local state`。

---

### Task 3：命中测试、指针、查询与重建规则

**Files:**
- Create: `src/main/java/jui/ui/engine/{HitList,Input}.java`
- Modify: `Engine.java`、`Node.java`、`Element.java`、`Tester.java`
- Test: `src/test/java/jui/ui/PointerTest.java`

**Interfaces:**
- **Consumes:** `StateTable`、`Engine.state`、`commit`（Task 2）。
- **Produces:**
  - **`Node.flags`**（`public int`），常量在 `Node` 里：`CLICKABLE = 1, FOCUSABLE = 2, HOVER = 4, DRAG = 8, TOGGLE = 16, KEYS = 32`。查询会顺带设置标志：
    - `clicked`/`clicks`/`doubleClicked`/`rightClicked` → `CLICKABLE`；
    - `pressed` → `CLICKABLE | HOVER`；
    - `hovered` → `HOVER`；
    - `dragX`/`dragY` → `DRAG`。
  - **`HitList`**：提交时按节点池的顺序（M2 没有绝对定位，节点池顺序就是绘制顺序）记录每个节点的身份、方框、父节点下标和标志位。数组只增不减。
    - **命中链**：从末尾往前找第一个包含该点的元素，再接上它的祖先，最里层在前。
    - **按下目标**：命中链里最里层带 `CLICKABLE | FOCUSABLE | DRAG` 的元素。
  - **`Engine` 的指针入口**（主线程，帧与帧之间调用）：
    - `void pointerMove(float x, float y, int mods)`
    - `void pointerDown(float x, float y, int button, int clickCount, int mods)`：`button` 取 0（主键）或 1（副键）
    - `void pointerUp(float x, float y, int button, int mods)`
    - `void pointerLeave()`
  - **规则**（同 MyGO 的 `ui/input.go`）：
    - **按下时**：目标的状态设 `pressed = pressPending = true`，命中链冻结。
      - 连击次数按元素重新计数：目标和上一次按下的不同，就从 1 数起。
      - 计数正好为 2 时 `doubleClicks++`。
    - **移动时**：按住期间累加目标的 `dragX/dragY`；没按住时重算命中链。
    - **松开时**：指针仍在目标的方框里、且目标没禁用，主键 `clicks++`、副键 `rightClicks++`。然后清掉按下状态，重算命中链。
    - **请求新帧**：
      - 按下和松开都请求新的一帧；
      - 移动时，新旧命中链里有带 `HOVER` 的元素，或者正在拖动带 `DRAG` 的元素，才请求。
  - **`Element` 的查询**（只在构建期间有效；返回真时把本帧标记为“消耗了输入”）：
    - `boolean hovered()`：元素在命中链里，且没禁用。
      - 按住期间，被按下的元素一直算悬停；冻结链里的其他元素，要指针还在它里面才算。
    - `boolean pressed()`：被按下、指针在它里面、且没禁用。读到 `pressPending` 时标记消耗，于是绘制前多建一遍。
    - `boolean clicked()`、`int clicks()`、`boolean doubleClicked()`、`boolean rightClicked()`。
    - `float dragX()`、`float dragY()`：按住时指针自上一遍以来的移动量。不为 0 时也标记消耗，同 MyGO。
  - **重建规则**：`buildAndLayout` 最多构建 3 遍。
    - 每一遍结束时，清掉这一遍里见过的状态的 `clicks`、`rightClicks`、`doubleClicks`、`pressPending`、`dragX/Y`、`changed`。
    - 这一遍没有消耗输入就停。
    - 第 3 遍仍然消耗了输入，就再请求一帧。
    - `int passes()` 返回上一帧构建的遍数，给测试和以后的帧统计用。
  - **提交之后**：用当前指针位置按新布局重算命中链（同 MyGO 的 `rehover`）。命中链变了、并且涉及带 `HOVER` 的元素，就请求一帧。
  - **`Tester` 的指针方法**：
    - `click(String s)`：先移到元素中心，再主键按下（连击 1）、松开；
    - `doubleClick(String s)`：连击 1 和 2 各按下、松开一次；
    - `rightClick(String s)`；
    - `hover(String s)`；
    - `press(float x, float y)`、`move(float x, float y)`、`release(float x, float y)`。

    每次输入后连续出帧，直到不再请求新帧，最多 20 帧（同 MyGO 的 `send`）。还没出过帧时，先出一帧，这样才有命中测试列表。

- [ ] **Step 1：写失败的测试**

```java
public final class PointerTest {
    int aClicks, aDoubles, aRights, bClicks;
    boolean aHovered, aPressed, bHovered;
    float dragX, dragY;

    final View view = c -> column(c).padding(10).gap(10).children(() -> {
        Element a = box(c).size(100, 40).label("a");
        if (a.clicked()) aClicks++;
        if (a.doubleClicked()) aDoubles++;
        if (a.rightClicked()) aRights++;
        aHovered = a.hovered();
        aPressed = a.pressed();
        dragX += a.dragX(); dragY += a.dragY();   // summed: a drag is consumed, so the next pass reads 0
        Element b = box(c).size(100, 40).label("b");
        if (b.clicked()) bClicks++;
        bHovered = b.hovered();
    });

    public void testClickIsReportedOnce() {
        Tester tt = Tester.of(view, 300, 200);
        tt.click("a");
        tt.frame();
        Check.equal(1, aClicks);
        Check.equal(0, bClicks);
    }

    public void testAClickRebuildsOnceMore() {
        Engine e = new Engine(null, Theme.light(), () -> {});
        e.buildAndLayout(view, 300, 200);
        Check.equal(1, e.passes());
        e.pointerDown(60, 30, 0, 1, 0);
        e.pointerUp(60, 30, 0, 0);
        e.buildAndLayout(view, 300, 200);
        Check.equal(2, e.passes());   // clicked() was true in pass 1, so pass 2 shows its result
        Check.equal(1, aClicks);
    }

    public void testDoubleClickAlsoClicks() {
        Tester tt = Tester.of(view, 300, 200);
        tt.doubleClick("a");
        Check.equal(1, aDoubles);
        Check.equal(2, aClicks);
    }

    public void testRightClickIsNotAClick() {
        Tester tt = Tester.of(view, 300, 200);
        tt.rightClick("a");
        Check.equal(1, aRights);
        Check.equal(0, aClicks);
    }

    public void testHoverFollowsThePointer() {
        Tester tt = Tester.of(view, 300, 200);
        tt.hover("a");
        Check.isTrue(aHovered && !bHovered, "a hovered");
        tt.move(250, 150);
        Check.isTrue(!aHovered, "off a");
        tt.hover("a");
        tt.engine().pointerLeave(); tt.frame();
        Check.isTrue(!aHovered, "left the window");
    }

    public void testPressThenDragOffCancelsTheClick() { // Review Focus 2
        Tester tt = Tester.of(view, 300, 200);
        tt.press(60, 30);
        Check.isTrue(aPressed, "pressed while inside");
        tt.move(60, 80);                       // over b
        Check.isTrue(!aPressed && aHovered && !bHovered, "a keeps the hover; b does not light up");
        tt.release(60, 80);
        Check.equal(0, aClicks); Check.equal(0, bClicks);
    }

    public void testDragReportsTheMovement() {
        Tester tt = Tester.of(view, 300, 200);
        tt.press(60, 30);
        tt.move(75, 35);
        Check.near(15, dragX); Check.near(5, dragY);
        tt.frame();
        Check.near(15, dragX);                 // reported once: nothing more in a later frame
    }

    public void testTheInnermostReceiverTakesThePress() {
        int[] outer = {0}, inner = {0};
        Tester tt = Tester.of(c -> {
            Element o = box(c).size(200, 100).padding(20).label("outer");
            o.children(() -> { if (box(c).size(50, 30).label("inner").clicked()) inner[0]++; });
            if (o.clicked()) outer[0]++;
        }, 300, 200);
        tt.click("inner");
        Check.equal(1, inner[0]); Check.equal(0, outer[0]);
        tt.press(150, 80); tt.release(150, 80);
        Check.equal(1, outer[0]);
    }

    public void testTextInsideAClickableBoxClicksTheBox() {
        int[] n = {0};
        Tester tt = Tester.of(c -> {
            Element b = box(c).padding(8);
            b.children(() -> text(c, "保存"));
            if (b.clicked()) n[0]++;
        }, 300, 200);
        tt.click("保存");
        Check.equal(1, n[0]);
    }

    public void testTheRowSlidingUnderThePointerIsNotClicked() { // Review Focus 1
        java.util.List<String> rows = new java.util.ArrayList<>(java.util.List.of("r1", "r2", "r3"));
        java.util.List<String> clicked = new java.util.ArrayList<>();
        boolean[] r2Hovered = {false};
        Tester tt = Tester.of(c -> column(c).children(() -> {
            for (String r : java.util.List.copyOf(rows)) {
                Element e = box(c.key(r)).size(100, 30).label(r);
                if (e.clicked()) { clicked.add(r); rows.remove(r); }
                if (r.equals("r2")) r2Hovered[0] = e.hovered();
            }
        }), 300, 200);
        tt.click("r1");
        Check.equal(java.util.List.of("r1"), clicked);
        Check.isTrue(r2Hovered[0], "r2 now under the pointer is hovered, not clicked");
    }

    public void testTwoClicksBeforeAFrameCountTwo() { // Review Focus 4
        int[] counts = new int[2];
        Engine e = new Engine(null, Theme.light(), () -> {});
        View v = c -> { Element a = box(c).size(100, 40); counts[0] += a.clicked() ? 1 : 0; counts[1] += a.clicks(); };
        e.buildAndLayout(v, 300, 200);
        e.pointerDown(50, 20, 0, 1, 0); e.pointerUp(50, 20, 0, 0);
        e.pointerDown(50, 20, 0, 1, 0); e.pointerUp(50, 20, 0, 0);
        e.buildAndLayout(v, 300, 200);
        Check.equal(1, counts[0]);   // clicked() once in pass 1, nothing left for pass 2
        Check.equal(2, counts[1]);
    }
}
```

`Tester.engine()` 是给测试用的访问方法，返回内部的 `Engine`。

- [ ] **Step 2**：运行 `./build.sh test PointerTest`。Expected：编译失败（查询方法和 Tester 的指针方法不存在）。
- [ ] **Step 3**：实现 Produces 里的内容。构建多遍的写法：每一遍重置节点池、调用视图，然后在“遍末”清除这一遍见过的状态的输入。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): pointer input, hit testing and the rebuild rule`。

---

### Task 4：回调、禁用、透明度

**Files:**
- Modify: `Engine.java`、`Node.java`、`Paint.java`、`Element.java`
- Test: `src/test/java/jui/ui/CallbackTest.java`、`src/test/java/jui/ui/engine/PaintTest.java`

**Interfaces:**
- **Consumes:** `clicked()`、多遍构建（Task 3）。
- **Produces:**
  - **`Element onClick(Runnable action)`**：
    - 登记时如果 `clicked()` 为真，就把（节点下标, action）放进引擎的动作列表。列表用复用的数组。
    - 这一遍的视图建完后按登记顺序执行，执行前再确认节点没被禁用。
    - 执行了任何动作，就标记本遍消耗了输入。
    - 同一次点击，`clicked()` 查询和 `onClick` 都会触发，且都只触发一次（同 MyGO 的 `frame_test.go:51`）。
  - **`Element disabled(boolean d)`**：`node.disabled = parent.disabled || d`。`Engine.open` 让子节点继承父节点的 `disabled`。禁用的元素：
    - 不报告点击、悬停、按下；
    - 松开时不产生点击；
    - 不进 Tab 顺序（Task 5）；
    - 快捷键查询返回假（Task 6）；
    - 绘制时透明度乘 0.5。
  - **`Element opacity(float o)`**：0–1。`Paint` 按祖先连乘出每个节点的透明度，乘到阴影、背景、边框和文字颜色的 alpha 上。禁用的子树在最外层的禁用节点上乘一次 0.5，不重复乘。

- [ ] **Step 1：写失败的测试**

```java
public final class CallbackTest {
    public void testOnClickRunsAfterTheBuildAndMayChangeTheList() {
        java.util.List<String> items = new java.util.ArrayList<>(java.util.List.of("a", "b", "c"));
        Tester tt = Tester.of(c -> column(c).children(() -> {
            for (String s : items) {                        // removing inside the loop would throw
                box(c.key(s)).padding(4).onClick(() -> items.remove(s)).children(() -> text(c, s));
            }
        }), 300, 200);
        tt.click("b");
        Check.equal(java.util.List.of("a", "c"), tt.texts());
    }

    public void testQueryAndCallbackBothFireOnce() {
        int[] q = {0}, cb = {0};
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(80, 30).label("x").onClick(() -> cb[0]++);
            if (e.clicked()) q[0]++;
        }, 300, 200);
        tt.click("x");
        tt.frame();
        Check.equal(1, q[0]); Check.equal(1, cb[0]);
    }

    public void testDisabledIsNotClickedHoveredOrPressed() {
        boolean[] seen = new boolean[3];
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(80, 30).label("x").disabled(true);
            seen[0] |= e.clicked(); seen[1] |= e.hovered(); seen[2] |= e.pressed();
        }, 300, 200);
        tt.click("x");
        Check.isTrue(!seen[0] && !seen[1] && !seen[2], "nothing reported");
    }

    public void testDisabledIsInherited() {
        int[] n = {0};
        Tester tt = Tester.of(c -> column(c).disabled(true).children(() -> {
            if (box(c).size(80, 30).label("x").clicked()) n[0]++;
        }), 300, 200);
        tt.click("x");
        Check.equal(0, n[0]);
    }

    public void testDisablingDuringAPressCancelsTheClick() { // Review Focus 5
        int[] n = {0};
        boolean[] off = {false};
        Tester tt = Tester.of(c -> {
            Element e = box(c).size(80, 30).label("x").disabled(off[0]);
            if (e.clicked()) n[0]++;
        }, 300, 200);
        Rect r = tt.find("x");
        tt.press(r.x() + 5, r.y() + 5);
        off[0] = true; tt.frame();
        tt.release(r.x() + 5, r.y() + 5);
        off[0] = false; tt.frame();
        Check.equal(0, n[0]);
    }
}

// PaintTest（新增；e 和 paint(scale) 是 PaintTest 已有的字段和辅助方法）
public void testDisabledPaintsAtHalfOpacity() {
    e.buildAndLayout(c -> box(c).size(50, 50).background(Color.rgb(0xFF0000)).disabled(true), 200, 100);
    Check.near(0.5, paint(1).instance(0)[11]);
}

public void testOpacityMultipliesDownTheTree() {
    e.buildAndLayout(c -> box(c).opacity(0.5f).children(() ->
            box(c).size(50, 50).opacity(0.8f).background(Color.rgb(0xFF0000))), 200, 100);
    Check.near(0.4, paint(1).instance(0)[11], 1e-3);
}
```

- [ ] **Step 2**：运行 `./build.sh test CallbackTest` 和 `./build.sh test PaintTest`。Expected：编译失败。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): click callbacks, disabled elements and opacity`。

---

### Task 5：焦点、Tab、激活键、焦点句柄、焦点环

**Files:**
- Create: `src/main/java/jui/ui/Handle.java`
- Modify: `Input.java`、`Engine.java`、`Paint.java`、`Element.java`、`Tester.java`
- Test: `src/test/java/jui/ui/FocusTest.java`、`PaintTest.java`

**Interfaces:**
- **Consumes:** 命中链、按下目标（Task 3），禁用（Task 4）。
- **Produces:**
  - **引擎的焦点**：`long focusedId`（0 表示没有）、`boolean focusVisible`、`boolean windowFocused`（默认真）。
    - **焦点顺序**：提交时按节点池顺序记录带 `FOCUSABLE` 且没禁用的身份。
    - **焦点链**：提交时记录获得焦点的元素及其祖先的身份。
  - **`Element` 的行为方法**：
    - `focusable()`：设 `FOCUSABLE`；
    - `autoFocus()`：元素的状态在本帧才创建（`born == frame`）时请求焦点；
    - `bind(Handle h)`；
    - `focusRing(boolean show)`：默认显示。
  - **`Element` 的查询**：
    - `focused()`：`focusedId == id && windowFocused`；
    - `focusVisible()`：`focused() && focusVisible`；
    - `focusWithin()`：身份在上一帧的焦点链里。
  - **点击聚焦**（主键按下时）：焦点给命中链里最里层带 `FOCUSABLE` 且没禁用的元素；没有就清空焦点。焦点因此变化时 `focusVisible = false`。
  - **`Engine.keyDown(Key key, int mods, boolean repeat)`** 当场处理两种键：
    - **`TAB`**：不带修饰键向后，带 `SHIFT` 向前。
      - 按焦点顺序循环；当前没有焦点（或焦点的身份已不在顺序里）时，向后到第一个、向前到最后一个。
      - 顺序为空时什么都不做。
      - 设 `focusVisible = true`，请求新帧。
    - **`ENTER`/`SPACE`**：不带修饰键、获得焦点的元素带 `CLICKABLE` 且没禁用时，该元素 `clicks++`，`focusVisible = true`。
      - 例外：元素带 `TOGGLE` 时，`ENTER` 只在窗口没有登记不带修饰键的 `ENTER` 快捷键时才激活。这条依赖 Task 6 的登记，先写成“永远激活”，在 Task 6 补上。
    - 其他键交给 Task 6 的队列。
  - **`Engine.windowFocusChanged(boolean focused)`**：更新 `windowFocused`，请求新帧。
  - **`Handle`**（`jui.ui`）：`public Handle()`、`void focus()`、`boolean focused()`。
    - `bind` 每遍把句柄绑到元素的身份上。
    - `focus()` 留下请求；某一遍里绑定的元素出现且没禁用时，在这一遍的视图建完后生效，同 MyGO 的 `ref.go:180`。
    - 焦点因请求而变化时标记消耗。
  - **焦点环**：`Paint` 在子元素之后画。
    - 获得焦点的节点在 `focusVisible` 且没关掉焦点环时画；带 `Node.ring = true` 的节点也画（控件用它把焦点环画在框或轨道上）。
    - 形状：`scene.box(x-3, y-3, w+6, h+6, 各角半径+3, 透明填充, 2, theme.focusRing())`。
  - **`Tester`**：`focused(String s)`，在获得焦点的元素就是 `s` 所指的元素、或者是它的祖先时返回真。

- [ ] **Step 1：写失败的测试**

```java
public final class FocusTest {
    static final Shortcut TAB = Shortcut.of(Key.TAB), BACK = Shortcut.of(Mod.SHIFT, Key.TAB);
    boolean bDisabled;
    final View three = c -> column(c).padding(10).gap(10).children(() -> {
        box(c).size(80, 30).label("a").focusable();
        box(c).size(80, 30).label("b").focusable().disabled(bDisabled);
        box(c).size(80, 30).label("c").focusable();
    });

    public void testClickFocusesAndClickingNothingClears() {
        Tester tt = Tester.of(three, 300, 200);
        tt.click("b");
        Check.isTrue(tt.focused("b"), "b focused");
        tt.press(250, 190); tt.release(250, 190);
        Check.isTrue(!tt.focused("b"), "a click on nothing clears focus");
    }

    public void testTabFollowsBuildOrderAndWraps() {
        Tester tt = Tester.of(three, 300, 200);
        tt.key(TAB); Check.isTrue(tt.focused("a"), "a");
        tt.key(TAB); Check.isTrue(tt.focused("b"), "b");
        tt.key(TAB); Check.isTrue(tt.focused("c"), "c");
        tt.key(TAB); Check.isTrue(tt.focused("a"), "wraps to a");
        tt.key(BACK); Check.isTrue(tt.focused("c"), "shift-tab wraps back to c");
    }

    public void testTabSkipsDisabled() {
        bDisabled = true;
        Tester tt = Tester.of(three, 300, 200);
        tt.key(TAB); tt.key(TAB);
        Check.isTrue(tt.focused("c"), "b skipped");
    }

    public void testTabWithNothingFocusableDoesNothing() {
        Tester tt = Tester.of(c -> text(c, "x"), 100, 100);
        tt.key(TAB);
        Check.isTrue(!tt.focused("x"), "no focus");
    }

    public void testFocusRingShowsOnlyAfterTheKeyboard() {
        boolean[] visible = new boolean[2];
        Tester tt = Tester.of(c -> column(c).padding(10).gap(10).children(() -> {
            visible[0] = box(c).size(80, 30).label("a").focusable().focusVisible();
            visible[1] = box(c).size(80, 30).label("b").focusable().focusVisible();
        }), 300, 200);
        tt.click("a");  Check.isTrue(!visible[0], "not after a click");
        tt.key(TAB);    Check.isTrue(visible[1], "after tab, on b");
        tt.click("b");  Check.isTrue(visible[1], "a click on the focused element changes no focus: the ring stays");
        tt.click("a");  Check.isTrue(!visible[0] && !visible[1], "a click that moves focus hides it");
    }

    public void testEnterAndSpaceClickTheFocusedElement() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (box(c).size(80, 30).label("a").focusable().clicked()) n[0]++; }, 300, 200);
        tt.key(TAB);
        tt.key(Shortcut.of(Key.ENTER)); tt.key(Shortcut.of(Key.SPACE));
        tt.key(Shortcut.of(Mod.SHIFT, Key.SPACE));
        Check.equal(2, n[0]);
    }

    public void testAutoFocusOnlyWhenItAppears() {
        boolean[] show = {false};
        Tester tt = Tester.of(c -> column(c).children(() -> {
            box(c).size(80, 30).label("a").focusable();
            if (show[0]) box(c).size(80, 30).label("dialog").focusable().autoFocus();
        }), 300, 200);
        tt.frame();
        show[0] = true; tt.frame();
        Check.isTrue(tt.focused("dialog"), "focused as it appears");
        tt.click("a"); tt.frame();
        Check.isTrue(tt.focused("a"), "autoFocus does not take focus back later");
    }

    public void testHandleFocusesItsElementOnceItIsBuilt() {
        Handle h = new Handle();
        boolean[] show = {false};
        Tester tt = Tester.of(c -> column(c).children(() -> {
            box(c).size(80, 30).label("go").onClick(h::focus);
            if (show[0]) box(c).size(80, 30).label("target").focusable().bind(h);
        }), 300, 200);
        tt.click("go");                       // the target is absent: the request waits
        show[0] = true; tt.frame();
        Check.isTrue(tt.focused("target") && h.focused(), "focused once it appears");
    }

    public void testPassesStopAtThree() {
        Handle h1 = new Handle(), h2 = new Handle();
        boolean[] flip = {false};
        Tester tt = Tester.of(c -> {
            box(c).size(10, 10).focusable().bind(h1);
            box(c).size(10, 10).focusable().bind(h2);
            if (flip[0] = !flip[0]) h1.focus(); else h2.focus();   // focus changes every pass
        }, 100, 100);
        tt.frame();
        Check.equal(3, tt.engine().passes());
        Check.isTrue(tt.framePending(), "still consuming after 3 passes: another frame");
    }

    public void testFocusWithin() {
        boolean[] within = {false};
        Tester tt = Tester.of(c -> {
            Element r = row(c).padding(10);
            r.children(() -> box(c).size(80, 30).label("b").focusable());
            within[0] = r.focusWithin();
        }, 300, 200);
        tt.click("b"); tt.frame();
        Check.isTrue(within[0], "the row has focus within");
    }

    public void testRemovedFocusThenTabGoesToTheFirst() { // Review Focus 3
        java.util.List<String> ids = new java.util.ArrayList<>(java.util.List.of("a", "b"));
        Tester tt = Tester.of(c -> column(c).children(() -> {
            for (String s : ids) box(c.key(s)).size(80, 30).label(s).focusable();
        }), 300, 200);
        tt.click("b");
        ids.remove("b"); tt.frame();
        ids.add("b2"); tt.frame();
        Check.isTrue(!tt.focused("b2"), "a new element does not inherit the focus");
        tt.key(TAB);
        Check.isTrue(tt.focused("a"), "tab starts over at the first");
    }

    public void testFocusNeedsTheWindowFocused() {
        Tester tt = Tester.of(three, 300, 200);
        tt.click("a");
        tt.engine().windowFocusChanged(false); tt.frame();
        Check.isTrue(!tt.focused("a"), "window inactive");
        tt.engine().windowFocusChanged(true); tt.frame();
        Check.isTrue(tt.focused("a"), "back");
    }
}

// PaintTest（新增）
public void testFocusRingAfterTab() {
    Tester tt = Tester.of(c -> column(c).padding(20).children(() -> box(c).size(80, 30).radius(4).focusable()), 200, 100);
    tt.key(Shortcut.of(Key.TAB));
    float[] ring = tt.scene().instance(tt.instances() - 1);
    Check.near(17, ring[0]); Check.near(17, ring[1]); Check.near(86, ring[2]); Check.near(36, ring[3]);
    Check.near(7, ring[4]);                  // radius 4 + 3
    Check.near(2, ring[16]);                 // stroke
    Check.near(0, ring[11]);                 // no fill
}
```

`Tester.scene()` 是给测试用的访问方法，返回上一帧绘制的 `Scene`。

- [ ] **Step 2**：运行 `./build.sh test FocusTest`。Expected：编译失败。
- [ ] **Step 3**：实现。焦点请求在“这一遍的视图建完之后、执行点击回调之前”生效，同 MyGO：先 `onChange`，再焦点请求，再 `onClick`。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): focus, tab order, activation keys, focus handles and the focus ring`。

---

### Task 6：快捷键

**Files:**
- Modify: `Input.java`、`Engine.java`、`Element.java`、`Context.java`、`Tester.java`
- Test: `src/test/java/jui/ui/ShortcutTest.java`、`FocusTest.java`（补 Task 5 留下的 `TOGGLE` 规则）

**Interfaces:**
- **Consumes:** `Engine.keyDown`、焦点链（Task 5）。
- **Produces:**
  - **快捷键登记**：每一遍构建开始时清空。
    - `Element.shortcut(s)` 登记 `(元素身份, mods, key)`，`c.shortcut(s)` 登记 `(0, mods, key)`。
    - 只保留最后一遍的登记，提交时成为“上一帧的登记”。
  - **按键队列**：`keyDown` 没有当场处理的键进队列，并请求新帧。
  - **路由**：每帧开始时取出队首一个键（细节第 2 条）：
    1. 按上一帧的焦点链，从最里层往外，找第一个登记了它的元素；
    2. 找不到就给窗口（身份 0）；
    3. 都没登记，就留作“未路由”：第 1 遍里第一个匹配的 `shortcut` 调用可以拿走它，元素要求焦点在它内部，窗口没有要求（同 MyGO 的重试）。
  - **投递**：
    - 只在第 1 遍有效；
    - 匹配一次后算消耗，标记本遍消耗了输入；
    - 第 1 遍结束时丢弃；
    - 队列里还有键时请求下一帧。
  - **API**：
    - `boolean Element.shortcut(Shortcut s)`：焦点在元素内部、且元素没禁用时才报告；
    - `boolean Context.shortcut(Shortcut s)`；
    - `int Context.modifiers()`：当前的修饰键。
  - **`Engine.modifiersChanged(int mods)`**：只记下修饰键，不请求新帧。指针和按键事件也会带上并更新修饰键。
  - **`Tester.key(Shortcut s)`**：调用 `keyDown(s.key(), s.mods(), false)`，然后连续出帧。
  - **按住重复的键**：照普通按键处理，同 MyGO。

- [ ] **Step 1：写失败的测试**

```java
public final class ShortcutTest {
    static final Shortcut CMD_P = Shortcut.cmd(Key.P), CMD_D = Shortcut.cmd(Key.D), UP = Shortcut.of(Key.UP);

    public void testWindowShortcutMatchesExactModifiers() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(CMD_P)) n[0]++; text(c, "x"); }, 100, 100);
        tt.key(CMD_P);
        tt.key(Shortcut.of(Key.P));
        tt.key(Shortcut.of(Mod.CMD | Mod.SHIFT, Key.P));
        Check.equal(1, n[0]);
    }

    public void testElementShortcutNeedsFocusWithin() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (box(c).size(80, 30).label("a").focusable().shortcut(CMD_D)) n[0]++; }, 300, 200);
        tt.key(CMD_D);
        Check.equal(0, n[0]);
        tt.click("a"); tt.key(CMD_D);
        Check.equal(1, n[0]);
    }

    public void testTheFocusedElementWinsOverTheWindow() {
        int[] el = {0}, win = {0};
        Tester tt = Tester.of(c -> {
            if (c.shortcut(CMD_D)) win[0]++;
            if (box(c).size(80, 30).label("a").focusable().shortcut(CMD_D)) el[0]++;
        }, 300, 200);
        tt.key(CMD_D);
        Check.equal(1, win[0]); Check.equal(0, el[0]);
        tt.click("a"); tt.key(CMD_D);
        Check.equal(1, win[0]); Check.equal(1, el[0]);
    }

    public void testAShortcutIsReportedOnce() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(UP)) n[0]++; text(c, "x"); }, 100, 100);
        tt.key(UP); tt.frame(); tt.frame();
        Check.equal(1, n[0]);
    }

    public void testRepeatedKeysAreNotLost() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(UP)) n[0]++; text(c, "x"); }, 100, 100);
        tt.frame();
        tt.engine().keyDown(Key.UP, 0, false);
        tt.engine().keyDown(Key.UP, 0, true);
        tt.settle();                                   // frames until none is pending
        Check.equal(2, n[0]);
    }

    public void testAShortcutSeesAKeyInTheFrameItFirstAppears() {
        int[] n = {0};
        boolean[] on = {false};
        Tester tt = Tester.of(c -> { if (on[0] && c.shortcut(CMD_P)) n[0]++; text(c, "x"); }, 100, 100);
        tt.frame();
        on[0] = true;                                   // registered only from the next frame
        tt.key(CMD_P);
        Check.equal(1, n[0]);
    }

    public void testDisabledElementsTakeNoShortcut() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (box(c).size(80, 30).label("a").focusable().disabled(true).shortcut(CMD_D)) n[0]++; }, 300, 200);
        tt.click("a"); tt.key(CMD_D);
        Check.equal(0, n[0]);
    }

    public void testModifiers() {
        int[] mods = {0};
        Tester tt = Tester.of(c -> { mods[0] = c.modifiers(); text(c, "x"); }, 100, 100);
        tt.engine().modifiersChanged(Mod.SHIFT); tt.frame();
        Check.equal(Mod.SHIFT, mods[0]);
    }
}
```

`Tester.settle()` 公开：连续出帧，直到不再请求新帧，最多 20 帧（Task 3 里已有，这里改成 `public`）。

在 `FocusTest` 里补上 `TOGGLE` 的规则。这个测试要等 Task 7 的 `checkbox` 才能写，放在 Task 7 的 `ControlsTest#testCheckboxSpaceTogglesAndEnterYieldsToAWindowShortcut`。这里只实现规则，由 Task 7 的测试先失败、后通过。

- [ ] **Step 2**：运行 `./build.sh test ShortcutTest`。Expected：编译失败。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): keyboard shortcuts on elements and the window`。

---

### Task 7：控件

**Files:**
- Modify: `Ui.java`、`Element.java`、`Engine.java`、`Node.java`、`Paint.java`
- Test: `src/test/java/jui/ui/ControlsTest.java`

**Interfaces:**
- **Consumes:** Task 3–6 的全部内容；`Theme` 的新字段（Task 1）。
- **Produces:**
  - **建子元素不用 lambda**：`int Engine.enter(int index)` 把节点设为当前父元素，返回原来的父元素；`void Engine.exit(int saved)` 恢复它，并检查没用掉的 key。控件函数里一律用这对方法。
  - **`Element onChange(Runnable action)`**：登记时 `changed()` 为真就放进动作列表，在焦点请求和 `onClick` 之前执行。
  - **`boolean Element.changed()`**：控件在这一遍改过绑定的值才为真，返回真时标记消耗。控件用 `Engine.markChanged(int index)` 设置它。
  - **按钮的变体色**：`Node.bgHover`、`Node.bgPressed`（0 表示没有）。
    - 提交时算出 `Node.hot`（悬停且没禁用）和 `Node.down`（按下、指针在里面、且没禁用）；
    - `Paint` 按 `down` → `bgPressed`、`hot` → `bgHover`、否则 `background` 的顺序取背景色；
    - 应用代码之后再调 `background(...)` 会清掉两个变体色，同 MyGO 的“背景被覆盖就不再套用悬停色”。
  - **控件**（尺寸单位 DIP，颜色取 `c.theme()`，数值同 MyGO 的 `widgets.go`/`base.go`）：

| 函数 | 结构和外观 |
|---|---|
| `buttonBase(c)` | `row`，居中，`shrink(0)`，标志 `FOCUSABLE \| CLICKABLE \| HOVER`。外观完全由调用者决定。 |
| `button(c, label)` | `buttonBase`，padding 6×14，gap 6，圆角 `theme.radius()`。背景 `surface`，悬停 `surfaceHover`，按下 `surfacePressed`。1 DIP 边框，颜色 `border`。文字颜色 `text`，`singleLine`。 |
| `primaryButton(c, label)` | 同上，但背景 `accent`/`accentHover`/`accentPressed`，文字 `accentText`，没有边框。 |
| `toggleBase(c, BoolRef ref)` | `row`，标志 `FOCUSABLE \| CLICKABLE \| HOVER \| TOGGLE`。`clicked()` 时翻转 `ref` 并 `markChanged`。 |
| `checkbox(c, ref, label)` | `toggleBase`，gap 8，关掉自身的焦点环。子元素有两个：<br>• 16×16 的框，圆角 4，`shrink(0)`。选中时背景 `accent`，里面居中放文字 `✓`（12 号、粗体、`accentText`）；未选中时背景 `background`，1 DIP 边框，颜色 `border.mix(text, 0.25)`，悬停时边框变 `accent`。<br>• 文字 `label`。<br>行处于 `focusVisible` 时，框上 `ring = true`。 |
| `toggle(c, ref, label)` | `toggleBase`，gap 8，关掉自身的焦点环。子元素有两个：<br>• 36×20 的轨道，圆角 10。背景：关 `border.mix(text, 0.15)`，开 `accent`。里面是 16×16 的白色圆形滑块，内缩 2，关时靠左、开时靠右。滑块带阴影：偏移 (0, 1)、模糊 3、`Color.rgba(0, 0.25f)`。<br>• 文字 `label`。<br>行处于 `focusVisible` 时，轨道上 `ring = true`。 |
| `sliderBase(c, DoubleRef v, double lo, double hi, double step)` | 节点种类 `SLIDER`，标志 `FOCUSABLE \| DRAG \| HOVER \| KEYS`，关掉自身的焦点环。<br>按住时，每一遍按 `v = lo + clamp((pointerX - cx) / cw, 0, 1) * (hi - lo)` 计算，用上一帧的内容框。<br>方向键：`LEFT`/`DOWN` 减一步，`RIGHT`/`UP` 加一步；`PAGE_DOWN`/`PAGE_UP` 加减 `max(step, (hi-lo)/10)`；`HOME` 到 `lo`，`END` 到 `hi`。`step` 为 0 时，键盘每步取 `(hi-lo)/100`。<br>结果先夹到 `[lo, hi]`；`step > 0` 时再取到 `lo + k*step`。值真的变了才 `markChanged`。<br>只有获得焦点且带 `KEYS` 的元素，才当场收到方向键（存进它状态里的 `keys`），而且要焦点链里没有元素登记同一个键。 |
| `slider(c, v, lo, hi)`、`slider(c, v, lo, hi, step)` | `sliderBase`，高 20，`minWidth(80)`，左右 padding 10。`Paint` 画三部分：<br>• 轨道：高 6，在内容框里纵向居中，颜色 `border`，圆角 3；<br>• 已填充部分：从左端到滑块头中心，颜色 `accent`；<br>• 滑块头：20×16 的胶囊，中心在 `cx + f*cw`，按住时放大到 1.35 倍。浅色 `#ffffff`，深色 `rgb(224,225,225)`。先画两层阴影：偏移 (0, 0.5)、模糊 1、`rgba(0,0,0,0.08)`；偏移 (0, 1.5)、模糊 7、`rgba(0,0,0,0.1)`。<br>`focusVisible` 时把焦点环画在滑块头上。 |

`f = (v - lo) / (hi - lo)`，存进 `Node.fraction`；按住状态存进 `Node.held`。

- [ ] **Step 1：写失败的测试**

```java
public final class ControlsTest {
    static final Color RED = Color.rgb(0xFF0000);

    /** The instance painted exactly over the element labeled s, or fails. */
    static float[] boxOf(Tester tt, String s) {
        Rect r = tt.find(s);
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] == 0 && Math.abs(in[0] - r.x()) < 0.01 && Math.abs(in[1] - r.y()) < 0.01
                    && Math.abs(in[2] - r.width()) < 0.01 && Math.abs(in[3] - r.height()) < 0.01) return in;
        }
        throw new AssertionError("no box over " + s);
    }

    static int rgba(float[] in) {
        return Math.round(in[8] * 255) << 24 | Math.round(in[9] * 255) << 16 | Math.round(in[10] * 255) << 8 | Math.round(in[11] * 255);
    }

    public void testButtonClicks() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (button(c, "保存").clicked()) n[0]++; }, 300, 200);
        tt.click("保存");
        Check.equal(1, n[0]);
    }

    public void testButtonColorsFollowHoverAndPress() {
        Theme t = Theme.light();
        Tester tt = Tester.of(c -> column(c).padding(20).gap(10).children(() -> {
            button(c, "普通").label("b");
            primaryButton(c, "主要").label("p");
        }), 300, 200);
        tt.frame();
        Check.equal(t.surface().rgba(), rgba(boxOf(tt, "b")));
        Check.equal(t.accent().rgba(), rgba(boxOf(tt, "p")));
        tt.hover("b");
        Check.equal(t.surfaceHover().rgba(), rgba(boxOf(tt, "b")));
        Rect r = tt.find("b");
        tt.press(r.x() + 5, r.y() + 5);
        Check.equal(t.surfacePressed().rgba(), rgba(boxOf(tt, "b")));
        tt.release(r.x() + 5, r.y() + 5);
        tt.hover("p");
        Check.equal(t.accentHover().rgba(), rgba(boxOf(tt, "p")));
    }

    public void testABackgroundSetLaterWinsOverHover() {
        Tester tt = Tester.of(c -> button(c, "x").label("b").background(RED), 300, 200);
        tt.hover("b");
        Check.equal(RED.rgba(), rgba(boxOf(tt, "b")));
    }

    public void testDisabledButtonHasNoHoverColor() {
        Tester tt = Tester.of(c -> button(c, "x").label("b").disabled(true), 300, 200);
        tt.hover("b");
        float[] in = boxOf(tt, "b");
        Check.equal(Theme.light().surface().rgba() & 0xFFFFFF00, rgba(in) & 0xFFFFFF00);
        Check.near(0.5, in[11], 0.01);
    }

    public void testButtonIsFocusableAndSpaceClicksIt() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (button(c, "x").label("b").clicked()) n[0]++; }, 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("b"), "focused by tab");
        tt.key(Shortcut.of(Key.SPACE));
        Check.equal(1, n[0]);
    }

    public void testCheckboxTogglesItsRefAndReportsChanged() {
        BoolRef done = new BoolRef(false);
        int[] changes = {0};
        Tester tt = Tester.of(c -> { if (checkbox(c, done, "完成").changed()) changes[0]++; }, 300, 200);
        tt.click("完成");
        Check.isTrue(done.get() && tt.hasText("✓"), "checked, with its mark");
        tt.click("完成");
        Check.isTrue(!done.get() && !tt.hasText("✓"), "unchecked");
        Check.equal(2, changes[0]);
    }

    public void testOnChangeRunsAfterTheBuild() {
        BoolRef on = new BoolRef(false);
        java.util.List<Boolean> log = new java.util.ArrayList<>();
        Tester tt = Tester.of(c -> checkbox(c, on, "x").onChange(() -> log.add(on.get())), 300, 200);
        tt.click("x");
        Check.equal(java.util.List.of(true), log);
    }

    public void testCheckboxSpaceTogglesAndEnterYieldsToAWindowShortcut() {
        BoolRef on = new BoolRef(false);
        boolean[] windowEnter = {false};
        int[] enters = {0};
        Tester tt = Tester.of(c -> {
            if (windowEnter[0] && c.shortcut(Shortcut.of(Key.ENTER))) enters[0]++;
            checkbox(c, on, "x");
        }, 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.SPACE));
        Check.isTrue(on.get(), "space toggles");
        tt.key(Shortcut.of(Key.ENTER));
        Check.isTrue(!on.get(), "enter toggles when the window does not want it");
        windowEnter[0] = true; tt.frame();
        tt.key(Shortcut.of(Key.ENTER));
        Check.isTrue(!on.get() && enters[0] == 1, "the window's enter wins over the toggle");
    }

    public void testToggleSwitchesAndMovesItsKnob() {
        BoolRef on = new BoolRef(false);
        Tester tt = Tester.of(c -> column(c).padding(20).children(() -> toggle(c, on, "深色")), 300, 200);
        tt.frame();
        float offKnob = knobX(tt);
        tt.click("深色");
        Check.isTrue(on.get(), "on");
        Check.near(offKnob + 16, knobX(tt));                         // 36 - 2*2 - 16 = 16 across
    }

    /** x of the 16 by 16 white knob. */
    static float knobX(Tester tt) {
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] == 0 && in[2] == 16 && in[3] == 16 && in[8] == 1 && in[9] == 1 && in[10] == 1) return in[0];
        }
        throw new AssertionError("no knob");
    }

    public void testSliderFollowsThePointer() {
        DoubleRef v = new DoubleRef(0);
        int[] changes = {0};
        Tester tt = Tester.of(c -> column(c).padding(20).children(() -> {
            if (slider(c, v, 0, 100).width(220).label("s").changed()) changes[0]++;
        }), 400, 200);
        tt.frame();
        Rect r = tt.find("s");                     // content box: x + 10, 200 wide
        tt.press(r.x() + 10 + 50, r.y() + 10);
        Check.near(25, v.get());
        tt.move(r.x() + 10 + 230, r.y() + 10);
        Check.near(100, v.get());                  // clamped
        int before = changes[0];
        tt.move(r.x() + 10 + 260, r.y() + 10);    // still past the end: no change
        Check.equal(before, changes[0]);
        tt.release(r.x() + 10 + 260, r.y() + 10);
    }

    public void testSliderStepsWithKeys() {
        DoubleRef v = new DoubleRef(50), w = new DoubleRef(0);
        Tester tt = Tester.of(c -> column(c).children(() -> {
            slider(c, v, 0, 100).label("v");
            slider(c, w, 0, 100, 5).label("w");
        }), 400, 200);
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.RIGHT)); Check.near(51, v.get());
        tt.key(Shortcut.of(Key.PAGE_UP)); Check.near(61, v.get());
        tt.key(Shortcut.of(Key.END)); Check.near(100, v.get());
        tt.key(Shortcut.of(Key.HOME)); Check.near(0, v.get());
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.RIGHT)); Check.near(5, w.get());
        Rect r = tt.find("w");
        tt.press(r.x() + 10 + (r.width() - 20) * 0.33f, r.y() + 10);
        Check.near(35, w.get());                   // 33 snaps to the step of 5
    }

    public void testSliderPaintsTrackFillAndKnob() {
        DoubleRef v = new DoubleRef(25);
        Theme t = Theme.light();
        Tester tt = Tester.of(c -> column(c).padding(20).children(() -> slider(c, v, 0, 100).width(220).label("s")), 400, 200);
        tt.frame();
        Rect r = tt.find("s");
        boolean track = false, fill = false, knob = false;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] != 0) continue;
            if (in[3] == 6 && in[2] == 200 && rgba(in) == t.border().rgba()) track = true;
            if (in[3] == 6 && Math.abs(in[2] - 50) < 0.01 && rgba(in) == t.accent().rgba()) fill = true;
            if (in[2] == 20 && in[3] == 16 && Math.abs(in[0] + 10 - (r.x() + 10 + 50)) < 0.01) knob = true;
        }
        Check.isTrue(track && fill && knob, "track " + track + ", fill " + fill + ", knob " + knob);
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test ControlsTest`。Expected：编译失败（控件函数不存在）。
- [ ] **Step 3**：实现。控件函数里不出现捕获变量的 lambda。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): buttons, check boxes, switches and sliders`。

---

### Task 8：明暗切换（引擎和 Tester）

**Files:**
- Modify: `Engine.java`、`Tester.java`
- Test: `src/test/java/jui/ui/ThemeTest.java`

**Interfaces:**
- **Consumes:** `Theme.of(boolean)`（Task 1）。
- **Produces:**
  - `void Engine.setDark(boolean dark)`：主题换成 `Theme.of(dark)`。值变了才请求新帧。
  - `Theme Engine.theme()` 返回当前主题，`c.theme()` 读的就是它。
  - `void Tester.setDark(boolean dark)`：同 MyGO 的 `SetDark`。设置后连续出帧。

- [ ] **Step 1：写失败的测试**

```java
public final class ThemeTest {
    public void testTheThemeFollowsTheAppearance() {
        boolean[] dark = {false};
        Tester tt = Tester.of(c -> { dark[0] = c.theme().dark(); text(c, "字"); }, 200, 100);
        tt.frame();
        Check.isTrue(!dark[0], "light by default");
        tt.setDark(true);
        Check.isTrue(dark[0], "dark after the switch");
        float[] glyph = tt.scene().instance(0);
        Check.near(0xF4 / 255.0, glyph[8], 0.01);     // the dark theme's text, #f4f4f5
    }

    public void testSettingTheSameAppearanceAsksForNoFrame() {
        Tester tt = Tester.of(c -> text(c, "x"), 100, 100);
        tt.frame();
        tt.engine().setDark(false);
        Check.isTrue(!tt.framePending(), "no change, no frame");
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test ThemeTest`。Expected：编译失败。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): switch between the light and dark themes`。

---

### Task 9：macOS 的输入与外观

**Files:**
- Create: `src/main/java/jui/platform/mac/MacKeys.java`
- Modify: `MacWindow.java`、`src/main/java/jui/ffi/{Sigs,ObjC}.java`、`src/main/java/jui/app/Window.java`
- Test: `src/test/java/jui/platform/mac/MacKeysTest.java`、`src/test/java/jui/platform/mac/MacWindowInputTest.java`

**Interfaces:**
- **Consumes:** `Engine` 的输入入口（Task 3、5、6）和 `setDark`（Task 8）；`Key`、`Mod`（Task 1）。
- **Produces:**
  - **`MacKeys`**：
    - `static Key key(int keyCode, String charsIgnoringModifiers)`：
      - 先查固定表：`0x24`/`0x4C` ENTER、`0x30` TAB、`0x31` SPACE、`0x33` BACKSPACE、`0x35` ESCAPE、`0x75` DELETE、`0x72` INSERT、`0x73` HOME、`0x77` END、`0x74` PAGE_UP、`0x79` PAGE_DOWN、`0x7B` LEFT、`0x7C` RIGHT、`0x7D` DOWN、`0x7E` UP；
      - F1–F12 依次是 `0x7A, 0x78, 0x63, 0x76, 0x60, 0x61, 0x62, 0x64, 0x65, 0x6D, 0x67, 0x6F`；
      - 不在表里时，取 `chars` 的第一个字符：`a-z`/`A-Z` 对应字母，`0-9` 对应数字，`` - = , . / ; ' [ ] \ ` `` 对应标点，其他是 `UNKNOWN`。
      - 同 MyGO 的 `surface.go:565-586`。
    - `static int mods(long modifierFlags)`：`1<<17` → SHIFT、`1<<18` → CTRL、`1<<19` → ALT、`1<<20` → CMD。
  - **`MacWindow.Input`**：每个窗口一个，由 `jui.app.Window` 实现。
    - `void pointerMove(double x, double y, int mods)`
    - `void pointerDown(double x, double y, int button, int clicks, int mods)`
    - `void pointerUp(double x, double y, int button, int mods)`
    - `void pointerLeave()`
    - `void keyDown(Key key, int mods, boolean repeat)`
    - `void modifiersChanged(int mods)`
    - `void windowFocusChanged(boolean focused)`
    - `void appearanceChanged(boolean dark)`

    坐标以视图左上角为原点（`isFlipped` 已经是 YES），单位是点。
  - **`JUIView` 新增的覆写**：
    - 鼠标：`mouseDown:`、`mouseUp:`、`mouseDragged:`、`rightMouseDown:`、`rightMouseUp:`、`rightMouseDragged:`、`mouseMoved:`、`mouseExited:`；
    - 键盘：`keyDown:`、`flagsChanged:`；
    - 布尔方法：`acceptsFirstResponder` 返回 YES，`acceptsFirstMouse:` 返回 YES；
    - 外观：`viewDidChangeEffectiveAppearance`。

    细节同 MyGO 的 `surface.go`：
    - 按住 Control 的主键按下，在松开之前都当作副键；
    - 每次按下都调 `[window makeFirstResponder:view]`；
    - 坐标用 `[view convertPoint:[event locationInWindow] fromView:nil]` 求出；
    - 连击次数取 `clickCount`；
    - 按键取 `keyCode`（低 16 位）、`charactersIgnoringModifiers`、`isARepeat`、`modifierFlags`。
  - **悬停**：视图创建时加一个 `NSTrackingArea`，矩形为零，`options = 0x01 | 0x02 | 0x40 | 0x200`（EnteredAndExited、MouseMoved、ActiveInActiveApp、InVisibleRect）。
  - **窗口焦点**：新建 `JUIWindowDelegate`（NSObject 子类），实现 `windowDidBecomeKey:`、`windowDidResignKey:`，设为窗口的 delegate。
  - **外观**：用 `[[view effectiveAppearance] bestMatchFromAppearancesWithNames:@[NSAppearanceNameAqua, NSAppearanceNameDarkAqua]]` 判断明暗，NSArray 用 `arrayWithObjects:count:` 创建。开窗口时读一次，之后在 `viewDidChangeEffectiveAppearance` 里再读。
  - **新的原生签名**（登记在 `Sigs`）：
    - `POINT_OF`：`CG_POINT (long, long)`；
    - `CONVERT_POINT`：`CG_POINT (long, long, CG_POINT, long)`；
    - `INIT_TRACKING_AREA`：`long (long, long, CG_RECT, long, long, long)`。

    对应的辅助方法放在 `ObjC`：`double[] locationInView(long view, long event)`、`long initTrackingArea(...)`。
  - **`MacWindow` 的新方法**：`setBackground(int rgb)`、`close()`。
  - **`jui.app.Window`**：
    - 实现 `MacWindow.Input`，把事件交给 `Engine`；
    - `appearanceChanged` 时调用 `engine.setDark`，并 `mac.setBackground(theme.background)`；
    - 新增 `update(Runnable change)`：任何线程都可以调用，用 `MacApp.post` 在主线程执行 `change`，然后 `invalidate()`；
    - 新增 `close()`。

- [ ] **Step 1：写失败的测试**

```java
public final class MacKeysTest {
    public void testFixedKeysByKeyCode() {
        Check.equal(Key.ENTER, MacKeys.key(0x24, "\r"));
        Check.equal(Key.ENTER, MacKeys.key(0x4C, "\u0003"));
        Check.equal(Key.TAB, MacKeys.key(0x30, "\t"));
        Check.equal(Key.LEFT, MacKeys.key(0x7B, ""));
        Check.equal(Key.F1, MacKeys.key(0x7A, ""));
        Check.equal(Key.F12, MacKeys.key(0x6F, ""));
    }

    public void testOtherKeysByTheirCharacter() {
        Check.equal(Key.P, MacKeys.key(0x23, "p"));
        Check.equal(Key.P, MacKeys.key(0x23, "P"));
        Check.equal(Key.DIGIT1, MacKeys.key(0x12, "1"));
        Check.equal(Key.SLASH, MacKeys.key(0x2C, "/"));
        Check.equal(Key.UNKNOWN, MacKeys.key(0x12, "!"));
        Check.equal(Key.UNKNOWN, MacKeys.key(0x00, ""));
    }

    public void testModifierFlags() {
        Check.equal(Mod.CMD | Mod.SHIFT, MacKeys.mods((1L << 20) | (1L << 17) | (1L << 16)));   // caps lock ignored
        Check.equal(Mod.CTRL | Mod.ALT, MacKeys.mods((1L << 18) | (1L << 19)));
    }
}
```

`MacWindowInputTest` 走一遍从 AppKit 事件到 `Input` 的真实路径。测试会开一个小窗口，结束时关闭：
1. 用测试自己的 FFM 句柄调用 `NSEvent mouseEventWithType:location:modifierFlags:timestamp:windowNumber:context:eventNumber:clickCount:pressure:` 造事件：type 1（左键按下），窗口坐标 (30, 40)，flags `1<<20`，clickCount 2。签名是 `long (long, long, long, CG_POINT, long, double, long, long, long, long, float)`。
2. 用 `ObjC.send(view, "mouseDown:", event)` 发给视图。
3. 断言记录下来的 `pointerDown` 满足：
   - `x == 30`；
   - `y == 视图高度 - 40`：窗口坐标以左下角为原点，视图是翻转的；
   - `button == 0`、`clicks == 2`、`mods == Mod.CMD`。
4. 再造一个按键事件，用 `keyEventWithType:location:modifierFlags:timestamp:windowNumber:context:characters:charactersIgnoringModifiers:isARepeat:keyCode:`：type 10、characters 都是 `"\t"`、keyCode `0x30`。签名是 `long (long, long, long, CG_POINT, long, double, long, long, long, long, boolean, short)`。用 `ObjC.send(view, "keyDown:", event)` 发给视图，断言收到 `keyDown(Key.TAB, 0, false)`。

测试需要能拿到视图，`MacWindow` 增加包内可见的 `long view()`。测试里先调用 `[NSApplication sharedApplication]`。

- [ ] **Step 2**：运行 `./build.sh test MacKeysTest` 和 `./build.sh test MacWindowInputTest`。Expected：编译失败。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：运行 `./build.sh demo`（卡片示例）。Expected：画面和 M1 一致；鼠标移动和点击不报错（stderr 里没有 `uncaught exception`）。
- [ ] **Step 6**：提交 `feat(mac): pointer, keyboard, window focus and appearance events`。

---

### Task 10：示例：计数器和待办清单

**Files:**
- Create: `src/main/java/jui/examples/{CounterDemo,TodoDemo}.java`
- Modify: `build.sh`
- Test: `src/test/java/jui/examples/{CounterDemoTest,TodoDemoTest}.java`

**Interfaces:**
- **Consumes:** 前面所有任务。
- **Produces:**
  - **`CounterDemo`**：照 MyGO 的 `examples/counter-native`，加一个步长滑块。
    - 状态：`int n`；`DoubleRef step = new DoubleRef(1)`。
    - 视图：
      - 窗口快捷键：`↑` 加 `step`，`↓` 减 `step`；“+”“−”两个按钮也按 `step` 加减；
      - `column(c).fill().center().gap(16)` 里依次是：
        - 数字，56 号粗体；
        - 一行三个按钮（gap 8）：
          - `button(c.key("dec"), "−").label("减少").width(44)`；
          - `button(c.key("reset"), "重置").disabled(n == 0)`；
          - `primaryButton(c.key("inc"), "+").label("增加").width(44)`；
        - 一行：文字 `"步长 " + (int) step` 和 `slider(c, step, 1, 10, 1).width(160).label("步长")`；
        - 提示文字 `"↑ 和 ↓ 也能计数"`，12 号，`textMuted`。
    - 窗口 360×300。
  - **`TodoDemo`**：
    - 状态：
      - 待办列表 `List<Todo>`，`Todo` 是可变类，字段为 `long id`、`String title`、`BoolRef done`；开始时有 3 条示例待办；
      - `BoolRef showDone = new BoolRef(true)`；
      - `long nextId`。
    - 视图：
      - 窗口快捷键 `⌘N` 新增一条 `"新待办 " + nextId`；
      - `column(c).fill().padding(24).gap(12)` 里依次是：
        - 标题 `"待办"`，22 号粗体；
        - 一行：`toggle(c, showDone, "显示已完成")`、`spacer(c)`、`text(c, "剩余 " + 未完成数 + " 项")`；
        - 每条待办一行，`c.key(todo.id)`：`checkbox(c, todo.done, todo.title)`、`spacer(c)`、`button(c, "删除").label("删除 " + todo.title).onClick(() -> todos.remove(todo))`；`showDone` 为假时跳过已完成的；
        - 一行：`primaryButton(c, "添加")`（等同 `⌘N`）、`button(c, "清除已完成").disabled(没有已完成的)`。
    - 窗口 420×480。
  - **`build.sh`**：
    - `demo [Name] [args]` 运行 `jui.examples.<Name>Demo`，默认 `Card`；
    - `native-demo [Name] [args]` 编译成 `build/<name>-demo` 后运行；
    - 更新脚本开头的用法说明。

- [ ] **Step 1：写失败的测试**

```java
public final class CounterDemoTest {
    public void testButtonsArrowsAndTheStepSlider() {
        CounterDemo s = new CounterDemo();
        Tester tt = Tester.of(s::view, 360, 300);
        tt.click("增加"); tt.click("增加"); tt.click("减少");
        Check.equal(1, s.n);
        Check.isTrue(tt.hasText("1"), "shows 1");
        tt.key(Shortcut.of(Key.UP)); tt.key(Shortcut.of(Key.UP)); tt.key(Shortcut.of(Key.DOWN));
        Check.equal(2, s.n);
        tt.click("重置");
        Check.equal(0, s.n);
        tt.click("重置");                                   // disabled at 0: nothing happens
        Check.equal(0, s.n);
    }

    public void testTabReachesTheButtonsAndTheSlider() {
        CounterDemo s = new CounterDemo();
        Tester tt = Tester.of(s::view, 360, 300);
        tt.key(Shortcut.of(Key.TAB)); Check.isTrue(tt.focused("减少"), "减少 first");
        tt.key(Shortcut.of(Key.TAB)); Check.isTrue(tt.focused("增加"), "重置 is disabled at 0, skipped");
        tt.key(Shortcut.of(Key.SPACE)); Check.equal(1, s.n);
        tt.key(Shortcut.of(Key.TAB)); Check.isTrue(tt.focused("步长"), "then the slider");
        tt.key(Shortcut.of(Key.RIGHT)); tt.key(Shortcut.of(Key.RIGHT));
        Check.near(3, s.step.get());
        tt.key(Shortcut.of(Key.UP));                         // the slider has focus and takes ↑
        Check.near(4, s.step.get());
        Check.equal(1, s.n);
    }
}

public final class TodoDemoTest {
    public void testCheckDeleteAddAndHide() {
        TodoDemo s = new TodoDemo();
        Tester tt = Tester.of(s::view, 420, 480);
        String first = s.todos.get(0).title;
        tt.click(first);
        Check.isTrue(s.todos.get(0).done.get(), "checked");
        Check.isTrue(tt.hasText("剩余 2 项"), "two left");
        tt.click("删除 " + first);
        Check.equal(2, s.todos.size());
        Check.isTrue(!tt.hasText(first), "gone");
        tt.key(Shortcut.cmd(Key.N));
        Check.equal(3, s.todos.size());
        tt.click(s.todos.get(1).title);                    // check the second
        tt.click("显示已完成");
        Check.isTrue(!tt.hasText(s.todos.get(1).title), "done todos hidden");
        tt.click("清除已完成");
        Check.equal(2, s.todos.size());
    }

    public void testTabAndSpaceCheckATodo() {
        TodoDemo s = new TodoDemo();
        Tester tt = Tester.of(s::view, 420, 480);
        tt.key(Shortcut.of(Key.TAB));                       // the switch
        tt.key(Shortcut.of(Key.TAB));                       // the first todo's check box
        tt.key(Shortcut.of(Key.SPACE));
        Check.isTrue(s.todos.get(0).done.get(), "space checked the first todo");
    }
}
```

`CounterDemo.n`、`step`，`TodoDemo.todos`，`Todo.title`、`done` 都设为包内可见，供测试读取。

- [ ] **Step 2**：运行 `./build.sh test CounterDemoTest` 和 `./build.sh test TodoDemoTest`。Expected：编译失败。
- [ ] **Step 3**：实现两个示例，改 `build.sh`。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：运行 `./build.sh native-demo Counter --print-window` 和 `./build.sh native-demo Todo --print-window`，用 `screencapture -l` 截图存到 `build/shot-counter.png`、`build/shot-todo.png`。Expected：画面和测试描述一致。
- [ ] **Step 6**：手动核对一遍（native image）：悬停按钮变色，点击计数，Tab 出现焦点环，`↑`/`↓` 计数；把系统切到深色，窗口跟着变深。在 README 里记下结果。
- [ ] **Step 7**：提交 `feat(examples): counter and todo list, with build.sh demo names`。

---

### Task 11：分配实测与 README

**Files:**
- Modify: `src/main/java/jui/spike/AllocSpike.java`、`README.md`

**Interfaces:**
- **Consumes:** 控件（Task 7）。
- **Produces:**
  - **新的分配场景 `controls`**：每帧建 40 个 `button`、40 个 `checkbox`、20 个 `slider`，key 用 `c.key(int)`，标签用预先建好的 `String[]`，`BoolRef`、`DoubleRef` 也预先建好。场景分两种：
    - 内容不变的帧；
    - 指针停在第一个按钮上的帧：先 `pointerMove` 一次。
  - **输出**：`ALLOC` 行增加 `controls_frame`（每控件字节数，扣除空帧）和 `controls_hover_frame`。
  - **native 门槛**：两项都必须不超过 0.5 B/控件，否则以非零码退出，和现有的文字场景一样。
  - **README**：增加 “M2 交互” 一节，写清楚：
    - 有哪些 API；
    - 两个示例和运行命令；
    - `./build.sh alloc` 的新数字；
    - 推迟到 M3/M4 的内容（本计划“定下的细节”第 4 条）。

- [ ] **Step 1**：先加场景，再运行 `./build.sh alloc`。Expected：native 的 `controls_frame` 和 `controls_hover_frame` 都是 0 或接近 0。超过 0.5 B 时，用 `AllocSpike` 的分段办法找到分配点，并在框架里修掉；不能放宽门槛。
- [ ] **Step 2**：运行 `./build.sh test` 和 `./build.sh alloc`。Expected：测试全部通过，`alloc` 以 0 退出，`card_frame` 不超过 32768。
- [ ] **Step 3**：更新 README。
- [ ] **Step 4**：提交 `perf(ui): measure what frames of controls allocate; document M2`。

---

## 完成标准

- `./build.sh test` 全部通过。
- `./build.sh alloc` 以 0 退出：控件场景在 native 下每控件不超过 0.5 B，卡片整帧不超过 32 KB。
- 计数器和待办清单在 native image 下跑通，有截图，手动核对过悬停、点击、Tab、快捷键和明暗切换。
- 最后对整个分支做一次审查（executing-plans 的 Final Review），再按 finishing-a-development-branch 决定怎么合并。
