# M4 文本输入 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal**：实现设计文档里的 M4。

- **单行输入框**：
  - `textInput(c, TextRef)` 和无外观的 `textInputBase`；
  - 光标、选区、键盘编辑、鼠标选择；
  - 中文输入法（`NSTextInputClient`）；
  - 剪贴板、撤销和重做；
  - 系统的“编辑”菜单。
- **M2 推迟下来的**：`submitted()` / `onSubmit(Runnable)`、`cursor(Cursor)`，以及鼠标指针形状。
- **TextEngine 的新能力**（设计 6.12）：按坐标找字符位置、按字符位置求光标位置。

验收示例是 1 万篇笔记的文件列表加上搜索框：输入时实时过滤；用拼音输入中文时，候选框出现在光标下面。

**Architecture**：
- **编辑模型**：`TextEdit` 是纯 Java 的单行编辑模型，按身份存在元素状态里。
  - 保存已提交的文字、光标和锚点（UTF-16 下标）、组字（输入法的拼音阶段）和撤销历史。
  - 组字不在文字里，显示时插在光标处，同 MyGO。
- **事件到达时立刻生效**（M2 定下的做法）：按键、文字、组字、菜单命令、鼠标按下和拖动，一到就改焦点所在输入框的 `TextEdit`。
  - 这样，输入法在两帧之间来查询选区、组字范围和光标位置时，读到的就是最新的状态。
- **和 `TextRef` 同步**：构建时，控件比较 `TextRef` 和上次同步的值。
  - 应用改了 `TextRef`：用应用的值，同 MyGO；
  - 用户改了文字：写回 `TextRef`，报告 `changed()`。
- **绘制**：输入框是新的节点种类 `INPUT`，由 `Paint` 直接画出选区、文字、组字下划线、光标和占位文字，同 M2 的滑块。
  - 横向滚动在提交时计算：获得焦点时让光标留在视野里，没有焦点时回到开头。
- **平台**：
  - `JUIView` 实现 `NSTextInputClient`。按键先按原始按键交给引擎（组字时除外），再交给 `interpretKeyEvents:`，文字从 `insertText:`、`setMarkedText:` 进来，同 MyGO 的 `surface.go`。
  - 剪贴板、指针形状、让输入法丢弃组字，都通过引擎的 `Host` 接口交给平台；`Tester` 用内存里的实现。
  - 编辑菜单的命令沿响应链到 `JUIView`，再交给引擎。

**Tech Stack**：
- Java 25、GraalVM CE 25（`graalvm-community-25.4.4.1.1+1.1`）；
- FFM（`jui.ffi`）、AppKit、Metal、Core Text；
- 零依赖：自己写的测试运行器，构建用 `build.sh`。

**Spec**：[docs/design/2026-10-24-ui-api-design.md](../design/2026-10-24-ui-api-design.md)，已定稿。本计划实现其中的 M4（第 10 节）：
- API 依据第 6.2（`submitted`、`onSubmit`、`cursor`）、6.4（`TextRef`）、6.7（编辑菜单）、6.10（`textInput`）、6.12（TextEngine 的新能力）、6.14（`type`、剪贴板）节；
- 内部设计依据第 7.2（文本输入状态）、7.3（按键先给文本输入）、7.7（输入法、剪贴板、光标）节；
- 性能依据第 8 节。

**行为依据**：MyGO（egoist/mygo，commit `8049993`）。可以用 `git clone https://github.com/egoist/mygo.git && git -C mygo checkout 8049993` 取一份。下文写“同 MyGO”的地方，以这个版本为准：
- 输入框和编辑：`ui/textinput.go`、`ui/editor.go`、`ui/editor_navigation.go`、`ui/editor_selection.go`、`ui/editor_history.go`、`ui/editor_layout.go`、`ui/editor_input.go`；
- 词的边界：`internal/text/word.go`、`ui/textbuffer.go`（`nextWord`、`prevWord`、`wordAt`）；
- 按键路由、焦点、指针形状：`ui/input.go`、`ui/runtime.go`（`updateCursor`）；
- 输入法和平台：`internal/darwin/surface.go`（第 915–1036 行）、`internal/darwin/surface_input.go`、`internal/darwin/system.go`（剪贴板）、`internal/darwin/menu.go`。

已经验证过的两点（2026-10-26，GraalVM CE 25，native image）：
- FFM 的 upcall 返回结构体（`NSRange`、`NSRect`）、接受 `NSPoint` 参数，在 native image 里都正常。
- `java.text.BreakIterator.getCharacterInstance()` 按扩展字素簇切分（组合附加符、emoji、ZWJ 家庭、国旗），在 native image 里和 JVM 上结果一致。

## Global Constraints

沿用 M3 的约束：
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
  - 框架内部：内容不变的帧里，不能产生和元素数量成正比的分配；
  - 整帧：每帧不超过 32 KB。

M4 新增三条：
- **文字的下标是 UTF-16**：Java 的 `String` 和 `NSTextInputClient` 都按 UTF-16 计数，`TextEdit`、`TextLayout` 和引擎的输入法接口一律用 UTF-16 下标。光标和选区的两端总在字素簇的边界上（MyGO 用码点存、按字素簇移动，结果相同）。
- **输入框帧的分配**：输入框获得焦点、只有光标在闪烁的帧，属于内容不变的帧，框架内部每个输入框分配 0 字节。编辑的那一帧可以分配，比如新的字符串、排版和撤销记录。
- **打字延迟不超过一帧**（设计第 8 节）：按键和文字到达时就改 `TextEdit` 并请求一帧，这一帧就把结果画出来。

## 本计划定下的细节

设计文档没写死、或者 M4 有意和设计文档或 MyGO 不同的地方，实现者照做。审查时请重点检查：

1. **API**：
   - `TextRef`：同其他引用对象，`get()`、`set(String)`；`set(null)` 存成空字符串。
   - `Ui.textInputBase(Context, TextRef)`：无外观，只处理指针、键盘、焦点、输入法和数据绑定；`Ui.textInput(Context, TextRef)`：带样式，建立在它之上（设计 6.10）。
   - `Element.placeholder(String)`：只对输入框有意义，用在别的元素上时开发模式报错（设计第 4 节第 1 条）。
   - `Element.submitted()`、`Element.onSubmit(Runnable)`：设计 6.2 有回调，MyGO 只有查询。
   - `Element.cursor(Cursor)`。`Cursor` 枚举取 MyGO 的一个子集：`DEFAULT`、`POINTER`、`TEXT`、`RESIZE_EW`、`RESIZE_NS`、`NOT_ALLOWED`、`CROSSHAIR`、`GRAB`、`GRABBING`。其余的等第一个用到它们的里程碑再加。
   - `EditCommand` 枚举：`UNDO`、`REDO`、`CUT`、`COPY`、`PASTE`、`SELECT_ALL`，对应 MyGO 的命令名。
   - **M4 不做**：只读（`ReadOnly`）、密码框、`OnPaste`、`OnTextKey`、`SetTextSelection`、关闭输入法、多行文本框，以及菜单里的“粘贴并匹配样式”“删除”“语音”。右键菜单在 M5 和 `contextMenu` 一起做。
   - 设计 6.12 给编辑器预留的“元素接管文本输入”（`handleTextInput`）和多样式的富文本片段，留给编辑器的设计；M4 的引擎接口只服务 `textInputBase`，但按“插入、组字、编辑命令、报告光标位置”这几样来分，以后可以直接开放。
2. **组字不进 `TextRef`**（同 MyGO）：
   - `TextRef` 只有已提交的文字；组字期间只是显示，不报告 `changed()`。
   - 例外：组字开始时删掉了选中的文字，这时已提交的文字变了，照常报告。
   - 输入法提交（`insertText:`）或结束组字（`unmarkText`）时，文字写进 `TextRef`，报告 `changed()`。
3. **和 `TextRef` 的同步规则**（同 MyGO 的 `textinput.go`、`value_input.go`）：
   - **应用改了 `TextRef`**（和上次同步的值不同）：用应用的值。光标和锚点各自截到新长度，组字丢弃并通知输入法，撤销历史里最后一步的“之后”改成应用的值，重做清空。
   - 用户在两帧之间也改了文字时，**应用的值优先**，用户的修改丢弃。
   - **否则用户改了文字**：写回 `TextRef`，在这一遍报告 `changed()`。
   - 应用的修改不报告 `changed()`。
4. **按键表**（同 MyGO 的 `editor.wants` 和 `editor.key` 在 macOS 上的行为）：
   - **输入框要的键，先于一切快捷键**，包括它自己的（设计 7.3：先看文本输入，再看元素的快捷键，最后是窗口快捷键）。不要的键照常交给快捷键。
   - 不带 ⌘ 和 Ctrl 的可打印键（含 Shift、Option 组合）也“要”，但什么都不做：它们的文字从 `insertText:` 进来。所以打字时，窗口上没有修饰键的快捷键不会被触发。
   - 单行输入框**不要**单独的 ↑/↓/PageUp/PageDown（带修饰键时才要），它们照常滚动外面的容器，或者交给快捷键。
   - Tab、Esc、F1–F12、⌘+其他字母、⌘回车 不要。
   - 具体的键见 Task 3。
5. **按 Tab 进入输入框时全选**（同 MyGO 的 `moveFocus`）。点击进入时不全选，光标放在点击处。
6. **输入法桥接**（同 MyGO 的 `surface.go`）：
   - `keyDown:`：输入框获得焦点、窗口在前台、没按 ⌘ 和 Ctrl 时，算作“文本输入”。
   - 不在组字，先把原始按键交给引擎；再调 `interpretKeyEvents:`，让输入法处理。
   - 按了 ⌘ 或 Ctrl 的键不经过输入法，只走原始按键和菜单。
   - `doCommandBySelector:`：**和 MyGO 不同**。MyGO 是空方法。j-ui 在“组字中、原始按键被压下了”的那次按键里，如果输入法把命令交回来，就补发那次原始按键，补一次。这样死键组字（比如 Option+E）之后按方向键不会丢；拼音输入法自己处理方向键，不会交回来。
7. **组字由 j-ui 自己提交时，告诉输入法**：
   - 焦点离开组字中的输入框、窗口失去焦点、在输入框里点击、组字中执行菜单命令时，j-ui 先提交组字（同 MyGO），再调 `[inputContext discardMarkedText]`。
   - **和 MyGO 不同**：MyGO 在同一个输入框里提交时不通知输入法，输入法可能和应用不同步。
   - 光标的位置变了，或者文本输入开始、结束时，调 `invalidateCharacterCoordinates`。
8. **候选框的位置**（同 MyGO）：`firstRectForCharacterRange:` 返回范围里**第一个字素簇**的矩形，空范围返回光标矩形（宽 1、高一行），`actualRange` 收到对应的范围。
   - 矩形不在输入框的内边距框里时，返回零矩形。
   - 引擎给的是窗口 DIP 坐标，平台用 `convertRect:toView:nil` 和 `convertRectToScreen:` 换成屏幕坐标。
9. **撤销**（同 MyGO 的 `editor_history.go`，存法不同）：
   - 每一步存修改前后的整段文字和选区，最多 200 步。单行输入框的文字短，存整段比存差异简单。
   - **合并**：打一个码点（没有选区）、Backspace 删一个字素簇，算“打字”，和上一步合并。条件是距上次编辑不到 1 秒，而且中间没有移动光标、撤销或重做。
   - 删词、向前删除、剪切、粘贴多个字符、替换选区，各自一步。
   - **一次组字是一步**：组字开始时删掉的选区、组字、提交，合在一起。
   - ⌘Z 撤销，⇧⌘Z 和 ⌘Y 重做。
10. **粘贴多行文字**：先把 `\r\n` 换成 `\n`，再把 `\n` 和 `\r` 都换成空格（同 MyGO）。
11. **编辑菜单**：
    - `MacApp` 在主菜单里加“Edit”菜单：Undo ⌘Z、Redo ⇧⌘Z、分隔线、Cut ⌘X、Copy ⌘C、Paste ⌘V、Select All ⌘A。
    - 动作的目标为空，沿响应链到 `JUIView`。`setAutoenablesItems:NO`，菜单项总是可用，同 MyGO。
    - 菜单标题先用英文，和现有的“Quit”一致；本地化放到 M5 的应用菜单。
    - **和 MyGO 不同**：没有获得焦点的输入框时，菜单命令改成对应的按键（⌘Z、⇧⌘Z、⌘X、⌘C、⌘V、⌘A）交给按键路由。否则菜单总是先拿到这些键，应用自己的 ⌘Z 快捷键就收不到了。
12. **外观**（同 MyGO 的 `TextInput` 和 `paintInput`）：
    - 内边距 6/10，圆角 `theme.radius()`，背景 `theme.surface()`，1 DIP 边框 `theme.border()`；
    - 获得焦点时边框用 `theme.accent()`，悬停时用 `border` 向 `text` 混合 25%；
    - **输入框不画焦点环**，Tab 进来也不画；
    - 选区只在获得焦点时画，颜色 `theme.selection()`，高一行；
    - 光标颜色 `theme.accent()`，宽 `max(round(scale), 1)` 个物理像素，高一行；
    - 组字的下划线：1 DIP 高，在行底往上 2 DIP，颜色同文字；
    - 占位文字：文字和组字都为空时显示，不管有没有焦点，颜色 `theme.textMuted()`；
    - 裁剪到内边距框（边框以内），直角；
    - 内容宽度默认 200 DIP，高度一行（同 MyGO 的 `intrinsicWidth` 返回 200）。
13. **一行的高度**：取 `"Ag中"` 这一行的高度，和当前文字那一行的高度中较大的一个。
    - Core Text 遇到汉字会回退到苹方，行高比系统字体高；用 `"Ag中"` 定高，输入框不会在打出第一个汉字时变高。
    - 文字在内容框里垂直居中，同 MyGO。
14. **光标闪烁**：周期 530 ms，按键、编辑、焦点变化、输入法回调时从“显示”重新开始，同 MyGO。
    - 30 秒没有操作就不再安排定时帧。**和 MyGO 不同**：这之后光标保持显示。MyGO 最后一次定时帧落在奇数个半周期上，光标多半停在隐藏状态。
15. **横向滚动**（同 MyGO 的 `layoutInput`）：
    - 获得焦点时按最小移动让光标留在视野里：`x − scrollX < 0` 时 `scrollX = x`；`x − scrollX > 内容宽 − 1` 时 `scrollX = x − 内容宽 + 1`；最后夹到 `[0, max(文字宽 − 内容宽 + 1, 0)]`。
    - 没有焦点时 `scrollX = 0`，显示文字的开头。
    - 拖动选择超出边缘时没有定时自动滚动：光标移到边缘外，下一次布局把它滚进来，所以要靠鼠标继续移动来推进（同 MyGO）。
16. **鼠标**（同 MyGO 的 `editor.press`、`editor.drag`）：
    - 单击放光标，Shift+单击扩展选区；双击选中一个词，或者一段非词字符；三击全选。
    - 双击后拖动按整词扩展，原来的词保持选中；三击后拖动不改变选区。
    - 按住拖到输入框外，指针形状仍是 I 形。
17. **指针形状**（同 MyGO 的 `updateCursor`）：
    - 按住时用按下的元素的形状；否则从悬停链最内层往外找第一个设了形状的元素，遇到可点击的元素就停（用默认形状）。
    - 输入框默认是 `TEXT`。
    - 只在形状变化时通知平台。平台在 `cursorUpdate:` 和 `mouseEntered:` 里重新应用，跟踪区域加上 `NSTrackingCursorUpdate`。
18. **词的边界**（同 MyGO 的 `internal/text/word.go`，不用词典）：
    - 字母、数字、下划线是词字符；
    - 每个汉字（含 `々`）、平假名、片假名（含 `ー` 和半角 `ｰ`）单独成词；
    - 所以 Option+← 在中文里一次走一个字。
19. **回调顺序**：一遍构建结束后，先 `onChange`，再处理焦点请求，再 `onSubmit`，最后 `onClick`。MyGO 没有提交回调，按设计 6.2 加在点击之前。
20. **Tester 也能按占位文字找元素**：`find`、`click` 按标签、文字或占位文字匹配，所以 `tt.click("搜索笔记")` 能点中空的搜索框。

## Review Focus

测试没有直接覆盖、但最容易让使用者踩坑的 5 种情况，以及大多数人会期望的行为：

1. **打字比出帧快**：两帧之间先后到了文字“ab”、←、文字“c”。结果是“acb”，顺序不乱，一帧就画出来。测试放在 Task 4。
2. **回车提交后，应用在同一帧里清空 `TextRef`**（待办清单添加一条）：输入框立刻变空，光标在 0；应用的清空不报告 `changed()`；之后按 ⌘Z 也不会把刚提交的文字找回来，因为应用的修改折进了最后一步（同 MyGO）。测试放在 Task 4。
3. **组字中焦点离开**（点别处、按 Tab、输入框消失、窗口切到后台）：组字提交进 `TextRef`，报告 `changed()`，并通知输入法丢弃它的组字。测试放在 Task 4（点别处、窗口切到后台）。
4. **粘贴多行文字、删除 emoji**：换行变成空格；Backspace 一次删掉整个 emoji 或 ZWJ 家庭；光标从不落在代理对中间。测试放在 Task 3。
5. **文字超出输入框**：一直打字，光标留在视野里；按 Home 回到开头；失去焦点后显示开头；滚动之后点击，落在点中的那个字上。测试放在 Task 5。

---

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `src/main/java/jui/ffi/{ObjCClass,Sigs,ObjC,NativeImageFeature}.java` | 带 `NSRange`、`NSRect`、`NSPoint` 的方法实现和调用；协议 | 1 |
| `src/main/java/jui/spike/FfiCheck.java`、`build.sh` | native image 里检查这些签名；`ffi-check` 命令 | 1 |
| `src/main/java/jui/text/{TextLayout,TextEngine,CoreText}.java` | 字形对应的字符下标；光标位置、按坐标找位置；一行的高度 | 2 |
| `src/main/java/jui/text/TextBounds.java` | 字素簇和词的边界 | 2 |
| `src/main/java/jui/ui/engine/TextEdit.java` | 单行编辑模型：文字、选区、组字、按键、命令、撤销 | 3 |
| `src/main/java/jui/ui/engine/Host.java` | 引擎交给平台的事：剪贴板、指针形状、输入法 | 4 |
| `src/main/java/jui/ui/engine/{Engine,Input,ElementState,Node,HitList,FlexLayout,Paint}.java` | 输入框节点、按键和文字的路由、焦点、提交、横向滚动、指针形状、绘制 | 4–6 |
| `src/main/java/jui/ui/{TextRef,Cursor,EditCommand,Element,Ui,Tester}.java` | 公开 API | 4–6 |
| `src/main/java/jui/render/Scene.java` | `scale()` | 6 |
| `src/main/java/jui/platform/mac/{MacWindow,MacApp,MacPasteboard,MacCursors}.java` | `NSTextInputClient`、按键、剪贴板、编辑菜单、指针形状 | 7、8 |
| `src/main/java/jui/app/Window.java` | 把平台的文本输入交给引擎，实现 `Host` | 9 |
| `src/main/java/jui/examples/{FilesDemo,TodoDemo}.java` | 搜索框过滤列表；输入新待办 | 9 |
| `src/main/java/jui/spike/AllocSpike.java`、`README.md` | 输入框帧的分配实测；M4 文档 | 10 |

---

### Task 0：分支

- [ ] **Step 1**：从 `main` 建分支 `m4-text-input`：`git checkout -b m4-text-input`。
- [ ] **Step 2**：在分支上提交本计划，提交信息 `docs: plan M4 (text input)`。

---

### Task 1：带结构体参数的 Objective-C 方法

`NSTextInputClient` 的方法要接受和返回 `NSRange`、`NSRect`、`NSPoint`。`ObjCClass` 现在只有五种签名，这里补齐，并在 native image 里检查一遍：漏登记一个签名，要等输入法第一次调用时才会崩溃。

**Files:**
- Modify: `src/main/java/jui/ffi/ObjCClass.java`、`Sigs.java`、`ObjC.java`、`NativeImageFeature.java`（只在登记循环需要时改）
- Create: `src/main/java/jui/spike/FfiCheck.java`
- Modify: `build.sh`（新增 `ffi-check`）
- Test: `src/test/java/jui/ffi/ObjCClassTest.java`

**Interfaces:**
- **Produces**：
  - `Sigs.NS_RANGE`：和 `CF_RANGE` 是同一个布局（location、length，两个 `long`）。
  - **新的 upcall 签名**（都登记在 `Sigs.UPCALLS`）：

    | 常量 | 签名 | 用在 |
    |---|---|---|
    | `IMP_RANGE` | `NSRange (id, SEL)` | `selectedRange`、`markedRange` |
    | `IMP_W` | `id (id, SEL)` | `validAttributesForMarkedText` |
    | `IMP_V_W_RANGE` | `void (id, SEL, id, NSRange)` | `insertText:replacementRange:` |
    | `IMP_V_W_RANGE2` | `void (id, SEL, id, NSRange, NSRange)` | `setMarkedText:selectedRange:replacementRange:` |
    | `IMP_W_RANGE_W` | `id (id, SEL, NSRange, NSRange *)` | `attributedSubstringForProposedRange:actualRange:` |
    | `IMP_RECT_RANGE_W` | `NSRect (id, SEL, NSRange, NSRange *)` | `firstRectForCharacterRange:actualRange:` |
    | `IMP_W_POINT` | `NSUInteger (id, SEL, NSPoint)` | `characterIndexForPoint:` |

  - **对应的 downcall**（`objc_msgSend`，登记在 `Sigs.DOWNCALLS`），给 `FfiCheck` 和测试调用：`MSG_RANGE`、`MSG_V_W_RANGE`、`MSG_V_W_RANGE2`、`MSG_W_RANGE_W`、`MSG_RECT_RANGE_W`、`MSG_W_POINT`。
  - **坐标换算的 downcall**：`RECT_RECT_W`：`CGRect (id, SEL, CGRect, id)`，用于 `convertRect:toView:`、`convertRect:fromView:`；`RECT_RECT`：`CGRect (id, SEL, CGRect)`，用于 `convertRectToScreen:`、`convertRectFromScreen:`。
  - **`ObjCClass` 的处理器接口和注册方法**：

    ```java
    public interface RangeOf { void call(long self, long cmd, long[] out); }            // out[0] location, out[1] length
    public interface Word0 { long call(long self, long cmd); }
    public interface TextRange { void call(long self, long cmd, long text, long location, long length); }
    public interface TextRanges { void call(long self, long cmd, long text, long selLocation, long selLength,
                                            long replLocation, long replLength); }
    public interface WordRange { long call(long self, long cmd, long location, long length, long actualRange); }
    public interface RectRange { void call(long self, long cmd, long location, long length, long actualRange,
                                           double[] out); }                              // out: x, y, width, height
    public interface WordPoint { long call(long self, long cmd, double x, double y); }

    public ObjCClass rangeMethod(String selector, RangeOf h);          // "{_NSRange=QQ}@:"
    public ObjCClass wordMethod(String selector, Word0 h);             // "@@:"
    public ObjCClass textRangeMethod(String selector, TextRange h);    // "v@:@{_NSRange=QQ}"
    public ObjCClass textRangesMethod(String selector, TextRanges h);  // "v@:@{_NSRange=QQ}{_NSRange=QQ}"
    public ObjCClass wordRangeMethod(String selector, WordRange h);    // "@@:{_NSRange=QQ}^{_NSRange=QQ}"
    public ObjCClass rectRangeMethod(String selector, RectRange h);    // "{CGRect={CGPoint=dd}{CGSize=dd}}@:{_NSRange=QQ}^{_NSRange=QQ}"
    public ObjCClass wordPointMethod(String selector, WordPoint h);    // "Q@:{CGPoint=dd}"
    public ObjCClass protocol(String name);  // objc_getProtocol + class_addProtocol, before register(); fails if the protocol is unknown
    ```

    - 返回结构体的入口把处理器写进的 `long[]`/`double[]` 拷进一块全局的返回内存再返回，不分配（主线程调用，同现有的 `RECT_ARG`）。
    - `actualRange` 是 `NSRange *`，可能为 0。
  - **`ObjC` 的辅助方法**：
    - `static final long NOT_FOUND = Long.MAX_VALUE`（`NSNotFound`）；
    - `long protocol(String name)`；`void writeRange(long address, long location, long length)`：地址为 0 时什么都不做；
    - 调用：`void sendRange(long obj, String sel, long[] out)`、`void sendTextRange(...)`、`void sendTextRanges(...)`、`long sendWordRange(...)`、`void sendRectRange(..., double[] out)`、`long sendWordPoint(long obj, String sel, double x, double y)`；
    - 换算：`double[] convertRect(long obj, String sel, double x, double y, double w, double h, long view)`（`toView:`/`fromView:`），`double[] convertRect(long obj, String sel, double x, double y, double w, double h)`（`ToScreen:`/`FromScreen:`）。
  - **`jui.spike.FfiCheck`**：
    - `static List<String> check()`：定义一个遵守 `NSTextInputClient` 的类 `JUIFfiCheck`（只定义一次），每种签名一个方法，再经 `objc_msgSend` 逐个调用，返回不对的地方，全对时是空列表；
    - `main`：全对时打印 `FFI ok`，否则打印每一条并以 1 退出。
  - **`./build.sh ffi-check`**：先在 JVM 上跑 `FfiCheck`，再编译成 native image `build/ffi-check` 跑一遍。

- [ ] **Step 1：写失败的测试**

```java
package jui.ffi;

import jui.testing.Check;

public final class ObjCClassTest {
    public void testMethodsWithRangesRectsAndPointsRoundTrip() {
        java.util.List<String> failures = jui.spike.FfiCheck.check();
        Check.isTrue(failures.isEmpty(), failures.toString());
    }

    public void testAClassTakesAProtocol() {
        long cls = ObjCClass.define("JUIProtocolTest", "NSObject").protocol("NSTextInputClient").register();
        long obj = ObjC.send(ObjC.send(cls, "alloc"), "init");
        try {
            Check.isTrue(ObjC.sendBool(obj, "conformsToProtocol:", ObjC.protocol("NSTextInputClient")), "conforms");
        } finally {
            ObjC.release(obj);
        }
    }

    public void testAnUnknownProtocolFails() {
        Check.fails(IllegalStateException.class, () -> ObjCClass.define("JUIProtocolTest2", "NSObject").protocol("NoSuchProtocol"));
    }
}
```

`FfiCheck.check()` 至少检查这些值：
- `rangeMethod` 返回 `(7, 3)`，以及 `(NOT_FOUND, 0)`；
- `textRangeMethod` 收到的字符串是 `"拼音"`、范围是 `(2, 5)`；
- `textRangesMethod` 收到 `"ni"`、`(1, 0)`、`(NOT_FOUND, 0)`；
- `wordMethod` 返回 42；
- `wordRangeMethod` 收到 `(4, 2)`，把 `actualRange` 写成 `(4, 1)`，返回 `4 * 10 + 2`；传 0 作 `actualRange` 也不出错；
- `rectRangeMethod` 收到 `(4, 2)`，返回 `{4.5, 2.25, 11.5, 22.5}`；
- `wordPointMethod` 收到 `(3, 4)`，返回 34。

- [ ] **Step 2**：运行 `./build.sh test ObjCClassTest`。Expected：编译失败（`FfiCheck`、新方法都不存在）。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：运行 `./build.sh ffi-check`。Expected：JVM 和 native image 各打印一行 `FFI ok`，退出码 0。
- [ ] **Step 6**：提交，信息 `feat(ffi): Objective-C methods that take and return ranges, rects and points`。

---

### Task 2：光标位置、按坐标找位置、字素簇和词的边界

**Files:**
- Modify: `src/main/java/jui/text/TextLayout.java`、`TextEngine.java`、`CoreText.java`
- Create: `src/main/java/jui/text/TextBounds.java`
- Test: `src/test/java/jui/text/TextCaretTest.java`、`src/test/java/jui/text/TextBoundsTest.java`

**Interfaces:**
- **Produces**：
  - **`TextLayout`**（排版时用 `CTRunGetStringIndices` 记下每个字形的 UTF-16 下标，签名同 `RUN_GET`）：
    - `int length()`：文字的 UTF-16 长度；
    - `int index(int glyph)`：字形对应的字符下标；
    - `float caretX(int index)`：下标 `index` 之前的光标位置，从这一行的左边算。规则：这一行里第一个下标 ≥ `index` 的字形的 x；没有这样的字形时是行宽（含行尾空格）。只用于单行的排版，多行的光标几何留给编辑器（设计第 12 节）；
    - `int indexAt(float x)`：离 `x` 最近的光标停靠点，停靠点是字素簇边界（`TextBounds.graphemes`，第一次用时算出并缓存在排版对象里）；`x` 在最左边之前时是 0，在最右边之后时是 `length()`；
    - 空文字的排版：`length()` 是 0，`caretX(0)` 是 0，`indexAt` 总是 0。
  - **`float TextEngine.lineHeight(float size, int weight)`**：`"Ag中"` 这一行的高度（走排版缓存）。输入框的一行取它和当前文字那一行中较高的一个。
  - **`jui.text.TextBounds`**（纯 Java，静态方法）：
    - `int[] graphemes(String s)`：字素簇边界，含 0 和 `s.length()`，用 `BreakIterator.getCharacterInstance()`；
    - `int before(int[] stops, int i)`：`i` 之前最近的边界，`i` 为 0 时是 0；`int after(int[] stops, int i)`：`i` 之后最近的边界，`i` 在末尾时是末尾；`int floor(int[] stops, int i)`：≤ `i` 的最大边界；
    - `boolean isWordChar(int codePoint)`：字母、数字或 `_`；
    - `boolean standsAlone(int codePoint)`：汉字（含 `々`）、平假名、片假名，以及 `ー`（U+30FC）和 `ｰ`（U+FF70），同 MyGO 的 `StandsAlone`；
    - `boolean wordBoundary(int a, int b)`：`isWordChar(a) != isWordChar(b)`，或者任一边 `standsAlone`；
    - `int nextWord(String s, int i)`、`int prevWord(String s, int i)`：按码点照搬 MyGO 的 `nextWord`、`prevWord`，返回 UTF-16 下标；
    - `int[] wordAt(String s, int i)`：`{start, end}`，照搬 MyGO 的 `wordAt`（`i` 先夹到 `[0, length − 1]`，空字符串返回 `{0, 0}`）。

- [ ] **Step 1：写失败的测试**

```java
package jui.text;

import jui.testing.Check;

public final class TextCaretTest {
    static final TextEngine T = new TextEngine("zh-Hans");

    public void testCaretPositionsGrowAcrossTheText() {
        TextLayout l = T.layout("ab中文 x", 13, 400, 0);
        Check.near(0, l.caretX(0));
        for (int i = 1; i <= l.length(); i++) {
            Check.isTrue(l.caretX(i) > l.caretX(i - 1), "caret " + i + " is right of caret " + (i - 1));
        }
        Check.near(l.width, l.caretX(l.length()));
    }

    public void testIndexAtFindsTheNearestCaretStop() {
        TextLayout l = T.layout("hello 世界", 13, 400, 0);
        for (int i = 0; i <= l.length(); i++) {
            Check.equal(i, l.indexAt(l.caretX(i)));
        }
        Check.equal(0, l.indexAt(-20));
        Check.equal(l.length(), l.indexAt(10_000));
        float mid = (l.caretX(1) + l.caretX(2)) / 2;
        Check.equal(1, l.indexAt(mid - 0.1f));
        Check.equal(2, l.indexAt(mid + 0.1f));
    }

    public void testACaretNeverLandsInsideAnEmoji() {
        TextLayout l = T.layout("a😀b", 13, 400, 0);
        for (float x = -1; x < l.width + 2; x += 0.25f) {
            Check.isTrue(l.indexAt(x) != 2, "no stop between the halves of a surrogate pair, at " + x);
        }
    }

    public void testAnEmptyLayoutHasOneCaretStop() {
        TextLayout l = T.layout("", 13, 400, 0);
        Check.equal(0, l.length());
        Check.near(0, l.caretX(0));
        Check.equal(0, l.indexAt(30));
    }

    public void testTheLineHeightFitsLatinAndHan() {
        float h = T.lineHeight(13, 400);
        Check.isTrue(h >= T.layout("Ag", 13, 400, 0).height - 0.01f, "fits Latin");
        Check.isTrue(h >= T.layout("中", 13, 400, 0).height - 0.01f, "fits Han");
    }
}
```

```java
package jui.text;

import jui.testing.Check;

public final class TextBoundsTest {
    public void testGraphemesKeepClustersWhole() {
        String s = "é😀👨‍👩‍👧中🇨🇳a";
        Check.equal("[0, 2, 4, 12, 13, 17, 18]", java.util.Arrays.toString(TextBounds.graphemes(s)));
        Check.equal("[0]", java.util.Arrays.toString(TextBounds.graphemes("")));
    }

    public void testStepsBeforeAndAfter() {
        int[] g = TextBounds.graphemes("a😀b");          // 0, 1, 3, 4
        Check.equal(1, TextBounds.before(g, 3));
        Check.equal(3, TextBounds.after(g, 1));
        Check.equal(0, TextBounds.before(g, 0));
        Check.equal(4, TextBounds.after(g, 4));
        Check.equal(1, TextBounds.floor(g, 2));
    }

    public void testWordsAreRunsOfLettersAndEachHanAlone() {
        Check.equal(5, TextBounds.nextWord("hello world", 0));
        Check.equal(11, TextBounds.nextWord("hello world", 5));
        Check.equal(6, TextBounds.prevWord("hello world", 11));
        Check.equal(2, TextBounds.nextWord("Go语言", 0));
        Check.equal(3, TextBounds.prevWord("Go语言", 4));
        Check.equal(2, TextBounds.prevWord("Go语言", 3));
        Check.equal(0, TextBounds.prevWord("Go语言", 2));
        Check.equal(10, TextBounds.nextWord("snake_case", 0));   // _ is a word char: one word
    }

    public void testWordAtTakesTheWordOrTheRunAround() {
        Check.equal("[0, 5]", java.util.Arrays.toString(TextBounds.wordAt("hello world", 2)));
        Check.equal("[5, 6]", java.util.Arrays.toString(TextBounds.wordAt("hello world", 5)));
        Check.equal("[2, 3]", java.util.Arrays.toString(TextBounds.wordAt("Go语言", 2)));
        Check.equal("[1, 3]", java.util.Arrays.toString(TextBounds.wordAt("a  b", 1)));
        Check.equal("[6, 11]", java.util.Arrays.toString(TextBounds.wordAt("hello world", 11)));
        Check.equal("[0, 0]", java.util.Arrays.toString(TextBounds.wordAt("", 0)));
    }

    public void testKanaAndTheProlongedSoundMarkStandAlone() {
        Check.isTrue(TextBounds.standsAlone('あ') && TextBounds.standsAlone('カ') && TextBounds.standsAlone('ー'), "kana");
        Check.isTrue(TextBounds.standsAlone('々'), "the iteration mark");
        Check.isTrue(!TextBounds.standsAlone('A') && !TextBounds.standsAlone('한'), "Latin and Hangul do not");
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test TextCaretTest` 和 `./build.sh test TextBoundsTest`。Expected：编译失败。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(text): caret positions, hit testing, and grapheme and word boundaries`。

---

### Task 3：单行编辑模型 `TextEdit`

纯 Java，不依赖引擎，时间由调用者传入（纳秒），所以撤销的合并规则能直接测。

**Files:**
- Create: `src/main/java/jui/ui/engine/TextEdit.java`、`src/main/java/jui/ui/EditCommand.java`
- Test: `src/test/java/jui/ui/engine/TextEditTest.java`

**Interfaces:**
- **Consumes**：Task 2 的 `TextBounds`。
- **Produces**：`public final class TextEdit`：
  - **构造和读取**：
    - `TextEdit(String text)`：光标和锚点都在末尾；
    - `String text()`：已提交的文字；`int caret()`、`int anchor()`、`int selStart()`、`int selEnd()`；
    - `void select(int anchor, int caret)`：两端夹到范围内，落到字素簇边界（`floor`）；`void selectAll()`；
    - `String compose()`：组字，不组字时是 `""`；`boolean composing()`；
    - `String display()`：显示的文字，组字插在光标处；不组字时返回 `text()` 本身（同一个对象），组字时的结果缓存到下一次修改，所以内容不变的帧不分配；
    - `int displayCaret()`：显示文字里光标的位置：`caret + 组字里选中范围的末尾`；
    - `boolean dirty`：用户改了已提交的文字，还没同步给 `TextRef`；`String synced`：上次和 `TextRef` 同步的值。
  - **按键**：
    - `static boolean wants(Key key, int mods)`，`m = mods & ~SHIFT`：
      - `BACKSPACE`、`DELETE`、`HOME`、`END`、`LEFT`、`RIGHT`：要；
      - `UP`、`DOWN`、`PAGE_UP`、`PAGE_DOWN`：`m != 0` 时要；
      - `ENTER`：`m == 0` 时要；
      - `TAB`、`ESCAPE`、`F1`–`F12`：不要；
      - `A`、`C`、`X`、`V`、`Z`、`Y`：`m == CMD` 时要；
      - `m == CTRL` 时 `B`、`F`、`P`、`N`、`H`、`D`、`A`、`E`、`K` 要；
      - 其余：`m == 0 || m == ALT` 时要（它们打出文字，文字另外进来）。
    - `static EditCommand commandOf(Key key, int mods)`：`m == CMD` 时 `A` → `SELECT_ALL`、`C` → `COPY`、`X` → `CUT`、`V` → `PASTE`、`Z` → `UNDO`（带 Shift 时 `REDO`）、`Y` → `REDO`；其余 `null`。
    - `boolean key(Key key, int mods, long now)`：执行一个 `wants` 为真、`commandOf` 为 `null` 的键，回车时返回 `true`（提交），其余返回 `false`。组字中先提交组字。行为（`shift` 是否按了 Shift，`m` 去掉 Shift，`n` 是文字长度）：
      - `m == CTRL`：`B`/`F`/`P`/`N`/`H`/`D` 当作 ←/→/↑/↓/Backspace/Delete，`m` 变成 0；`A` 到 0、`E` 到 `n`（带 Shift 扩展）；`K` 删到末尾；
      - `LEFT`/`RIGHT`：`m == CMD` 到 0 或 `n`；`m == ALT` 到 `prevWord(caret)` 或 `nextWord(caret)`（从光标出发，不是从选区边缘）；有选区且没按 Shift，收到选区的左端或右端；否则走一个字素簇。带 Shift 时扩展选区；
      - `UP`/`PAGE_UP` 到 0，`DOWN`/`PAGE_DOWN` 到 `n`，带 Shift 扩展；`HOME` 到 0，`END` 到 `n`；
      - `BACKSPACE`：有选区删选区；`m == CMD` 删到 0；`m == ALT` 删到 `prevWord(caret)`；否则删前一个字素簇（算打字）；
      - `DELETE`：有选区删选区；`m == ALT` 删到 `nextWord(caret)`；否则删后一个字素簇；
      - `ENTER`：返回 `true`；
      - 其余（可打印键）：什么都不做。
  - **文字和组字**（下标都是显示文字里的，即 `NSTextInputClient` 的文档）：
    - `void insert(String s, long now)`：组字中用 `s` 替换组字，否则替换选区；`s` 先按细节第 10 条清理换行；
    - `void replace(int start, int end, String s, long now)`：带替换范围的 `insertText:`，先提交组字，再替换已提交文字里的 `[start, end)`（夹到范围内、落到字素簇边界）；
    - `void setMarked(String s, int selStart, int selLength, int replStart, int replLength, long now)`：`replStart < 0` 表示没有替换范围。规则同 MyGO 的 `SetMarkedText`：
      - 还没在组字，或者给了替换范围：删掉选区（或替换范围映射回已提交文字的部分），不在同一次组字里时开一个撤销步；
      - 保存组字 `s` 和它里面的选中范围（夹到范围内，不切开代理对）；
      - `s` 为空时取消组字，删掉的选区不恢复。
    - `void unmark(long now)`：把组字变成已提交的文字，光标到它后面；
    - `int length()`：显示文字的长度；`void selectedRange(int[] out)`：组字中是 `(caret + 组字选中起点, 组字选中长度)`，否则是选区；`boolean markedRange(int[] out)`：组字中写 `(caret, 组字长度)` 并返回 `true`；`String substring(int start, int end)`：显示文字的一段，夹到范围内。
  - **命令**：`String command(EditCommand c, String clipboard, long now)`：先提交组字。
    - `COPY` 返回选中的文字，没有选区返回 `null`；`CUT` 同样返回，并删掉选区；
    - `PASTE` 用 `clipboard`（非空时）替换选区；`SELECT_ALL` 全选；`UNDO`、`REDO`；
    - 只有 `COPY`、`CUT` 返回非 `null`，调用者把它写进剪贴板。
  - **鼠标**：
    - `void press(int index, int clicks, boolean shift)`：先提交组字。1 次：放光标，Shift 时从锚点扩展；2 次：`wordAt`；3 次及以上：全选；记下这次按下的次数和原来的词；
    - `void drag(int index)`：1 次按下时把光标移到 `index`，锚点不动；2 次按下时按整词扩展，原来的词保持选中；3 次时不变。
  - **应用的修改**：`void setText(String s)`：细节第 3 条：光标和锚点各自截到新长度（落到字素簇边界），组字清空，最后一个撤销步的“之后”换成 `s`，重做清空；`synced = s`，`dirty = false`。
  - **几何**（Task 5 在提交时填写，供点击和输入法用）：`TextLayout layout`、`float originX, lineTop, lineH`、`float clipX0, clipY0, clipX1, clipY1`、`float scrollX`。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui.engine;

import jui.testing.Check;
import jui.ui.EditCommand;
import jui.ui.Key;
import jui.ui.Mod;

public final class TextEditTest {
    static final long S = 1_000_000_000L;

    static TextEdit at(String text, int anchor, int caret) {
        TextEdit e = new TextEdit(text);
        e.select(anchor, caret);
        return e;
    }

    public void testTypingReplacesTheSelection() {
        TextEdit e = at("hello", 1, 4);
        e.insert("i", 0);
        Check.equal("hio", e.text());
        Check.equal(2, e.caret());
        Check.equal(2, e.anchor());
        Check.isTrue(e.dirty, "dirty");
    }

    public void testArrowsStepByGraphemesAndCollapseASelection() {
        TextEdit e = at("a😀b", 4, 4);
        e.key(Key.LEFT, 0, 0);
        Check.equal(3, e.caret());
        e.key(Key.LEFT, 0, 0);
        Check.equal(1, e.caret());
        e = at("hello", 1, 4);
        e.key(Key.LEFT, 0, 0);
        Check.equal(1, e.caret());
        Check.equal(1, e.anchor());
        e = at("hello", 4, 1);
        e.key(Key.RIGHT, 0, 0);
        Check.equal(4, e.caret());
        e = at("hello", 1, 1);
        e.key(Key.RIGHT, Mod.SHIFT, 0);
        e.key(Key.RIGHT, Mod.SHIFT, 0);
        Check.equal(1, e.selStart());
        Check.equal(3, e.selEnd());
    }

    public void testOptionMovesByWordsAndCmdToTheEnds() {
        TextEdit e = at("hello world", 0, 0);
        e.key(Key.RIGHT, Mod.ALT, 0);
        Check.equal(5, e.caret());
        e.key(Key.RIGHT, Mod.ALT, 0);
        Check.equal(11, e.caret());
        e.key(Key.LEFT, Mod.ALT, 0);
        Check.equal(6, e.caret());
        e.key(Key.LEFT, Mod.CMD, 0);
        Check.equal(0, e.caret());
        e.key(Key.END, Mod.SHIFT, 0);
        Check.equal(0, e.anchor());
        Check.equal(11, e.caret());
        e = at("Go语言", 4, 4);
        e.key(Key.LEFT, Mod.ALT, 0);
        Check.equal(3, e.caret());
    }

    public void testUpAndDownAreLeftToOthersUnlessModified() {
        Check.isTrue(!TextEdit.wants(Key.UP, 0) && !TextEdit.wants(Key.DOWN, Mod.SHIFT), "plain up and down");
        Check.isTrue(!TextEdit.wants(Key.PAGE_DOWN, 0), "page down");
        Check.isTrue(TextEdit.wants(Key.UP, Mod.ALT) && TextEdit.wants(Key.DOWN, Mod.CMD), "with a modifier");
        TextEdit e = at("hello", 2, 2);
        e.key(Key.UP, Mod.ALT, 0);
        Check.equal(0, e.caret());
        e.key(Key.DOWN, Mod.CMD | Mod.SHIFT, 0);
        Check.equal(5, e.caret());
        Check.equal(0, e.anchor());
    }

    public void testWhatItWants() {
        Check.isTrue(TextEdit.wants(Key.A, 0) && TextEdit.wants(Key.A, Mod.SHIFT) && TextEdit.wants(Key.A, Mod.ALT),
                "printable keys type text");
        Check.isTrue(TextEdit.wants(Key.A, Mod.CMD) && TextEdit.wants(Key.Z, Mod.CMD | Mod.SHIFT), "edit shortcuts");
        Check.isTrue(!TextEdit.wants(Key.P, Mod.CMD) && !TextEdit.wants(Key.ENTER, Mod.CMD), "the app's shortcuts");
        Check.isTrue(!TextEdit.wants(Key.TAB, 0) && !TextEdit.wants(Key.ESCAPE, 0) && !TextEdit.wants(Key.F3, 0), "others");
        Check.isTrue(TextEdit.wants(Key.ENTER, Mod.SHIFT), "shift enter submits");
        Check.isTrue(TextEdit.wants(Key.B, Mod.CTRL) && !TextEdit.wants(Key.Q, Mod.CTRL), "emacs keys only");
        Check.equal(EditCommand.REDO, TextEdit.commandOf(Key.Z, Mod.CMD | Mod.SHIFT));
        Check.equal(EditCommand.REDO, TextEdit.commandOf(Key.Y, Mod.CMD));
        Check.equal(null, TextEdit.commandOf(Key.A, 0));
    }

    public void testBackspaceDeletesWholeClustersWordsAndToTheStart() {
        TextEdit e = at("a👨‍👩‍👧", 9, 9);
        e.key(Key.BACKSPACE, 0, 0);
        Check.equal("a", e.text());
        e = at("Go语言", 4, 4);
        e.key(Key.BACKSPACE, Mod.ALT, 0);
        Check.equal("Go语", e.text());
        e = at("hello world", 8, 8);
        e.key(Key.BACKSPACE, Mod.CMD, 0);
        Check.equal("rld", e.text());
        Check.equal(0, e.caret());
        e = at("hello world", 6, 6);
        e.key(Key.DELETE, Mod.ALT, 0);
        Check.equal("hello ", e.text());
    }

    public void testEmacsKeys() {
        TextEdit e = at("hello", 2, 2);
        e.key(Key.A, Mod.CTRL, 0);
        Check.equal(0, e.caret());
        e.key(Key.E, Mod.CTRL, 0);
        Check.equal(5, e.caret());
        e.key(Key.B, Mod.CTRL, 0);
        Check.equal(4, e.caret());
        e.key(Key.H, Mod.CTRL, 0);
        Check.equal("helo", e.text());
        e.key(Key.A, Mod.CTRL, 0);
        e.key(Key.K, Mod.CTRL, 0);
        Check.equal("", e.text());
    }

    public void testEnterSubmits() {
        TextEdit e = at("x", 1, 1);
        Check.isTrue(e.key(Key.ENTER, 0, 0), "enter");
        Check.isTrue(e.key(Key.ENTER, Mod.SHIFT, 0), "shift enter");
        Check.isTrue(!e.key(Key.LEFT, 0, 0), "not a submit");
    }

    public void testCopyCutPaste() {
        TextEdit e = at("hello world", 0, 5);
        Check.equal("hello", e.command(EditCommand.COPY, null, 0));
        Check.equal("hello", e.command(EditCommand.CUT, null, 0));
        Check.equal(" world", e.text());
        e.select(0, 0);
        Check.equal(null, e.command(EditCommand.COPY, null, 0));
        e.command(EditCommand.PASTE, "a\r\nb\nc\rd", 0);
        Check.equal("a b c d world", e.text());
        e.command(EditCommand.SELECT_ALL, null, 0);
        Check.equal(0, e.selStart());
        Check.equal(13, e.selEnd());
    }

    public void testTypingWithinASecondIsOneUndoStep() {
        TextEdit e = new TextEdit("");
        e.insert("a", 0);
        e.insert("b", S / 2);
        e.insert("c", S / 2 + 9 * S / 10);    // 0.9 s after the last: the same step
        e.insert("d", 3 * S);                 // a pause: a new step
        e.command(EditCommand.UNDO, null, 3 * S);
        Check.equal("abc", e.text());
        e.command(EditCommand.UNDO, null, 3 * S);
        Check.equal("", e.text());
        e.command(EditCommand.REDO, null, 3 * S);
        Check.equal("abc", e.text());
    }

    public void testACaretMoveStartsANewStepAndBackspaceJoinsTyping() {
        TextEdit e = new TextEdit("");
        e.insert("a", 0);
        e.insert("b", 1);
        e.key(Key.BACKSPACE, 0, 2);           // joins the typing
        e.key(Key.LEFT, 0, 3);
        e.insert("x", 4);                     // after a move: a new step
        e.command(EditCommand.UNDO, null, 5);
        Check.equal("a", e.text());
        e.command(EditCommand.UNDO, null, 5);
        Check.equal("", e.text());
    }

    public void testUndoRestoresTheSelectionAndANewEditClearsRedo() {
        TextEdit e = at("hello", 1, 4);
        e.insert("i", 0);
        e.command(EditCommand.UNDO, null, S);
        Check.equal("hello", e.text());
        Check.equal(1, e.anchor());
        Check.equal(4, e.caret());
        e.insert("z", 2 * S);                 // over the restored selection "ell": "hzo"
        e.command(EditCommand.REDO, null, 3 * S);
        Check.equal("hzo", e.text());         // the new edit cleared redo: nothing changes
    }

    public void testUndoKeepsAtMost200Steps() {
        TextEdit e = new TextEdit("");
        for (int i = 0; i < 250; i++) {
            e.insert("x", i * 2 * S);         // a pause each time: 250 steps
        }
        for (int i = 0; i < 300; i++) {
            e.command(EditCommand.UNDO, null, 600 * S);
        }
        Check.equal(50, e.text().length());
    }

    public void testACompositionShowsButIsNotCommitted() {
        TextEdit e = at("ab", 2, 2);
        e.setMarked("ni", 2, 0, -1, 0, 0);
        Check.equal("ab", e.text());
        Check.equal("abni", e.display());
        Check.equal(4, e.displayCaret());
        Check.isTrue(!e.dirty, "a composition is not an edit of the text");
        int[] r = new int[2];
        Check.isTrue(e.markedRange(r), "marked");
        Check.equal(2, r[0]);
        Check.equal(2, r[1]);
        e.selectedRange(r);
        Check.equal(4, r[0]);
        Check.equal(0, r[1]);
        e.insert("你", 1);
        Check.equal("ab你", e.text());
        Check.equal(3, e.caret());
        Check.isTrue(!e.composing() && e.dirty, "committed");
    }

    public void testACompositionOverASelectionIsOneUndoStep() {
        TextEdit e = at("cafe", 3, 4);
        e.setMarked("é", 1, 0, -1, 0, 0);
        Check.equal("caf", e.text());
        Check.equal("café", e.display());
        e.insert("é", 1);
        Check.equal("café", e.text());
        e.command(EditCommand.UNDO, null, 2);
        Check.equal("cafe", e.text());
        Check.equal(3, e.selStart());
        Check.equal(4, e.selEnd());
    }

    public void testUnmarkCommitsAndAnEmptyMarkCancels() {
        TextEdit e = at("", 0, 0);
        e.setMarked("hui", 3, 0, -1, 0, 0);
        e.unmark(1);
        Check.equal("hui", e.text());
        Check.equal(3, e.caret());
        e.setMarked("x", 1, 0, -1, 0, 2);
        e.setMarked("", 0, 0, -1, 0, 3);
        Check.equal("hui", e.display());
        Check.isTrue(!e.composing(), "cancelled");
    }

    public void testAReplacementRangeCountsInTheShownText() {
        TextEdit e = at("abc", 3, 3);
        e.setMarked("xy", 2, 0, -1, 0, 0);
        e.replace(3, 5, "Z", 1);
        Check.equal("abcZ", e.text());
        Check.equal(4, e.caret());
    }

    public void testAnAppChangeClampsTheCaretAndFoldsIntoUndo() {
        TextEdit e = new TextEdit("x");
        e.insert("y", 0);                     // "xy"
        e.setText("xy!");                     // the app appends
        Check.equal(2, e.caret());
        Check.equal("xy!", e.synced);
        e.command(EditCommand.UNDO, null, S);
        Check.equal("x", e.text());           // back to before the last user edit
        e = at("hello", 5, 5);
        e.setText("he");
        Check.equal(2, e.caret());
        Check.equal(2, e.anchor());
    }

    public void testPressPlacesSelectsWordsAndAll() {
        TextEdit e = new TextEdit("hello world");
        e.press(2, 1, false);
        Check.equal(2, e.caret());
        e.press(4, 1, true);
        Check.equal(2, e.anchor());
        Check.equal(4, e.caret());
        e.press(1, 2, false);
        Check.equal(0, e.selStart());
        Check.equal(5, e.selEnd());
        e.drag(8);                            // by whole words, the first kept
        Check.equal(0, e.selStart());
        Check.equal(11, e.selEnd());
        e.press(1, 3, false);
        Check.equal(0, e.selStart());
        Check.equal(11, e.selEnd());
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test TextEditTest`。Expected：编译失败。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): a single-line editing model with words, graphemes, composition and undo`。

---

### Task 4：引擎里的输入框：控件、按键、文字、同步、提交

**Files:**
- Create: `src/main/java/jui/ui/engine/Host.java`、`src/main/java/jui/ui/TextRef.java`、`src/main/java/jui/ui/Cursor.java`（`Host` 用到它；`Element.cursor` 和形状的计算在 Task 5）
- Modify: `Engine.java`、`Input.java`、`ElementState.java`、`Node.java`、`FlexLayout.java`、`Ui.java`、`Element.java`、`Tester.java`
- Test: `src/test/java/jui/ui/TextInputTest.java`

**Interfaces:**
- **Consumes**：Task 3 的 `TextEdit`、`EditCommand`。
- **Produces**：
  - **`Node`**：种类 `INPUT = 5`；标志 `TEXT_INPUT = 256`；字段 `String placeholder`。输入框的显示文字放在 `text` 里。
  - **`ElementState`**：`public TextEdit edit`、`public int submits`；`reset()` 清空它们，`Engine.endPass` 像 `clicks` 一样清零 `submits`。
  - **`FlexLayout`**：`INPUT` 和 `TEXT` 一样走测量回调。`Engine.measure` 对 `INPUT`：宽 200；高 `max(text.lineHeight(size, weight), 显示文字那一行的高)`；`n.layout` 是显示文字不换行的排版。
  - **`jui.ui.engine.Host`**：

    ```java
    public interface Host {
        String readClipboard();              // null when it holds no text
        void writeClipboard(String s);
        void setCursor(jui.ui.Cursor c);     // Task 5 calls it
        void discardMarkedText();            // the engine committed or dropped a composition itself
        void textInputChanged();             // the caret moved, or text input began or ended

        /** A host in memory, for Tester and windowless engines: a clipboard string, the cursor last set. */
        final class Memory implements Host { public String clipboard; public jui.ui.Cursor cursor = jui.ui.Cursor.DEFAULT;
                                             public int discards, changes; /* ... */ }
    }
    ```

    `Engine.setHost(Host)`、`Host Engine.host()`；默认是一个新的 `Host.Memory`。`discards` 和 `changes` 记下 `discardMarkedText`、`textInputChanged` 被调了几次，给测试用。
  - **`Ui.textInputBase(Context c, TextRef ref)`**：开一个 `INPUT` 节点，标志 `FOCUSABLE | HOVER | DRAG | TEXT_INPUT`，`clip = true`。构建时：
    - 第一次构建时 `state.edit = new TextEdit(ref.get())`，`synced = ref.get()`；
    - 按细节第 3 条同步：应用改了值时调 `edit.setText(...)`，丢掉了组字就 `host.discardMarkedText()`；否则 `edit.dirty` 时写回 `ref`、`markChanged`；
    - 禁用时丢掉组字（`setMarked("", ...)`），并通知输入法；
    - `n.text = edit.display()`。
  - **`Ui.textInput(Context c, TextRef ref)`**：Task 6 加外观，这个任务里先只调 `textInputBase`。
  - **`Element`**：
    - `placeholder(String)`：设 `n.placeholder`，不是输入框时开发模式报错；
    - `boolean submitted()`：读 `state.submits > 0` 并消耗，同 `clicked()`；
    - `Element onSubmit(Runnable)`：同 `onClick`，在一遍构建结束后、点击回调之前运行（细节第 19 条）。
  - **按键路由**（`Input.keyDown` 的第一个分支，在 Tab 之前）：焦点所在的条目启用、带 `TEXT_INPUT`、状态里有 `edit`，而且 `TextEdit.wants(key, mods)`：
    - `commandOf` 不为 `null` 时执行命令：`COPY`/`CUT` 的结果写进 `host.writeClipboard`，`PASTE` 读 `host.readClipboard()`；
    - 否则 `edit.key(...)`，返回 `true` 时 `submits++`；
    - 组字中收到的按键先提交组字，再 `host.discardMarkedText()`（macOS 上组字时不会发原始按键，这条给 Tester 和其他平台）；
    - 重置闪烁（`blinkStart = clockNow()`），请求一帧，返回。不要的键照旧往下走。
  - **按 Tab 进入输入框时全选**：`moveFocus` 之后，新焦点带 `TEXT_INPUT` 且有 `edit` 时 `edit.selectAll()`。
  - **文本输入的状态**：`Input.updateTextInput()`：文本输入的“对象”是“焦点所在、启用、窗口在前台、带 `TEXT_INPUT` 的元素”的身份，没有时为 0。
    - 它变了，而且原来那个还在组字：先 `unmark`（标记 `dirty`，请求一帧），再 `host.discardMarkedText()`；
    - 每次变化都调 `host.textInputChanged()`；
    - 在每个改变焦点的事件之后调：按下、Tab、窗口焦点变化；`commit` 末尾也调一次，覆盖焦点请求和元素消失。
  - **引擎给平台的文本输入接口**（主线程，两帧之间调用；没有文本输入时什么都不做，返回 `false`、`-1` 或 `null`）：

    ```java
    public boolean textActive();
    public boolean textComposing();
    public void textInsert(String s, int replStart, int replLength);   // replStart < 0: none
    public void textMark(String s, int selStart, int selLength, int replStart, int replLength);
    public void textUnmark();
    public boolean textMarkedRange(int[] out);
    public boolean textSelectedRange(int[] out);
    public int textLength();
    public String textIn(int start, int end);
    public void editCommand(jui.ui.EditCommand c);   // a menu's command: see detail 11
    ```

    - 每个修改都重置闪烁、请求一帧；`textInsert` 和 `textMark` 在没有组字时也照样作用于选区。
    - `editCommand` 没有文本输入时，改成对应的按键交给 `keyDown`（细节第 11 条）。
  - **`TextRef`**：同其他引用对象。
  - **`Tester`**（用 `Host.Memory`）：
    - `void type(String s)`：`textInsert(s, -1, 0)` 后 `settle()`；
    - `void compose(String text, int caret)`：`textMark(text, caret, 0, -1, 0)` 后 `settle()`，`text` 为空时结束组字；
    - `void command(EditCommand c)`；
    - `String clipboard()`、`void setClipboard(String s)`；
    - `int[] textSelection()`：焦点所在输入框的 `{selStart, selEnd}`（组字中是组字里的光标位置），没有时为 `null`；
    - `Engine.find` 也按 `placeholder` 匹配（细节第 20 条）。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class TextInputTest {
    public void testTypingWritesTheRefAndReportsChangedOnce() {
        TextRef q = new TextRef("");
        int[] changes = {0};
        Tester tt = Tester.of(c -> { if (textInput(c, q).placeholder("搜索").changed()) changes[0]++; }, 300, 200);
        tt.click("搜索");
        tt.type("会议");
        Check.equal("会议", q.get());
        Check.equal(1, changes[0]);
        tt.frame();
        Check.equal(1, changes[0]);
    }

    public void testEditsBetweenFramesApplyInOrderAndShowInOneFrame() { // Review Focus 1
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> textInput(c, q).placeholder("搜索"), 300, 200);
        tt.click("搜索");
        tt.engine().textInsert("ab", -1, 0);
        tt.engine().keyDown(Key.LEFT, 0, false);
        tt.engine().textInsert("c", -1, 0);
        tt.frame();                                        // one frame
        Check.equal("acb", q.get());
    }

    public void testEnterSubmitsAndTheAppCanClearTheRef() { // Review Focus 2
        TextRef q = new TextRef("");
        java.util.List<String> added = new java.util.ArrayList<>();
        int[] changes = {0};
        Tester tt = Tester.of(c -> {
            Element in = textInput(c, q).placeholder("新待办");
            if (in.changed()) changes[0]++;
            if (in.submitted()) {
                added.add(q.get());
                q.set("");
            }
        }, 300, 200);
        tt.click("新待办");
        tt.type("买菜");
        int before = changes[0];
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(java.util.List.of("买菜"), added);
        Check.equal("", q.get());
        Check.equal(before, changes[0]);                   // the app's change is not reported
        Check.equal("[0, 0]", java.util.Arrays.toString(tt.textSelection()));
        // The app's clearing folded into the last step ("" → "买菜"), whose "after" is now "":
        // undo goes back to before the user's edit, which is "" too (as MyGO). Nothing changes.
        tt.key(Shortcut.cmd(Key.Z));
        Check.equal("", q.get());
        Check.equal(before, changes[0]);
    }

    public void testAWindowShortcutWorksWhileTypingButPlainKeysDoNot() {
        TextRef q = new TextRef("");
        int[] palette = {0}, slash = {0};
        Tester tt = Tester.of(c -> {
            if (c.shortcut(Shortcut.cmd(Key.P))) palette[0]++;
            if (c.shortcut(Shortcut.of(Key.SLASH))) slash[0]++;
            textInput(c, q).placeholder("搜索");
        }, 300, 200);
        tt.click("搜索");
        tt.key(Shortcut.cmd(Key.P));
        tt.key(Shortcut.of(Key.SLASH));
        Check.equal(1, palette[0]);
        Check.equal(0, slash[0]);                          // it types a slash, which comes as text
    }

    public void testTabIntoAnInputSelectsItsText() {
        TextRef q = new TextRef("hello");
        Tester tt = Tester.of(c -> column(c).children(() -> {
            button(c, "前");
            textInput(c, q);
        }), 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.TAB));
        Check.equal("[0, 5]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testAnAppChangeShowsWithoutChanged() {
        TextRef q = new TextRef("a");
        int[] changes = {0};
        Tester tt = Tester.of(c -> { if (textInput(c, q).placeholder("p").changed()) changes[0]++; }, 300, 200);
        tt.click("a");
        tt.key(Shortcut.of(Key.END));                      // caret 1, wherever the click put it
        q.set("abc");
        tt.frame();                                        // the app's value shows; the caret stays at 1
        Check.equal(0, changes[0]);
        tt.type("!");
        Check.equal("a!bc", q.get());
        Check.equal(1, changes[0]);
    }

    public void testACompositionStaysOutOfTheRefUntilCommitted() {
        TextRef q = new TextRef("");
        int[] changes = {0};
        Tester tt = Tester.of(c -> { if (textInput(c, q).placeholder("搜索").changed()) changes[0]++; }, 300, 200);
        tt.click("搜索");
        tt.compose("hui", 3);
        tt.compose("huiyi", 5);
        Check.equal("", q.get());
        Check.equal(0, changes[0]);
        tt.type("会议");
        Check.equal("会议", q.get());
        Check.equal(1, changes[0]);
    }

    public void testACompositionIsCommittedWhenFocusLeaves() { // Review Focus 3
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).children(() -> {
            textInput(c, q).placeholder("搜索");
            button(c, "别处");
        }), 300, 200);
        jui.ui.engine.Host.Memory host = (jui.ui.engine.Host.Memory) tt.engine().host();
        tt.click("搜索");
        tt.compose("ni", 2);
        int discards = host.discards;
        tt.click("别处");
        Check.equal("ni", q.get());
        Check.equal(discards + 1, host.discards);
        tt.click("搜索");                                  // back in: the caret after "ni"
        tt.key(Shortcut.of(Key.END));
        tt.compose("hao", 3);
        tt.engine().windowFocusChanged(false);             // the window goes to the back
        tt.settle();
        Check.equal("nihao", q.get());
        Check.equal(discards + 2, host.discards);
    }

    public void testCopyAndPasteGoThroughTheClipboard() {
        TextRef q = new TextRef("hello");
        Tester tt = Tester.of(c -> textInput(c, q), 300, 200);
        tt.click("hello");
        tt.key(Shortcut.cmd(Key.A));
        tt.key(Shortcut.cmd(Key.C));
        Check.equal("hello", tt.clipboard());
        tt.setClipboard("a\nb");
        tt.key(Shortcut.cmd(Key.V));
        Check.equal("a b", q.get());
        tt.command(EditCommand.SELECT_ALL);
        tt.command(EditCommand.CUT);
        Check.equal("", q.get());
        Check.equal("a b", tt.clipboard());
    }

    public void testAMenuCommandWithoutAnInputIsTheKey() {
        int[] undos = {0};
        Tester tt = Tester.of(c -> { if (c.shortcut(Shortcut.cmd(Key.Z))) undos[0]++; button(c, "x"); }, 300, 200);
        tt.command(EditCommand.UNDO);
        Check.equal(1, undos[0]);
    }

    public void testADisabledInputTakesNoText() {
        TextRef q = new TextRef("");
        boolean[] off = {false};
        Tester tt = Tester.of(c -> textInput(c, q).placeholder("搜索").disabled(off[0]), 300, 200);
        tt.click("搜索");
        off[0] = true;
        tt.frame();
        tt.frame();
        tt.type("x");
        Check.equal("", q.get());
    }

    public void testOnSubmitRunsAfterTheBuild() {
        TextRef q = new TextRef("x");
        java.util.List<String> log = new java.util.ArrayList<>();
        Tester tt = Tester.of(c -> {
            textInput(c, q).onSubmit(() -> log.add("submit " + q.get()));
            log.add("built");
        }, 300, 200);
        tt.click("x");
        log.clear();
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal("submit x", log.get(1));
    }

    public void testTheInputMethodSeesTheSelectionAndTheComposition() {
        TextRef q = new TextRef("ab");
        Tester tt = Tester.of(c -> textInput(c, q), 300, 200);
        tt.click("ab");
        tt.key(Shortcut.of(Key.END));
        tt.compose("ni", 2);
        int[] r = new int[2];
        Check.isTrue(tt.engine().textMarkedRange(r), "composing");
        Check.equal("[2, 2]", java.util.Arrays.toString(r));
        Check.isTrue(tt.engine().textSelectedRange(r), "a selection");
        Check.equal("[4, 0]", java.util.Arrays.toString(r));
        Check.equal("abni", tt.engine().textIn(0, 99));
        Check.equal(4, tt.engine().textLength());
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test TextInputTest`。Expected：编译失败。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): text inputs bound to TextRef, with keys, composition, clipboard and submit`。

---

### Task 5：鼠标选择、横向滚动、候选框位置和指针形状

**Files:**
- Modify: `Engine.java`、`Input.java`、`HitList.java`、`Node.java`、`Element.java`、`Tester.java`
- Test: `src/test/java/jui/ui/TextInputPointerTest.java`

**Interfaces:**
- **Consumes**：Task 3 的 `press`、`drag` 和几何字段；Task 4 的 `Host`。
- **Produces**：
  - **提交时的几何**（`commit` 里，对每个 `INPUT` 节点）：
    - 内容框 `(cx, cy, cw, ch)`；一行的高 `lineH`（同测量）；`lineTop = cy + max((ch − lineH) / 2, 0)`；
    - 按细节第 15 条更新 `edit.scrollX`，光标的 x 是 `layout.caretX(edit.displayCaret())`；
    - `edit.originX = cx − scrollX`，`edit.lineTop`、`edit.lineH`、`edit.layout`；裁剪框是内边距框；
    - 节点上记下绘制要的：`inputX = originX`、`inputTop`、`inputLineH`（Task 6 用）；
    - 文本输入的光标矩形和上一帧不同时，调 `host.textInputChanged()`（细节第 7 条）。
  - **按下**（`Input.pointerDown`）：目标条目带 `TEXT_INPUT`、主按钮、启用、有 `edit` 时：组字中先提交并 `host.discardMarkedText()`；然后 `edit.press(edit.layout.indexAt(x − edit.originX), count, shift)`，`count` 是按元素重算过的连击次数；重置闪烁。
  - **拖动**（`Input.pointerMove`）：按住的是输入框时 `edit.drag(edit.layout.indexAt(x − edit.originX))`。
  - **候选框和点位**：

    ```java
    public boolean textRect(int start, int end, float[] out);   // window DIPs: x, y, w, h; see detail 8
    public int textIndexAt(float x, float y);                   // -1 without text input
    ```

    - `start == end` 时是光标矩形 `(originX + caretX(start), lineTop, 1, lineH)`；否则是 `start` 所在字素簇的矩形，`out[4]`（`out.length ≥ 5` 时）写回实际范围的末尾；
    - 矩形和裁剪框不相交时返回 `false`；
    - 组字中的下标按显示文字算。
  - **指针形状**：
    - `Element.cursor(Cursor)` 设 `n.cursor = (byte) (c.ordinal() + 1)`（`Cursor` 枚举在 Task 4 建好，见细节第 1 条）；输入框没设时当作 `TEXT`；
    - `HitList` 每个条目记下 `cursor`；
    - `Input.updateCursor()` 按细节第 17 条算出形状，和上次不同时 `host.setCursor(...)`。在 `commit` 之后、`rehover` 改变悬停链时、按下和松开时调用。
  - **`Tester`**：
    - `Rect textCaret()`：`textRect(选区光标, 同)` 的矩形，没有文本输入时为 `null`；
    - `float textScroll(String s)`：找到的输入框的 `edit.scrollX`；
    - `Cursor cursor()`：`Host.Memory` 最后收到的形状。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class TextInputPointerTest {
    static final String LONG = "0123456789".repeat(8);

    /** The caret's x before character k of the focused input (ASCII text: one arrow a character). */
    static float xOf(Tester tt, int k) {
        tt.key(Shortcut.of(Key.HOME));
        for (int i = 0; i < k; i++) {
            tt.key(Shortcut.of(Key.RIGHT));
        }
        return tt.textCaret().x();
    }

    /** The middle of the focused input's line. */
    static float lineY(Tester tt) {
        Rect r = tt.textCaret();
        return r.y() + r.height() / 2;
    }

    static void clickAt(Tester tt, float x, float y, int count, int mods) {
        tt.engine().pointerDown(x, y, 0, count, mods);
        tt.settle();
        tt.engine().pointerUp(x, y, 0, mods);
        tt.settle();
    }

    public void testAClickPlacesTheCaretAtTheCharacter() {
        TextRef q = new TextRef("hello world");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in")), 400, 200);
        tt.click("in");                                   // focus first
        float x = xOf(tt, 6) + 0.3f, y = lineY(tt);       // just right of the caret before "w"
        clickAt(tt, x, y, 1, 0);
        Check.equal("[6, 6]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testDoubleAndTripleClicksAndShiftClick() {
        TextRef q = new TextRef("hello world");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in")), 400, 200);
        tt.click("in");
        float inWorld = (xOf(tt, 8) + xOf(tt, 9)) / 2, inE = xOf(tt, 1) + 0.3f, onR = xOf(tt, 8) + 0.3f, y = lineY(tt);
        clickAt(tt, inWorld, y, 1, 0);
        clickAt(tt, inWorld, y, 2, 0);
        Check.equal("[6, 11]", java.util.Arrays.toString(tt.textSelection()));
        clickAt(tt, inWorld, y, 3, 0);
        Check.equal("[0, 11]", java.util.Arrays.toString(tt.textSelection()));
        clickAt(tt, inE, y, 1, 0);
        clickAt(tt, onR, y, 1, Mod.SHIFT);
        Check.equal("[1, 8]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testDraggingSelects() {
        TextRef q = new TextRef("hello world");
        Tester tt = Tester.of(c -> textInput(c, q).label("in"), 400, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.HOME));
        Rect a = tt.textCaret();
        tt.key(Shortcut.of(Key.END));
        Rect b = tt.textCaret();
        float y = a.y() + a.height() / 2;
        tt.press(a.x() + 0.5f, y);
        tt.move(b.x() + 30, y);                          // past the end
        tt.release(b.x() + 30, y);
        Check.equal("[0, 11]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testTheCaretStaysInViewAndUnfocusedShowsTheStart() { // Review Focus 5
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> {
            textInput(c, q).label("in").width(120);
            button(c, "别处");
        }), 400, 200);
        tt.click("in");
        tt.type(LONG);
        Rect box = tt.find("in");
        Rect caret = tt.textCaret();
        // The content box's right is box right − border 1 − padding 10; the caret keeps 1 inside it.
        Check.isTrue(caret.x() <= box.x() + box.width() - 11 + 0.01f, "the caret is in the content box: " + caret);
        Check.isTrue(tt.textScroll("in") > 0, "scrolled");
        tt.key(Shortcut.of(Key.HOME));
        Check.near(0, tt.textScroll("in"));
        tt.key(Shortcut.of(Key.END));
        tt.click("别处");
        Check.near(0, tt.textScroll("in"));
    }

    public void testAClickAfterScrollingLandsOnTheCharacterUnderIt() { // Review Focus 5
        TextRef q = new TextRef(LONG);
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in").width(120)), 400, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        Rect end = tt.textCaret();
        tt.press(end.x() - 0.2f, end.y() + 2);
        tt.release(end.x() - 0.2f, end.y() + 2);
        Check.equal("[80, 80]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testTheCaretRectIsForTheCandidateWindow() {
        TextRef q = new TextRef("ab");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in")), 400, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        Rect caret = tt.textCaret();
        Rect box = tt.find("in");
        Check.near(1, caret.width());
        Check.isTrue(caret.y() >= box.y() && caret.y() + caret.height() <= box.y() + box.height(), "inside the box");
        float[] r = new float[6];
        Check.isTrue(tt.engine().textRect(0, 2, r), "a range");
        Check.near(box.x() + 1 + 10, r[0], 0.5);          // its first grapheme: "a", at the content's left
        Check.equal(1, (int) r[4]);                       // the actual range ends after "a"
    }

    public void testCursorShapes() {
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).padding(10).gap(10).children(() -> {
            textInput(c, q).label("in");
            box(c).size(120, 60).cursor(Cursor.POINTER).label("link").children(() -> button(c, "b").width(40));
            box(c).size(50, 30).label("plain");
        }), 400, 300);
        tt.hover("in");
        Check.equal(Cursor.TEXT, tt.cursor());
        Rect link = tt.find("link");
        tt.move(link.x() + 100, link.y() + 50);           // in the box, clear of its button
        Check.equal(Cursor.POINTER, tt.cursor());
        tt.hover("b");                                    // a clickable element without a cursor stops the search
        Check.equal(Cursor.DEFAULT, tt.cursor());
        tt.hover("plain");
        Check.equal(Cursor.DEFAULT, tt.cursor());
        Rect in = tt.find("in");
        tt.press(in.x() + 20, in.y() + in.height() / 2);
        tt.move(in.x() + 20, in.y() + 200);               // held: the I-beam stays
        Check.equal(Cursor.TEXT, tt.cursor());
        tt.release(in.x() + 20, in.y() + 200);
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test TextInputPointerTest`。Expected：编译失败。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): select with the pointer, scroll the caret into view, cursor shapes`。

---

### Task 6：外观和绘制

**Files:**
- Modify: `Paint.java`、`Node.java`、`Ui.java`（`textInput`）、`Engine.java`（闪烁）、`src/main/java/jui/render/Scene.java`（`scale()`）
- Test: `src/test/java/jui/ui/engine/InputPaintTest.java`

**Interfaces:**
- **Consumes**：Task 5 在节点上记下的 `inputX`、`inputTop`、`inputLineH`。
- **Produces**：
  - **`Node`** 增加绘制要的字段，由 `textInputBase` 在构建时、`commit` 在提交时填写：`inputCaret`（显示文字里的光标）、`inputSelA`/`inputSelB`（选区，没有时相等）、`inputMarkA`/`inputMarkB`（组字范围，没有时为 −1）、`inputFocused`、`caretShown`。
  - **闪烁**（`textInputBase` 里，获得焦点时）：`phase = now − blinkStart`；`caretShown = phase ≥ 30 s || (phase / 530 ms) % 2 == 0`；`phase < 30 s` 时 `c.after(530 ms − phase % 530 ms)`（细节第 14 条）。
  - **`Paint` 画 `INPUT`**（在背景和边框之后，裁剪到内边距框，直角）：
    1. 文字和组字都为空、有占位文字：在 `(内容左边, inputTop)` 画占位文字，颜色 `theme.textMuted()`；
    2. 获得焦点且有选区：一个方框，从 `inputX + caretX(selA)` 到 `inputX + caretX(selB)`，高 `inputLineH`，颜色 `theme.selection()`；
    3. 文字：`scene.text(layout, inputX, inputTop + (inputLineH − layout.height) / 2, 文字颜色)`；
    4. 组字下划线：从 `caretX(markA)` 到 `caretX(markB)`，`y = inputTop + inputLineH − 2`，高 1，文字颜色；
    5. 获得焦点、`caretShown`、启用：光标方框 `(inputX + caretX(inputCaret), inputTop, max(round(scale), 1) / scale, inputLineH)`，颜色 `theme.accent()`；
    - 输入框不画焦点环（细节第 12 条）。
  - **`Ui.textInput`**：`textInputBase(c, ref).padding(6, 10).radius(t.radius()).background(t.surface()).border(1, ...)`，边框颜色：获得焦点时 `accent`，悬停时 `mix(border, text, 0.25)`（`Ui.mix` 的整数版本，不分配；和 `Color.mix` 的取整相同），否则 `border`。
  - **`Scene.scale()`**：返回 `begin` 设的缩放。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui.engine;

import jui.testing.Check;
import jui.ui.*;

import static jui.ui.Ui.*;

public final class InputPaintTest {
    static final Theme T = Theme.light();

    static int rgba(float[] in) {
        return Math.round(in[8] * 255) << 24 | Math.round(in[9] * 255) << 16 | Math.round(in[10] * 255) << 8 | Math.round(in[11] * 255);
    }

    /** Boxes (not glyphs or shadows) filled with color. */
    static java.util.List<float[]> boxes(Tester tt, int color) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] == 0 && rgba(in) == color) out.add(in);
        }
        return out;
    }

    /** Boxes whose border (instance floats 12–15, width at 16) is color. */
    static int borders(Tester tt, int color) {
        int n = 0;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            int border = Math.round(in[12] * 255) << 24 | Math.round(in[13] * 255) << 16 | Math.round(in[14] * 255) << 8
                    | Math.round(in[15] * 255);
            if (in[18] == 0 && in[16] > 0 && border == color) n++;
        }
        return n;
    }

    /** Carets: accent boxes 1 DIP wide (2 pixels at the tester's scale 2). */
    static int carets(Tester tt) {
        int n = 0;
        for (float[] in : boxes(tt, T.accent().rgba())) {
            if (Math.abs(in[2] - 1) < 0.01) n++;
        }
        return n;
    }

    public void testTheCaretIsAnAccentLineOneDeviceLineWide() {
        TextRef q = new TextRef("ab");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in")), 300, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        Rect caret = tt.textCaret();
        java.util.List<float[]> lines = boxes(tt, T.accent().rgba());
        boolean found = false;
        for (float[] in : lines) {
            if (Math.abs(in[2] - 1) < 0.01 && Math.abs(in[0] - caret.x()) < 0.51) found = true;  // scale 2: 2 px = 1 DIP
        }
        Check.isTrue(found, "a 1-DIP accent caret at the caret's x");
    }

    public void testTheCaretBlinksAndRestsShownAfter30Seconds() {
        TextRef q = new TextRef("ab");
        Tester tt = Tester.of(c -> textInput(c, q).label("in"), 300, 200);
        tt.click("in");                                   // the blink starts over at the press, at 0
        Check.equal(1, carets(tt));
        tt.advance(java.time.Duration.ofMillis(540));     // a frame at 530 ms hid it
        Check.equal(0, carets(tt));
        tt.advance(java.time.Duration.ofMillis(530));     // and one at 1060 ms showed it
        Check.equal(1, carets(tt));
        tt.advance(java.time.Duration.ofSeconds(31));
        Check.equal(1, carets(tt));                       // at rest, shown
        Check.isTrue(tt.engine().nextWake() == Long.MAX_VALUE, "no more timers");
    }

    public void testTheSelectionShowsOnlyWithFocus() {
        TextRef q = new TextRef("hello");
        Tester tt = Tester.of(c -> column(c).children(() -> { textInput(c, q).label("in"); button(c, "别处"); }), 300, 200);
        tt.click("in");
        tt.key(Shortcut.cmd(Key.A));
        Check.equal(1, boxes(tt, T.selection().rgba()).size());
        tt.click("别处");
        Check.equal(0, boxes(tt, T.selection().rgba()).size());
    }

    public void testThePlaceholderAndTheCompositionUnderline() {
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> textInput(c, q).placeholder("搜索笔记").label("in"), 300, 200);
        tt.frame();
        boolean muted = false;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] != 0 && (rgba(in) & 0xFFFFFF00) == (T.textMuted().rgba() & 0xFFFFFF00)) muted = true;
        }
        Check.isTrue(muted, "the placeholder's glyphs are muted");
        tt.click("in");
        tt.compose("ni", 2);
        boolean underline = false;
        for (float[] in : boxes(tt, T.text().rgba())) {
            if (Math.abs(in[3] - 1) < 0.01 && in[2] > 2) underline = true;
        }
        Check.isTrue(underline, "a 1-DIP underline under the composition, in the text's color");
    }

    public void testBorderColorsAndNoFocusRing() {
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> { button(c, "前"); textInput(c, q).label("in"); }), 300, 200);
        int hover = T.border().mix(T.text(), 0.25f).rgba();
        tt.hover("in");
        Check.equal(1, borders(tt, hover));               // hovered: the border leans to the text's color
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.TAB));                     // into the input, from the keyboard
        Check.equal(0, borders(tt, T.focusRing().rgba()));
        Check.equal(1, borders(tt, T.accent().rgba()));
    }

    public void testTheInputIsOneLineHigh() {
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).children(() -> textInput(c, q).label("in")), 300, 200);
        tt.frame();
        float line = new jui.text.TextEngine("zh-Hans").lineHeight(13, 400);
        Check.near(line + 12 + 2, tt.find("in").height(), 0.01);
        q.set("中文");
        tt.frame();
        Check.near(line + 12 + 2, tt.find("中文").height(), 0.01);
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test InputPaintTest`。Expected：编译失败或断言失败（没有画光标、选区、占位文字）。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): the text input's looks: caret, selection, composition, placeholder`。

---

### Task 7：macOS 的 `NSTextInputClient` 和按键

**Files:**
- Modify: `src/main/java/jui/platform/mac/MacWindow.java`
- Test: `src/test/java/jui/platform/mac/MacWindowTextTest.java`

**Interfaces:**
- **Consumes**：Task 1 的签名和辅助方法；Task 4、5 的引擎接口（经 `MacWindow.Input`）。
- **Produces**：
  - **`MacWindow.Input` 增加默认方法**（默认什么都不做、返回 `false`/`-1`/`null`，现有的实现不用改），签名和 Task 4、5 的引擎接口一致：`boolean textActive()`、`boolean textComposing()`、`void textInsert(String s, int replStart, int replLength)`、`void textMark(String s, int selStart, int selLength, int replStart, int replLength)`、`void textUnmark()`、`boolean textMarkedRange(int[] out)`、`boolean textSelectedRange(int[] out)`、`int textLength()`、`String textIn(int start, int end)`、`boolean textRect(int start, int end, float[] out)`、`int textIndexAt(double x, double y)`、`void editCommand(EditCommand c)`。
    - 进来的范围：`NSNotFound` 的位置转成 −1，其余截成 `int`；出去的 −1 转成 `NOT_FOUND`。
  - **`JUIView`**：加协议 `NSTextInputClient` 和这些方法：
    - `hasMarkedText`、`markedRange`、`selectedRange`：没有时返回 `(NOT_FOUND, 0)`；
    - `setMarkedText:selectedRange:replacementRange:`（字符串参数可能是 `NSAttributedString`，取 `string`）、`unmarkText`、`insertText:replacementRange:`，以及旧的 `insertText:`；
    - `validAttributesForMarkedText`：空的 `NSArray`；
    - `attributedSubstringForProposedRange:actualRange:`：用 `textIn` 的结果建 `NSAttributedString`（autorelease），写回夹好的实际范围，没有文本输入时返回 0；
    - `characterIndexForPoint:`：屏幕点 → `[window convertRectFromScreen:]` → `[view convertRect:fromView:nil]` → `textIndexAt`，−1 时返回 `NOT_FOUND`；
    - `firstRectForCharacterRange:actualRange:`：`textRect`，成功时 `[view convertRect:toView:nil]`、`[window convertRectToScreen:]`，写回实际范围；失败时返回零矩形；
    - `doCommandBySelector:`：细节第 6 条。
  - **`keyDown:`**（细节第 6 条）：

    ```java
    boolean text = input.textActive() && (mods & (Mod.CMD | Mod.CTRL)) == 0;
    boolean composing = text && input.textComposing();
    if (!composing) input.keyDown(key, mods, repeat);
    if (text) {
        suppressed = composing; owedKey = key; owedMods = mods; owedRepeat = repeat;
        send(view, "interpretKeyEvents:", ObjC.array(event));
        suppressed = false;
    }
    // doCommandBySelector: while suppressed sends the owed key once, then clears suppressed
    ```

  - **给 `Host` 用的方法**：`void discardMarkedText()`：`[[view inputContext] discardMarkedText]`；`void invalidateCharacterCoordinates()`：`[[view inputContext] invalidateCharacterCoordinates]`。

- [ ] **Step 1：写失败的测试**

`MacWindowTextTest` 沿用 `MacWindowInputTest` 的做法：每个测试开自己的 `MacWindow`，结束时关闭；`Input` 是一个记录器。
- 真实的 `interpretKeyEvents:` 交给当前的输入法，结果取决于这台机器正在用的输入法（拼音下按 a 是组字）。所以 `MacWindow` 有一个包内的测试钩子 `java.util.function.LongConsumer interpretHook`：不为 `null` 时，`keyDown:` 用它代替 `interpretKeyEvents:`，参数是事件；
- 包内方法 `long window()` 给测试换算坐标用。

```java
package jui.platform.mac;

// imports as in MacWindowInputTest, plus jui.ui.EditCommand

public final class MacWindowTextTest {
    // KEY_EVENT: the same NSEvent factory handle as MacWindowInputTest's.

    /** Records what the view told it; a text input has focus. */
    static final class Recorder implements MacWindow.Input {
        final java.util.List<String> events = new java.util.ArrayList<>();
        boolean composing;
        int[] selected = {0, 0}, marked;
        public void pointerMove(double x, double y, int mods) {}
        public void pointerDown(double x, double y, int button, int clicks, int mods) {}
        public void pointerUp(double x, double y, int button, int mods) {}
        public void pointerLeave() {}
        public void keyDown(Key key, int mods, boolean repeat) { events.add("key " + key + " " + mods + " " + repeat); }
        public void modifiersChanged(int mods) {}
        public void windowFocusChanged(boolean focused) {}
        public void appearanceChanged(boolean dark) {}
        public void scroll(double x, double y, double dx, double dy, boolean precise, int mods) {}
        @Override public boolean textActive() { return true; }
        @Override public boolean textComposing() { return composing; }
        @Override public void textInsert(String s, int replStart, int replLength) {
            events.add("insert " + s + " " + replStart + " " + replLength);
        }
        @Override public void textMark(String s, int selStart, int selLength, int replStart, int replLength) {
            events.add("mark " + s + " " + selStart + " " + selLength + " " + replStart + " " + replLength);
        }
        @Override public void textUnmark() { events.add("unmark"); }
        @Override public boolean textMarkedRange(int[] out) {
            if (marked == null) return false;
            out[0] = marked[0]; out[1] = marked[1];
            return true;
        }
        @Override public boolean textSelectedRange(int[] out) { out[0] = selected[0]; out[1] = selected[1]; return true; }
        @Override public boolean textRect(int start, int end, float[] out) {
            out[0] = 20; out[1] = 30; out[2] = 1; out[3] = 16;
            return true;
        }
        @Override public void editCommand(EditCommand c) { events.add("edit " + c); }
    }

    /** Sends keyDown: with an event of these characters, key code and modifier flags. */
    static void keyDown(MacWindow w, String chars, int code, long flags) throws Throwable { /* KEY_EVENT, then send */ }

    public void testAKeyGoesRawFirstThenToTheInputMethod() throws Throwable {
        Recorder r = new Recorder();
        MacWindow w = MacWindow.open("text", 200, 150, 0xFFFFFF, null, (s, width, height) -> {}, r);
        try {
            w.interpretHook = e -> r.events.add("ime");
            keyDown(w, "", 0x7B, 0);
            Check.equal(java.util.List.of("key LEFT 0 false", "ime"), r.events);
        } finally {
            w.close();
        }
    }

    public void testCmdKeysSkipTheInputMethod() throws Throwable {
        // as above, with keyDown(w, "p", 0x23, 1L << 20): events are exactly ["key P 8 false"]
    }

    public void testWhileComposingAKeyGoesToTheInputMethodAndAReturnedCommandSendsItOnce() throws Throwable {
        // r.composing = true; w.interpretHook sends doCommandBySelector:(moveLeft:) twice after adding "ime";
        // keyDown(w, "", 0x7B, 0): events are exactly ["ime", "key LEFT 0 false"]
    }

    public void testACommandWhenNotComposingAddsNothing() throws Throwable {
        // w.interpretHook sends doCommandBySelector:(moveLeft:); events are exactly ["key LEFT 0 false"]
    }

    public void testInsertAndMarkedTextReachTheInput() {
        // ObjC.sendTextRange(view, "insertText:replacementRange:", nsString("你好"), NOT_FOUND, 0) → "insert 你好 -1 0"
        // ObjC.sendTextRanges(view, "setMarkedText:selectedRange:replacementRange:", nsString("ni"), 2, 0, NOT_FOUND, 0)
        //   → "mark ni 2 0 -1 0"; an NSAttributedString of "ni" gives the same
        // ObjC.send(view, "unmarkText") → "unmark"
    }

    public void testRangesComeFromTheInput() {
        // r.marked = null: markedRange is (NOT_FOUND, 0), hasMarkedText NO
        // r.selected = {3, 2}: selectedRange is (3, 2)
    }

    public void testTheFirstRectIsInScreenCoordinates() {
        // ObjC.sendRectRange(view, "firstRectForCharacterRange:actualRange:", 0, 0, 0, rect) equals
        // convertRect(window, "convertRectToScreen:", convertRect(view, "convertRect:toView:", 20, 30, 1, 16, 0)),
        // each of the four within 0.01
    }
}
```

注释写成的测试，实现者照注释里的调用和期望值写成代码；期望值就是注释里写的，不另加判断。

- [ ] **Step 2**：运行 `./build.sh test MacWindowTextTest`。Expected：编译失败。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：运行 `./build.sh ffi-check`。Expected：两行 `FFI ok`。
- [ ] **Step 6**：提交，信息 `feat(mac): NSTextInputClient on the window's view; keys reach the input method`。

---

### Task 8：macOS 的剪贴板、编辑菜单和指针形状

**Files:**
- Create: `src/main/java/jui/platform/mac/MacPasteboard.java`、`src/main/java/jui/platform/mac/MacCursors.java`
- Modify: `MacApp.java`、`MacWindow.java`
- Test: `src/test/java/jui/platform/mac/MacPasteboardTest.java`；`MacWindowTextTest` 增加菜单和指针的测试

**Interfaces:**
- **Produces**：
  - **`MacPasteboard`**：
    - `static MacPasteboard general()`：`[NSPasteboard generalPasteboard]`；`static MacPasteboard unique()`：`[NSPasteboard pasteboardWithUniqueName]`，测试用，用完 `release()`（`releaseGlobally`）；
    - `String read()`：`stringForType:` `NSPasteboardTypeString`（AppKit 的常量，值是 `public.utf8-plain-text`），没有时 `null`；
    - `void write(String s)`：`clearContents` 后 `setString:forType:`。MyGO 用延迟提供数据的 `NSPasteboardItem`，j-ui 只有纯文本，直接写字符串。
  - **编辑菜单**：`MacApp.installMenu` 加上细节第 11 条的“Edit”菜单，`setAutoenablesItems:NO`；`installMenu` 改成包内可见，给测试用。
  - **`JUIView` 的菜单动作**：`undo:`、`redo:`、`cut:`、`copy:`、`paste:`、`selectAll:` → `input.editCommand(...)`。
  - **`MacCursors`**：`static long of(Cursor c)`：`DEFAULT` → `arrowCursor`、`POINTER` → `pointingHandCursor`、`TEXT` → `IBeamCursor`、`RESIZE_EW` → `resizeLeftRightCursor`、`RESIZE_NS` → `resizeUpDownCursor`、`NOT_ALLOWED` → `operationNotAllowedCursor`、`CROSSHAIR` → `crosshairCursor`、`GRAB` → `openHandCursor`、`GRABBING` → `closedHandCursor`。
  - **`MacWindow.setCursor(Cursor c)`**：记下形状；指针在视图里时 `[cursor set]`。跟踪区域的选项加 `NSTrackingCursorUpdate`（`0x04`）；`cursorUpdate:`、`mouseEntered:` 重新应用（细节第 17 条）。

- [ ] **Step 1：写失败的测试**

```java
package jui.platform.mac;

import jui.testing.Check;

public final class MacPasteboardTest {
    public void testTextRoundTripsThroughAPasteboard() {
        MacPasteboard p = MacPasteboard.unique();    // not the user's clipboard
        try {
            Check.equal(null, p.read());
            p.write("剪贴板 ✓");
            Check.equal("剪贴板 ✓", p.read());
        } finally {
            p.release();
        }
    }
}
```

`MacWindowTextTest` 增加：
- `testTheEditMenuHasTheStandardItems`：调 `MacApp.installMenu(app)` 后，在主菜单里找到标题为 “Edit” 的子菜单，依次检查各项的动作（`undo:`、`redo:`、分隔线、`cut:`、`copy:`、`paste:`、`selectAll:`）和按键（`z` ⌘、`Z` ⌘、`x`、`c`、`v`、`a`）；
- `testMenuActionsBecomeEditCommands`：`ObjC.send(w.view(), "copy:", 0)` 记下 `edit COPY`，`undo:` 记下 `edit UNDO`；
- `testCursorsMapToAppKit`：`MacCursors.of(Cursor.TEXT) == ObjC.send(ObjC.cls("NSCursor"), "IBeamCursor")`，每个枚举值都不为 0。

- [ ] **Step 2**：运行 `./build.sh test MacPasteboardTest` 和 `./build.sh test MacWindowTextTest`。Expected：编译失败。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(mac): pasteboard, the Edit menu and cursor shapes`。

---

### Task 9：窗口接线和验收示例

**Files:**
- Modify: `src/main/java/jui/app/Window.java`、`src/main/java/jui/examples/FilesDemo.java`、`src/main/java/jui/examples/TodoDemo.java`
- Test: `src/test/java/jui/examples/FilesDemoTest.java`（新建）、`src/test/java/jui/examples/TodoDemoTest.java`

**Interfaces:**
- **Consumes**：Task 4–8 的全部接口。
- **Produces**：
  - **`Window`**：
    - 实现 `MacWindow.Input` 的文本方法，转给引擎（−1 和 `NOT_FOUND` 的换算在 `MacWindow` 里做完）；
    - 实现 `Host`：剪贴板用 `MacPasteboard.general()`，`setCursor` → `mac.setCursor`，`discardMarkedText` → `mac.discardMarkedText()`，`textInputChanged` → `mac.invalidateCharacterCoordinates()`；构造时 `engine.setHost(this)`。
  - **`FilesDemo` 的搜索框**（验收示例）：
    - 顶部一行：`textInput(c, query).placeholder("搜索笔记").grow(1)`；它 `changed()` 时 `files.scrollToTop()`；
    - 按标题过滤：包含查询串（不区分大小写，按 `String.toLowerCase(Locale.ROOT)` 比较）。结果按查询串缓存，查询不变的帧不重新过滤；
    - 计数显示“N 篇笔记”（匹配的数目），没有匹配时列表区域显示“没有匹配的笔记”；
    - `forTools()` 照旧返回视图和列表状态，另外返回 `query`（第三个元素），给测试和工具用。
  - **`TodoDemo`**：顶部加一行：`textInput(c, title).placeholder("新待办").grow(1)` 和“添加”按钮；回车或点“添加”时，标题非空就加一条，然后清空输入框。原来的 ⌘N 照旧加一条固定标题的待办。

- [ ] **Step 1：写失败的测试**

```java
package jui.examples;

import jui.testing.Check;
import jui.ui.Tester;
import jui.ui.TextRef;

public final class FilesDemoTest {
    public void testTheSearchBoxFiltersTheList() {
        Object[] parts = FilesDemo.forTools();
        Tester tt = Tester.of((jui.ui.View) parts[0], 360, 640);
        tt.click("搜索笔记");
        tt.type("会议纪要");
        for (String s : tt.texts()) {
            if (s.startsWith("笔记 ")) Check.isTrue(s.contains("会议纪要"), "only matching titles: " + s);
        }
        Check.equal("会议纪要", ((TextRef) parts[2]).get());
        tt.type("不存在的词");
        Check.isTrue(tt.hasText("没有匹配的笔记"), "the empty state");
    }

    public void testPinyinCompositionDoesNotFilterUntilCommitted() {
        Object[] parts = FilesDemo.forTools();
        Tester tt = Tester.of((jui.ui.View) parts[0], 360, 640);
        tt.click("搜索笔记");
        tt.compose("huiyi", 5);
        Check.isTrue(tt.hasText("10000 篇笔记"), "a composition does not filter");
        tt.type("会议");
        Check.isTrue(!tt.hasText("10000 篇笔记"), "the committed text does");
    }
}
```

`TodoDemoTest` 增加 `testTypingATitleAndEnterAddsATodo`：点“新待办”，`type("买菜")`，按回车，断言 `tt.hasText("买菜")`，输入框清空（再按回车不会多加一条空标题的待办）。

- [ ] **Step 2**：运行 `./build.sh test FilesDemoTest` 和 `./build.sh test TodoDemoTest`。Expected：失败（没有搜索框和输入框）。
- [ ] **Step 3**：实现 Produces 里的内容。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：运行 `./build.sh native-demo Files`，用真实的键盘试一遍：英文和拼音输入、候选框位置、方向键和 Option+方向键、⌘C/⌘V（和别的应用之间）、⌘Z、拖动选择、I 形指针。Expected：都正常。手动核对的结果在 Task 10 写进 README；有问题按 superpowers:systematic-debugging 处理。
- [ ] **Step 6**：提交，信息 `feat(examples): a search box over the notes; type new todos`。

---

### Task 10：分配实测和文档

**Files:**
- Modify: `src/main/java/jui/spike/AllocSpike.java`、`README.md`

**Interfaces:**
- **Produces**：
  - **`AllocSpike` 的新场景 `inputs_frame`**：20 个输入框，各有一段文字，其中一个获得焦点、光标在闪烁；预热后连续跑内容不变的帧，算出框架内部每个输入框每帧分配的字节数。
    - 输出行里加 `inputs_frame=...`；
    - 超过 0.5 字节时打印 `FAIL: the framework allocates %.1f bytes per text input` 并以 1 退出（同控件场景的写法）。
  - **README 的 M4 一节**（和 M2、M3 的写法一致）：
    - 做了什么：输入框、编辑键、鼠标选择、输入法、剪贴板、撤销、编辑菜单、指针形状、TextEngine 的新能力；
    - 验收示例的截图 `docs/m4-search.png`（`./build.sh native-demo Files`，搜索“会议”的画面）；
    - 测试数目；`./build.sh alloc` 的结果（`inputs_frame`）；`./build.sh ffi-check`；
    - **已手动核对通过**的项目（Task 9 Step 5 的结果）；M3 遗留的“按 Tab 滚进视野”这次一并手动核对；
    - **推迟到后面的**：细节第 1 条的“M4 不做”。
  - README 的“运行”一节加上 `./build.sh ffi-check`。

- [ ] **Step 1**：运行 `./build.sh alloc`，在加场景之前记下当前的输出。Expected：和 M3 一样全部通过。
- [ ] **Step 2**：加上 `inputs_frame` 场景，再运行 `./build.sh alloc`。Expected：native image 下 `inputs_frame` 不超过 0.5 字节；超过时找出分配的来源（比如每帧新建显示字符串、闪烁时分配），修掉，不要放宽门槛。
- [ ] **Step 3**：运行 `./build.sh listbench`。Expected：仍然通过（p95 ≤ 4 ms，最大 ≤ 8 ms，图集没有填满）。
- [ ] **Step 4**：截图，写 README。
- [ ] **Step 5**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 6**：提交，信息 `docs: M4 text input; measure what frames of text inputs allocate`。

---

## 完成之后

- 整体审查一次（superpowers:executing-plans 的 Final Review），审查时对照本计划的“定下的细节”和 Review Focus。
- 合并方式由用户决定。
