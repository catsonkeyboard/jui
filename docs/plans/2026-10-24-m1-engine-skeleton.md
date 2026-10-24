# M1 引擎骨架 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal**：实现设计文档里的 M1。先在 native image 上实测内存分配，必要时调整 API；然后完成 MyGO 风格 API 的骨架：`Context`、`Element`、节点池、身份和 key、`box`/`row`/`column`/`spacer`/`divider`/`text`、不含换行和绝对定位的 flexbox、绘制到 `Scene`、按需出帧、基础版 `Tester`。最后用新 API 重写原型的卡片示例。

**Architecture**：
- `jui.ui` 是公开 API：静态控件函数 `Ui`、每个窗口一个的 `Context`、`Element` 句柄；
- `jui.ui.engine` 是内部实现：节点池、身份、flexbox、绘制；
- `jui.app` 用现有的 AppKit 窗口承载引擎；
- 原型里的 `jui.mac` 和 `jui.metal` 按设计第 9 节迁到 `jui.platform.mac` 和 `jui.render`。

每帧的流程：构建（重置节点池，调用视图）→ 布局 → 绘制到 `Scene` → `MetalRenderer`。

**Tech Stack**：
- Java 25、GraalVM CE 25（`graalvm-community-25.4.4.1.1+1.1`）；
- FFM（`jui.ffi`）、AppKit、Metal、Core Text；
- 零依赖：自己写的测试运行器，构建继续用 `build.sh`。

**Spec**：[docs/design/2026-10-24-ui-api-design.md](../design/2026-10-24-ui-api-design.md)，已定稿。本计划实现其中的 M1（第 10 节），API 依据第 6 节，内部设计依据第 7 节，分配规则依据第 8 节。

## Global Constraints

- **平台**：Apple Silicon、macOS 14 及以上。`build.sh` 里的 `MACOS_MIN` 改为 `14.0`。
- **原生调用**：全部走 FFM，通过 `jui.ffi.Native` 不绑定地址的句柄调用；新的签名登记在 `jui.ffi.Sigs`。不用 JNI，不写 C 或 Objective-C 代码（`baseline/spike.m` 是对照组，除外）。
- **不用反射**：主代码在运行时不用反射，也不在运行时生成代码。只有测试运行器可以用反射。
- **零第三方依赖**：Maven Central 当前拒绝本机出口 IP 的下载，也不要尝试绕过封禁。
- **线程**：UI 函数只能在主线程调用。JVM 下用 `-XstartOnFirstThread` 运行，测试也一样。
- **开发模式**：在 JVM 上运行，或设置了 `-Djui.dev=true` 时开启。开启时，误用 API 抛 `IllegalStateException`；关闭时忽略。
- **API 命名**：按设计第 6 节，和 MyGO 对应。
- **类型约定**：长度单位是 DIP，类型为 `float`；颜色是 `jui.ui.Color` record，内部是一个 `int` 0xRRGGBBAA。
- **注释和文档**：代码注释用英文，和现有代码一致；文档用中文。
- **提交信息**：每条提交信息末尾加上 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- **分配门槛**（设计第 8 节）：
  - 框架内部：内容不变的帧里，不能产生和元素数量成正比的分配；
  - 整帧：以基准外壳为准，每帧不超过 32 KB（32768 字节）。

## Review Focus

测试没有直接覆盖、但最容易让使用者踩坑的 5 种情况，以及大多数人会期望的行为：

1. **一行里，文字和会伸缩的兄弟元素挨在一起**（比如文字设了 `grow(1)`，旁边是固定宽度的按钮）：文字应该在剩下的宽度内换行，而不是溢出。测试放在 Task 6。
2. **窗口缩到极小，或者内边距比容器还大**：所有尺寸都应该不小于 0，不出现 NaN，也不崩溃。测试放在 Task 5。
3. **空字符串，以及没有空格的长串**（比如一个很长的网址）：空字符串宽高都是 0，没有字形；长串在窄容器里应该按字符换行，宽度不超过容器。测试放在 Task 6。
4. **`Element` 被存进字段，在后面的帧里再用**：开发模式下应该抛出说明清楚的异常。测试放在 Task 3。
5. **窗口在 2 倍屏和 1 倍屏之间移动**：方框的位置和尺寸（DIP）不变，字形按新的缩放比例重新光栅化，像素尺寸随之变化。测试放在 Task 7。

---

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `src/test/java/jui/testing/Check.java`、`RunTests.java` | 零依赖的断言和测试运行器 | 1 |
| `src/main/java/jui/platform/mac/{MacApp,MacWindow}.java` | 由 `jui.mac` 迁来；`MacWindow` 改为接收外部传入的 `TextEngine`，并新增 `requestFrame()` | 1、8 |
| `src/main/java/jui/render/Scene.java`、`jui/render/metal/MetalRenderer.java` | 由 `jui.metal` 迁来；`Scene` 新增 `instance(int)`，供测试读取 | 1、7 |
| `src/main/java/jui/ui/{Color,Theme,Align,Justify,Rect}.java` | 公开的值类型 | 2 |
| `src/main/java/jui/ui/{View,Context,Element,Ui}.java` | 公开 API | 3、5、6、7 |
| `src/main/java/jui/ui/BoolRef.java` | 引用对象（分配实测要用到） | 4 |
| `src/main/java/jui/ui/engine/{Node,NodePool,Identity,Dev,Engine}.java` | 节点、池、身份、开发模式检查、每帧流程 | 3 |
| `src/main/java/jui/ui/engine/FlexLayout.java` | flexbox | 5 |
| `src/main/java/jui/ui/engine/Paint.java` | 把节点树绘制到 `Scene` | 7 |
| `src/main/java/jui/ui/Tester.java` | 不开窗口测试视图 | 8 |
| `src/main/java/jui/app/{App,Window,WindowOptions}.java` | 应用和窗口 | 8 |
| `src/main/java/jui/spike/AllocSpike.java` | native image 上的分配实测 | 4 |
| `src/main/java/jui/examples/CardDemo.java` | 验收示例 | 9 |
| `build.sh` | 新增 `test`、`alloc`、`demo`、`native-demo` 命令 | 1、4、9 |

---

### Task 0：版本控制

项目目录目前不是 git 仓库。用户批准本计划，即视为同意初始化仓库。

- [ ] **Step 1**：在 `j-ui` 执行 `git init -b main`，确认 `.gitignore` 里有 `build/`。
- [ ] **Step 2**：提交现有代码：

```bash
git add -A
git commit -m "chore: import the macOS spike (FFM, Metal, Core Text) and the UI design

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

预期结果：`git status` 显示工作区干净。

---

### Task 1：测试运行器与包结构迁移

**Files:**
- Create：`src/test/java/jui/testing/Check.java`、`src/test/java/jui/testing/RunTests.java`、`src/test/java/jui/text/TextEngineTest.java`
- Move：`src/main/java/jui/mac/*` → `src/main/java/jui/platform/mac/`；`src/main/java/jui/metal/Scene.java` → `src/main/java/jui/render/Scene.java`；`src/main/java/jui/metal/MetalRenderer.java` → `src/main/java/jui/render/metal/MetalRenderer.java`
- Modify：`build.sh`，以及所有引用这些包的 import（`jui.spike.Main` 等）

**Interfaces:**
- Produces：
  - `jui.testing.Check`：
    - `equal(Object expected, Object actual)`、`equal(long expected, long actual)`；
    - `near(double expected, double actual)`（容差 0.01）、`near(double, double, double tolerance)`；
    - `isTrue(boolean, String message)`；
    - `<T extends Throwable> T fails(Class<T> type, Runnable code)`；
    - 断言失败时抛 `AssertionError("expected <e> but was <a>")`。
  - `RunTests`：
    - 扫描 `build/test-classes` 下所有以 `Test` 结尾的类，用无参构造函数创建实例；
    - 按方法名顺序运行所有 `public void test*()` 方法，每条输出 `PASS`/`FAIL 类名#方法: 消息`；
    - 最后输出 `N passed, M failed`；有失败、或一条测试都没找到时，退出码为 1；
    - 可选参数用来过滤，格式是 `类名` 或 `类名#方法`。
  - `build.sh test [过滤条件]`：
    - 编译主代码到 `build/classes`，编译测试到 `build/test-classes`；
    - 运行：`java -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -Djui.dev=true -cp build/classes:build/test-classes jui.testing.RunTests [过滤条件]`。
  - `Scene.writeTo(MemorySegment, int)` 改为 `public`，因为迁移后 `MetalRenderer` 和 `Scene` 不再在同一个包里。

- [ ] **Step 1：先写测试**，固定现有的、设计里给出公式的行为，同时验证运行器能工作

```java
// src/test/java/jui/text/TextEngineTest.java
public final class TextEngineTest {
    public void testPositionsFollowCoreGraphicsQuantization() {
        Check.equal(2, TextEngine.positions(16, 2));  // 32 px: ceil(100/96) = 2
        Check.equal(1, TextEngine.positions(17, 2));  // 34 px: one position from 34 px an em
        Check.equal(5, TextEngine.positions(6, 1));   // 6 px: ceil(100/18) = 6, at most 5
    }
    public void testShadeIsLuminanceInQuarters() {
        Check.equal(0, TextEngine.shadeOf(0x111827FF));
        Check.equal(3, TextEngine.shadeOf(0xFFFFFFFF)); // at most 3/4
    }
}
```

- [ ] **Step 2**：实现 `Check`、`RunTests` 和 `build.sh test`。
- [ ] **Step 3**：运行 `./build.sh test`。预期输出 `2 passed, 0 failed`。
- [ ] **Step 4**：用 `git mv` 迁移包，修正所有 import，把 `build.sh` 里的 `MACOS_MIN` 改为 `14.0`。
- [ ] **Step 5**：验证迁移没有破坏原型：
  - 运行 `./build.sh test`，预期全部通过；
  - 运行 `./build.sh native && ./build/jui-spike --bench`，预期输出 `BENCH mode=native` 和 `TEXT` 两行，退出码为 0；
  - 用 `otool -l build/jui-spike | grep -A4 LC_BUILD_VERSION` 查看，预期 `minos 14.0`。
- [ ] **Step 6：提交**

```bash
git add -A
git commit -m "build: zero-dependency test runner; move mac and metal into platform and render

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2：公开值类型

**Files:**
- Create：`src/main/java/jui/ui/{Color,Theme,Align,Justify,Rect}.java`
- Test：`src/test/java/jui/ui/ValuesTest.java`

**Interfaces:**
- Produces：
  - `record Color(int rgba)`：
    - `static Color rgb(int rgb)`：alpha 为 0xFF；
    - `static Color rgba(int rgb, float alpha)`：alpha 按 `Math.round(alpha * 255)` 换算；
    - `boolean transparent()`：alpha 为 0 时返回 true；
    - `static final Color TRANSPARENT`。
  - `record Theme(Color background, Color surface, Color surfaceHover, Color border, Color text, Color textMuted, Color accent, Color accentText, Color selection, Color focusRing, float fontSize, float radius)`：
    - `static Theme light()`，取值见测试。
  - `enum Align { START, CENTER, END, STRETCH }`：`BASELINE` 不在本期范围（设计 6.8）。
  - `enum Justify { START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }`。
  - `record Rect(float x, float y, float width, float height)`。

- [ ] **Step 1：先写测试**

```java
public final class ValuesTest {
    public void testColorPacksRgba() {
        Check.equal(0x2563EBFF, Color.rgb(0x2563EB).rgba());
        Check.equal(0x2563EB80, Color.rgba(0x2563EB, 0.5f).rgba()); // round(127.5) = 128
        Check.isTrue(Color.TRANSPARENT.transparent(), "transparent");
    }
    public void testLightTheme() {
        Theme t = Theme.light();
        Check.equal(0xFFFFFFFF, t.background().rgba());
        Check.equal(0xF3F4F6FF, t.surface().rgba());
        Check.equal(0xE5E7EBFF, t.surfaceHover().rgba());
        Check.equal(0xE5E7EBFF, t.border().rgba());
        Check.equal(0x111827FF, t.text().rgba());
        Check.equal(0x6B7280FF, t.textMuted().rgba());
        Check.equal(0x2563EBFF, t.accent().rgba());
        Check.equal(0xFFFFFFFF, t.accentText().rgba());
        Check.equal(0xBFDBFEFF, t.selection().rgba());
        Check.equal(0x3B82F6FF, t.focusRing().rgba());
        Check.near(13, t.fontSize());
        Check.near(6, t.radius());
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test ValuesTest`。预期编译失败（类还不存在）。
- [ ] **Step 3**：实现这 5 个类型。
- [ ] **Step 4**：运行 `./build.sh test ValuesTest`。预期全部通过。
- [ ] **Step 5：提交**：`git commit -m "feat(ui): Color, Theme, Align, Justify, Rect"`（末尾加上 Co-Authored-By 行，下同）。

---

### Task 3：节点池、身份、Context 与 Element

**Files:**
- Create：`src/main/java/jui/ui/{View,Context,Element,Ui}.java`、`src/main/java/jui/ui/engine/{Node,NodePool,Identity,Dev,Engine}.java`
- Test：`src/test/java/jui/ui/engine/BuildTest.java`

**Interfaces:**
- Consumes：Task 2 的 `Theme`、`Rect`。
- Produces：
  - `@FunctionalInterface interface View { void build(Context c); }`
  - `Node`：引擎内部类，用 public 字段。后续任务都靠这些字段名衔接：
    - **结构**：`long id`、`int gen`、`int parent`、`int firstChild`、`int lastChild`、`int nextSibling`、`int childCount`、`byte kind`（常量 `BOX`、`TEXT`、`SPACER`、`DIVIDER`）、`String label`。
    - **布局输入**（Task 5 读取）：
      - `boolean row`、`boolean reverse`；
      - `float width`、`height`、`minWidth`、`maxWidth`、`minHeight`、`maxHeight`：NaN 表示自动，或没有上下限；
      - `float widthPercent`、`heightPercent`：NaN 表示未设置；
      - `float grow`（默认 0）、`shrink`（默认 1）、`basis`（NaN 表示自动）、`gap`；
      - `float padTop`、`padRight`、`padBottom`、`padLeft`；
      - `float marTop`、`marRight`、`marBottom`、`marLeft`：`Ui.AUTO` 表示自动外边距；
      - `float border`：边框宽度，同时算作内边距的一部分；
      - `Justify justify`、`Align alignItems`、`Align alignSelf`（null 表示跟随父元素的 `alignItems`）。
    - **文字输入**（Task 6 读取）：`String text`、`int textColor`（0 表示继承）、`float fontSize`（NaN 表示继承）、`int fontWeight`（0 表示继承）、`boolean singleLine`。
    - **视觉**（Task 7 读取）：`int background`、`int borderColor`、`int shadowColor`、`float radiusTL`、`radiusTR`、`radiusBR`、`radiusBL`、`float shadowX`、`shadowY`、`shadowBlur`、`shadowSpread`。
    - **布局结果**（Task 5、6 写入）：`float x`、`y`、`w`、`h`（DIP，从窗口左上角算起）、`TextLayout layout`、`int resolvedColor`。
    - `void reset()`：把所有字段恢复为上面的默认值。
  - `NodePool`：
    - 按块分配，每块 32 个节点；
    - `void reset()`：每帧开始时调用，所有节点重新可用；
    - `int open()`：取一个节点，`gen` 加 1，调用 `reset()`，返回编号；
    - `Node node(int index)`；
    - `int chunks()`：当前分配了多少块。
  - `Identity`：
    - `static long child(long parentId, int position)`：未指定 key 时用位置；
    - `static long keyed(long parentId, long keyHash)`：指定了 key 时用；
    - 两者用不同的盐值，再经过 splitmix64 混合，保证“位置 n”和“key 为 n”算出的身份不会相同；
    - `static long hash(String s)`：64 位 FNV-1a。
  - `Dev`：
    - `static final boolean ENABLED`：设置了 `-Djui.dev=true`，或系统属性 `org.graalvm.nativeimage.imagecode` 为空（即运行在 JVM 上）时为 true；
    - `static void fail(String message)`：开发模式下抛 `IllegalStateException`，否则什么都不做。
  - `Engine`（内部类，`public` 方法也只供框架内部使用）：
    - `Engine(TextEngine text, Theme theme, Runnable requestFrame)`：`text` 为 null 时只能构建和布局不含文字的视图；
    - `Context context()`、`Theme theme()`；
    - `void buildAndLayout(View view, float width, float height)`：本任务只做构建，布局在 Task 5 加入；
    - `long identity(String labelOrText)`、`Rect bounds(String labelOrText)`：找不到时分别返回 0 和 null；
    - `int nodeCount()`、`int poolChunks()`；
    - 根节点是一个 column，尺寸等于窗口，子元素横向拉伸。
  - `Context`：
    - `Theme theme()`；
    - `Context key(long)`、`key(int)`、`key(String)`、`key(Object)`：给下一个构造的元素指定 key，返回 `this`；
    - `void invalidate()`：调用 Engine 的 `requestFrame`。
  - `Element`：
    - `Element children(Runnable content)`、`Element label(String)`；
    - 句柄记录 `(Context, 节点编号, gen)`；
    - 每个方法先校验：正在构建中，并且池里节点的 `gen` 和句柄记录的相同。不满足时，开发模式抛异常，正式版忽略这次调用。
  - `Ui`：
    - `static final float AUTO = Float.POSITIVE_INFINITY`；
    - `box(Context)`、`column(Context)`、`row(Context)`、`spacer(Context)`、`divider(Context)`，都返回 `Element`；
    - `column` 和 `box` 默认 `alignItems(STRETCH)`；`row` 设 `row = true`，默认 `alignItems(CENTER)`；
    - `spacer` 是 `grow = 1` 的空元素；`divider` 由 Task 5 和 Task 7 处理成 1 DIP 宽的细线。

- [ ] **Step 1：先写测试**

```java
public final class BuildTest {
    final Engine e = new Engine(null, Theme.light(), () -> {});

    public void testIdentityIsStableAcrossFrames() {
        View v = c -> column(c).children(() -> { box(c).label("a"); box(c).label("b"); });
        e.buildAndLayout(v, 100, 100);
        long a = e.identity("a");
        e.buildAndLayout(v, 100, 100);
        Check.equal(a, e.identity("a"));
        Check.isTrue(a != e.identity("b"), "siblings differ");
    }
    public void testKeyedIdentityIgnoresPosition() {
        boolean[] extra = {true};
        View v = c -> column(c).children(() -> {
            if (extra[0]) box(c).label("extra");
            box(c.key("k")).label("keyed");
            box(c).label("plain");
        });
        e.buildAndLayout(v, 100, 100);
        long keyed = e.identity("keyed"), plain = e.identity("plain");
        extra[0] = false;
        e.buildAndLayout(v, 100, 100);
        Check.equal(keyed, e.identity("keyed"));
        Check.isTrue(plain != e.identity("plain"), "an unkeyed element takes its position's identity");
    }
    public void testDuplicateSiblingKeysFail() {
        Check.fails(IllegalStateException.class, () -> e.buildAndLayout(
                c -> column(c).children(() -> { box(c.key(7L)); box(c.key(7L)); }), 100, 100));
    }
    public void testUnusedKeyFails() {
        Check.fails(IllegalStateException.class, () -> e.buildAndLayout(c -> c.key("lost"), 100, 100));
    }
    public void testStaleElementFails() { // Review Focus 4
        Element[] kept = new Element[1];
        e.buildAndLayout(c -> kept[0] = box(c), 100, 100);
        e.buildAndLayout(c -> box(c), 100, 100);
        IllegalStateException x = Check.fails(IllegalStateException.class, () -> kept[0].label("late"));
        Check.isTrue(x.getMessage().contains("build pass"), "the message says why");
    }
    public void testPoolIsReusedAcrossFrames() {
        View v = c -> { for (int i = 0; i < 1000; i++) box(c); };
        e.buildAndLayout(v, 100, 100);
        int chunks = e.poolChunks();
        e.buildAndLayout(v, 100, 100);
        Check.equal(chunks, e.poolChunks());
        Check.equal(1001, e.nodeCount()); // the root and 1000 boxes
    }
    public void testBuildingOffTheMainThreadFails() { // design §7.8
        Throwable[] thrown = new Throwable[1];
        Thread t = new Thread(() -> {
            try { e.buildAndLayout(c -> box(c), 100, 100); } catch (Throwable x) { thrown[0] = x; }
        });
        t.start();
        try { t.join(); } catch (InterruptedException x) { throw new AssertionError(x); }
        Check.isTrue(thrown[0] instanceof IllegalStateException, "only the main thread builds views");
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test BuildTest`。预期编译失败。
- [ ] **Step 3**：实现上面列出的所有类。
  - 子元素的位置等于创建时父元素的 `childCount`；
  - 开发模式下检查重复 key：线性扫描同一父元素下已有的子节点 `id`；
  - 构建结束时，以及每次 `children` 返回时，如果还有没被用掉的 key，就调用 `Dev.fail`。
  - `buildAndLayout` 开始时，用 `Libc.pthreadMainNp()` 检查当前是否在主线程，不是就调用 `Dev.fail`。每帧只检查一次，开销可以忽略。
- [ ] **Step 4**：运行 `./build.sh test`。预期全部通过。
- [ ] **Step 5：提交**：`feat(ui): node pool, identity and keys, Context and Element handles`

---

### Task 4：分配实测（决定 API 是否要调整）

设计第 8 节要求在写控件之前完成这项实测。它的结论决定后面的任务是否照原计划继续。

**Files:**
- Create：`src/main/java/jui/ui/BoolRef.java`、`src/main/java/jui/spike/AllocSpike.java`
- Modify：`build.sh`（新增 `alloc` 命令）；如果需要启用退路，还要改 `src/main/java/jui/ui/Context.java`
- Docs：`README.md`（新增“M1 分配实测”一节）

**Interfaces:**
- Consumes：Task 3 的 `Engine`、`Ui`、`Context.key(long)`、`Context.key(Object)`。
- Produces：
  - `final class BoolRef { BoolRef(boolean); boolean get(); void set(boolean); }`
  - `Context` 新增内部开关 `static final boolean POOLED_HANDLES`，默认 false：
    - false：每次创建一个新的 `Element`；
    - true：每个节点编号对应一个复用的 `Element`，复用时更新它的 `gen`。
  - `build.sh alloc`：
    - 把 `jui.spike.AllocSpike` 编译成 `build/alloc-spike` 并运行；
    - 再在 JVM 上运行一次作为参照。

- [ ] **Step 1**：实现 `AllocSpike`。
  - 用 `com.sun.management.ThreadMXBean#getCurrentThreadAllocatedBytes()` 统计主线程分配的字节数（已确认在 native image 里可用）。
  - 每个场景先预热 20000 帧，再测 1000 帧，取平均。帧用 `Engine.buildAndLayout(view, 800, 600)` 驱动，引擎的 `text` 传 null。
  - 场景如下：
    - **handles**：300 个 `box(c).width(10).grow(1)`，结果按每个元素平均；
    - **lambdas**：100 个 `box(c).padding(4).children(() -> box(c).width(n))`，其中 `n` 是循环里的局部变量，所以这是捕获变量的 lambda，结果按每个容器平均；
    - **temprefs**：100 次 `fakeCheckbox(c, new BoolRef(i % 2 == 0), "x")`。`fakeCheckbox` 是实测专用的静态方法，规模接近真实控件：约 40 条语句，读写这个引用对象，并创建两个 box。结果按每次调用平均；
    - **key_long**：30 个 `box(c.key(1_000_000L + i))`；
    - **key_object**：30 个 `box(c.key((Object) Long.valueOf(1_000_000L + i)))`；
    - **shell_frame**：模拟一个外壳帧，包括 200 个 handles、60 个 lambdas 容器、10 次 temprefs、30 个 key_long，结果按每帧。
  - 输出一行：`ALLOC handles=<B> lambdas=<B> temprefs=<B> key_long=<B> key_object=<B> shell_frame=<B>`。
- [ ] **Step 2**：运行 `./build.sh alloc`，记下 native image 和 JVM 的两行结果。
- [ ] **Step 3：按结果判断**
  - 如果 native image 的 `handles` > 0.5 B：
    - 把 `Context.POOLED_HANDLES` 改为 true，重新运行 `./build.sh alloc`；
    - 确认 `handles` 降到 ≤ 0.5 B，再运行 `./build.sh test`，确认全部通过。
    - 注意：启用复用后，`BuildTest.testStaleElementFails` 可能失效，因为过期的句柄会被新的 `gen` 覆盖。如果失效，把这个测试改为在开发模式下跳过，并在 README 里写明这一限制。
  - 如果 native image 的 `shell_frame` > 32768 B：**停止执行**。把两行结果和各场景的数字报告给用户，因为这需要修改设计（第 8 节）。后面的任务暂停。
  - 其他情况：照原计划继续。
- [ ] **Step 4**：在 `README.md` 新增“M1 分配实测”一节，写清楚：两行原始结果、是否启用了 `POOLED_HANDLES`、结论。
- [ ] **Step 5：提交**：`perf: measure per-frame allocation of the UI API on native image`

---

### Task 5：flexbox 布局

**Files:**
- Create：`src/main/java/jui/ui/engine/FlexLayout.java`
- Modify：`Element.java`（新增布局方法）、`Engine.java`（`buildAndLayout` 里加入布局）
- Test：`src/test/java/jui/ui/engine/FlexLayoutTest.java`

**Interfaces:**
- Consumes：Task 3 的 `Node` 布局字段。
- Produces：
  - `Element` 新增以下方法，都返回 `this`：
    - `width(float)`、`height(float)`、`size(float w, float h)`；
    - `minWidth`、`maxWidth`、`minHeight`、`maxHeight`；
    - `widthPercent(float)`、`heightPercent(float)`；
    - `fill()`（等于宽高都是 100%）、`fillWidth()`、`fillHeight()`；
    - `grow(float)`、`shrink(float)`、`basis(float)`、`gap(float)`；
    - `padding(float)`、`padding(float vertical, float horizontal)`、`padding(float top, float right, float bottom, float left)`；
    - `margin(...)`：同样三种重载，参数可以传 `Ui.AUTO`；
    - `justify(Justify)`、`alignItems(Align)`、`alignSelf(Align)`；
    - `center()`：等于 `justify(CENTER)` 加 `alignItems(CENTER)`；
    - `reverse()`。
  - `FlexLayout`：
    - `static void layout(NodePool pool, int root, float width, float height, Measure measure)`；
    - 结果写入每个节点的 `x`、`y`、`w`、`h`（DIP，从窗口左上角算起）。
  - `interface Measure { float[] measure(int node, float maxWidth); }`：返回 `{宽, 高}`，`maxWidth` 为 0 表示不限宽。本任务的测试只用到方框，不会调用它；Task 6 提供文字的实现。

- [ ] **Step 1：先写测试**。每个元素都用 `label` 命名，用 `e.bounds(label)` 读取结果；`near` 的容差是 0.01

```java
public final class FlexLayoutTest {
    final Engine e = new Engine(null, Theme.light(), () -> {});
    Rect at(String label) { return e.bounds(label); }

    public void testRowPlacesFixedChildrenWithGapAndCentersThem() {
        e.buildAndLayout(c -> row(c).size(300, 100).gap(10).children(() -> {
            box(c).size(50, 20).label("a"); box(c).size(60, 20).label("b"); }), 300, 100);
        Check.near(0, at("a").x()); Check.near(60, at("b").x());
        Check.near(40, at("a").y()); // a row centers across by default
    }
    public void testColumnStretchesByDefault() {
        e.buildAndLayout(c -> column(c).size(200, 300).children(() -> box(c).height(40).label("a")), 200, 300);
        Check.near(200, at("a").width()); Check.near(40, at("a").height());
    }
    public void testGrowSharesFreeSpace() {
        e.buildAndLayout(c -> row(c).size(300, 50).children(() -> {
            box(c).width(100); box(c).grow(1).label("g1"); box(c).grow(2).label("g2"); }), 300, 50);
        Check.near(66.67, at("g1").width()); Check.near(133.33, at("g2").width());
        Check.near(166.67, at("g2").x());
    }
    public void testShrinkWhenOverflowing() {
        e.buildAndLayout(c -> row(c).size(200, 50).children(() -> {
            box(c).basis(150).label("a"); box(c).basis(150).label("b"); }), 200, 50);
        Check.near(100, at("a").width()); Check.near(100, at("b").width());
    }
    public void testJustify() {
        float[][] expected = {{100, 150}, {0, 250}, {50, 200}, {66.67f, 183.33f}};
        Justify[] modes = {Justify.CENTER, Justify.SPACE_BETWEEN, Justify.SPACE_AROUND, Justify.SPACE_EVENLY};
        for (int i = 0; i < modes.length; i++) {
            Justify j = modes[i];
            e.buildAndLayout(c -> row(c).size(300, 50).justify(j).children(() -> {
                box(c).size(50, 10).label("a"); box(c).size(50, 10).label("b"); }), 300, 50);
            Check.near(expected[i][0], at("a").x()); Check.near(expected[i][1], at("b").x());
        }
    }
    public void testPaddingInsetsContent() {
        e.buildAndLayout(c -> column(c).size(200, 200).padding(10).children(() -> box(c).height(20).label("a")), 200, 200);
        Check.near(10, at("a").x()); Check.near(10, at("a").y()); Check.near(180, at("a").width());
    }
    public void testAutoMarginPushesToTheEnd() {
        e.buildAndLayout(c -> row(c).size(300, 50).children(() -> {
            box(c).size(50, 10); box(c).size(50, 10).margin(0, 0, 0, Ui.AUTO).label("end"); }), 300, 50);
        Check.near(250, at("end").x());
    }
    public void testPercentAndFill() {
        e.buildAndLayout(c -> row(c).size(400, 200).children(() -> box(c).widthPercent(25).height(10).label("p")), 400, 200);
        Check.near(100, at("p").width());
        e.buildAndLayout(c -> box(c).fill().label("f"), 400, 200);
        Check.near(400, at("f").width()); Check.near(200, at("f").height());
    }
    public void testMaxClampRedistributes() {
        e.buildAndLayout(c -> row(c).size(300, 50).children(() -> {
            box(c).grow(1).maxWidth(120).label("a"); box(c).grow(1).label("b"); }), 300, 50);
        Check.near(120, at("a").width()); Check.near(180, at("b").width());
    }
    public void testReverseStartsFromTheEnd() {
        e.buildAndLayout(c -> row(c).size(300, 50).reverse().children(() -> {
            box(c).size(50, 10).label("a"); box(c).size(60, 10).label("b"); }), 300, 50);
        Check.near(250, at("a").x()); Check.near(190, at("b").x());
    }
    public void testAlignItemsEndAndNestedContentSize() {
        e.buildAndLayout(c -> column(c).size(300, 200).children(() ->
            row(c).gap(5).alignItems(Align.END).label("r").children(() -> {
                box(c).size(40, 30); box(c).size(50, 20).label("short"); })), 300, 200);
        Check.near(30, at("r").height());      // as high as its tallest child
        Check.near(300, at("r").width());      // stretched across the column
        Check.near(10, at("short").y());       // bottom-aligned in the 30-high row
    }
    public void testTinySpacesNeverGoNegative() { // Review Focus 2
        e.buildAndLayout(c -> row(c).size(20, 10).padding(30).children(() -> {
            box(c).size(50, 50).shrink(0).label("a"); box(c).grow(1).label("b"); }), 20, 10);
        for (String s : new String[] {"a", "b"}) {
            Rect r = at(s);
            Check.isTrue(r.width() >= 0 && r.height() >= 0 && !Float.isNaN(r.x()) && !Float.isNaN(r.y()), s + " " + r);
        }
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test FlexLayoutTest`。预期编译失败。
- [ ] **Step 3**：实现 `FlexLayout`。按 CSS Flexbox Level 1 第 9 节的算法，不含换行（9.3 的多行部分跳过）。下面这些规则测试推不出来，单独说明：
  - **内边距**：等于 padding 加上 `border`。`Node.border` 由 Task 7 的 `border()` 设置，本任务就要把它算进去。
  - **`grow(g)` 相当于 CSS 的 `flex: g`**（MyGO 的 `Grow(1)` 也是这样定义的）：如果没有显式设置 `basis`，就把基准尺寸当作 0。这样，会伸缩的文字只占剩余的空间，不会按自己的内容宽度把兄弟元素挤窄（Review Focus 1）。
  - **flex 基准尺寸**：有 `basis` 用 `basis`；`grow > 0` 且没设 `basis` 时为 0；否则用主轴方向上设置的尺寸（行用 `width`，列用 `height`）；都没有就用内容尺寸。
    - 容器的内容尺寸：主轴方向是子元素尺寸加间距的和，交叉方向取最大值，再加上内边距。
    - 列方向：先确定每个子元素的交叉尺寸（拉伸后的宽度，或它自己的宽度），再在这个宽度下测量内容的高度作为基准尺寸。
  - **最小尺寸**：没设置时为 0。这是和 Yoga 一致的语义，不实现 CSS 的 `min-width: auto`。
  - **伸缩**：按 9.7 处理伸缩和冻结：先冻结违反 min/max 的项目，再重新分配剩余空间。收缩按 `shrink × 基准尺寸` 的比例分配。
  - **自动外边距**：先吸收剩余空间，剩下的再交给 `justify` 分配。
  - **反向**：`reverse` 时主轴的起点在另一端。
  - **百分比**：相对于父元素内容区的尺寸。
  - **divider**：在行里宽 1，在列里高 1，交叉方向拉伸。
  - **保护**：任何尺寸都截断到 ≥ 0。
- [ ] **Step 4**：运行 `./build.sh test`。预期全部通过。
- [ ] **Step 5：提交**：`feat(ui): flexbox layout without wrapping`

---

### Task 6：文字元素

**Files:**
- Modify：`Ui.java`（新增 `text`）、`Element.java`（文字样式方法）、`Engine.java`（基于 `TextEngine` 实现 `Measure`，并处理文字样式的继承）、`src/main/java/jui/text/TextEngine.java`（构造函数里确保加载 AppKit 框架，因为 `NSFont` 需要它）
- Test：`src/test/java/jui/ui/engine/TextElementTest.java`

**Interfaces:**
- Consumes：Task 5 的 `Measure` 接口；现有的 `TextEngine.layout(String, float size, int weight, float maxWidth)` 和 `TextLayout.width`/`height`。
- Produces：
  - `Ui.text(Context, String)`：返回 `Element`，节点种类是 `TEXT`；
  - `Element` 新增：`textColor(Color)`、`fontSize(float)`、`fontWeight(int)`、`bold()`（等于 `fontWeight(700)`）、`singleLine()`；
  - 布局完成后，`Node.layout` 是按最终宽度排版的结果，`Node.resolvedColor` 是继承后的颜色。

- [ ] **Step 1：先写测试**

```java
public final class TextElementTest {
    final Engine e = new Engine(new TextEngine("zh-Hans"), Theme.light(), () -> {});
    static final String LONG = "Java 通过 FFM 直接调用 Core Text：由它找字体，为缺字的字体回退，再整形和断行。";

    float oneLine() {
        e.buildAndLayout(c -> column(c).children(() -> text(c, "一行").label("t")), 2000, 200);
        return e.bounds("t").height();
    }
    public void testTextWrapsToTheContainer() {
        float line = oneLine();
        e.buildAndLayout(c -> column(c).width(120).children(() -> text(c, LONG).label("t")), 2000, 2000);
        Check.isTrue(e.bounds("t").height() > 2 * line * 0.9f, "several lines");
        Check.isTrue(e.bounds("t").width() <= 120.01f, "within the column");
    }
    public void testSingleLineDoesNotWrap() {
        float line = oneLine();
        e.buildAndLayout(c -> column(c).width(120).children(() -> text(c, LONG).singleLine().label("t")), 2000, 200);
        Check.near(line, e.bounds("t").height());
    }
    public void testFontSizeIsInherited() {
        e.buildAndLayout(c -> column(c).children(() -> text(c, "继承").fontSize(26).label("own")), 2000, 200);
        float own = e.bounds("own").height();
        e.buildAndLayout(c -> column(c).fontSize(26).children(() -> text(c, "继承").label("inh")), 2000, 200);
        Check.near(own, e.bounds("inh").height());
    }
    public void testBoldIsWider() {
        e.buildAndLayout(c -> row(c).children(() -> {
            text(c, "Bold 粗体").label("r"); text(c, "Bold 粗体").bold().label("b"); }), 2000, 200);
        Check.isTrue(e.bounds("b").width() > e.bounds("r").width(), "700 is wider than 400");
    }
    public void testTextBesideAFixedBoxWrapsInTheRest() { // Review Focus 1
        e.buildAndLayout(c -> row(c).width(300).alignItems(Align.START).children(() -> {
            box(c).size(100, 20); text(c, LONG).grow(1).label("t"); }), 300, 2000);
        Check.isTrue(e.bounds("t").width() <= 200.01f, "fits beside the box");
        Check.isTrue(e.bounds("t").height() > oneLine() * 1.5f, "wraps");
    }
    public void testEmptyAndUnbrokenText() { // Review Focus 3
        float line = oneLine();
        e.buildAndLayout(c -> column(c).width(100).children(() -> {
            text(c, "").label("empty");
            text(c, "https://example.com/" + "a".repeat(200)).label("url"); }), 2000, 2000);
        Check.near(0, e.bounds("empty").height());                       // no line, and no crash
        Check.isTrue(e.bounds("url").height() > line * 1.5f, "breaks inside the word instead of overflowing");
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test TextElementTest`。预期编译失败。
- [ ] **Step 3：实现**
  - **测量**：用 `TextEngine.layout(text, 字号, 字重, singleLine ? 0 : 可用宽度)`。在行里计算内容尺寸时先按不限宽测量，确定最终宽度后再按这个宽度排版一次，排版结果存进 `Node.layout`。
  - **默认值**：字号默认取 `theme.fontSize()`，字重默认 400，颜色默认取 `theme.text()`。
  - **继承**：在布局阶段从根往下传递继承的文字样式，所以样式设在 `children()` 之前或之后都能生效。
- [ ] **Step 4**：运行 `./build.sh test`。预期全部通过。
- [ ] **Step 5：提交**：`feat(ui): text elements measured and wrapped by Core Text`

---

### Task 7：绘制到 Scene

**Files:**
- Create：`src/main/java/jui/ui/engine/Paint.java`
- Modify：`Element.java`（视觉样式方法）、`Engine.java`（新增 `paint`）、`src/main/java/jui/render/Scene.java`（新增 `instance(int)`）
- Test：`src/test/java/jui/ui/engine/PaintTest.java`

**Interfaces:**
- Consumes：Task 5 和 Task 6 写入的布局结果；现有的 `Scene.box(...)`、`Scene.shadow(x, y, w, h, radius, color, blur)`、`Scene.text(TextLayout, x, y, color)`、`Scene.begin(scale, TextEngine, GlyphAtlas)`。
- Produces：
  - `Element` 新增：`background(Color)`、`border(float width, Color)`、`radius(float)`、`radius(float tl, float tr, float br, float bl)`、`shadow(float x, float y, float blur, float spread, Color)`；
  - `Engine.paint(Scene scene)`：绘制最近一次的布局，`scene` 必须已经由调用方 `begin` 过；
  - `Scene.instance(int i)`：返回第 i 个实例的 20 个 float 的副本。布局依次是：rect 0–3、radii 4–7、color 8–11、border 12–15、params 16–19；种类在第 18 位，0 是方框，1 是阴影，2 是字形。

- [ ] **Step 1：先写测试**

```java
public final class PaintTest {
    final TextEngine text = new TextEngine("zh-Hans");
    final Engine e = new Engine(text, Theme.light(), () -> {});
    Scene paint(float scale) {
        Scene s = new Scene();
        s.begin(scale, text, new GlyphAtlas(512, 512));
        e.paint(s);
        return s;
    }

    public void testShadowThenBoxAtLayoutBounds() {
        e.buildAndLayout(c -> column(c).padding(10, 20).children(() ->
            box(c).height(50).background(Color.rgb(0xFFFFFF)).radius(8).border(1, Color.rgb(0xE5E7EB))
                  .shadow(0, 4, 12, 2, Color.rgba(0x000000, 0.2f))), 200, 100);
        Scene s = paint(2);
        float[] shadow = s.instance(0), box = s.instance(1);
        Check.near(1, shadow[18]); Check.near(0, box[18]);
        Check.near(20, box[0]); Check.near(10, box[1]); Check.near(160, box[2]); Check.near(50, box[3]);
        Check.near(8, box[4]);
        Check.near(18, shadow[0]); Check.near(12, shadow[1]); // offset by (0, 4), grown by the spread 2
        Check.near(164, shadow[2]); Check.near(54, shadow[3]);
    }
    public void testBorderInsetsChildren() {
        e.buildAndLayout(c -> column(c).border(2, Color.rgb(0)).padding(10).children(() -> box(c).height(5).label("in")), 100, 100);
        Check.near(12, e.bounds("in").x());
    }
    public void testParentsPaintBeforeChildrenAndTextBecomesGlyphs() {
        e.buildAndLayout(c -> column(c).background(Color.rgb(0xF3F4F6)).children(() -> text(c, "Hi")), 200, 100);
        Scene s = paint(2);
        Check.near(0, s.instance(0)[18]);                     // the column's box comes first
        Check.isTrue(s.count() >= 3, "two glyphs after it");
        Check.near(2, s.instance(1)[18]);
    }
    public void testScaleChangesPixelsNotPoints() { // Review Focus 5
        e.buildAndLayout(c -> column(c).children(() -> {
            box(c).size(40, 10).background(Color.rgb(0)); text(c, "W"); }), 200, 100);
        Scene one = paint(1), two = paint(2);
        Check.near(one.instance(0)[2], two.instance(0)[2]);   // the box: same width in points
        Check.isTrue(two.instance(1)[2] > one.instance(1)[2] * 1.8f, "the glyph rasterized at twice the pixels");
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test PaintTest`。预期编译失败。
- [ ] **Step 3：实现 `Paint`**。按构建顺序深度优先遍历，每个节点依次画：
  - 阴影（`shadowColor` 不透明时）：矩形是“方框偏移 `(shadowX, shadowY)` 后，四周各扩大 `shadowSpread`”，圆角是 `radiusTL + shadowSpread`，模糊用 `shadowBlur`；
  - 方框（有背景色或有边框时）；
  - `TEXT` 节点：`scene.text(node.layout, x, y, resolvedColor)`；
  - `DIVIDER` 节点：用 `theme.border()` 填充；
  - 然后递归画子元素。

  颜色传给 `Scene` 时直接用 `Color.rgba()` 的值。
- [ ] **Step 4**：运行 `./build.sh test`。预期全部通过。
- [ ] **Step 5：提交**：`feat(ui): paint boxes, shadows, borders and text into the scene`

---

### Task 8：Tester，以及应用和窗口

**Files:**
- Create：`src/main/java/jui/ui/Tester.java`、`src/main/java/jui/app/{App,Window,WindowOptions}.java`
- Modify：`src/main/java/jui/platform/mac/MacWindow.java`、`src/main/java/jui/spike/Main.java`
- Test：`src/test/java/jui/ui/TesterTest.java`

**Interfaces:**
- Consumes：`Engine` 的 `buildAndLayout`、`paint`、`bounds`；`MacApp.run`。
- Produces：
  - `Tester`：
    - `static Tester of(View view, float width, float height)`：缩放比例为 2，使用自己的 `TextEngine("zh-Hans")` 和 `GlyphAtlas(1024, 1024)`；
    - `void frame()`：构建、布局、绘制；
    - `List<String> texts()`：按构建顺序列出所有文字元素的内容；
    - `boolean hasText(String)`；
    - `Rect find(String textOrLabel)`：找不到时抛 `AssertionError`；
    - `int instances()`；
    - `boolean framePending()`：调用过 `invalidate`、还没被 `frame()` 消费时为 true。
  - `Engine` 新增 `List<String> texts()`。
  - `MacWindow`：
    - `open(String title, double w, double h, int background, TextEngine text, Painter painter)`：`TextEngine` 改为由外部传入，不再由 `MacWindow` 自己静态创建；
    - 新增 `public void requestFrame()`：调用 `setNeedsDisplay:`；
    - `Painter` 接口不变：`void paint(Scene, double w, double h)`。
  - `App`：
    - `static void run(Runnable whenReady)`；
    - `static TextEngine text()`：在主线程懒创建 `TextEngine("zh-Hans")`，整个应用共用一个。
  - `record WindowOptions(String title, float width, float height, View content)`：
    - `static WindowOptions of(String title)`：默认尺寸 800×600；
    - `WindowOptions size(float w, float h)`、`WindowOptions content(View)`。
  - `Window`：
    - `static Window open(WindowOptions)`：创建 `Engine(App.text(), Theme.light(), this::invalidate)`，再用一个 Painter 打开 `MacWindow`。这个 Painter 先调用 `engine.buildAndLayout(view, w, h)`，再调用 `engine.paint(scene)`。清屏色取 `theme.background().rgba() >>> 8`；
    - `void invalidate()`：转给 `MacWindow.requestFrame()`；
    - `long windowNumber()`：给截图工具用。

- [ ] **Step 1：先写测试**

```java
public final class TesterTest {
    public void testFindsTextsAndTheirBoxes() {
        Tester tt = Tester.of(c -> column(c).padding(20).gap(8).children(() -> {
            text(c, "标题").fontSize(22); text(c, "正文"); }), 400, 300);
        tt.frame();
        Check.equal(List.of("标题", "正文"), tt.texts());
        Check.isTrue(tt.hasText("正文"), "has 正文");
        Check.near(20, tt.find("标题").x()); Check.near(20, tt.find("标题").y());
        Check.isTrue(tt.find("正文").y() > tt.find("标题").y() + 8, "below the title and the gap");
        Check.isTrue(tt.instances() > 0, "painted");
    }
    public void testInvalidateAsksForAFrame() {
        Tester tt = Tester.of(c -> { text(c, "x"); c.invalidate(); }, 100, 100);
        tt.frame();
        Check.isTrue(tt.framePending(), "invalidate during the build asks for another frame");
    }
    public void testFindFailsClearly() {
        Tester tt = Tester.of(c -> text(c, "x"), 100, 100);
        tt.frame();
        Check.fails(AssertionError.class, () -> tt.find("missing"));
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test TesterTest`。预期编译失败。
- [ ] **Step 3**：实现 `Tester`、`App`、`WindowOptions`、`Window`，修改 `MacWindow`，再把 `jui.spike.Main` 改为显式创建并传入 `TextEngine`。
- [ ] **Step 4**：运行 `./build.sh test`，预期全部通过。再运行 `./build.sh native && ./build/jui-spike --bench`，预期原型照常输出 `BENCH` 行。
- [ ] **Step 5：提交**：`feat: Tester, App and Window hosting the UI engine`

---

### Task 9：验收示例：用新 API 重写卡片

**Files:**
- Create：`src/main/java/jui/examples/CardDemo.java`
- Modify：`build.sh`（新增 `demo` 和 `native-demo` 命令）、`README.md`（新增 M1 小节）
- Test：`src/test/java/jui/examples/CardDemoTest.java`

**Interfaces:**
- Consumes：Task 3、5、6、7 的全部 API，以及 Task 8 的 `App`、`Window`、`Tester`。
- Produces：
  - `CardDemo.view(Context c)`：静态方法，供测试直接使用；
  - `CardDemo.main(String[])`：打开 760×560 的窗口；带 `--print-window` 参数时打印 `window=<编号>`；
  - `build.sh demo`：在 JVM 上运行；
  - `build.sh native-demo`：编译成 `build/card-demo` 并运行。

视图的内容和原型 `jui.spike.Main.paint` 一一对应，颜色照抄原型。下面写的 `0xRRGGBB` 都用 `Color.rgb(...)` 构造；带透明度的颜色用 `Color.rgba(rgb, alpha)` 构造。
- **外层**：`box(c).fill().background(0xF3F4F6).padding(32)`。
- **卡片**：`column(c).grow(1).background(白).border(1, 0xE5E7EB).radius(16).shadow(0, 8, 24, 0, Color.rgba(0x0F172A, 0x26/255f)).padding(26, 32, 32, 32)`，里面从上到下依次是：
  1. 标题“j-ui 文字渲染”：26pt，粗体，`0x111827`；
  2. 副标题“Core Text 排版 · Core Graphics 光栅化 · Metal 绘制”：13pt，`0x6B7280`，上方间距 2；
  3. 原型里的同一段正文：16pt，`0x1F2937`，上方间距 16；
  4. 字重一行：gap 24，15pt，四种字重 400/500/600/700，上方间距 16；
  5. 11pt 小字那一行：`0x6B7280`，上方间距 12；
  6. 三个标签：高 28，左右内边距 12，圆角 14，13pt、字重 500，文字居中，配色同原型，标签之间 gap 10，上方间距 16；
  7. 一个 `spacer`；
  8. 底部一行：
     - 主按钮 152×44，圆角 10，`0x2563EB`，阴影 `(0, 4, 12, 0, 0x2563EB55)`，白色“确定 OK”，15pt、字重 500；
     - 次按钮 152×44，白底，边框 `0xD1D5DB`，“取消 Cancel”，和主按钮之间 gap 16；
     - 一个 `spacer`；
     - 圆形 44×44，圆角 22，`0xF43F5E`。

- [ ] **Step 1：先写测试**

```java
public final class CardDemoTest {
    public void testTheCardShowsEverything() {
        Tester tt = Tester.of(CardDemo::view, 760, 560);
        tt.frame();
        for (String s : List.of("j-ui 文字渲染", "Regular 常规", "Bold 粗体", "已完成", "Metal", "GraalVM 25", "确定 OK", "取消 Cancel")) {
            Check.isTrue(tt.hasText(s), "shows " + s);
        }
        Rect title = tt.find("j-ui 文字渲染");
        Check.near(65, title.x()); Check.near(59, title.y());  // outer padding 32 + border 1 + card padding
        Rect ok = tt.find("确定 OK");
        Check.isTrue(ok.y() + ok.height() <= 560 - 32 - 1 - 32 + 0.01f, "the buttons sit at the bottom inside the card");
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test CardDemoTest`。预期编译失败。
- [ ] **Step 3**：实现 `CardDemo`，并在 `build.sh` 里加上 `demo` 和 `native-demo`。
- [ ] **Step 4**：运行 `./build.sh test`。预期全部通过。
- [ ] **Step 5：在 native image 下验证画面**
  - 运行 `./build.sh native-demo`。用 `--print-window` 拿到窗口编号，然后执行 `screencapture -o -x -l<编号> build/shot-card.png`。
  - 和 `docs/screenshot.png` 对比。验收标准：元素齐全，字体和颜色一致，位置差在 ±4 DIP 以内；中文回退到苹方。
- [ ] **Step 6**：在 `README.md` 新增 M1 小节，写清楚：新 API 的示例代码片段、截图（另存为 `docs/m1-card.png`），以及 Task 4 的分配实测结论。
- [ ] **Step 7：提交**：`feat(examples): rebuild the prototype card with the new UI API`

---

## M1 之后

设计文档第 10 节的 M2～M6，每个里程碑在上一个完成后再单独写实现计划，写到 `docs/plans/`。原因：
- Task 4 的分配实测可能会改动公开 API；
- M2 的输入模型需要在 M1 的节点池和身份机制之上设计。

下面这些在 M1 里有意没做，留给后面的里程碑：
- 状态表和局部状态（M2，配合输入一起做）；
- 平台接口 `jui.platform`，以及暗色主题和跟随系统外观（M2：输入事件需要平台层；M1 的窗口仍直接使用 `jui.platform.mac`）；
- `clip()`、`invisible()`、`maxLines` 和省略号（M3：裁剪要配合滚动视图，列表行需要省略号）；
- 布局缓存和帧统计（M6）。
