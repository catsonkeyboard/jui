# M3 滚动和列表 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal**：实现设计文档里的 M3。

- **滚动和列表**：
  - `scroll`：裁剪；触控板和滚轮；惯性，由 macOS 的动量事件提供，同 MyGO；
  - `list`：只构建可见的行，行高可以各不相同；
  - `ScrollState`、`ListState`；
  - 滚动条、键盘滚动、Tab 时把焦点滚进视野。
- **flexbox**：补齐换行（`wrap`、`gapX`/`gapY`、`alignContent`）、绝对定位，以及 `clip()`、`invisible()`。
- **M2 推迟下来的**：
  - 帧时钟 `c.now()`、`c.after(...)`、`Tester.advance`；
  - 元素动画 `animate`，开关用它补上 140 ms 的滑动；
  - macOS 的 display link（设计 7.6）。

验收示例是 1 万篇笔记的文件列表：滚动时只构建可见的行，在 native image 下帧耗时在预算内。

**Architecture**：
- **滚动偏移在布局里生效**：滚动容器沿纵轴给内容不限高度，量出内容高度，把偏移夹到 `[0, 内容高 − 视口高]`，再把流内子元素整体上移。于是节点坐标都是窗口坐标，命中测试和绘制直接用。
- **裁剪**：
  - 绘制时，`Scene` 里有一个裁剪栈，裁剪变化处把实例分成批，`MetalRenderer` 每批设一次 `setScissorRect:`；
  - 命中测试时，每个条目记下祖先裁剪框的交集。
- **列表**：
  - **建之前**：用上一帧的视口高、已量行高和估计行高，从“锚点行”（第一行可见的行）出发，算出要建的行，上下各多建 2 行。
  - **布局之后**：量出这些行的实际高度并记下，重新确定锚点。行没有盖满视口时，同一帧里再建一次、再排一次，最多一次。
- **帧时钟**：引擎每帧取一次时间，`Window` 用 `System.nanoTime()`，`Tester` 用自己的时钟。
  - `after` 留下最早的唤醒时间，每帧结束时重新安排；
  - `animate` 动画进行时持续请求新帧；
  - macOS 上，连续出帧走 display link，空闲时暂停。

**Tech Stack**：
- Java 25、GraalVM CE 25（`graalvm-community-25.4.4.1.1+1.1`）；
- FFM（`jui.ffi`）、AppKit、Metal、Core Text；
- 零依赖：自己写的测试运行器，构建用 `build.sh`。

**Spec**：[docs/design/2026-10-24-ui-api-design.md](../design/2026-10-24-ui-api-design.md)，已定稿。本计划实现其中的 M3（第 10 节）：
- API 依据第 6.2（`now`、`after`）、6.8（布局）、6.10（`scroll`、`list`）、6.14（`advance`）节；
- 内部设计依据第 7.4、7.5、7.6 节；
- 性能依据第 8 节。

**行为依据**：MyGO（egoist/mygo，commit `8049993`）。本地副本在 `/tmp/mygo`。下文写“同 MyGO”的地方，以这个版本为准：
- 滚动和列表：`ui/scroll.go`、`ui/list.go`、`ui/wheel.go`；
- 布局：`ui/layout.go`；
- 绘制和滚动条：`ui/paint.go`；
- 动画：`ui/motion.go`；
- 时钟：`ui/runtime.go`、`ui/headless.go`；
- display link：`internal/darwin/surface.go`。

## Global Constraints

沿用 M2 的约束：
- **平台**：Apple Silicon、macOS 14 及以上，`MACOS_MIN=14.0`。
- **原生调用**：全部走 FFM，通过 `jui.ffi.Native` 不绑定地址的句柄调用；新的签名登记在 `jui.ffi.Sigs`。不写 C 或 Objective-C。
- **不用反射**：主代码在运行时不用反射，也不在运行时生成代码；测试运行器和测试代码除外。
- **零第三方依赖**：Maven Central 当前拒绝本机出口 IP 的下载，也不要尝试绕过封禁。
- **线程**：UI 函数和事件处理都在主线程。JVM 下用 `-XstartOnFirstThread` 运行，测试也一样。
- **开发模式**：在 JVM 上运行，或设置了 `-Djui.dev=true` 时开启。开启时，误用 API 抛 `IllegalStateException`；关闭时忽略。
- **命名**：按设计第 6 节，和 MyGO 对应。
- **类型约定**：长度单位是 DIP，类型为 `float`；颜色是 `jui.ui.Color`（0xRRGGBBAA）。
- **注释和文档**：代码注释用英文，文档用中文。
- **提交信息**：每条提交信息末尾加上 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- **框架控件内部不用捕获变量的 lambda**：要建子元素时用 `Engine.enter`/`exit`。
- **每次输入只报告一次**。
- **分配门槛**（设计第 8 节）：
  - 框架内部：内容不变的帧里，不能产生和元素数量成正比的分配，包括滚动容器和列表；
  - 整帧：每帧不超过 32 KB。

M3 新增两条：
- **滚动帧的耗时门槛**：
  - 测法：native image 下，1 万行列表连续滚动的每一帧，构建 + 布局 + 绘制到 `Scene` 的 CPU 时间；
  - 门槛：p95 不超过 4 ms，最大值不超过 8 ms（预热之后）；
  - 每帧构建的行数：不超过“可见行 + 上下各 2 行 + 1”。
- **时间单位**：`c.now()` 返回纳秒（`long`），时长参数用 `java.time.Duration`。

## 本计划定下的细节

设计文档没写死、或者 M3 有意和设计文档或 MyGO 不同的地方，实现者照做。审查时请重点检查：

1. **API 名字**：
   - **滚动状态**：设计 6.10 写的是 `scroll(c, ScrollState)`，MyGO 是 `Scroll(c).TrackScroll(&s)`。照设计，因为设计第 6 节是约束。
   - **`ScrollState`** 是绑定到滚动容器身份上的句柄，同 `Handle` 的做法：
     - `y()` 读当前偏移，`maxY()` 读最大偏移；
     - `scrollTo(y)` 请求偏移，下一帧生效、立刻跳过去；`scrollToTop()`、`scrollToEnd()`；
     - 没绑定时请求先存着，绑定后生效。
   - **`ListState`**：
     - `scrollToTop()`（设计 6.15 用到）；
     - `scrollTo(int row)`：把该行放到视口顶部，同 MyGO 的 `ScrollTo(row, Start)`；
     - `first()`、`last()`：上一帧可见的第一行和最后一行，同 MyGO 的 `Visible()`。
2. **M3 只做纵向滚动**：MyGO 的 `ScrollHorizontal`、`ScrollBoth` 推到第一个需要它们的里程碑；Obsidian 外壳只用纵向。滚轮的横向分量忽略。
3. **`ScrollState.scrollTo` 立刻跳过去**：MyGO 的 `ScrollTo` 带动画，留到有需要时再加。滚轮的 150 ms 平滑照 MyGO 做。
4. **惯性**：macOS 把触控板的动量当作后续的精确滚动事件发来，MyGO 自己不做惯性，M3 也不做。不做回弹，偏移总是夹在范围里。
5. **裁剪只做矩形**：设计 7.5 写“圆角裁剪以后再做”；MyGO 的最内层裁剪带圆角，M3 不带。命中测试也按矩形算。
6. **列表的建行方式和 MyGO 不同，结果一致**：
   - MyGO 在布局时发现缺行，就当场补建（`buildLate`）。
   - j-ui 按上一帧的视口和行高预估，建之前就定下要建的行；布局之后行没盖满视口，同一帧里再建一次、再排一次（最多一次）。
   - 两种做法都保证第一帧就是满的。
7. **列表行高按行号缓存**：
   - 存储：一个 `float[]`，未量过的记 NaN，另外记已量行数和高度总和。估计行高 = 已量行的平均值，一行都没量过时是 32（同 MyGO）。
   - 代价：求某行的顶部位置时，从头累加到该行，是 O(行号) 的。设计的目标是 1 万行，这样足够快。
   - 和 MyGO 的差别：MyGO 用 64 行一块的稀疏块和懒更新的前缀和，能撑到几千万行。M6 量过之后再决定要不要换。
8. **列表的行没有包装元素**：
   - 每次调用 `row` 回调必须建出且只建出一个顶层元素，这就是那一行；多建或没建在开发模式下报错。
   - 行的身份默认是“列表的身份 + 行号”；回调里用 `c.key(...)` 指定 key 时，改用这个 key。这样设计 6.15 里 `noteRow(c.key(n.id()), ...)` 的局部状态会跟着数据项走。
   - MyGO 是给每行包一个按 `ListState.Key(i)` 或行号指定 key 的盒子。j-ui 不包，是为了让设计 6.15 的写法直接成立。
9. **列表的锚点只按行号**：MyGO 有 `Key` 时会按 key 找回锚点（插入行后位置不跳），M3 不做。获得焦点的行滚出视野后不再构建；这时按 Tab，从当前可见的第一个可聚焦元素开始（MyGO 会一直构建获得焦点的行）。
10. **`alignContent` 的默认值是 `START`**，同 MyGO；CSS 的默认是拉伸。M3 只支持 `START`、`CENTER`、`END`、`STRETCH`，`space-*` 推到需要时再加。`wrapReverse` 也不做。
11. **绝对定位**：
    - 包含块是父元素的内边距框；没有任何偏移时放在左上角（没有 CSS 的“静态位置”）；
    - 不参与父元素的尺寸；滚动容器里的绝对定位子元素不随内容滚动；
    - 绘制和命中都排在流内子元素之后。这些都同 MyGO。
    - `top/right/bottom/left` 只对绝对定位的元素生效。MyGO 用它们给流内元素做相对定位，M3 不做。
12. **滚动条**：叠在内容上，不占空间；只在指针位于容器上方或正在拖动时显示，没有淡出（同 MyGO）。
    - 宽 6，颜色是 `theme.scrollbar()`，滑块最短 24；
    - 拖滑块滚动；点击滑块以外的轨道，翻 0.9 个视口。
13. **键盘滚动**：
    - 方向键 40；PageUp/PageDown 和空格、Shift+空格滚 `max(视口 − 40, 视口 / 2)`；Home、End 到两端（同 MyGO 的 `scrollKey`）；
    - 只在按键没被快捷键认领时生效；
    - 目标是焦点链里最内层还能滚动的容器，没有就用指针下的。
14. **Tab 时滚进视野**：按“最少滚动”把焦点元素滚进各层滚动容器的视口，边距为 0，内边距不算，比视口高的对齐到顶部（同 MyGO 的 `reveal`），在同一帧里重新布局。
15. **窗口被遮挡时**：display link 暂停、不画，窗口重新露出来时补一帧。这是设计 7.6 允许的简化版。

## Review Focus

测试没有直接覆盖、但最容易让使用者踩坑的 5 种情况，以及大多数人会期望的行为：

1. **滚到底部以后内容变少**（比如列表过滤后只剩几行）：偏移夹到新的最大值，视口里不出现大片空白。测试放在 Task 6（滚动容器）和 Task 11（列表行数变少）。
2. **滚动以后窗口变高或变矮**：偏移重新夹紧；列表在同一帧里盖满新的视口。测试放在 Task 11。
3. **指针停在滚动容器里的按钮或文字上滚动**：滚动交给外面的容器，不被按钮吃掉。测试放在 Task 6。
4. **行高差别很大的列表，拖滑块到底再拖回顶**：能精确到达最后一行和第一行；任何时候可见的行都首尾相接，没有缝隙和重叠。测试放在 Task 11。
5. **滚动以后点击**：点中的是滚动后位置上的行；落在滚动视口之外、被裁掉的部分点不中。测试放在 Task 5 和 Task 6。

---

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `src/main/java/jui/ui/engine/Motion.java` | 一个动画通道：起点、终点、开始时间、时长、缓动 | 1 |
| `src/main/java/jui/ui/engine/{Engine,ElementState,Input,HitList,Node,Paint,FlexLayout}.java` | 时钟、定时器、动画；换行、绝对定位、裁剪；滚动；列表的建行和布局后处理 | 1、3–11 |
| `src/main/java/jui/ui/engine/ListModel.java` | 列表的行高缓存、锚点、请求、可见范围 | 10、11 |
| `src/main/java/jui/ui/{Element,Context,Ui,Tester,Theme}.java` | 公开 API：`animate`、`now`、`after`、布局方法、`scroll`、`list`；Tester 的时钟、滚动、改窗口大小；主题加滚动条颜色 | 1、3–11 |
| `src/main/java/jui/ui/{ScrollState,ListState}.java` | 滚动和列表的状态 | 6、10、11 |
| `src/main/java/jui/render/Scene.java`、`jui/render/metal/MetalRenderer.java` | 裁剪栈和分批；每批设裁剪矩形 | 5 |
| `src/main/java/jui/ffi/{Sigs,Native,ObjC}.java` | `setScissorRect:`、带 `baseInstance` 的绘制、display link 用到的签名 | 2、5 |
| `src/main/java/jui/platform/mac/MacWindow.java`、`jui/app/Window.java` | display link、定时器、遮挡；滚轮事件 | 2、9 |
| `src/main/java/jui/examples/FilesDemo.java` | 验收示例：1 万篇笔记的文件列表 | 12 |
| `src/main/java/jui/spike/{ListBench,AllocSpike}.java` | 滚动帧耗时门槛；列表的分配实测 | 12 |
| `build.sh` | 新增 `listbench` 命令 | 12 |

---

### Task 0：分支

- [ ] **Step 1**：从 `main` 建分支 `m3-scroll-and-list`：`git checkout -b m3-scroll-and-list`。
- [ ] **Step 2**：在分支上提交本计划，提交信息 `docs: plan M3 (scrolling and lists)`。

---

### Task 1：帧时钟、定时器和动画

**Files:**
- Create: `src/main/java/jui/ui/engine/Motion.java`
- Modify: `Engine.java`、`ElementState.java`、`Element.java`、`Context.java`、`Tester.java`、`Ui.java`（`toggle`）
- Test: `src/test/java/jui/ui/TimeTest.java`；修改 `ControlsTest#testToggleSwitchesAndMovesItsKnob`

**Interfaces:**
- **Produces:**
  - **时钟**：
    - `Engine(TextEngine, Theme, Runnable requestFrame, LongSupplier clock)`：新构造函数。原有的三参数构造函数用 `System::nanoTime`。
    - `long Engine.now()`：本帧开始时的时间，`buildAndLayout` 开始时取一次。
  - **`long Context.now()`**：本帧时间，纳秒，同 MyGO 的 `c.Now()`。
  - **定时器**：
    - `void Context.after(Duration d)`：记下 `now + d` 和已有唤醒时间中较早的一个，不返回值；应用自己拿 `now()` 比较，同 MyGO。
    - `long Engine.nextWake()`：本帧记下的唤醒时间，没有时为 `Long.MAX_VALUE`。每帧开始时清空，所以下一帧不再调用 `after`，定时器就停了，同 MyGO 的“每帧重新安排”。
  - **动画**：`float Element.animate(String key, float target, Duration d)`，同 MyGO 的 `Animate`：
    - 每个元素的每个 `key` 一个通道，存在 `ElementState` 里（第一次用时创建 `HashMap<String, Motion>`）；
    - 第一次调用：直接停在 `target`，返回 `target`；
    - 目标变化：从当前值出发，在 `now` 重新开始，**这一帧返回旧值**；
    - 运动中：返回 `from + (to − from) · ease(t)`，`t = (now − start) / d`，缓动 `ease(t) = 1 − (1 − t)³`；
    - `t ≥ 1` 时停在目标；运动中每帧调用 `requestFrame`。
  - **开关的滑块**：`toggle` 用 `row.animate("knob", on ? 1 : 0, 140 ms)` 得到位置 `pos`：
    - 滑块的左外边距是 `16 · pos`，代替原来的 `justify(END)`；
    - 轨道背景是 `mix(off, accent, pos)`，用 `Ui.mix` 的整数版本，不分配。
  - **`Tester`**：
    - 用自己的时钟，从 0 开始；`long now()` 读它。
    - `void advance(Duration d)`，同 MyGO 的 `Advance`，按时间顺序处理两种事：
      - 有帧请求时，在上一帧之后 1/60 秒出一帧，1/60 秒按整数纳秒取 `1_000_000_000 / 60 = 16_666_666`，所以 50 ms 内的第三帧在 49.999998 ms；
      - `nextWake()` 到期时出一帧。
      - 两者都没有就结束，结束时时钟停在 `now + d`。
    - `settle()` 不推进时钟：连续出帧时时间不变，所以运动中的动画在 `settle` 里不前进，同 MyGO。

- [ ] **Step 1：写失败的测试**

```java
public final class TimeTest {
    static final long MS = 1_000_000;

    public void testAdvanceRunsFramesAt60HzWhileFramesAreAskedFor() {
        java.util.List<Long> times = new java.util.ArrayList<>();
        Tester tt = Tester.of(c -> { times.add(c.now()); c.invalidate(); text(c, "x"); }, 100, 100);
        tt.frame();
        times.clear();
        tt.advance(java.time.Duration.ofMillis(50));
        Check.equal(3, times.size());
        Check.near(16.667, times.get(0) / 1e6, 0.01);
        Check.near(33.333, times.get(1) / 1e6, 0.01);
        Check.near(50.0, times.get(2) / 1e6, 0.01);
        Check.equal(50 * MS, tt.now());
    }

    public void testAfterWakesAFrameAtItsTime() {
        int[] frames = {0};
        boolean[] fired = {false};
        Tester tt = Tester.of(c -> {
            frames[0]++;
            if (c.now() >= 100 * MS) fired[0] = true; else c.after(java.time.Duration.ofMillis(100));
            text(c, "x");
        }, 100, 100);
        tt.frame();
        tt.advance(java.time.Duration.ofMillis(99));
        Check.isTrue(!fired[0], "not yet");
        tt.advance(java.time.Duration.ofMillis(1));
        Check.isTrue(fired[0], "fired at 100 ms");
        Check.equal(2, frames[0]);                 // no frames in between: nothing asked for them
    }

    public void testAFrameThatDoesNotCallAfterStopsTheTimer() {
        int[] frames = {0};
        Tester tt = Tester.of(c -> { if (frames[0]++ == 0) c.after(java.time.Duration.ofMillis(100)); text(c, "x"); }, 100, 100);
        tt.frame();
        tt.frame();                                // does not call after
        tt.advance(java.time.Duration.ofMillis(200));
        Check.equal(2, frames[0]);
    }

    public void testAnimateEasesOutToItsTarget() {
        float[] target = {0}, value = {0};
        Tester tt = Tester.of(c -> value[0] = box(c).size(10, 10).animate("x", target[0], java.time.Duration.ofMillis(100)), 100, 100);
        tt.frame();
        Check.near(0, value[0]);                   // first call: at its target
        target[0] = 1;
        tt.frame();
        Check.near(0, value[0]);                   // the frame the target changes returns the old value
        Check.isTrue(tt.framePending(), "moving: asks for frames");
        tt.advance(java.time.Duration.ofMillis(50));
        Check.near(0.875, value[0], 1e-3);         // 1 - (1 - 0.5)^3
        tt.advance(java.time.Duration.ofMillis(60));
        Check.near(1, value[0]);
        Check.isTrue(!tt.framePending(), "at rest: no more frames");
    }
}
```

修改 `ControlsTest#testToggleSwitchesAndMovesItsKnob`：点击之后先 `tt.advance(java.time.Duration.ofMillis(200))`，再检查 `knobX(tt) == offKnob + 16`；并在点击后、`advance` 之前加一句断言：滑块还在原处 `knobX(tt) == offKnob`（动画从这一帧开始）。

- [ ] **Step 2**：运行 `./build.sh test TimeTest` 和 `./build.sh test ControlsTest`。Expected：`TimeTest` 编译失败；`ControlsTest` 里改过的测试失败（滑块立刻到了终点）。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): frame clock, timers and animations; the switch slides`。

---

### Task 2：macOS 的 display link、定时器和遮挡

**Files:**
- Modify: `src/main/java/jui/platform/mac/MacWindow.java`、`src/main/java/jui/app/Window.java`、`src/main/java/jui/ffi/{Sigs,Native,ObjC}.java`
- Test: `src/test/java/jui/platform/mac/MacWindowFrameTest.java`

**Interfaces:**
- **Consumes:** `Engine.nextWake()`（Task 1）。
- **Produces**（同 MyGO 的 `internal/darwin/surface.go:165-257, 795-821`）：
  - **建立 display link**：开窗口时，`[view displayLinkWithTarget:view selector:@selector(juiTick:)]` 建出一个 `CADisplayLink`。
    - 先暂停，再 `addToRunLoop:[NSRunLoop mainRunLoop] forMode:NSRunLoopCommonModes`；
    - `NSRunLoopCommonModes` 是 Foundation 里的 NSString 常量，按 `kCGColorSpaceSRGB` 的做法读符号的值。
  - **`MacWindow.requestFrame()`**：
    - display link 正在跑：只记下“有帧要画”；
    - 否则，距上一帧已超过 2 个刷新周期、且窗口没被遮挡：`setNeedsDisplay:YES`，由 `updateLayer` 画；
    - 否则：取消暂停，记下“有帧要画”。
    - 刷新周期是 `1 / max(screen.maximumFramesPerSecond, 30)`，取不到时按 60。
  - **`juiTick:`**：
    - 没有帧要画：暂停 display link；
    - 有帧要画：清掉标记，直接画一帧（和 `updateLayer` 走同一个 `draw()`）。
    - 窗口被遮挡（`[window occlusionState] & 2` 为 0）：暂停，并记下“欠一帧”。
  - **遮挡**：窗口代理新增 `windowDidChangeOcclusionState:`，窗口露出来并且欠着帧时，请求一帧。
  - **定时器**：
    - `void armTimer(long delayNanos)`：先 `[NSObject cancelPreviousPerformRequestsWithTarget:view selector:@selector(juiTimer:) object:nil]`，再 `performSelector:withObject:afterDelay:`；
    - `void cancelTimer()`；
    - `juiTimer:` 触发时请求一帧。
  - **`Window`**：每帧画完后读 `engine.nextWake()`，有就 `mac.armTimer(wake − engine.now())`，没有就 `mac.cancelTimer()`。
  - **测试用的访问方法**（包内可见）：`boolean displayLinkRunning()`、`int frames`（已有，公开字段）。
  - **新签名**：`[link setPaused:]` 和 `addToRunLoop:forMode:` 都是整数参数，现有的 `send` 就够。`occlusionState` 返回 `NSUInteger`。不需要新的 `Sigs`。

- [ ] **Step 1：写失败的测试**

```java
public final class MacWindowFrameTest {
    // Runs the main run loop for up to seconds, until done says so.
    static void pump(double seconds, java.util.function.BooleanSupplier done) { /* CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.01, true) in a loop, as MacAppTest does */ }

    public void testFramesComeOnRequestAndTheLinkPausesWhenIdle() {
        ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        int[] painted = {0};
        MacWindow w = MacWindow.open("frames", 200, 150, 0xFFFFFF, null, (scene, width, height) -> painted[0]++);
        try {
            pump(2, () -> w.frames > 0);                       // the first frame, from opening
            int before = w.frames;
            for (int i = 0; i < 3; i++) w.requestFrame();      // asked for together: one frame
            pump(0.5, () -> w.frames > before);
            Check.isTrue(w.frames > before, "a requested frame was drawn");
            Check.isTrue(w.frames <= before + 2, "requests are paced, not one frame each");
            pump(0.3, () -> false);                            // idle
            Check.isTrue(!w.displayLinkRunning(), "the link pauses when no frame is asked for");
            int idle = w.frames;
            w.armTimer(50_000_000L);
            pump(1, () -> w.frames > idle);
            Check.isTrue(w.frames > idle, "the timer drew a frame");
        } finally {
            w.close();
        }
    }
}
```

`pump` 用测试自己的 FFM 句柄调用 `CFRunLoopRunInMode`，写法同 `MacAppTest`。测试会开一个小窗口，结束时关闭。

- [ ] **Step 2**：运行 `./build.sh test MacWindowFrameTest`。Expected：编译失败（`displayLinkRunning`、`armTimer` 不存在）。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：运行 `./build.sh demo Todo`，点开关。Expected：滑块滑过去（140 ms），stderr 里没有异常。
- [ ] **Step 6**：提交 `feat(mac): display link, timers and occlusion for frames on demand`。

---

### Task 3：flexbox 换行

**Files:**
- Modify: `FlexLayout.java`、`Node.java`、`Element.java`
- Test: `src/test/java/jui/ui/engine/FlexLayoutTest.java`

**Interfaces:**
- **Produces**（同 MyGO 的 `ui/layout.go:650-885`）：
  - **`Element` 的新方法**：
    - `wrap()`；
    - `gapX(float)`、`gapY(float)`：`gap(g)` 同时设两者；`Node.gap` 拆成 `gapX`、`gapY`；
    - `alignContent(Align)`：默认 `START`。
  - **哪个间距用在哪里**：行（row）里，`gapX` 是主轴间距，`gapY` 是行与行之间的间距；列（column）里反过来。
  - **断行**：
    - 每个子元素占 `外部尺寸 = 夹紧后的基准尺寸 + 主轴外边距`，自动外边距算 0；
    - 当前行非空、且 `当前行已占 + 间距 + 外部尺寸 > 主轴内尺寸` 时换行；
    - 主轴尺寸不确定时只有一行。
  - **每行单独伸缩**：每一行各自按 §9.7 解析 `grow`/`shrink`。
  - **行的交叉尺寸**：该行子元素交叉尺寸加外边距的最大值。
    - 不换行的单行容器，行高仍然等于容器的交叉尺寸（现有行为）；
    - `wrap()` 容器即使只有一行，也保持自然行高。
  - **`alignContent`**：只在交叉尺寸确定、并且换行时生效。
    - `START`：从起点排；
    - `CENTER`：居中，可以溢出；
    - `END`：靠终点，可以溢出；
    - `STRETCH`：空间有剩余时，每行加 `剩余 / 行数`。
    - 行内 `STRETCH` 的子元素拉伸到所在行的最终交叉尺寸。
  - **主轴对齐**：每行内部做 `justify`，剩余空间按 `max(剩余, 0)` 算，主轴上不溢出。
  - **内容尺寸**：
    - 换行行的最大内容宽度 = 子元素之和加间距；
    - 换行列的内容高度 = 各列之和加间距。

- [ ] **Step 1：写失败的测试**

```java
// FlexLayoutTest（新增）。ten(c) 建一个 row：宽 100、wrap()、gap(10)，里面是若干 40×20 的盒子，标签 a0, a1, …
public void testARowWrapsWhenTheNextItemDoesNotFit() {
    e.buildAndLayout(c -> row(c).width(100).wrap().gap(10).alignItems(Align.START).children(() -> {
        for (int i = 0; i < 3; i++) box(c).size(40, 20).label("a" + i);
    }), 300, 300);
    Check.equal(new Rect(0, 0, 40, 20), e.bounds("a0"));
    Check.equal(new Rect(50, 0, 40, 20), e.bounds("a1"));
    Check.equal(new Rect(0, 30, 40, 20), e.bounds("a2"));     // 90 + 10 + 40 > 100
}

public void testALineIsAsTallAsItsTallestItem() {
    e.buildAndLayout(c -> row(c).width(100).wrap().gapX(10).gapY(4).alignItems(Align.START).children(() -> {
        box(c).size(40, 20).label("a"); box(c).size(40, 30).label("b"); box(c).size(40, 10).label("c");
    }), 300, 300);
    Check.near(34, e.bounds("c").y());                        // line 1 is 30 high, then gapY 4
}

public void testAlignContentDefaultsToStartAndCanCenterOrStretch() {
    for (Align a : new Align[] {null, Align.CENTER, Align.STRETCH}) {
        e.buildAndLayout(c -> {
            Element r = row(c).size(100, 200).wrap().gap(10).alignItems(Align.START);
            if (a != null) r.alignContent(a);
            r.children(() -> { for (int i = 0; i < 3; i++) box(c).size(40, 20).label("a" + i); });
        }, 300, 300);
        float y0 = e.bounds("a0").y(), y2 = e.bounds("a2").y();
        if (a == null) { Check.near(0, y0); Check.near(30, y2); }
        if (a == Align.CENTER) { Check.near(75, y0); Check.near(105, y2); }      // free 200 - 50 = 150
        if (a == Align.STRETCH) { Check.near(0, y0); Check.near(105, y2); }      // each line + 75
    }
}

public void testAColumnWrapsIntoColumns() {
    e.buildAndLayout(c -> column(c).height(100).wrap().gap(10).alignItems(Align.START).children(() -> {
        for (int i = 0; i < 3; i++) box(c).size(30, 40).label("a" + i);
    }), 300, 300);
    Check.equal(new Rect(0, 50, 30, 40), e.bounds("a1"));
    Check.equal(new Rect(40, 0, 30, 40), e.bounds("a2"));     // a new column after gapX
}

public void testAWrappingContainerWithOneLineKeepsItsNaturalHeight() {
    e.buildAndLayout(c -> row(c).size(300, 200).wrap().alignItems(Align.STRETCH).children(() ->
            box(c).width(40).label("s")), 300, 300);
    Check.near(0, e.bounds("s").height());   // stretched to its line, whose natural height is 0, not to the 200-high container
}

public void testEachLineFlexesOnItsOwn() {
    e.buildAndLayout(c -> row(c).width(100).wrap().children(() -> {
        box(c).width(60).label("a"); box(c).basis(60).grow(1).label("b");   // grow alone means basis 0, which never wraps
    }), 300, 300);
    Check.near(100, e.bounds("b").width());                   // alone on line 2, it grows to the whole line
}
```

- [ ] **Step 2**：运行 `./build.sh test FlexLayoutTest`。Expected：编译失败（`wrap`、`gapX`、`alignContent` 不存在）。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过，原有的单行布局测试不变。
- [ ] **Step 5**：提交 `feat(ui): flex wrap, gapX/gapY and alignContent`。

---

### Task 4：绝对定位和 invisible

**Files:**
- Modify: `FlexLayout.java`、`Node.java`、`Element.java`、`Paint.java`、`Engine.java`（提交命中列表的顺序）、`Input.java`（焦点顺序）
- Test: `src/test/java/jui/ui/engine/FlexLayoutTest.java`、`src/test/java/jui/ui/PositionTest.java`

**Interfaces:**
- **Produces**（同 MyGO 的 `ui/layout.go:1170-1245`、`paint.go:296-316`、`runtime.go:1046-1114`）：
  - **`Element` 的新方法**：`absolute()`；`top(float)`、`right(float)`、`bottom(float)`、`left(float)`，未设时为 NaN；`invisible()`。
  - **包含块**：父元素的内边距框，即边框盒减去边框，宽 `pw`、高 `ph`。
  - **宽度**：
    - 设了宽度就用它（夹到最小、最大宽度）；
    - 否则 left 和 right 都设了：`pw − left − right − 横向外边距`；
    - 否则按内容收缩，不超过 `pw − 横向外边距`。
  - **高度**：同样的规则，换成 top、bottom 和纵向。
  - **位置**：
    - 默认 `left + 左外边距`；
    - 只设了 right：`pw − right − w − 右外边距`；
    - top 和 bottom 同理；
    - 都没设时放在左上角。
  - **不影响父元素**：绝对定位的子元素不参与伸缩、内容尺寸，也不参与滚动内容高度（Task 6）。
  - **排列顺序**：
    - 绘制：先画流内子元素，再按建立顺序画绝对定位的子元素；
    - 命中列表：按同样的顺序提交，所以后画的在上层；
    - 节点池顺序不再等于绘制顺序。`commit` 按绘制顺序遍历，焦点顺序仍然按建立顺序（同 MyGO 的焦点顺序取提交顺序，这里就是按绘制顺序）。
  - **`invisible()`**：
    - 保留布局空间，但它和它的子元素都不画；
    - 不进命中列表；
    - 不进焦点顺序；获得焦点时失去焦点。

- [ ] **Step 1：写失败的测试**

```java
// FlexLayoutTest（新增）
public void testAbsoluteIsPlacedInThePaddingBox() {
    e.buildAndLayout(c -> box(c).size(200, 100).padding(10).border(2, Color.rgb(0)).children(() -> {
        box(c).absolute().top(5).left(7).size(20, 20).label("tl");
        box(c).absolute().right(5).bottom(5).size(20, 20).label("br");
        box(c).absolute().left(10).right(10).height(8).label("wide");
        box(c).absolute().size(4, 4).label("none");
    }), 300, 300);
    Check.equal(new Rect(9, 7, 20, 20), e.bounds("tl"));     // 2 (border) + 7, 2 + 5
    Check.equal(new Rect(173, 73, 20, 20), e.bounds("br"));  // 2 + 196 - 5 - 20, 2 + 96 - 5 - 20
    Check.equal(new Rect(12, 2, 176, 8), e.bounds("wide"));  // 196 - 10 - 10
    Check.equal(new Rect(2, 2, 4, 4), e.bounds("none"));
}

public void testAbsoluteChildrenDoNotSizeTheirParent() {
    e.buildAndLayout(c -> column(c).alignItems(Align.START).children(() ->
        column(c).padding(5).label("p").children(() -> box(c).absolute().size(300, 300))), 400, 400);
    Check.near(10, e.bounds("p").height());
    Check.near(10, e.bounds("p").width());
}

// PositionTest（新增，jui.ui 包）
public void testAbsoluteChildrenPaintAndHitOverInFlowOnes() {
    int[] abs = {0}, flow = {0};
    Tester tt = Tester.of(c -> box(c).size(200, 100).children(() -> {
        if (box(c).absolute().size(50, 50).label("abs").clicked()) abs[0]++;   // built first
        if (box(c).size(100, 100).label("flow").clicked()) flow[0]++;          // under it
    }), 300, 200);
    tt.press(10, 10); tt.release(10, 10);
    Check.equal(1, abs[0]); Check.equal(0, flow[0]);
}

public void testInvisibleKeepsItsRoomButIsNotPaintedHitOrFocused() {
    int[] n = {0};
    Tester tt = Tester.of(c -> column(c).children(() -> {
        Element a = box(c).size(80, 40).label("a").background(Color.rgb(0xFF0000)).focusable().invisible();
        if (a.clicked()) n[0]++;
        box(c).size(80, 40).label("b").focusable();
    }), 300, 200);
    tt.frame();
    Check.near(40, tt.find("b").y());
    Check.equal(0, tt.instances());                          // nothing painted: a is invisible, b has no look
    tt.press(10, 10); tt.release(10, 10);
    Check.equal(0, n[0]);
    tt.key(Shortcut.of(Key.TAB));
    Check.isTrue(tt.focused("b"), "tab skips the invisible element");
}
```

- [ ] **Step 2**：运行 `./build.sh test FlexLayoutTest` 和 `./build.sh test PositionTest`。Expected：编译失败。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): absolute positioning and invisible elements`。

---

### Task 5：裁剪

**Files:**
- Modify: `src/main/java/jui/render/Scene.java`、`src/main/java/jui/render/metal/MetalRenderer.java`、`src/main/java/jui/ffi/{Sigs,Native,ObjC}.java`、`Paint.java`、`HitList.java`、`Engine.java`、`Node.java`、`Element.java`
- Test: `src/test/java/jui/render/SceneTest.java`、`src/test/java/jui/render/metal/MetalRendererTest.java`、`src/test/java/jui/ui/engine/PaintTest.java`、`src/test/java/jui/ui/PointerTest.java`

**Interfaces:**
- **Produces：**
  - **`Scene`**：
    - `void pushClip(float x, float y, float w, float h)`：参数是点；新裁剪框和当前框求交。
    - `void popClip()`。
    - 分批：裁剪变化时开新的一批。
    - 批的查询：`int batches()`、`int batchStart(int i)`、`int batchEnd(int i)`；`boolean batchClipped(int i)`；`void batchScissor(int i, int[] out)`，写出像素坐标 `{x, y, w, h}`。
    - 像素坐标的取法：四条边各自四舍五入到整像素，同 MyGO 的 `snap`；空的交集是宽或高为 0 的框。
    - `begin` 时清空栈，第一批不裁剪。
  - **`MetalRenderer`**：
    - 每批：裁剪批先把框夹到画布内再 `setScissorRect:`，不裁剪的批设成整个画布；空框跳过这一批；
    - 然后 `drawPrimitives:vertexStart:vertexCount:instanceCount:baseInstance:`。
    - `instancesDrawn()` 统计实际画了的实例。
  - **新签名**：
    - `Sigs.MTL_SCISSOR`：四个 `JAVA_LONG` 的结构体；
    - `SET_SCISSOR`：`void (long, long, MTL_SCISSOR)`；
    - `ObjC.send` 增加 5 个参数的重载，给带 `baseInstance` 的绘制用（全是整数，走现有的 `Native.call` 7 参数版）。
  - **`Element.clip()`**：裁剪子元素。
    - 元素自己的背景和文字在压栈前画；有边框时，边框不在这时画，而是在子元素之后、出栈之后画，盖在内容上，只画一次，同 MyGO 的 `paint.go:240-325`；
    - 裁剪框是内边距框（边框盒减去边框）。
  - **命中测试**：
    - `HitList` 每个条目增加 `clipX0/clipY0/clipX1/clipY1`：所有裁剪祖先的内边距框的交集，没有裁剪祖先时为无穷；
    - `contains` 同时要求点在框内和裁剪内；
    - 绝对定位的子元素也受祖先裁剪。

- [ ] **Step 1：写失败的测试**

```java
// SceneTest（新增）
public void testClipsSplitTheSceneIntoBatchesWithPixelScissors() {
    Scene s = new Scene();
    s.begin(2, null, null);
    s.box(0, 0, 10, 10, 0, 0xFF0000FF);
    s.pushClip(10.2f, 20, 50, 30);
    s.box(0, 0, 10, 10, 0, 0xFF0000FF);
    s.pushClip(0, 0, 40, 40);                    // nested: intersected
    s.box(0, 0, 10, 10, 0, 0xFF0000FF);
    s.popClip();
    s.popClip();
    s.box(0, 0, 10, 10, 0, 0xFF0000FF);
    Check.equal(4, s.batches());
    int[] r = new int[4];
    Check.isTrue(!s.batchClipped(0), "unclipped first");
    s.batchScissor(1, r); Check.equal(java.util.List.of(20, 40, 100, 60), java.util.List.of(r[0], r[1], r[2], r[3]));
    s.batchScissor(2, r); Check.equal(java.util.List.of(20, 40, 60, 40), java.util.List.of(r[0], r[1], r[2], r[3]));
    Check.isTrue(!s.batchClipped(3), "unclipped after the pops");
    Check.equal(1, s.batchStart(1)); Check.equal(2, s.batchEnd(1));
}

// MetalRendererTest（新增）
public void testClippedBatchesDrawAndEmptyOnesAreSkipped() {
    // as the existing test: an offscreen layer; 10 boxes, 5 inside pushClip(0, 0, 50, 50), 3 inside pushClip(500, 500, 10, 10) (outside the 100×30 drawable)
    // render → true; instancesDrawn() == 7 (the 3 in the empty clip are skipped)
}

// PaintTest（新增）
public void testAClippingBoxPaintsItsChildrenInsideItsPaddingBox() {
    e.buildAndLayout(c -> box(c).size(100, 50).background(Color.rgb(0xEEEEEE)).border(2, Color.rgb(0)).clip().children(() ->
            box(c).size(300, 300).background(Color.rgb(0xFF0000))), 200, 100);
    Scene s = paint(1);
    Check.equal(3, s.batches());                // own background | the child, clipped | the border, over it
    int[] r = new int[4];
    s.batchScissor(1, r);
    Check.equal(java.util.List.of(2, 2, 96, 46), java.util.List.of(r[0], r[1], r[2], r[3]));
}

// PointerTest（新增）
public void testAClippedChildIsNotHitOutsideItsClip() { // Review Focus 5
    int[] n = {0};
    Tester tt = Tester.of(c -> box(c).size(100, 50).clip().children(() -> {
        if (box(c).size(300, 300).label("big").clicked()) n[0]++;
    }), 400, 400);
    tt.press(150, 150); tt.release(150, 150);  // inside big's box, outside the clip
    Check.equal(0, n[0]);
    tt.press(50, 25); tt.release(50, 25);
    Check.equal(1, n[0]);
}
```

`MetalRendererTest` 的新测试照现有测试的写法补全：离屏 layer、`Scene`、`render`、断言 `instancesDrawn()`。

- [ ] **Step 2**：运行四个测试类。Expected：编译失败。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：运行 `./build.sh demo`（卡片示例）。Expected：画面不变。
- [ ] **Step 6**：提交 `feat(render): clip stacks, scissored batches and clipped hit testing`。

---

### Task 6：滚动容器

**Files:**
- Create: `src/main/java/jui/ui/ScrollState.java`
- Modify: `FlexLayout.java`、`Node.java`、`ElementState.java`、`Engine.java`、`Input.java`、`HitList.java`、`Ui.java`、`Tester.java`
- Test: `src/test/java/jui/ui/ScrollTest.java`

**Interfaces:**
- **Consumes:** 裁剪（Task 5），绝对定位（Task 4）。
- **Produces：**
  - **`Ui.scroll(Context c)`**：一个纵向滚动的列，同 MyGO 的 `Scroll`。
    - 标志 `Node.SCROLL = 64`，外加 `HOVER`（滚动条要随悬停显示，Task 8）；
    - 裁剪子元素，按 `clip()` 的规则。
  - **`Ui.scroll(Context c, ScrollState s)`**：同上，并把 `s` 绑定到这个容器的身份上。
  - **`ScrollState`**：
    - `float y()`、`float maxY()`：上一帧提交的偏移和最大偏移；
    - `void scrollTo(float y)`、`scrollToTop()`、`scrollToEnd()`（取 `Float.MAX_VALUE`），下一帧在布局里夹紧后生效；
    - 没绑定时请求先存着。
  - **`ElementState` 新字段**：`scrollY`、`contentH`、`viewH`，以及 `requestY`（NaN 表示没有请求）。
  - **布局**（`FlexLayout`）：
    - 滚动容器的流内子元素按“纵向空间不确定”解析：只用基准尺寸，不伸缩。基准尺寸只来自 `grow` 的子元素（基准为 0 时）改用内容高度，不然会被压成 0。
    - `contentH = 子元素高度之和 + 间距 + 上下内边距和边框`。
    - 偏移：有 `requestY` 就先采用，再夹到 `[0, max(0, contentH − h)]`。
    - 流内子元素整体上移这个偏移；绝对定位的子元素不移。
  - **提交**：写回状态的 `scrollY`、`contentH`、`viewH`；绑定了 `ScrollState` 的同步它。
  - **滚动事件**：`Engine.scroll(float x, float y, float dx, float dy, boolean precise, int mods)`。
    - `dy > 0` 表示偏移增大，内容向上走；`dx` 忽略。
    - 路由（同 MyGO 的 `input.go:485-522`）：取指针处的命中链，从最内层往外，找第一个往 `dy` 方向还能动的滚动容器。
      - 精确事件：立刻按 `scrollY += dy` 夹紧，请求新帧。
      - 剩下的量丢掉，不交给外层；所以内层滚到头以后，下一个事件才轮到外层。
    - 非精确事件：Task 7 做平滑，这一步先按精确处理。
  - **`Tester`**：
    - `void scroll(float x, float y, float dx, float dy)` 发精确事件；
    - `void scroll(String s, float dx, float dy)` 在元素中心发；
    - `void resize(float w, float h)` 改窗口大小，给 Task 11 用。

- [ ] **Step 1：写失败的测试**

```java
public final class ScrollTest {
    final ScrollState st = new ScrollState();
    int rows = 10;
    final int[] clicks = new int[20];

    /** A 200×100 scroll container (labeled "s") of rows 40 high, labeled r0, r1, …; each counts its clicks. */
    final View view = c -> column(c).padding(10).children(() ->
        scroll(c, st).size(200, 100).label("s").children(() -> {
            for (int i = 0; i < rows; i++) {
                if (box(c.key(i)).height(40).label("r" + i).clicked()) clicks[i]++;
            }
        }));

    public void testContentScrollsAndTheOffsetClamps() {
        Tester tt = Tester.of(view, 400, 300);
        tt.frame();
        Check.near(300, st.maxY());                               // 400 - 100
        tt.scroll("s", 0, 50);
        Check.near(50, st.y());
        Check.near(10 - 50, tt.find("r0").y());
        tt.scroll("s", 0, 1000);
        Check.near(300, st.y());
        Check.near(110, tt.find("r9").y() + 40);                  // the last row ends at the bottom: 10 + 100
        tt.scroll("s", 0, -5000);
        Check.near(0, st.y());
    }

    public void testScrollStateRequests() {
        Tester tt = Tester.of(view, 400, 300);
        st.scrollTo(120);                                         // unbound yet: kept until bound
        tt.frame();
        Check.near(120, st.y());
        Check.near(10, tt.find("r3").y());                        // 3 * 40 - 120 from the top
        st.scrollToEnd(); tt.frame(); Check.near(300, st.y());
        st.scrollToTop(); tt.frame(); Check.near(0, st.y());
    }

    public void testContentShrinkingClampsTheOffset() { // Review Focus 1
        Tester tt = Tester.of(view, 400, 300);
        tt.scroll("s", 0, 300);
        rows = 5;
        tt.frame();
        Check.near(100, st.y());                                  // 200 - 100
        Check.near(110, tt.find("r4").y() + 40);
    }

    public void testScrollingOverAButtonScrollsTheContainer() { // Review Focus 3
        Tester tt = Tester.of(c -> scroll(c, st).size(200, 100).children(() -> {
            for (int i = 0; i < 10; i++) button(c.key(i), "按钮 " + i).height(40);
        }), 400, 300);
        tt.scroll("按钮 1", 0, 30);
        Check.near(30, st.y());
    }

    public void testTheInnerContainerTakesTheWheelUntilItsEnd() {
        ScrollState inner = new ScrollState(), outer = new ScrollState();
        Tester tt = Tester.of(c -> scroll(c, outer).size(200, 100).children(() -> {
            scroll(c, inner).size(200, 80).label("in").children(() -> box(c).height(130));
            box(c).height(200);
        }), 400, 300);
        tt.scroll("in", 0, 40); tt.scroll("in", 0, 40);           // inner max is 50: 40, then 50 (10 is dropped)
        Check.near(50, inner.y()); Check.near(0, outer.y());
        tt.scroll("in", 0, 40);
        Check.near(40, outer.y());
    }

    public void testAScrolledRowIsHitWhereItIsAndNotOutsideTheViewport() { // Review Focus 5
        Tester tt = Tester.of(view, 400, 300);
        tt.scroll("s", 0, 50);
        Rect r2 = tt.find("r2");                                  // 10 + 80 - 50 = 40 .. 80
        tt.press(50, r2.y() + 5); tt.release(50, r2.y() + 5);
        Check.equal(1, clicks[2]);
        tt.press(50, 130); tt.release(50, 130);                   // below the viewport: r4 is laid out there, clipped
        Check.equal(0, clicks[4]);
    }

    public void testGrowingChildrenKeepTheirContentHeightInAScroll() {
        Tester tt = Tester.of(c -> scroll(c, st).size(200, 100).children(() ->
            column(c).grow(1).label("g").children(() -> box(c).height(300))), 400, 300);
        tt.frame();
        Check.near(300, tt.find("g").height());
        Check.near(200, st.maxY());
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test ScrollTest`。Expected：编译失败。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): scroll containers and ScrollState`。

---

### Task 7：滚轮平滑、键盘滚动、Tab 时滚进视野

**Files:**
- Modify: `Input.java`、`Engine.java`、`ElementState.java`、`FlexLayout.java` 或 `Engine.java`（布局之后的 reveal）、`Tester.java`
- Test: `src/test/java/jui/ui/ScrollTest.java`

**Interfaces:**
- **Consumes:** `Engine.scroll`（Task 6），帧时钟（Task 1）。
- **Produces：**
  - **滚轮平滑**（同 MyGO 的 `wheel.go:10-114`）：
    - 非精确事件不立刻改偏移，而是 `目标 += dy`，从当前值出发重新开始一条 150 ms 的 `EaseOut` 曲线；起点时间取事件发生时的时钟（引擎 `clock` 的当前值，Tester 里是它自己的时钟）；
    - 每帧开始时（建之前）按曲线推进各个正在滑动的容器，用夹紧的方式改偏移；
    - 到终点、动不了、或者应用 / 列表 / reveal 改了偏移，就停下；
    - 滑动中请求新帧；
    - 路由时，“还能动”要把已经在路上的滑动量算进去。
  - **`Tester.wheel(float x, float y, float dx, float dy)`** 和 **`Tester.wheel(String s, float dx, float dy)`**：发非精确事件。乘 40 是平台层的事，Tester 直接给 DIP。
  - **键盘滚动**（同 MyGO 的 `input.go:580-629`）：在 `keyDown` 里，排在 Tab、滑块按键、回车和空格激活之后。
    - 按键没被 `claimedBy` 认领时：`DOWN`/`UP` ±40；`PAGE_DOWN`、`SPACE` 往下滚 `max(视口 − 40, 视口 / 2)`，`PAGE_UP`、`Shift+SPACE` 往上滚同样的量；`HOME`/`END` ∓1e9；`Cmd+UP`/`Cmd+DOWN` 同 `HOME`/`END`。
    - 目标：焦点链里最内层还能往这个方向动的滚动容器，没有就用指针下的命中链。
    - 立刻生效，请求新帧；都没有目标时，按原来的规则进按键队列。
  - **Tab 时滚进视野**（同 MyGO 的 `scroll.go:238-296`）：
    - Tab 移动焦点后记下 `revealId`。
    - 下一帧布局之后，从内往外处理每一层滚动容器：视口是边框以内（不算内边距）；元素超出视口就按最少的量滚动，比视口高就让顶部对齐。
    - 然后在同一帧里重新排一次（引擎的“重排”循环见 Task 10，这里先实现为：改了偏移就再布局一次）。
    - `Element` 暂不提供 `scrollIntoView()`，M3 只在 Tab 时自动滚。

- [ ] **Step 1：写失败的测试**

```java
// ScrollTest（新增）
public void testTheWheelGlidesOver150Milliseconds() {
    Tester tt = Tester.of(view, 400, 300);
    tt.wheel("s", 0, 40);
    Check.near(0, st.y());                                    // settle runs frames at one time: no glide yet
    tt.advance(java.time.Duration.ofMillis(50));              // frames at 16.7, 33.3, 50.0 ms
    Check.near(28.15, st.y(), 0.1);                           // 40 * (1 - (1 - 1/3)^3)
    tt.advance(java.time.Duration.ofMillis(150));
    Check.near(40, st.y());
    Check.isTrue(!tt.framePending(), "the glide ended");
}

public void testWheelNotchesAddUp() {
    Tester tt = Tester.of(view, 400, 300);
    tt.wheel("s", 0, 40);
    tt.advance(java.time.Duration.ofMillis(30));
    tt.wheel("s", 0, 40);
    tt.advance(java.time.Duration.ofMillis(300));
    Check.near(80, st.y());
}

public void testKeysScrollTheFocusedContainer() {
    Tester tt = Tester.of(c -> scroll(c, st).size(200, 100).focusable().label("s").children(() -> box(c).height(400)), 400, 300);
    tt.click("s");
    tt.key(Shortcut.of(Key.DOWN));      Check.near(40, st.y());
    tt.key(Shortcut.of(Key.PAGE_DOWN)); Check.near(100, st.y());   // + max(100 - 40, 50)
    tt.key(Shortcut.of(Key.END));       Check.near(300, st.y());
    tt.key(Shortcut.of(Key.HOME));      Check.near(0, st.y());
    tt.key(Shortcut.of(Key.SPACE));     Check.near(60, st.y());
    tt.key(Shortcut.of(Mod.SHIFT, Key.SPACE)); Check.near(0, st.y());
}

public void testKeysScrollTheContainerUnderThePointerWithoutFocus() {
    Tester tt = Tester.of(view, 400, 300);
    tt.hover("s");
    tt.key(Shortcut.of(Key.DOWN));
    Check.near(40, st.y());
}

public void testAShortcutClaimsItsKeyFromScrolling() {
    int[] n = {0};
    Tester tt = Tester.of(c -> { if (c.shortcut(Shortcut.of(Key.DOWN))) n[0]++; view.build(c); }, 400, 300);
    tt.hover("s");
    tt.key(Shortcut.of(Key.DOWN));
    Check.equal(1, n[0]);
    Check.near(0, st.y());
}

public void testTabScrollsTheFocusIntoView() {
    Tester tt = Tester.of(c -> scroll(c, st).size(200, 100).children(() -> {
        for (int i = 0; i < 10; i++) box(c.key(i)).height(40).label("f" + i).focusable();
    }), 400, 300);
    for (int i = 0; i < 4; i++) tt.key(Shortcut.of(Key.TAB));      // f3: 120..160, below the 100-high view
    Check.isTrue(tt.focused("f3"), "f3 focused");
    Check.near(60, st.y());                                   // as little as shows it: 160 - 100
    Check.near(60, tt.find("f3").y());                        // its bottom at the view's bottom, in the same frame
}
```

- [ ] **Step 2**：运行 `./build.sh test ScrollTest`。Expected：编译失败（`wheel` 不存在）；补上 `wheel` 的空实现后，新增的测试失败。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): wheel glides, keyboard scrolling and revealing focus`。

---

### Task 8：滚动条

**Files:**
- Modify: `Theme.java`、`Paint.java`、`Node.java`、`Engine.java`、`Input.java`
- Test: `src/test/java/jui/ui/ScrollTest.java`、`src/test/java/jui/ui/ValuesTest.java`

**Interfaces:**
- **Consumes:** 滚动容器（Task 6）。
- **Produces**（同 MyGO 的 `paint.go:975-1093`、`input.go:223-243, 308, 1437-1485`）：
  - **`Theme` 新增两个字段**，放在最后：
    - `Color scrollbar`：浅色 `Color.rgba(0x000000, 0.32f)`，深色 `Color.rgba(0xFFFFFF, 0.35f)`；
    - `float scrollbarWidth`：6。
  - **何时显示**：提交时，滚动容器在悬停链里（指针在它或它的后代上），或者正在拖它的滑块，就显示；内容比视口高 0.5 以上才有滚动条。
  - **几何**：
    - 轨道：`x = 右边 − 6 − 6`，宽 `6 + 6`，从容器顶到底；
    - 滑块：`x = 右边 − 6 − 3`，宽 6，圆角 3；
    - 滑块长度：`inner = 轨道高 − 4`，`thumb = clamp(inner · 视口 / 内容, 24, inner)`；
    - 滑块位置：`y = 轨道顶 + 2 + (inner − thumb) · 偏移 / (内容 − 视口)`；
    - 拖动中滑块加宽 2。
  - **绘制**：在子元素之后、裁剪出栈之后画，盖在内容上。
  - **按下**：在普通的按下目标之前处理，取命中链里最内层、上一帧显示了滚动条、且轨道包含该点的容器。
    - 按在滑块上：开始拖动，记下起始偏移和当时的内容高度；
    - 按在滑块上方或下方：立刻翻 `0.9 × 视口`；
    - 两种情况都取消滚轮的滑动。按在滚动条上的事件不再交给内容。
  - **拖动**：`偏移 = clamp(起始偏移 / 起始可滚范围 + 移动量 / (inner − thumb), 0, 1) × 当前可滚范围`。这样列表的估计高度变化时，拖动仍然跟手。

- [ ] **Step 1：写失败的测试**

```java
// ValuesTest（新增）
public void testThemesHaveMyGosScrollbar() {
    Check.equal(Color.rgba(0x000000, 0.32f), Theme.light().scrollbar());
    Check.equal(Color.rgba(0xFFFFFF, 0.35f), Theme.dark().scrollbar());
    Check.near(6, Theme.light().scrollbarWidth());
}

// ScrollTest（新增）
/** The scroll bar's thumb, a 6-wide box in the theme's scrollbar color, or null. */
static float[] thumb(Tester tt) {
    for (int i = 0; i < tt.instances(); i++) {
        float[] in = tt.scene().instance(i);
        if (in[18] == 0 && Math.abs(in[2] - 6) < 0.01 && Math.abs(in[11] - 0.32f) < 0.01) return in;
    }
    return null;
}

public void testTheBarShowsWhileThePointerIsOverTheContainer() {
    Tester tt = Tester.of(view, 400, 300);
    tt.frame();
    Check.isTrue(thumb(tt) == null, "hidden without hover");
    tt.hover("s");
    float[] t = thumb(tt);
    Check.near(10 + 200 - 6 - 3, t[0]);
    Check.near(24, t[3]);                                     // 96 * 100 / 400 = 24, the minimum
    Check.near(10 + 2, t[1]);
}

public void testDraggingTheThumbScrolls() {
    Tester tt = Tester.of(view, 400, 300);
    tt.hover("s");
    float[] t = thumb(tt);
    float x = t[0] + 3, y = t[1] + 12;
    tt.press(x, y);
    tt.move(x, y + 36);                                       // half of the travel, 96 - 24 = 72
    Check.near(150, st.y());
    tt.release(x, y + 36);
}

public void testClickingTheTrackPages() {
    Tester tt = Tester.of(view, 400, 300);
    tt.hover("s");
    float[] t = thumb(tt);
    tt.press(t[0] + 3, t[1] + 60); tt.release(t[0] + 3, t[1] + 60);
    Check.near(90, st.y());                                   // 0.9 of the 100-high view
    Check.equal(0, clicks[1]);                                // the press stayed with the bar
}

public void testNoBarWhenTheContentFits() {
    rows = 2;
    Tester tt = Tester.of(view, 400, 300);
    tt.hover("s");
    Check.isTrue(thumb(tt) == null, "nothing to scroll");
}
```

- [ ] **Step 2**：运行 `./build.sh test ScrollTest` 和 `./build.sh test ValuesTest`。Expected：编译失败（`scrollbar` 不存在）；补上主题字段后，滚动条测试失败。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): overlay scroll bars`。

---

### Task 9：macOS 的滚轮事件

**Files:**
- Modify: `MacWindow.java`、`Window.java`
- Test: `src/test/java/jui/platform/mac/MacWindowInputTest.java`

**Interfaces:**
- **Consumes:** `Engine.scroll`（Task 6）。
- **Produces**（同 MyGO 的 `surface.go:876-889`）：
  - **`MacWindow.Input` 新增**：`void scroll(double x, double y, double dx, double dy, boolean precise, int mods)`。
  - **`JUIView` 覆写 `scrollWheel:`**：
    - 读 `scrollingDeltaX`/`scrollingDeltaY`，返回 double，用 `ObjC.sendDouble`；
    - 读 `hasPreciseScrollingDeltas`；
    - 不精确的乘 40，再取反：`dy = −scrollingDeltaY`，内容向上滚为正；
    - 位置按鼠标事件的方式换算。
  - **`Window`** 把它交给 `engine.scroll`。

- [ ] **Step 1：写失败的测试**：在 `MacWindowInputTest` 里新增 `testScrollWheelEventsReachTheInput`。
  1. 用 `CGEventCreateScrollWheelEvent2(0, units, 1, wheel1, 0, 0)` 造事件，加上 `[NSEvent eventWithCGEvent:]`。`CGEventCreateScrollWheelEvent2` 不是可变参数函数，签名是 `long (long, int, int, int, int, int)`，在 CoreGraphics 里。
  2. 先用像素单位（`units = 0`，`wheel1 = 10`，表示向上滚 10）调用 `ObjC.send(w.view(), "scrollWheel:", event)`。断言记录到 `precise == true` 且 `dy == -10`，误差 0.01。
  3. 再用行单位（`units = 1`，`wheel1 = 1`）。断言 `precise == false` 且 `dy < 0`，它的绝对值是事件 `scrollingDeltaY` 的 40 倍。
  4. 位置不断言：用 CGEvent 造的事件没有窗口坐标。
- [ ] **Step 2**：运行 `./build.sh test MacWindowInputTest`。Expected：编译失败（`Input.scroll` 不存在）。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(mac): scroll wheel and trackpad events`。

---

### Task 10：列表（虚拟化的核心）

**Files:**
- Create: `src/main/java/jui/ui/engine/ListModel.java`、`src/main/java/jui/ui/ListState.java`
- Modify: `Engine.java`、`FlexLayout.java`、`Node.java`、`Identity.java`（不改算法，只复用 `child(listId, index)`）、`Ui.java`
- Test: `src/test/java/jui/ui/ListTest.java`

**Interfaces:**
- **Consumes:** 滚动容器（Task 6），裁剪（Task 5）。
- **Produces：**
  - **`Ui.list(Context c, ListState state, int count, IntConsumer row)`**：
    - 一个纵向滚动容器，标志 `SCROLL | LIST`（`Node.LIST = 128`）；
    - `state` 为 null 时，用元素的局部状态 `local(e, "list", ListState::new)`；
    - `gap()` 设的是行与行之间的间距。
  - **`ListState`**（`jui.ui`）：
    - `scrollToTop()`、`scrollTo(int row)`、`int first()`、`int last()`，后两个在列表还没出现时为 −1；
    - 内部持有 `final jui.ui.engine.ListModel model`，包内可见。
  - **`ListModel`**（`jui.ui.engine`，公开的内部类，同 `ElementState` 的定位）。字段：
    - 行：`count`；行高 `heights`（`float[]`，NaN 表示没量过）、已量行数 `known`、已量总和 `knownSum`（double）；`gap`；
    - 锚点：`anchor`、`anchorY`（锚点行顶部相对视口顶部的位置，`≤ 0`）；
    - 偏移：`wroteY`（上次提交写出的偏移）、`requestY`（NaN 表示没有）、`requestRow`（−1 表示没有）；
    - 范围：`first`/`last`（可见行）、`builtFirst`/`builtLast`。
    - 派生量：
      - `estimate() = known > 0 ? knownSum / known : 32`；
      - `height(i)`：量过的值，没量过取 `estimate()`；
      - `top(i) = Σ_{j<i}(height(j) + gap)`，O(i)；
      - `content() = knownSum + (count − known) · estimate() + gap · (count − 1)`，O(1)。
  - **建行之前**：`int Engine.listPlan(int index, ListModel m, int count)`，在 `Ui.list` 里调用，返回要建的第一行。
    1. **行数变了**：调整 `heights` 的长度，丢掉超出部分；`anchor = min(anchor, count − 1)`。
    2. **视口高 `v`**：
       - 容器设了固定高度，就用它；
       - 否则用 `ListModel.viewH`：最近一轮布局量出的列表内容框高。同一帧的第二轮用第一轮刚量出的值，所以窗口变高时第二轮就能补满；
       - 第一帧用窗口高度。
    3. **偏移的变化**，按顺序取第一个适用的：
       - 有 `requestRow`：`anchor = requestRow`，`anchorY = 0`；
       - 有 `requestY`：`anchorAt(夹紧后的 requestY)`；
       - 偏移（请求的，或事件写进状态的）离最大值不到 0.5：按“到底”处理，同 MyGO。从最后一行往上累加行高，直到盖满 `v`，以那一行为锚点，让最后一行的底部对齐视口底部；
       - 否则 `d = 状态的 scrollY − wroteY`（事件、滑块拖动写进状态的量）：`|d| ≤ 2v` 时 `anchorY −= d`，否则 `anchorAt(scrollY)`。
       - `anchorAt(y)`：从第 0 行累加，找到第一个 `top(i) + height(i) > y` 的行 `i`，令 `anchor = i`，`anchorY = top(i) − y`。
    4. **规范化**：
       - 当 `anchor < count − 1` 且 `anchorY + height(anchor) + gap ≤ 0` 时，`anchorY += height(anchor) + gap`，`anchor++`；
       - 当 `anchor > 0` 且 `anchorY > 0` 时，`anchor--`，`anchorY −= height(anchor) + gap`。
    5. **要建的行**：
       - `first = max(0, anchor − 2)`；
       - 从 `anchor` 出发、`y = anchorY`，逐行 `y += height + gap`，直到 `y ≥ v` 或到最后一行；
       - `last = min(count − 1, 最后一行 + 2)`；
       - `Node.listFirstY = anchorY − Σ_{first ≤ j < anchor}(height(j) + gap)`。
    6. **建行**：`Ui.list` 依次对 `first..last` 调用 `row.accept(i)`。
       - 每次调用前，`Engine.rowIndex(i)` 让下一个元素默认用身份 `Identity.child(listId, i)`；回调里的 `c.key` 会取代它。
       - 每次调用必须恰好建出一个顶层元素，开发模式检查。
       - 用 `Engine.enter/exit` 建，不用 lambda 包装。
  - **布局**（`FlexLayout`）：列表的子元素按列排，每行宽度等于内容框宽，高度等于内容高度（不伸缩）；第一行的顶部在 `内容框顶 + listFirstY`。
  - **布局之后**：`Engine.afterLayout()` 在 `commit` 之前对每个列表执行：
    1. 把建了的行的实际高度记进 `heights`，更新 `known` 和 `knownSum`；把列表这一轮的内容框高记进 `viewH`。
    2. 重新确定锚点：第一行底部超过视口顶部的建了的行；`anchorY` 是它的顶部相对视口顶部的位置。
    3. **两端修正**：
       - 建了最后一行、且它的底部高于视口底部：`anchorY` 下移这个差，但第 0 行的顶部不得低于视口顶部；
       - 建了第 0 行、且它的顶部低于视口顶部：上移。
       - 发生修正时，要求重排。
    4. **覆盖检查**：要求顶部被盖住（第一行顶部 ≤ 视口顶部，或者第一行就是第 0 行），并且底部被盖住（最后一行底部 ≥ 视口底部，或者最后一行就是最后一行）。没盖住就要求重排。
    5. **偏移**：`scrollY = clamp(top(anchor) − anchorY, 0, max(0, content() − v))`；写进状态和 `wroteY`；`contentH = content() + 内边距`。
    6. 清掉 `requestY` 和 `requestRow`；记下 `first`、`last`。
  - **重排循环**：`buildAndLayout` 在“构建（最多 3 遍）→ 布局 → 布局之后”这一轮后，如果有列表或 reveal（Task 7）要求重排，就再来一轮，每帧最多两轮。第二轮的构建不会重复报告输入，因为输入在第一轮的遍末已经清掉了。

- [ ] **Step 1：写失败的测试**

```java
public final class ListTest {
    final ListState state = new ListState();
    int count = 10_000;

    static float varied(int i) { return 20 + i * 7 % 45; }

    /** A list 200 high, at y = 0, of rows varied(i) high labeled row0, row1, … */
    final View view = c -> list(c, state, count, i -> box(c).height(varied(i)).label("row" + i)).height(200).label("list");

    /** The labels of built rows, in order. */
    static java.util.List<Integer> built(Tester tt) { /* scan "row" + i for i in 0..count via tt.engine().identity(...) != 0 */ return null; }

    /** Visible rows meet end to end and cover the 200-high view. */
    static void checkCovered(Tester tt, ListState s) {
        float y = tt.find("row" + s.first()).y();
        Check.isTrue(y <= 0.01, "the first visible row starts at or above the top: " + y);
        for (int i = s.first(); i < s.last(); i++) {
            Rect a = tt.find("row" + i), b = tt.find("row" + (i + 1));
            Check.near(a.y() + a.height(), b.y());
        }
        Rect l = tt.find("row" + s.last());
        Check.isTrue(l.y() + l.height() >= 200 - 0.01 || s.last() == 9_999, "the view is covered");
    }

    public void testOnlyTheRowsInViewAreBuiltAndTheFirstFrameIsFull() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        checkCovered(tt, state);
        int visible = state.last() - state.first() + 1;
        Check.isTrue(tt.engine().nodeCount() <= visible + 2 + 2 + 3, "built " + tt.engine().nodeCount() + " nodes");   // rows + root + list
        Check.isTrue(tt.engine().identity("row40") == 0, "row 40 is not built");
    }

    public void testScrollingMovesRowsByExactlyTheDelta() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        float y = tt.find("row3").y();
        tt.scroll("list", 0, 13);
        Check.near(y - 13, tt.find("row3").y());
    }

    public void testVariedRowsStayEndToEndWhileScrolling() {
        Tester tt = Tester.of(view, 300, 400);
        for (int k = 0; k < 60; k++) {
            tt.scroll("list", 0, 37);
            checkCovered(tt, state);
        }
        for (int k = 0; k < 60; k++) {
            tt.scroll("list", 0, -37);
            checkCovered(tt, state);
        }
        Check.equal(0, state.first());
        Check.near(0, tt.find("row0").y());
    }

    public void testRowsKeyedInTheCallbackKeepTheirStateWhenItemsMove() {
        java.util.List<Integer> items = new java.util.ArrayList<>(java.util.List.of(10, 11, 12, 13));
        Tester tt = Tester.of(c -> list(c, state, items.size(), i -> {
            int id = items.get(i);
            Element r = box(c.key(id)).height(30).label("item" + id);
            if (r.clicked()) localBool(r, "picked", false).set(true);
            if (localBool(r, "picked", false).get()) r.background(Color.rgb(0xFF0000));
        }).height(200), 300, 400);
        tt.click("item12");
        items.remove(0);                          // item12 moves from row 2 to row 1
        tt.frame();
        Check.isTrue(tt.engine().node(nodeOf(tt, "item12")).background != 0, "its local state followed the key");
    }

    public void testANullStateKeepsTheOffsetInTheList() {
        Tester tt = Tester.of(c -> list(c, null, 1000, i -> box(c).height(30).label("row" + i)).height(200).label("list"), 300, 400);
        tt.scroll("list", 0, 300);
        tt.frame(); tt.frame();
        Check.near(0, tt.find("row10").y());      // 10 * 30 - 300
    }

    public void testARowMustBuildOneElement() {
        Tester tt = Tester.of(c -> list(c, state, 3, i -> { box(c).height(10); box(c).height(10); }).height(200), 300, 400);
        Check.fails(IllegalStateException.class, tt::frame);
    }

    public void testAShortListStartsAtTheTop() {
        count = 3;
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        Check.near(0, tt.find("row0").y());
        Check.equal(0, state.first()); Check.equal(2, state.last());
    }
}
```

`nodeOf(tt, label)` 是测试里的辅助方法：按标签找到节点下标，可以用 `Engine` 现有的查询，或者在测试里遍历 `nodeCount()`。`built(tt)` 如果用不到可以删掉，以 `checkCovered` 为准。

- [ ] **Step 2**：运行 `./build.sh test ListTest`。Expected：编译失败（`list`、`ListState` 不存在）。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): virtualized lists with rows of any height`。

---

### Task 11：列表的跳转、行数变化、窗口大小和 Tab

**Files:**
- Modify: `ListModel.java`、`ListState.java`、`Engine.java`、`Input.java`（滑块拖动对列表）
- Test: `src/test/java/jui/ui/ListTest.java`

**Interfaces:**
- **Consumes:** Task 10 的全部内容；滚动条（Task 8）；Tab 时滚进视野（Task 7）。
- **Produces：**
  - `ListState.scrollToTop()` 设 `requestRow = 0`；`scrollTo(row)` 设 `requestRow = clamp(row, 0, count − 1)`。
  - **滑块拖动**：写的是状态里的绝对偏移，`listPlan` 发现变化超过 2 个视口时改用 `anchorAt`，所以拖到哪儿都准。
  - **行数变少**：锚点夹到新的末行；两端修正把末行拉到视口底部。
  - **窗口变大**：视口变高时，覆盖检查触发同一帧的重排，补建缺的行。
  - **Tab**：列表上下各多建的 2 行让 Tab 能走到视口外的下一行，reveal 把它滚进视野。对列表来说，reveal 改的是 `anchorY`，并要求重排。

- [ ] **Step 1：写失败的测试**

```java
// ListTest（新增）
public void testScrollToTopAndToARow() {
    Tester tt = Tester.of(view, 300, 400);
    state.scrollTo(500); tt.frame();
    Check.equal(500, state.first());
    Check.near(0, tt.find("row500").y());
    state.scrollToTop(); tt.frame();
    Check.equal(0, state.first());
    Check.near(0, tt.find("row0").y());
}

public void testDraggingTheThumbToTheEndAndBackReachesBothEnds() { // Review Focus 4
    count = 1000;
    Tester tt = Tester.of(view, 300, 400);
    tt.hover("list");
    float[] t = ScrollTest.thumb(tt);
    float x = t[0] + 3, y = t[1] + 12;
    tt.press(x, y);
    tt.move(x, y + 400);                         // past the end
    Check.equal(999, state.last());
    Rect last = tt.find("row999");
    Check.near(200, last.y() + last.height());
    checkCovered(tt, state);
    tt.move(x, y - 400);                         // past the top
    Check.equal(0, state.first());
    Check.near(0, tt.find("row0").y());
    checkCovered(tt, state);
    tt.release(x, y - 400);
}

public void testFewerRowsClampTheList() { // Review Focus 1
    count = 1000;
    Tester tt = Tester.of(view, 300, 400);
    state.scrollTo(900); tt.frame();
    count = 100;
    tt.frame();
    Check.equal(99, state.last());
    Rect last = tt.find("row99");
    Check.near(200, last.y() + last.height());
}

public void testATallerWindowIsFilledInTheSameFrame() { // Review Focus 2
    Tester tt = Tester.of(c -> list(c, state, 10_000, i -> box(c).height(varied(i)).label("row" + i)).fill().label("list"), 300, 200);
    tt.scroll("list", 0, 5000);
    tt.resize(300, 600);
    tt.frame();
    Rect l = tt.find("row" + state.last());
    Check.isTrue(l.y() + l.height() >= 600 - 0.01, "the 600-high view is covered in one frame");
}

public void testTabWalksDownTheRowsAndRevealsThem() {
    Tester tt = Tester.of(c -> list(c, state, 1000, i -> box(c).height(30).label("row" + i).focusable()).height(200).label("list"), 300, 400);
    for (int i = 0; i < 12; i++) tt.key(Shortcut.of(Key.TAB));
    Check.isTrue(tt.focused("row11"), "row 11 has focus");
    Rect r = tt.find("row11");
    Check.near(200, r.y() + r.height());      // revealed: its bottom at the view's bottom
}

public void testTheWheelGlidesAList() {
    count = 1000;
    Tester tt = Tester.of(c -> list(c, state, count, i -> box(c).height(30).label("row" + i)).height(200).label("list"), 300, 400);
    tt.wheel("list", 0, 40);
    tt.advance(java.time.Duration.ofMillis(300));
    Check.near(-10, tt.find("row1").y());     // 30 - 40
}
```

- [ ] **Step 2**：运行 `./build.sh test ListTest`。Expected：新增的测试有失败（`scrollTo` 还没实现等）；已有的测试仍然通过。
- [ ] **Step 3**：实现。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交 `feat(ui): list jumps, count changes, resizing and tab`。

---

### Task 12：验收示例、滚动帧耗时门槛和 README

**Files:**
- Create: `src/main/java/jui/examples/FilesDemo.java`、`src/main/java/jui/spike/ListBench.java`
- Modify: `src/main/java/jui/spike/AllocSpike.java`、`build.sh`、`README.md`
- Test: `src/test/java/jui/examples/FilesDemoTest.java`

**Interfaces:**
- **Consumes:** 前面所有任务。
- **Produces：**
  - **`FilesDemo`**：1 万篇笔记的文件列表。
    - **标题**：用固定的伪随机种子，从约 200 个常用词里组合，例如 `"笔记 0042 · 会议纪要 产品规划"`。
      - 用有限的词表，是为了让字形数量留在图集容量内；图集的扩容和回收在 M6 做。
      - 大约每 7 篇里有 1 篇带一行摘要，行高因此不同。
    - **布局**：
      - 顶部一行：`"10000 篇笔记"`，和 `ListState` 的 `first()`–`last()`，例如 `"第 1–12 篇"`；
      - 下面是 `list(c, files, notes.size(), i -> noteRow(...))`，`grow(1)`；
      - 底部是 `button(c, "回到顶部")`，点击调用 `files.scrollToTop()`。
    - **行**：
      - 点击选中，选中的行背景是 `accent`、文字是 `accentText`；
      - 悬停时背景是 `surfaceHover`；
      - 行用 `c.key(note.id)` 指定 key。
    - 窗口 360×640。
  - **`ListBench`**（`jui.spike`）：
    - 用无窗口的 `Engine` + `Scene` + `TextEngine`，建 `FilesDemo` 的视图，窗口 360×640；
    - 先预热 300 帧，再从顶部开始，每帧精确滚动 24 DIP，共 3000 帧；
    - 每帧测 `buildAndLayout` 加 `paint` 的纳秒数，并统计这一帧构建的行数；
    - 输出 `LISTBENCH p50=… p95=… max=… rows_max=… visible_max=…`；
    - native 下，`p95 > 4 ms`、`max > 8 ms` 或 `rows_max > visible_max + 5` 时以非零码退出。
  - **`AllocSpike`**：新增 `list_frame` 场景：1 万行的列表，内容不变的帧里每个可见行的分配，扣除空帧。native 门槛是不超过 0.5 B/行。
  - **`build.sh`**：新增 `listbench` 命令，编译 native 的 `jui.spike.ListBench` 并运行；更新用法说明。
  - **README**：增加 “M3 滚动和列表” 一节，写清楚：
    - 有哪些 API；
    - 示例截图和运行命令；
    - `listbench` 和 `alloc` 的数字；
    - 推迟的内容（本计划“定下的细节”第 2、3、5、9、10、11 条）；
    - 需要手动核对的项目：触控板滚动和惯性、滚轮平滑、滚动条拖动、Tab 滚进视野、遮挡后恢复。

- [ ] **Step 1：写失败的测试**

```java
public final class FilesDemoTest {
    public void testTheListShowsTenThousandNotesAndSelectsOnClick() {
        FilesDemo s = new FilesDemo();
        Tester tt = Tester.of(s::view, 360, 640);
        Check.isTrue(tt.hasText("10000 篇笔记"), "the count");
        String second = s.notes.get(1).title;
        tt.click(second);
        Check.equal(s.notes.get(1), s.selected);
    }

    public void testScrollingToTheEndShowsTheLastNoteAndBackToTop() {
        FilesDemo s = new FilesDemo();
        Tester tt = Tester.of(s::view, 360, 640);
        s.files.scrollTo(9_999);
        tt.frame();
        Check.isTrue(tt.hasText(s.notes.get(9_999).title), "the last note");
        tt.click("回到顶部");
        Check.equal(0, s.files.first());
    }
}
```

`FilesDemo.notes`、`selected`、`files` 和 `Note.title` 都设为包内可见。

- [ ] **Step 2**：运行 `./build.sh test FilesDemoTest`。Expected：编译失败。
- [ ] **Step 3**：实现 `FilesDemo`；运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 4**：实现 `ListBench` 和 `AllocSpike` 的列表场景，然后运行 `./build.sh listbench` 和 `./build.sh alloc`。Expected：
  - 两者都以 0 退出；
  - native 下滚动帧 p95 ≤ 4 ms、max ≤ 8 ms，`list_frame` ≤ 0.5 B/行，卡片整帧不超过 32 KB。
  - 达不到时，用分段计时找到原因并修掉，不能放宽门槛。
- [ ] **Step 5**：运行 `./build.sh native-demo Files --print-window` 截图，存到 `build/shot-files.png`，复制一份到 `docs/m3-files.png`。
- [ ] **Step 6**：更新 README。
- [ ] **Step 7**：提交 `feat(examples): a list of 10000 notes, with a scroll frame gate`。

---

## 完成标准

- `./build.sh test` 全部通过。
- `./build.sh alloc` 以 0 退出：控件和列表的帧在 native 下每元素不超过 0.5 B，卡片整帧不超过 32 KB。
- `./build.sh listbench` 以 0 退出：1 万行列表的滚动帧 p95 ≤ 4 ms、max ≤ 8 ms，只构建可见行。
- 文件列表示例在 native image 下跑通，有截图；需要手动核对的项目写进 README，交给用户核对。
- 最后对整个分支做一次审查（executing-plans 的 Final Review），再按 finishing-a-development-branch 决定怎么合并。
