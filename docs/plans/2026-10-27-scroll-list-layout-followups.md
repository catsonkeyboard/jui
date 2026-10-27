# 滚动、列表和布局的后续 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal**：补上 M2、M3 推迟下来的 8 项功能：
- 横向滚动；
- `ScrollState.scrollTo` 带动画；
- 圆角裁剪；
- 列表：按 key 找回锚点、获得焦点的行滚出视野后仍然构建、跟随末尾；
- `alignContent` 的 space-* 系列和 `wrapReverse`；
- 流内元素的相对定位；
- `Element.scrollIntoView()`；
- 复选框的勾号改成描边。

**Architecture**：
- **布局**：
  - flexbox 的换行容器按 MyGO 的公式分配行间空间，`wrapReverse` 把行和行内的交叉轴位置镜像过来；
  - 流内元素布局完成后，再按 `top`/`left`（或 `bottom`/`right`）整体平移，不影响兄弟元素。
- **滚动**：
  - 滚动容器分横向和纵向两个轴，用 `SCROLL_X`、`SCROLL_Y` 两个标志表示；滚动的那个轴，内容尺寸不限；
  - 偏移、滚轮的平滑滚动、滚动条、键盘、Tab 时滚进视野都按轴各算一份；
  - 带动画的 `scrollTo` 是每帧按 ease-out 曲线算出的偏移请求；
  - `scrollIntoView` 复用 Tab 的“滚进视野”，在同一帧布局后生效。
- **绘制**：
  - 每个 GPU 实例多带一个裁剪矩形和四个圆角（80 → 112 字节）；
  - 着色器用最内层裁剪的圆角矩形距离场乘到覆盖率上，外层裁剪仍用 scissor 矩形；
  - 新增一种描边实例：最多两段线段、圆头圆角，用距离场画，复选框的勾号用它画。
- **列表**：
  - `ListState.keys(...)` 给出每行的 key：行的身份按 key 定，锚点也按 key 在旧位置附近找回；
  - 列表记下上一帧每行的身份，用来找到获得焦点的那一行，在视野外额外构建它；
  - 跟随末尾是一个跨帧保持的状态。

**Tech Stack**：Java 25、GraalVM CE 25，FFM、AppKit、Metal、Core Text，零依赖，自己写的测试运行器，`build.sh`。

**Spec**：[docs/design/2026-10-24-ui-api-design.md](../design/2026-10-24-ui-api-design.md)。这些功能大多在设计文档以外，是 M2、M3 计划“推迟到后面的”部分；设计 6.8（布局属性）、6.10（`scroll`、`list`）、7.5（裁剪，“圆角裁剪以后再做”）是它们的出处。

**行为依据**：MyGO（egoist/mygo，commit `8049993`，可以用 `git clone https://github.com/egoist/mygo.git && git -C mygo checkout 8049993` 取一份）。下文写“同 MyGO”的地方以这个版本为准：
- 滚动：`ui/scroll.go`、`ui/wheel.go`、`ui/widgets.go`（`Scroll`、`ScrollHorizontal`、`ScrollBoth`）、`ui/input.go`（`scroll`、`scrollKey`、`scrollbarPress`）、`ui/paint.go`（`scrollBars`、`scrollThumb`、`clipRect`）；
- 布局：`ui/layout.go`（`computeBox`、`justifyOffsets`、`relative`，换行和 `flipCross`）；
- 列表：`ui/list.go`（`Key`、`sync`、`find`、`focusedRow`、`FollowEnd`）；
- 绘制：`internal/gpu/gpu.go`（实例和裁剪）、`internal/gpu/metal/shader.metal`（`clipCoverage`、`sdRoundRect`）、`ui/widgets.go`（`checkPath`）。

## Global Constraints

沿用 M4 的约束：
- **平台**：Apple Silicon、macOS 14 及以上，`MACOS_MIN=14.0`。
- **原生调用**：全部走 FFM，新签名登记在 `jui.ffi.Sigs`。不写 C 或 Objective-C（着色器是 Metal，不算）。
- **不用反射**：主代码在运行时不用反射，不在运行时生成代码。
- **零第三方依赖**：不要尝试绕过 Maven Central 的封禁。
- **线程**：UI 函数和事件处理都在主线程。
- **开发模式**：在 JVM 上运行，或设置了 `-Djui.dev=true` 时开启。开启时误用 API 抛 `IllegalStateException`，关闭时忽略。
- **命名**：和 MyGO 对应。
- **类型约定**：长度单位是 DIP，`float`；颜色是 `jui.ui.Color`（0xRRGGBBAA）。
- **注释和文档**：代码注释用英文，文档用中文。
- **提交信息**：末尾加 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- **框架控件内部不用捕获变量的 lambda**。
- **每次输入只报告一次**。
- **分配门槛**（设计第 8 节）：内容不变的帧里，框架内部不产生和元素数量成正比的分配，包括横向滚动容器、圆角裁剪和描边；整帧不超过 32 KB。`./build.sh alloc`、`./build.sh listbench` 都要继续通过。

## 本计划定下的细节

审查时请重点检查：

1. **API 名字**：
   - **横向滚动**：
     - `Ui.scrollHorizontal(c)`、`Ui.scrollHorizontal(c, ScrollState)`：横向滚动的行，子元素纵向居中，同 `row`；
     - `Ui.scrollBoth(c)`、`Ui.scrollBoth(c, ScrollState)`：两个方向都滚动的列。
     - MyGO 的 `ScrollHorizontal`、`ScrollBoth` 就是这两个。
   - **`ScrollState` 增加的方法**：`x()`、`maxX()`、`scrollTo(float x, float y)`（立刻跳过去）、`scrollTo(float y, Duration d)` 和 `scrollTo(float x, float y, Duration d)`（带动画）、`scrolling()`。
     - 原有的 `scrollTo(float y)`、`scrollToTop()`、`scrollToEnd()` 不变，仍然立刻跳过去。
     - MyGO 用公开字段 `X`/`Y` 跳转，用 `ScrollTo(x, y, Animation)` 做动画。j-ui 的 `ScrollState` 一直用方法，这里保持一致。
   - **其他元素方法**：`Element.scrollIntoView()`、`Element.wrapReverse()`。
   - **`Element.alignContent(Justify)`**：新增的重载，接受 `SPACE_BETWEEN`、`SPACE_AROUND`、`SPACE_EVENLY`，也接受 `START`/`CENTER`/`END`。
     - `alignContent(Align)` 不变（`START`/`CENTER`/`END`/`STRETCH`）。
     - MyGO 两者共用一个 `Align` 枚举；j-ui 的 `Align` 是给 `alignItems` 用的，加进 space-* 会让 `alignItems` 出现无意义的取值，所以用重载。
   - **列表**：
     - `ListState.keys(IntToLongFunction keyOf)`：每行的 key；
     - `ListState.followEnd(boolean)`、`ListState.atEnd()`、`ListState.scrollToEnd()`。
     - 同 MyGO 的 `Key`、`FollowEnd`、`AtEnd()`、`ScrollToEnd()`。key 用 `long`，和 `c.key(long)` 一样不装箱。
   - **绘制**：`Scene.pushClip(x, y, w, h, tl, tr, br, bl)` 带圆角；`Scene.stroke(ax, ay, bx, by, cx, cy, width, color)` 是两段的折线；`Scene.line(x0, y0, x1, y1, width, color)` 是一段线段。
2. **横向滚动的布局**（同 MyGO 的 `computeBox`）：
   - 滚动的那个轴内容尺寸不限，那个轴上的 `grow` 不起作用；
   - `scrollBoth` 的列里，子元素宽度取各自内容宽度，需要拉伸的子元素都拉伸到**最宽的那个子元素**；
   - 内容宽度是 `max(子元素用掉的宽度, 内容框宽度) + 左右内边距和边框`，偏移夹到 `[0, 内容宽度 − 自身宽度]`；
   - 纵向滚动容器上请求的 `x`，夹到 0。
3. **滚轮和触控板**（同 MyGO 的 `input.go` 第 485–521 行）：
   - 按住 Shift 且 `dx == 0` 时，交换两个方向；
   - 从指针下最内层往外找，**第一个在任一方向还能动的容器拿走整个 (dx, dy)**，只用它滚动的那个轴，不按轴分别往外传；
   - 纵向滚轮落在只横向滚动的容器上，不会变成横向滚动，而是交给外面的容器；
   - 滚轮的平滑滚动（150 ms）按轴各算一份。
4. **键盘**（同 MyGO 的 `scrollKey`）：
   - 不带修饰键的 ← / → 横向滚 40；
   - 它们排在按键路由的最后：输入框、滑块和快捷键都不要时才轮到；
   - 目标是焦点链上最内层、还能往那个方向动的横向滚动容器，没有就找指针下的；
   - PageUp/PageDown、空格、Home/End 仍然只管纵向。
5. **滚动条**（同 MyGO 的 `scrollBars`）：
   - 横向滚动条在底边：粗 `theme.scrollbarWidth()`（6），离底边 3，滑块最短 24，轨道两端各内缩 2；
   - 两个方向的滚动条都显示时，各自的轨道让出 `宽度 + 3` 的角；
   - 拖滑块横向滚动，点轨道横向翻 0.9 个视口宽；
   - 显示规则和纵向的一样：悬停或正在拖动，并且内容超出 0.5 以上。
6. **带动画的 `scrollTo`**（同 MyGO 的 `scroll.go`，只做曲线）：
   - **曲线**：ease-out cubic `1 − (1 − t)³`，和元素动画同一条；`Duration.ZERO` 立刻跳过去。
   - **起点和重新瞄准**：从当前偏移出发；动画进行中再次调用，从当前位置重新出发。
   - **夹取**：目标按上一帧的最大偏移夹住，每次布局再夹一次。
   - **读到的值**：进行中 `x()`/`y()` 读到的是当前值，`scrolling()` 为真。
   - **什么会中断它**：滚轮、触控板、键盘滚动、按下滚动条滑块、立刻跳转的 `scrollTo`。动画开始时丢掉正在进行的滚轮平滑滚动。
   - **和 MyGO 的差别**：MyGO 的默认动画是 500 ms 的弹簧，j-ui 没有弹簧，所以由调用者给时长；MyGO 的“减少动画”系统设置，j-ui 还没有。
7. **`scrollIntoView`**（同 MyGO）：
   - 不带动画，两个方向都按“最少移动”，元素比视口大时显示开头；
   - 在调用的这一帧布局之后生效；
   - 它和 Tab 共用一套逻辑；一帧里多次调用，按调用顺序依次处理；
   - 中断它移动的容器的滚轮平滑滚动和动画；
   - 列表里已经构建的行可以滚进视野，没构建的行用 `ListState.scrollTo(row)`；
   - 每帧都调用会让用户无法把元素滚走，文档里写明。
8. **圆角裁剪**（同 MyGO 的 `clipRect` 和 `gpu.go` 第 383–460 行）：
   - **范围**：`clip()` 的元素和滚动容器裁剪到边框以内的矩形，每个角的圆角是 `max(该角圆角 − 边框宽度, 0)`（圆角先按 CSS 规则夹到短边的一半）。
   - **GPU 上只有最内层裁剪是圆角**：每个实例带上最内层裁剪的矩形和圆角，片元着色器用它的距离场乘到覆盖率上，对方框、阴影、字形、描边都起作用；外层裁剪只用 scissor 矩形。
   - **命中测试**：j-ui 只按最内层裁剪的圆角判断，和绘制一致。**和 MyGO 不同**：MyGO 每一层祖先都按圆角判断。
   - 滚动条画在容器的裁剪以外，不被圆角裁掉（j-ui 已经是这样）。
9. **描边**：
   - **和 MyGO 不同**：MyGO 的勾号是在 CPU 上把路径光栅化进字形图集。j-ui 新增一种实例，种类为 3：最多两段线段（三个点），圆头圆角，着色器用“到两段的最小距离 − 半宽”算一个覆盖率。一次算出覆盖率，半透明时连接处不会叠深。这是 M6 线段图元的第一步，M6 的 `Painter` 在它之上加。
   - **勾号**：同 MyGO 的 `checkPath`：在 16×16 的框里，从 (0.22w, 0.52h) 到 (0.42w, 0.71h) 再到 (0.78w, 0.31h)，宽 2，颜色 `theme.accentText()`，画在框的背景之后。
10. **相对定位**（同 MyGO 的 `relative`）：
    - 流内元素布局完成后，按 `left`（没有时用 `−right`）和 `top`（没有时用 `−bottom`）平移，`left`、`top` 优先；
    - 它的子元素随它一起移动；兄弟元素和父元素的尺寸不受影响；
    - 滚动容器的内容尺寸不因此变大；
    - 列表的行不应用（同 MyGO）；
    - 绝对定位元素的 `top`/`left` 含义不变，仍是相对父元素内边距框的距离。
11. **`alignContent` 的 space-***（同 MyGO 的 `justifyOffsets`）：
    - 换行容器里，设交叉轴剩余空间为 `free`、行数为 `n`：
      - `SPACE_BETWEEN`：`n > 1` 时行间加 `free / (n − 1)`，否则从起点排；
      - `SPACE_AROUND`：起点 `free / n / 2`，行间 `free / n`；
      - `SPACE_EVENLY`：起点和行间都是 `free / (n + 1)`。
    - 空间不够（`free < 0`）时 space-* 都退回起点。
    - 只有一行时，`SPACE_BETWEEN` 在起点，另外两个居中。
    - 不换行的容器忽略 `alignContent`，和现在一样。
12. **`wrapReverse`**（同 MyGO 的 `flipCross`）：
    - 同时打开换行；
    - 每个子元素的交叉轴位置按正常算法算完后镜像：`内容交叉尺寸 − 位置 − 子元素交叉尺寸`，交叉轴两侧的外边距互换；
    - 所以 `alignContent START` 把行排在交叉轴末端（行容器的底部），`alignItems START`/`END` 在每一行内也翻过来。
13. **列表按 key 找回锚点**（同 MyGO 的 `sync` 和 `find`）：
    - 给了 `keys(...)` 时，每帧布局后记下锚点行的 key；下一帧构建之前，从旧锚点位置向两边交替找这个 key，最多找 1000 行；
    - 找到且位置变了：锚点移过去，已量的行高整体平移同样的行数，锚点行的像素位置不变；
    - 找不到：按行号锚定，和现在一样；
    - 行的身份也由这个 key 决定，所以行回调里**不要**再用 `c.key(...)` 给行指定 key，开发模式下会报 “a key was set twice”；
    - MyGO 的 `KeepOffset` 不做。
14. **获得焦点的行**（同 MyGO 的 `focusedRow`）：
    - 列表记下上一帧每一行的身份和行号；获得焦点的元素在其中某一行里（或就是那一行）时，这一帧额外构建这一行，即使它在视野外；
    - 给了 `keys(...)` 时，先核对这个行号的 key，不对就按 key 在附近找（最多 1000 行），找不到就不构建（焦点随之丢失）；
    - 这一行放在它按已量或估计的行高该在的位置（视野外），尺寸完整，不参与锚定和“是否盖满视口”的判断，但量出的行高照常记下；
    - 构建顺序按行号：在视野上方的，排在可见行之前；在下方的，排在之后。所以 Tab 从它出发，走到构建顺序里的下一个可聚焦元素（同 MyGO）。
15. **跟随末尾**（同 MyGO 的 `FollowEnd`，只做跳转）：
    - `followEnd(true)` 的列表第一帧显示末尾；
    - 跟随中，追加行或最后一行变高，末尾始终在视野里，这个状态跨帧保持；
    - 用户向上滚，停止跟随；滚回离末尾 0.5 以内，或调用 `scrollToEnd()`，恢复跟随；
    - MyGO 的 `FollowAnimation`（追加时的平滑滑动）不做。

## Review Focus

测试没有直接覆盖、但最容易让使用者踩坑的 5 种情况：

1. **横向滚动容器放在纵向滚动容器里**：
   - 在横向容器上转滚轮，交给外面的纵向容器；
   - Shift+滚轮滚横向容器；
   - 触控板斜着划，被内层整个拿走（同 MyGO）。
   - 测试放在 Task 6。
2. **带动画滚动的过程中，内容变少或窗口变大**：偏移随时夹在新的范围里，动画结束在新的末端，不回弹、不出界。测试放在 Task 7。
3. **圆角的滚动容器有背景和边框**：内容的四角不露出圆角外，边框盖在内容上，滚动条不被圆角裁掉，点击圆角外的位置点不中内容。测试放在 Task 3。
4. **按 key 的列表，在视野上方插入或删除数据**：视野里的内容不跳；删掉锚点那一项时，退回按行号锚定，不出错也不跳到开头。测试放在 Task 9。
5. **跟随末尾的日志，飞快追加**：在末尾时始终贴底；向上翻看时新行不把视野拉走；滚回末尾又开始跟随。测试放在 Task 10。

---

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `src/main/java/jui/ui/engine/FlexLayout.java` | space-*、`wrapReverse`、相对定位、横向滚动的布局、列表的额外行 | 1、2、5、9 |
| `src/main/java/jui/ui/engine/{Node,ElementState}.java` | 新字段：滚动轴、横向偏移和内容宽度、动画、`wrapReverse`、勾号 | 1–10 |
| `src/main/java/jui/ui/{Element,Ui,ScrollState,ListState}.java` | 公开 API | 1、2、5–10 |
| `src/main/java/jui/render/Scene.java`、`src/main/resources/jui/shaders/shader.metal` | 实例里的圆角裁剪，描边实例 | 3、4 |
| `src/main/java/jui/ui/engine/{Paint,HitList,ScrollBar}.java` | 圆角裁剪的推入和命中，勾号，横向滚动条 | 3、4、6 |
| `src/main/java/jui/ui/engine/{Engine,Input}.java` | 滚动轴、请求和动画、滚轮和键盘、`scrollIntoView`、列表的 key 和焦点行、跟随末尾 | 5–10 |
| `src/main/java/jui/ui/engine/ListModel.java` | key、行高平移、额外行、跟随末尾 | 9、10 |
| `src/main/java/jui/examples/ScrollDemo.java`、`build.sh`、`README.md` | 示例和文档 | 11 |

---

### Task 0：分支

- [ ] **Step 1**：从 `main` 建分支 `m3-followups`：`git checkout -b m3-followups`。
- [ ] **Step 2**：在分支上提交本计划，信息 `docs: plan the scroll, list and layout follow-ups`。

---

### Task 1：`alignContent` 的 space-* 和 `wrapReverse`

**Files:**
- Modify: `Node.java`、`Element.java`、`FlexLayout.java`（`placeWrapped`、`placeLine`）
- Test: `src/test/java/jui/ui/WrapLayoutTest.java`

**Interfaces:**
- **Produces**：
  - `Element alignContent(Justify j)`：设 `n.alignContentSpread = j`；`alignContent(Align)` 把它清回 `null`；
  - `Element wrapReverse()`：设 `n.wrap = n.wrapReverse = true`；
  - `Node`：`Justify alignContentSpread`（`null` 表示按 `alignContent` 的 `Align`）、`boolean wrapReverse`。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class WrapLayoutTest {
    /** Four 50 by 20 boxes in a 100 by 100 wrapping row: two lines of two, 60 of free space across. */
    static Tester lines(java.util.function.Consumer<Element> setup, float height) {
        return Tester.of(c -> {
            Element r = row(c).size(100, height).wrap().alignItems(Align.START).label("r");
            setup.accept(r);
            r.children(() -> {
                for (int i = 0; i < 4; i++) box(c).size(50, 20).label("b" + i);
            });
        }, 300, 300);
    }

    static float top(Tester tt, String s) {
        return tt.find(s).y() - tt.find("r").y();
    }

    public void testSpaceBetweenAroundAndEvenly() {
        Tester tt = lines(r -> r.alignContent(Justify.SPACE_BETWEEN), 100);
        Check.near(0, top(tt, "b0"));
        Check.near(80, top(tt, "b2"));                      // 20 + 60
        tt = lines(r -> r.alignContent(Justify.SPACE_AROUND), 100);
        Check.near(15, top(tt, "b0"));                      // 60 / 2 / 2
        Check.near(65, top(tt, "b2"));                      // 15 + 20 + 30
        tt = lines(r -> r.alignContent(Justify.SPACE_EVENLY), 100);
        Check.near(20, top(tt, "b0"));
        Check.near(60, top(tt, "b2"));
    }

    public void testOverflowFallsBackToTheStart() {
        Tester tt = lines(r -> r.alignContent(Justify.SPACE_EVENLY), 30);   // 40 of lines in 30
        Check.near(0, top(tt, "b0"));
        Check.near(20, top(tt, "b2"));
    }

    public void testOneLineAroundAndEvenlyCenterBetweenStarts() {
        for (Justify j : new Justify[] {Justify.SPACE_AROUND, Justify.SPACE_EVENLY, Justify.SPACE_BETWEEN}) {
            Tester tt = Tester.of(c -> row(c).size(100, 100).wrap().alignItems(Align.START).alignContent(j).label("r")
                    .children(() -> box(c).size(50, 20).label("b0")), 300, 300);
            Check.near(j == Justify.SPACE_BETWEEN ? 0 : 40, top(tt, "b0"));
        }
    }

    public void testWrapReverseStacksLinesFromTheCrossEnd() { // MyGO's style_test.go:174
        Tester tt = Tester.of(c -> row(c).size(50, 40).wrapReverse().alignItems(Align.START).label("r").children(() -> {
            box(c).size(30, 10).label("b0");
            box(c).size(30, 10).label("b1");
        }), 300, 300);
        Check.near(30, top(tt, "b0"));                      // the first line at the bottom
        Check.near(20, top(tt, "b1"));                      // the second above it
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test WrapLayoutTest`。Expected：编译失败。
- [ ] **Step 3**：实现（细节第 11、12 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): alignContent space-between, -around and -evenly; wrapReverse`。

---

### Task 2：流内元素的相对定位

**Files:**
- Modify: `FlexLayout.java`（`place` 的单行路径、`placeLine`）、`Element.java`（`top`/`right`/`bottom`/`left` 的文档）
- Test: `src/test/java/jui/ui/PositionTest.java`（已有，增加测试）

**Interfaces:**
- **Produces**：流内子元素在 `set(...)` 之后、`place(c)` 之前平移 `(dx, dy)`：`dx = !isNaN(left) ? left : !isNaN(right) ? −right : 0`，`dy` 同理用 `top`/`bottom`。列表的行（`placeList`）不平移。

- [ ] **Step 1：写失败的测试**

```java
public void testTopAndLeftMoveAnElementInTheFlowButNotItsSiblings() { // MyGO's style_test.go:207
    Tester tt = Tester.of(c -> row(c).height(10).alignItems(Align.START).label("r").children(() -> {
        box(c).size(20, 10).label("a").top(5).left(3);
        box(c).size(20, 10).label("b");
        box(c).size(20, 10).label("c").right(4).bottom(2);
        box(c).size(20, 10).label("d").left(1).right(9).top(2).bottom(9);   // left and top win
    }), 300, 200);
    Rect r = tt.find("r");
    Check.near(r.x() + 3, tt.find("a").x());
    Check.near(r.y() + 5, tt.find("a").y());
    Check.near(r.x() + 20, tt.find("b").x());             // where the flow put it
    Check.near(r.x() + 40 - 4, tt.find("c").x());
    Check.near(r.y() - 2, tt.find("c").y());
    Check.near(r.x() + 60 + 1, tt.find("d").x());
    Check.near(r.y() + 2, tt.find("d").y());
}

public void testAMovedElementIsHitWhereItShows() {
    int[] clicks = {0};
    Tester tt = Tester.of(c -> column(c).padding(10).alignItems(Align.START).children(() -> {
        if (box(c).size(40, 20).label("a").top(30).left(50).clicked()) clicks[0]++;
    }), 300, 200);
    tt.frame();                                             // in the flow at (10, 10); moved to (60, 40)
    tt.press(30, 20);                                       // where the flow put it: nothing there now
    tt.release(30, 20);
    Check.equal(0, clicks[0]);
    tt.press(80, 50);                                       // where it shows
    tt.release(80, 50);
    Check.equal(1, clicks[0]);
}

public void testAMovedElementDoesNotGrowAScrollContainersContent() {
    ScrollState st = new ScrollState();
    Tester tt = Tester.of(c -> scroll(c, st).size(100, 100).children(() -> {
        box(c).height(200);
        box(c).height(10).top(500);
    }), 300, 300);
    tt.frame();
    Check.near(110, st.maxY());                             // 210 − 100, the offset aside
}
```

- [ ] **Step 2**：运行 `./build.sh test PositionTest`。Expected：前两个新测试失败（元素没有移动），第三个已经通过。它守住的是“不扩大内容尺寸”。
- [ ] **Step 3**：实现（细节第 10 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): top, left, bottom and right move an element in the flow`。

---

### Task 3：圆角裁剪

**Files:**
- Modify: `Scene.java`、`shader.metal`、`Paint.java`、`HitList.java`、`Engine.java`（`commitHits`）
- Test: `src/test/java/jui/render/SceneTest.java`、`src/test/java/jui/ui/engine/PaintTest.java`、`src/test/java/jui/ui/PointerTest.java`

**Interfaces:**
- **Produces**：
  - **实例**：`Scene.FLOATS` 从 20 改成 28，第 20–23 个是最内层裁剪的矩形 `x, y, w, h`，第 24–27 个是它的四个圆角（左上、右上、右下、左下）；没有圆角时全为 0，着色器跳过。
  - `Scene.writeTo` 把第 20–27 个分量都按缩放换成像素，**所有种类都换**（字形的其他分量已经是像素，这几个不是）。
  - `Scene.pushClip(float x, float y, float w, float h, float tl, float tr, float br, float bl)`；原来的 `pushClip(x, y, w, h)` 等于四个圆角都是 0。
  - **着色器**：`Inst` 加 `float4 clip; float4 clipRadii;`。片元着色器在每种实例算出的透明度上乘 `clipRadii` 不全为 0 时的 `clamp(0.5 − sdRoundRect(p, clip, clipRadii), 0, 1)`。
  - **`Paint`**：裁剪的元素推入边框以内的矩形，圆角 = `max(min(r, 短边/2) − border, 0)`。
  - **`HitList`**：每个条目另记最内层**圆角**裁剪的矩形和四个圆角（没有圆角时为 0），`contains` 在矩形判断之外再按圆角矩形判断。

- [ ] **Step 1：写失败的测试**

```java
// SceneTest
public void testInstancesCarryTheInnermostClipsRoundedRect() {
    Scene s = new Scene();
    s.begin(2, null, null);
    s.box(0, 0, 10, 10, 0, 0xFF0000FF);                     // no clip
    s.pushClip(10, 20, 50, 30, 8, 8, 4, 4);
    s.box(12, 22, 10, 10, 0, 0xFF0000FF);
    s.pushClip(15, 25, 20, 10);                            // a rectangle inside: the rounding goes (as MyGO)
    s.box(16, 26, 5, 5, 0, 0xFF0000FF);
    s.popClip();
    s.popClip();
    float[] none = s.instance(0), rounded = s.instance(1), inner = s.instance(2);
    Check.near(0, none[24] + none[25] + none[26] + none[27]);
    Check.equal(java.util.List.of(10f, 20f, 50f, 30f, 8f, 8f, 4f, 4f), java.util.List.of(rounded[20], rounded[21],
            rounded[22], rounded[23], rounded[24], rounded[25], rounded[26], rounded[27]));
    Check.near(0, inner[24] + inner[25] + inner[26] + inner[27]);
}

public void testTheClipGoesToPixelsForEveryKind() {
    Scene s = new Scene();
    s.begin(2, null, null);
    s.pushClip(10, 20, 50, 30, 8, 8, 8, 8);
    s.box(12, 22, 10, 10, 0, 0xFF0000FF);
    s.popClip();
    java.lang.foreign.MemorySegment out = java.lang.foreign.Arena.ofAuto().allocate(Scene.BYTES);
    s.writeTo(out, 1);
    Check.near(20, out.getAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, 20));
    Check.near(16, out.getAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, 24));
}

// PaintTest
public void testAClippingBoxWithARadiusClipsItsChildrenToARoundedRectInsideItsBorder() {
    // box(c).size(100, 60).radius(10).border(2, ...).clip() holding a child that fills it:
    // the child's instance carries clip {x+2, y+2, 96, 56} and radii {8, 8, 8, 8}
}

// PointerTest
public void testAPressInAClippedCornerMissesTheChild() { // Review Focus 3
    int[] clicks = {0};
    Tester tt = Tester.of(c -> column(c).padding(10).children(() ->
            box(c).size(100, 100).radius(30).clip().label("card").children(() -> {
                if (box(c).fill().label("inside").clicked()) clicks[0]++;
            })), 300, 300);
    Rect r = tt.find("card");
    tt.press(r.x() + 2, r.y() + 2);                         // in the square corner, outside the rounding
    tt.release(r.x() + 2, r.y() + 2);
    Check.equal(0, clicks[0]);
    tt.click("inside");                                      // its middle still clicks
    Check.equal(1, clicks[0]);
}
```

`PaintTest` 那一个按注释写成代码：用 `Tester` 建出来，在 `tt.scene()` 里找子元素的那个实例（大小和位置同子元素），断言第 20–27 个分量。

- [ ] **Step 2**：运行 `./build.sh test SceneTest`、`PaintTest`、`PointerTest`。Expected：编译失败或断言失败。
- [ ] **Step 3**：实现（细节第 8 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过（现有测试读的是第 0–19 个分量，不受影响）。
- [ ] **Step 5**：提交，信息 `feat(render): rounded clips: the innermost clip's corners in the shader`。

---

### Task 4：描边和复选框的勾号

**Files:**
- Modify: `Scene.java`、`shader.metal`、`Node.java`、`Paint.java`、`Ui.java`（`checkbox`）
- Test: `SceneTest`、`src/test/java/jui/ui/ControlsTest.java`

**Interfaces:**
- **Produces**：
  - `Scene.stroke(float ax, float ay, float bx, float by, float cx, float cy, float width, int color)`：种类 3 的实例：
    - 矩形是三个点的外接框外扩 `width / 2`；
    - 第 4–7 个分量是 `ax, ay, bx, by`，第 12、13 个是 `cx, cy`，第 16 个是 `width / 2`；
    - 都是长度，`writeTo` 换成像素。
  - `Scene.line(x0, y0, x1, y1, width, color)` 等于 `stroke(x0, y0, x1, y1, x1, y1, width, color)`。
  - **着色器**：顶点阶段给种类 3 外扩 1 像素；片元阶段 `d = min(sdSegment(p, a, b), sdSegment(p, b, c)) − hw`，覆盖率 `clamp(0.5 − d, 0, 1)`。
  - **`Node.checkMark`**：为真时 `Paint` 在框的背景之后画细节第 9 条的勾号，颜色 `theme.accentText()`，乘元素的透明度。`checkbox` 选中时给框设上它，不再建文字 “✓”。

- [ ] **Step 1：写失败的测试**

```java
// SceneTest
public void testAStrokeIsAnInstanceOfItsOwn() {
    Scene s = new Scene();
    s.begin(2, null, null);
    s.stroke(10, 10, 20, 30, 40, 0, 2, 0xFFFFFFFF);
    float[] in = s.instance(0);
    Check.near(3, in[18]);                                  // kind 3
    Check.equal(java.util.List.of(9f, -1f, 32f, 32f), java.util.List.of(in[0], in[1], in[2], in[3]));  // bounds, 1 out
    Check.equal(java.util.List.of(10f, 10f, 20f, 30f, 40f, 0f), java.util.List.of(in[4], in[5], in[6], in[7], in[12], in[13]));
    Check.near(1, in[16]);                                  // half the width
}

// ControlsTest: in testCheckboxTogglesItsRefAndReportsChanged, "✓" becomes the stroke:
static int checkMarks(Tester tt) {
    int n = 0;
    for (int i = 0; i < tt.instances(); i++) if (tt.scene().instance(i)[18] == 3) n++;
    return n;
}
// tt.click("完成") → Check.isTrue(done.get() && checkMarks(tt) == 1, "checked, with its mark")
// tt.click("完成") → Check.isTrue(!done.get() && checkMarks(tt) == 0, "unchecked")

public void testTheCheckMarkIsMyGOsPath() {
    BoolRef on = new BoolRef(true);
    Tester tt = Tester.of(c -> column(c).padding(10).children(() -> checkbox(c, on, "x")), 300, 200);
    tt.frame();
    // the box is the 16 by 16 instance at the row's left; the mark's points, from the box's top left:
    // (3.52, 8.32) → (6.72, 11.36) → (12.48, 4.96), width 2, color accentText
}
```

`testTheCheckMarkIsMyGOsPath` 按注释写成代码：找到种类 3 的那个实例，用框的位置算出三个点，逐个对比（误差 0.01），并比较颜色和半宽 1。

- [ ] **Step 2**：运行 `./build.sh test SceneTest` 和 `./build.sh test ControlsTest`。Expected：编译失败或断言失败。
- [ ] **Step 3**：实现（细节第 9 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(render): strokes; the check box's mark is drawn, not a glyph`。

---

### Task 5：横向滚动的布局和 `ScrollState` 的 x

**Files:**
- Modify: `Node.java`、`ElementState.java`、`Engine.java`（`makeScroll`、`commit`、`requestScroll`）、`FlexLayout.java`、`Ui.java`、`ScrollState.java`
- Test: `src/test/java/jui/ui/ScrollXTest.java`

**Interfaces:**
- **Produces**：
  - **标志**：`Node.SCROLL` 表示“滚动容器”（任一方向）；新加 `SCROLL_X = 512`、`SCROLL_Y = 1024` 表示方向。`scroll` 设 `SCROLL | SCROLL_Y`，`scrollHorizontal` 设 `SCROLL | SCROLL_X`，`scrollBoth` 三个都设。现有按纵向处理的地方改成看 `SCROLL_Y`。
  - **`Node` 和 `ElementState`**：
    - `Node`：`scrollX`、`scrollRequestX`、`contentW`；
    - `ElementState`：`scrollX`、`contentW`、`viewW`、`requestX`（NaN 表示没有请求）；
    - `commit` 按轴各自写回。
  - **`Engine`**：`makeScroll(int index, boolean x, boolean y)`；`requestScroll(long id, float x, float y)`，NaN 表示这个方向不动。
  - **`FlexLayout`**（细节第 2 条）：
    - 滚动的轴传 NaN；
    - 那个轴上 `grow` 不起作用；
    - `scrollBoth` 的列里，要拉伸的子元素拉伸到最宽的子元素；
    - 算出 `contentW`，夹住 `scrollX`，子元素整体左移 `scrollX`。
  - **`ScrollState`**：`float x()`、`float maxX()`、`void scrollTo(float x, float y)`。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ScrollXTest {
    public void testAHorizontalScrollContainerScrollsItsRowAcross() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).label("h").children(() -> {
            for (int i = 0; i < 3; i++) box(c).size(200, 40).label("b" + i);
        }), 400, 200);
        tt.frame();
        Check.near(300, st.maxX());                         // 600 − 300
        Rect h = tt.find("h");
        Check.near(h.y() + 30, tt.find("b0").y());          // centered down, as a row
        st.scrollTo(150, 0);
        tt.frame();
        Check.near(h.x() - 150, tt.find("b0").x());
        st.scrollTo(1000, 0);
        tt.frame();
        Check.near(300, st.x());                            // held within its content
    }

    public void testGrowDoesNothingAlongTheScrollingAxis() {
        Tester tt = Tester.of(c -> scrollHorizontal(c).size(300, 100).children(() -> box(c).size(50, 20).grow(1).label("g")),
                400, 200);
        Check.near(50, tt.find("g").width());
    }

    public void testScrollBothStretchesChildrenToTheWidest() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scrollBoth(c, st).size(300, 200).children(() -> {
            box(c).size(500, 300).label("wide");
            box(c).height(100).label("stretched");
        }), 400, 400);
        tt.frame();
        Check.near(200, st.maxX());
        Check.near(200, st.maxY());
        Check.near(500, tt.find("stretched").width());
    }

    public void testAVerticalContainerKeepsXAtZero() {
        ScrollState st = new ScrollState();
        Tester tt = Tester.of(c -> scroll(c, st).size(100, 100).children(() -> box(c).size(500, 500)), 300, 300);
        tt.frame();
        st.scrollTo(50, 0);
        tt.frame();
        Check.near(0, st.x());
        Check.near(0, st.maxX());
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test ScrollXTest`。Expected：编译失败。
- [ ] **Step 3**：实现（细节第 2 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过（纵向滚动的测试不受影响）。
- [ ] **Step 5**：提交，信息 `feat(ui): horizontal scroll containers, and scrolling both ways`。

---

### Task 6：横向滚动的输入、滚动条和滚进视野

**Files:**
- Modify: `Input.java`（`scroll`、`advanceGlides`、`scrollKey`、`scrollByKey`、`barPress`、`pointerMove`）、`ElementState.java`（横向的平滑滚动）、`ScrollBar.java`、`Paint.java`（横向滚动条、角）、`Engine.java`（`scroll` 传 dx、`reveal` 按轴）
- Test: `ScrollXTest`

**Interfaces:**
- **Produces**：
  - `Input.scroll(float x, float y, float dx, float dy, boolean precise, int mods)`：细节第 3 条；
  - `ElementState`：`wheelDistanceX`、`wheelDoneX`；`wheeling` 按轴拆成 `wheelingX`、`wheelingY`；
  - `scrollKey`：不带修饰键的 `LEFT`/`RIGHT` 是 ±40，只作用于 `SCROLL_X` 的容器（细节第 4 条）；
  - `ScrollBar`：
    - `thumbLength(float track, float view, float content)`：在长 `track` 的轨道上，视口长 `view`、内容长 `content` 时滑块的长度；
    - `thumbStart(float start, float track, float view, float content, float offset)`：滑块的起点；
    - 纵向、横向共用这两个函数；
  - 横向滚动条按细节第 5 条显示、绘制、拖动和翻页；
  - `reveal`：两个方向各按“最少移动”。

- [ ] **Step 1：写失败的测试**

```java
public void testAWheelAcrossAndShiftWheelScrollAcross() {
    ScrollState st = new ScrollState();
    Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).label("h").children(() -> {
        for (int i = 0; i < 3; i++) box(c).size(200, 40);
    }), 400, 200);
    tt.scroll("h", 30, 0);                                  // a trackpad across: at once
    Check.near(30, st.x());
    Rect h = tt.find("h");
    tt.engine().scroll(h.x() + 10, h.y() + 10, 0, 40, false, Mod.SHIFT);   // Shift turns a wheel's notch across
    tt.advance(java.time.Duration.ofMillis(200));
    Check.near(70, st.x());
}

public void testAWheelDownOverAHorizontalContainerGoesToTheOneAroundIt() { // Review Focus 1
    ScrollState outer = new ScrollState(), inner = new ScrollState();
    Tester tt = Tester.of(c -> scroll(c, outer).size(300, 200).children(() -> {
        scrollHorizontal(c, inner).size(300, 100).label("h").children(() -> box(c).size(900, 40));
        box(c).height(500);
    }), 400, 400);
    tt.scroll("h", 0, 50);
    Check.near(50, outer.y());
    Check.near(0, inner.x());
}

public void testArrowKeysScrollAcrossWhenNothingElseTakesThem() {
    ScrollState st = new ScrollState();
    Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).children(() -> {
        button(c, "b");
        box(c).size(900, 40);
    }), 400, 200);
    tt.key(Shortcut.of(Key.TAB));                          // focus inside it
    tt.key(Shortcut.of(Key.RIGHT));
    Check.near(40, st.x());
}

public void testTheBarAlongTheBottomDragsAndPages() {
    ScrollState st = new ScrollState();
    Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).label("h").children(() -> box(c).size(1200, 40)),
            400, 200);
    Rect h = tt.find("h");
    tt.hover("h");
    float y = h.y() + h.height() - 3 - 3;                   // the bar's middle: 6 thick, 3 from the bottom
    tt.press(h.x() + h.width() - 20, y);                   // on the track, right of the thumb: a page
    tt.release(h.x() + h.width() - 20, y);
    Check.near(270, st.x());                                // 0.9 × 300
}

public void testTabRevealsAnElementOffTheRightEdge() {
    ScrollState st = new ScrollState();
    Tester tt = Tester.of(c -> scrollHorizontal(c, st).size(300, 100).children(() -> {
        for (int i = 0; i < 6; i++) box(c).size(100, 40).focusable().label("f" + i);
    }), 400, 200);
    for (int i = 0; i < 5; i++) tt.key(Shortcut.of(Key.TAB));       // to f4, at 400–500
    Check.near(200, st.x());                                // its right edge at the view's right
}
```

- [ ] **Step 2**：运行 `./build.sh test ScrollXTest`。Expected：新测试失败（横向没有输入、滚动条和滚进视野）。
- [ ] **Step 3**：实现（细节第 3、4、5 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): wheels, keys, bars and reveal across`。

---

### Task 7：带动画的 `scrollTo`

**Files:**
- Modify: `ScrollState.java`、`ElementState.java`、`Engine.java`、`Input.java`（中断）
- Test: `src/test/java/jui/ui/ScrollAnimationTest.java`

**Interfaces:**
- **Produces**：
  - `ScrollState.scrollTo(float y, Duration d)`、`scrollTo(float x, float y, Duration d)`、`boolean scrolling()`；
  - `ElementState`：`animating`，各轴的 `animFrom*`、`animTo*`，以及 `animStart`、`animDuration`；
  - `Engine.animateScroll(long id, float x, float y, long durationNanos)`：NaN 表示这个轴不动；
  - `makeScroll` 每帧按曲线算出当前偏移，作为请求交给布局，没结束就请求下一帧；
  - 细节第 6 条列出的输入都会把 `animating` 清掉。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import java.time.Duration;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ScrollAnimationTest {
    final ScrollState st = new ScrollState();
    final int[] rows = {50};
    final View view = c -> scroll(c, st).size(200, 200).label("s").children(() -> {
        for (int i = 0; i < rows[0]; i++) box(c).height(20);
    });

    public void testItEasesOutToItsTarget() {
        Tester tt = Tester.of(view, 300, 300);
        tt.frame();
        st.scrollTo(400, Duration.ofMillis(200));
        tt.frame();
        Check.isTrue(st.scrolling(), "scrolling");
        tt.advance(Duration.ofMillis(100));
        Check.near(350, st.y(), 0.5);                       // 400 × (1 − 0.5³)
        tt.advance(Duration.ofMillis(120));
        Check.near(400, st.y());
        Check.isTrue(!st.scrolling() && !tt.framePending(), "at rest");
    }

    public void testAWheelStopsIt() {
        Tester tt = Tester.of(view, 300, 300);
        tt.frame();
        st.scrollTo(600, Duration.ofMillis(300));
        tt.advance(Duration.ofMillis(50));
        tt.scroll("s", 0, -10);                             // the user takes over
        float at = st.y();
        tt.advance(Duration.ofMillis(400));
        Check.isTrue(!st.scrolling(), "stopped");
        Check.near(at, st.y());
    }

    public void testFewerRowsMidWayEndAtTheNewEnd() { // Review Focus 2
        Tester tt = Tester.of(view, 300, 300);
        tt.frame();
        st.scrollTo(800, Duration.ofMillis(200));          // max 800
        tt.advance(Duration.ofMillis(50));
        rows[0] = 20;                                       // max 200 now
        tt.advance(Duration.ofMillis(300));
        Check.near(200, st.y());
        Check.isTrue(!st.scrolling(), "done");
    }

    public void testZeroJumps() {
        Tester tt = Tester.of(view, 300, 300);
        tt.frame();
        st.scrollTo(300, Duration.ZERO);
        tt.frame();
        Check.near(300, st.y());
        Check.isTrue(!st.scrolling(), "no animation");
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test ScrollAnimationTest`。Expected：编译失败。
- [ ] **Step 3**：实现（细节第 6 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): ScrollState.scrollTo with an ease-out animation`。

---

### Task 8：`Element.scrollIntoView()`

**Files:**
- Modify: `Element.java`、`Engine.java`（`reveal` 改为处理一列身份）、`Input.java`（Tab 往列表里加）
- Test: `src/test/java/jui/ui/ScrollTest.java`（增加测试）

**Interfaces:**
- **Produces**：
  - `Element scrollIntoView()`：把元素的身份加进这一帧要滚进视野的列表；
  - `Engine.reveal()` 在布局后按顺序处理这个列表，两个方向都处理；
  - Tab 的 `revealId` 也走这个列表；处理完清空。

- [ ] **Step 1：写失败的测试**

```java
public void testScrollIntoViewRevealsThroughNestedContainersInTheSameFrame() {
    ScrollState outer = new ScrollState(), inner = new ScrollState();
    boolean[] show = {false};
    Tester tt = Tester.of(c -> scroll(c, outer).size(300, 200).label("outer").children(() -> {
        box(c).height(300);
        scroll(c, inner).size(300, 150).label("inner").children(() -> {
            box(c).height(400);
            if (show[0]) box(c).height(30).label("target").scrollIntoView();
        });
    }), 400, 400);
    tt.frame();
    show[0] = true;
    tt.frame();                                             // the frame it appears in
    Rect t = tt.find("target"), in = tt.find("inner");
    Check.isTrue(t.y() >= in.y() - 0.01f && t.y() + t.height() <= in.y() + in.height() + 0.01f, "inside the inner view");
    Check.isTrue(t.y() >= -0.01f && t.y() + t.height() <= 200.01f, "inside the outer view");
}

public void testScrollIntoViewStopsAWheelsGlide() {
    ScrollState st = new ScrollState();
    boolean[] reveal = {false};
    Tester tt = Tester.of(c -> scroll(c, st).size(200, 200).label("s").children(() -> {
        Element top = box(c).height(30).label("top");
        if (reveal[0]) top.scrollIntoView();
        box(c).height(2000);
    }), 300, 300);
    tt.wheel("s", 0, 400);                                  // a glide down
    reveal[0] = true;
    tt.frame();
    reveal[0] = false;
    tt.advance(java.time.Duration.ofMillis(300));
    Check.near(0, st.y());                                  // the glide did not carry it away again
}
```

- [ ] **Step 2**：运行 `./build.sh test ScrollTest`。Expected：编译失败。
- [ ] **Step 3**：实现（细节第 7 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): Element.scrollIntoView`。

---

### Task 9：列表按 key 找回锚点，焦点行一直构建

**Files:**
- Modify: `ListState.java`、`ListModel.java`、`Engine.java`（`listPlan`、`rowIndex`、`afterList`）、`Ui.java`（`list` 的建行顺序）、`FlexLayout.java`（`placeList` 的额外行）
- Test: `src/test/java/jui/ui/ListKeyTest.java`

**Interfaces:**
- **Produces**：
  - `ListState.keys(IntToLongFunction keyOf)`：存在 `ListModel.keyOf`；
  - **找回锚点**：`ListModel.anchorKey`、`hasAnchorKey`；`find(long key, int near, int count)` 按细节第 13 条找，找不到返回 −1；`shift(int delta)` 把行高整体平移；
  - **行的身份**：给了 `keyOf` 时，`Engine.rowIndex(i)` 用 `keyOf(i)` 作 key；
  - **上一帧的行**：`ListModel` 记下上一帧每行的身份和行号（`lastIds`、`lastRows`，可复用的数组）；
  - **额外的焦点行**：`ListModel.extraRow`（没有为 −1）；`Ui.list` 按细节第 14 条的顺序建它；`FlexLayout.placeList` 按 `Node.listExtra`（它在子元素里的位置）和 `Node.listExtraY` 放它，不把它算进连续的行。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import java.util.ArrayList;
import java.util.List;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ListKeyTest {
    final ListState state = new ListState();
    final List<Long> items = new ArrayList<>();
    {
        for (long i = 0; i < 200; i++) items.add(i);
        state.keys(i -> items.get(i));
    }
    final View view = c -> list(c, state, items.size(), i -> box(c).height(30).label("item" + items.get(i))).height(300).label("list");

    public void testRowsInsertedAboveKeepTheViewOnTheSameItem() { // Review Focus 4
        Tester tt = Tester.of(view, 300, 400);
        state.scrollTo(50);
        tt.frame();
        Rect before = tt.find("item50");
        for (long k = 1000; k < 1003; k++) items.add(0, k);  // three new items at the front
        tt.frame();
        Check.near(before.y(), tt.find("item50").y());      // the same item where it was
        Check.equal(53, state.first());
    }

    public void testTheAnchoredItemRemovedFallsBackToItsIndex() { // Review Focus 4
        Tester tt = Tester.of(view, 300, 400);
        state.scrollTo(50);
        tt.frame();
        items.remove(Long.valueOf(50));
        tt.frame();
        Check.equal(50, state.first());                     // by index now: no jump to the start
    }

    public void testTheFocusedRowStaysBuiltOutOfView() {
        int[] presses = {0};
        Tester tt = Tester.of(c -> list(c, state, items.size(), i -> {
            Element r = box(c).height(30).focusable().label("item" + items.get(i));
            if (r.shortcut(Shortcut.of(Key.D))) presses[0]++;
        }).height(300).label("list"), 300, 400);
        tt.click("item0");
        tt.scroll("list", 0, 3000);                         // item 0 far above the view
        Check.isTrue(tt.focused("item0"), "still built, still focused");
        Check.isTrue(tt.find("item0").y() + 30 <= tt.find("list").y() + 0.01f, "above the view");
        tt.key(Shortcut.of(Key.D));                          // its keys still reach it
        Check.equal(1, presses[0]);
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test ListKeyTest`。Expected：编译失败。
- [ ] **Step 3**：实现（细节第 13、14 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过（`ListTest` 不受影响）。
- [ ] **Step 5**：提交，信息 `feat(ui): lists anchored by key; the focused row is built out of view`。

---

### Task 10：跟随末尾

**Files:**
- Modify: `ListState.java`、`ListModel.java`、`Engine.java`（`listPlan`、`afterList`）
- Test: `src/test/java/jui/ui/ListFollowTest.java`

**Interfaces:**
- **Produces**：`ListState.followEnd(boolean)`、`boolean atEnd()`、`scrollToEnd()`；`ListModel.followEnd`、`following`（细节第 15 条）。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ListFollowTest { // Review Focus 5
    final ListState state = new ListState().followEnd(true);
    final int[] count = {100};
    final View view = c -> list(c, state, count[0], i -> box(c).height(30).label("row" + i)).height(300).label("list");

    public void testItStartsAtTheEndAndFollowsAppendedRows() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        Check.equal(99, state.last());
        count[0] = 120;
        tt.frame();
        Check.equal(119, state.last());
        Check.isTrue(state.atEnd(), "at the end");
    }

    public void testScrollingUpStopsFollowingAndBackDownResumes() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        tt.scroll("list", 0, -300);
        int last = state.last();
        count[0] = 130;
        tt.frame();
        Check.equal(last, state.last());                    // not pulled down
        tt.scroll("list", 0, 100_000);                      // back to the end
        count[0] = 140;
        tt.frame();
        Check.equal(139, state.last());
    }

    public void testScrollToEndResumes() {
        Tester tt = Tester.of(view, 300, 400);
        tt.frame();
        tt.scroll("list", 0, -300);
        state.scrollToEnd();
        tt.frame();
        count[0] = 110;
        tt.frame();
        Check.equal(109, state.last());
    }
}
```

`followEnd` 返回 `this`，方便上面那样在字段初始化时设好。

- [ ] **Step 2**：运行 `./build.sh test ListFollowTest`。Expected：编译失败。
- [ ] **Step 3**：实现（细节第 15 条）。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): lists that follow their end`。

---

### Task 11：示例、门槛和文档

**Files:**
- Create: `src/main/java/jui/examples/ScrollDemo.java`、`src/test/java/jui/examples/ScrollDemoTest.java`
- Modify: `README.md`

**Interfaces:**
- **Produces**：**`ScrollDemo`**（`./build.sh demo Scroll`）。一个窗口，上下三块：
  1. **横向卡片条**：
     - 圆角 12、带边框、`clip()` 的 `scrollHorizontal`，里面 20 张彩色卡片，每张能获得焦点；
     - 两个按钮“往左（动画）”“往右（动画）”，用 `scrollTo(x ± 400, 0, 300 ms)`。
  2. **日志列表**：
     - `followEnd(true)` 的列表，按 key 构建，每行是“第 N 条日志”；
     - 两个按钮：“追加 5 行”、“回到末尾”（`scrollToEnd()`）。
  3. **一排复选框**：看描边的勾号。
- **`ScrollDemoTest`**：建出视图；点“追加 5 行”，末行跟到最后一条；点“往右（动画）”，推进 400 ms，横向偏移到 400。
- **README 一节**：做了什么，测试数目，`./build.sh alloc`、`./build.sh listbench` 的结果，示例截图 `docs/scroll-demo.png`，需要手动核对的项目。手动核对的项目有：
  - 圆角裁剪的边缘；
  - 勾号的样子；
  - 触控板横向滑动、Shift+滚轮；
  - 横向滚动条；
  - 带动画的滚动；
  - 日志跟随末尾。

- [ ] **Step 1**：写 `ScrollDemoTest`，运行 `./build.sh test ScrollDemoTest`。Expected：编译失败。
- [ ] **Step 2**：写 `ScrollDemo`。运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 3**：运行 `./build.sh alloc`、`./build.sh listbench`、`./build.sh ffi-check`。Expected：都通过。实例变大以后，native image 里每个元素仍然是 0 字节分配。
- [ ] **Step 4**：`./build.sh native-demo Scroll --print-window`，截图存成 `docs/scroll-demo.png`。打开截图，检查三处：圆角的边缘平滑、卡片在圆角处被裁掉、勾号是描边。
- [ ] **Step 5**：写 README，运行 `./build.sh test`，提交，信息 `feat(examples): a scroll demo; docs: the scroll, list and layout follow-ups`。

---

## 完成之后

- 整体审查一次（superpowers:executing-plans 的 Final Review），对照本计划的细节和 Review Focus。
- 合并方式由用户决定。
