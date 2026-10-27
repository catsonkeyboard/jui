# 输入框的选项和 Edit 菜单 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal**：补上 M4 推迟的输入框选项和两个菜单项：
- 只读输入框 `readOnly(boolean)`；
- 密码框 `password()`；
- 粘贴之前先问应用 `onPaste(fn)`；
- 输入框取用按键之前先问应用 `onTextKey(fn)`；
- 设置和读取选区 `setTextSelection(start, end)`、`textSelectionStart()`、`textSelectionEnd()`；
- 关闭输入法 `inputMethod(false)`；
- Edit 菜单的“Paste and Match Style”和“Delete”。

**Architecture**：
- **选项在构建之后才生效**：现在 `Engine.textInput` 一调用就定下输入框显示的文字、光标、选区和闪烁，链在它后面的选项来不及影响这一帧。改为：`textInput` 只同步 `TextRef` 和记下 `TextRef`；这一帧所有构建结束之后、布局之前，新的 `Engine.settleInputs()` 按最终的选项算出每个输入框显示的状态，并把选项写进它的 `TextEdit`，供到下一帧之前的输入使用（同 MyGO 的“对之后的输入一直有效，直到下一帧”）。
- **两个取法**：`Input.focusedEdit()` 是获得焦点、未禁用的输入框（含只读的），按键和菜单命令用它；`Input.activeEdit()` 再排除只读的，是“接收文字输入、输入法”的那个。
- **回调存在元素状态里**：`onPaste`、`onTextKey` 的函数存在 `ElementState`，`textInput` 每次构建时先清掉，链式调用再设上，所以不再调用就失效。
- **密码框只改显示**：真实文字照常编辑；显示的是每个码点一个 “•” 的掩码，下标在真实文字（UTF-16）和掩码之间换算；输入法只看到组字本身。
- **关闭输入法在平台层**：`MacWindow` 不再把按键交给 `interpretKeyEvents:`，改为把按键的字符（去掉控制字符）当文字插入。

**Tech Stack**：Java 25、GraalVM CE 25，FFM、AppKit，零依赖，自己写的测试运行器，`build.sh`。

**Spec**：[docs/design/2026-10-24-ui-api-design.md](../design/2026-10-24-ui-api-design.md)。设计 6.10 只列了 `textInput`；这些选项是 M4 计划“推迟到后面的”部分，行为以 MyGO 为准。

**行为依据**：MyGO（egoist/mygo，commit `8049993`）。下文写“同 MyGO”的地方以这个版本为准：
- `ui/textinput.go`（`ReadOnly`、`Password`、`OnPaste`、`OnTextKey`、`TextKey`、`SetTextSelection`、`TextSelection`）；
- `ui/editor.go`（`rebuild` 每次构建重置选项）、`ui/editor_navigation.go`（只读时取用的键、命令、`typesText`、粘贴）、`ui/editor_layout.go`（掩码）、`ui/editor_input.go`（密码框给输入法的回答）；
- `ui/handler.go`（`InputMethod`）、`internal/darwin/surface.go`、`surface_input.go`（`typedChars`、`pasteAsPlainText:`、`delete:`）；
- `ui/menu.go`（Edit 菜单的项目和快捷键）。

## Global Constraints

沿用 M4 的约束：
- **平台**：Apple Silicon、macOS 14 及以上，`MACOS_MIN=14.0`。
- **原生调用**：全部走 FFM，新签名登记在 `jui.ffi.Sigs`。不写 C 或 Objective-C。
- **不用反射**：主代码在运行时不用反射，不在运行时生成代码。
- **零第三方依赖**：不要尝试绕过 Maven Central 的封禁。
- **线程**：UI 函数和事件处理都在主线程。
- **开发模式**：在 JVM 上运行，或设置了 `-Djui.dev=true` 时开启。开启时误用 API 抛 `IllegalStateException`，关闭时忽略。
- **命名**：和 MyGO 对应。
- **下标**：文字的下标一律是 UTF-16（Java `String` 的下标），和 `TextRef`、`TextEdit` 一致。**和 MyGO 不同**：MyGO 的 `SetTextSelection` 按码点计。
- **注释和文档**：代码注释用英文，文档用中文。
- **提交信息**：末尾加 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- **框架控件内部不用捕获变量的 lambda**。
- **每次输入只报告一次**。
- **分配门槛**（设计第 8 节）：内容不变的帧里，框架内部不产生和元素数量成正比的分配，包括密码框和只读输入框；`./build.sh alloc` 的 `inputs_frame` 仍是 0。

## 本计划定下的细节

1. **构建之后再定显示状态**：
   - `Engine.textInput(index, ref)` 只做三件事：建或取 `TextEdit`；同步 `TextRef`（规则不变：应用改了值以应用为准，用户的编辑写回并报告 `changed()`）；把 `ref` 存进 `ElementState.ref`，并清掉 `onPaste`、`onTextKey`。
   - 新的 `Engine.settleInputs()` 在最后一遍构建之后、`inheritTextStyle()` 之前调用。对每个输入框节点：
     - 把这一帧的选项写进 `TextEdit`：`readOnly`、`password`、`inputMethodOff`；
     - 选项刚变的处理：变成禁用或只读时丢掉组字（`setMarked("")`），关闭输入法时提交组字；都通知输入法 `discardMarkedText()`；
     - 再算显示状态：`n.text`（密码框是掩码）、`inputCaret`、`inputSelA/B`、`inputMarkA/B`（密码框换算成掩码下标）、`inputFocused`、`caretShown` 和闪烁的唤醒。只读和禁用的输入框不显示光标，也不请求闪烁帧。
   - 现有的行为不变，现有测试全部通过。
2. **`readOnly(boolean on)`**（同 MyGO）：
   - 可以获得焦点，Tab 进入时全选；点击、拖动、双击照常选择；获得焦点时画选区，不画光标。
   - 它取用的键只有：只按着 ⌘ 的 ⌘A、⌘C；按着 Shift 的 ←/→/↑/↓/Home/End。其他键照常交给快捷键和滚动；回车不提交。
   - Edit 菜单命令：只执行 Copy 和 Select All；Cut、Paste、Paste and Match Style、Delete、Undo、Redo 什么都不做，也不转给快捷键。
   - 不接收文字输入：`textActive()` 为假，输入法不参与；`onPaste` 不会被调用。
   - 只用于输入框；用在别的元素上，开发模式报错。
3. **`password()`**（同 MyGO）：
   - 只在调用它的帧里生效；不再调用就重新显示文字。
   - 显示：文字加组字，每个码点一个 “•”（U+2022）。掩码字符串缓存在 `TextEdit` 里，只在显示的文字变了时重建。
   - 下标换算：真实下标 `i` → 掩码下标 `display.codePointCount(0, i)`；掩码下标 `k` → 真实下标 `display.offsetByCodePoints(0, k)`，再向下对齐到字素簇边界。点击、拖动、光标矩形都经过换算。
   - 光标移动、删除、按词移动仍按真实文字的字素簇和词。**和 MyGO 不同**：MyGO 的 ←/→ 按码点走。
   - Copy 和 Cut 什么都不做（Cut 也不删除），剪贴板不变；Paste、`onPaste`、撤销照常。
   - 输入法照常可用，但它只看到组字：`textLength` 是组字的长度，`textIn` 取组字的一段，选区在没有组字时是 {0, 0}，组字范围从 0 开始，替换范围一律忽略，`textRect` 总是光标的矩形，`textIndexAt` 返回 -1。
4. **Edit 菜单的两项**（同 MyGO）：
   - 菜单顺序：Undo、Redo、分隔线、Cut、Copy、Paste、**Paste and Match Style**、**Delete**、Select All。
   - Paste and Match Style：选择子 `pasteAsPlainText:`，快捷键 ⌥⇧⌘V；在输入框里等于粘贴，`onPaste` 也看得到。
   - Delete：选择子 `delete:`，没有快捷键；有选区时删掉选区（单独一个撤销步骤），没有选区时什么都不做。
   - 没有获得焦点的输入框时：Paste and Match Style 变成 ⌥⇧⌘V 交给快捷键；Delete 什么都不做。
   - `EditCommand` 增加 `PASTE_AND_MATCH_STYLE`、`DELETE`。
5. **`onPaste(Predicate<String> fn)`**（同 MyGO 的文档）：
   - Paste 和 Paste and Match Style（来自菜单或 ⌘V）插入之前调用，在事件到来时调用。**和 MyGO 不同**：MyGO 的实现等到下一帧处理输入时才调用。
   - 顺序：先提交组字；读剪贴板（没有文字时是 ""）；调用 `fn`；返回 `true` 表示应用接手，输入框什么都不变（文字、选区、撤销历史都不变）；返回 `false` 且文字非空时照常插入。
   - 只读的输入框不会调用它。
6. **`onTextKey(Predicate<TextKey> fn)`**（同 MyGO）：
   - `TextKey` 是 `jui.ui` 里的接口：`Key key()`、`int mods()`、`String text()`、`void setText(String s)`。
   - `fn` 只看到不打出文字的键：除了“字母、数字、标点、空格、`UNKNOWN`，并且只按着 Shift 和/或 Option”以外的键，例如带 ⌘ 或 Ctrl 的键，以及回车、Tab、Esc、Backspace、Delete、方向键、Home/End、PageUp/PageDown、F 键。
   - 时机：在 `Input.keyDown` 的最前面。条件：获得焦点的输入框设了 `fn`、未禁用（只读的也算）、没有在组字；并且输入框要用这个键（`TextEdit.wants`），或者没有快捷键认领它（`claimedBy`）。所以输入框外层的 Esc 快捷键先于 `fn`，而 ← 这类输入框要用的键先给 `fn`。
   - 返回 `true` 表示应用接手：这个键什么都不做（不插入、不提交、Tab 不移焦点），光标重新开始闪烁，请求一帧。返回 `false`：照常处理。
   - `text()` 读当前已提交的文字，包括这一帧之前刚打进去的字。`setText(s)` 设置文字（并入上一个撤销步骤，同 MyGO）、写回 `TextRef`、请求一帧。
   - `TextKey` 只在 `fn` 执行期间有效；之后再调用它的方法，开发模式抛异常，关闭时忽略。引擎只用一个可复用的对象，不按键分配。
7. **`setTextSelection(int start, int end)`、`textSelectionStart()`、`textSelectionEnd()`**：
   - 下标是 UTF-16；两端各自夹到 [0, 长度]，再向下对齐到字素簇边界。`start` 是锚点，`end` 是光标，所以 `start > end` 是反向选区。
   - 不移动焦点，不动撤销历史。正在组字时先提交组字，并通知输入法。**和 MyGO 不同**：MyGO 把光标移到组字下面。
   - 在构建中调用，这一帧就显示（细节第 1 条）；获得焦点时光标保持在视野里。
   - `textSelectionStart()`、`textSelectionEnd()` 是选区较小、较大的一端，没有输入框状态时是 0。
8. **`inputMethod(boolean on)`**（同 MyGO）：
   - 关闭时：按键照常先给输入框，然后不再交给 `interpretKeyEvents:`；按键的 `characters` 去掉控制字符（< 0x20）、0x7F 和 0xF700–0xF8FF 后，非空就作为文字插入。死键不打字，Option 组合出的字符照常打出。
   - 组字时关闭：提交组字，通知输入法 `discardMarkedText()`。
   - `MacWindow.Input` 增加 `default boolean textInputMethod() { return true; }`，`jui.app.Window` 转给 `Engine.textInputMethod()`。
   - `Tester.compose` 在输入法关闭时什么都不做（同 MyGO 的 headless）。

## Review Focus

测试没有直接覆盖、但最容易让使用者踩坑的 5 种情况：

1. **两帧之间选项变了**：在把输入框变成只读或密码框的那一帧之前打的字要保留；变了之后的输入按新选项处理。测试放在 Task 2。
2. **密码框里有 emoji、汉字和组字**：点击、拖动、光标矩形、输入法查询都要在掩码和真实文字之间换算对，不落在代理对中间。测试放在 Task 3。
3. **`onTextKey` 和快捷键、组字、输入框自己要的键之间的先后**：外层快捷键认领的 Esc 先给快捷键；组字时按键归输入法；输入框要的 ← 先给 `fn`。测试放在 Task 6。
4. **只读输入框在滚动容器里**：普通的 ↓ 和空格滚动容器；Tab 进入全选；菜单的 Paste 不漏给快捷键。测试放在 Task 2。
5. **组字时变成只读、禁用，或关闭输入法**：前两者丢掉组字，后者提交组字，都通知输入法。测试放在 Task 2 和 Task 7。

---

## 文件结构

| 文件 | 职责 | 任务 |
|---|---|---|
| `src/main/java/jui/ui/engine/Engine.java` | `textInput` 拆分、`settleInputs`、密码框的输入法查询、`textInputMethod` | 1–3、7 |
| `src/main/java/jui/ui/engine/TextEdit.java` | 选项字段、掩码、只读的键和命令、两条新命令 | 1–4 |
| `src/main/java/jui/ui/engine/Input.java` | `focusedEdit`/`activeEdit`、只读的按键和命令、`onPaste`、`onTextKey` | 2–7 |
| `src/main/java/jui/ui/engine/ElementState.java` | `ref`、`onPaste`、`onTextKey` | 1、5、6 |
| `src/main/java/jui/ui/{Element,TextKey,EditCommand,Tester}.java` | 公开 API | 1–7 |
| `src/main/java/jui/platform/mac/{MacWindow,MacApp}.java`、`jui/app/Window.java` | 两个菜单项、关闭输入法 | 4、7 |
| `src/main/java/jui/examples/FormDemo.java` | 示例 | 8 |

---

### Task 1：构建之后再定显示状态；设置和读取选区

**Files:**
- Modify: `Engine.java`（`textInput`、新的 `settleInputs`、`buildAndLayout` 里调用它）、`ElementState.java`（`ref`）、`TextEdit.java`、`Element.java`
- Test: `src/test/java/jui/ui/TextSelectionTest.java`

**Interfaces:**
- **Produces**：
  - `Engine.settleInputs()`：细节第 1 条；后面的任务在这里加选项。
  - `ElementState.ref`（`jui.ui.TextRef`）。
  - `Engine.setTextSelection(int index, int start, int end)`、`Engine.textSelection(int index, boolean end)`。
  - `Element setTextSelection(int start, int end)`、`int textSelectionStart()`、`int textSelectionEnd()`。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class TextSelectionTest {
    final TextRef q = new TextRef("hello world");
    final int[] ask = {-1, -1};                              // a selection to set in the next build, -1 for none
    final int[] seen = {-1, -1};

    Tester tester() {
        return Tester.of(c -> {
            Element in = textInput(c, q).label("in");
            if (ask[0] >= 0) {
                in.setTextSelection(ask[0], ask[1]);
                ask[0] = ask[1] = -1;
            }
            seen[0] = in.textSelectionStart();
            seen[1] = in.textSelectionEnd();
        }, 300, 200);
    }

    public void testASelectionSetInTheBuildShowsInThatFrame() {
        Tester tt = tester();
        tt.click("in");
        ask[0] = 1;
        ask[1] = 3;
        tt.frame();
        Check.equal("[1, 3]", java.util.Arrays.toString(tt.textSelection()));
        int fill = Theme.light().selection().rgba(), painted = 0;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] == 0 && Math.round(in[11] * 255) == (fill & 0xFF) && Math.round(in[8] * 255) == (fill >>> 24)) painted++;
        }
        Check.equal(1, painted);                            // its box, in the frame that set it
        tt.frame();
        Check.equal(1, seen[0]);
        Check.equal(3, seen[1]);
    }

    public void testEndsAreHeldInTheTextAndOnGraphemes() {
        q.set("a😀b");
        Tester tt = tester();
        tt.click("in");
        ask[0] = 0;
        ask[1] = 99;
        tt.frame();
        Check.equal("[0, 4]", java.util.Arrays.toString(tt.textSelection()));
        ask[0] = ask[1] = 2;                                 // inside the emoji's surrogate pair
        tt.frame();
        Check.equal("[1, 1]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testABackwardSelectionKeepsItsAnchor() {
        Tester tt = tester();
        tt.click("in");
        ask[0] = 5;
        ask[1] = 2;
        tt.frame();
        tt.key(Shortcut.of(Mod.SHIFT, Key.LEFT));           // the caret, at 2, moves; the anchor stays at 5
        Check.equal("[1, 5]", java.util.Arrays.toString(tt.textSelection()));
    }

    public void testItCommitsAComposition() {
        q.set("");
        Tester tt = tester();
        tt.click("in");
        tt.compose("ni", 2);
        ask[0] = ask[1] = 0;
        tt.frame();
        Check.equal("ni", q.get());
        Check.equal("[0, 0]", java.util.Arrays.toString(tt.textSelection()));
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test TextSelectionTest`。Expected：编译失败。
- [ ] **Step 3**：实现细节第 1、7 条。`setTextSelection` 用 `TextEdit.select(anchor, caret)`（它已经夹取并对齐）；正在组字时先 `unmark` 并 `host.discardMarkedText()`。`Paint.input` 读的仍是节点上的字段，不用改。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过（现有的输入框测试都不变）。
- [ ] **Step 5**：提交，信息 `feat(ui): set and read a text input's selection; inputs settle after the build`。

---

### Task 2：只读输入框

**Files:**
- Modify: `TextEdit.java`（`readOnly` 字段、`wantsReadOnly`、命令守卫）、`Input.java`（`focusedEdit`、`keyDown`、`editCommand`、`updateTextInput`）、`Engine.java`（`settleInputs`、`textActive` 走 `activeEdit`）、`Element.java`、`Tester.java`
- Test: `src/test/java/jui/ui/ReadOnlyTest.java`

**Interfaces:**
- **Consumes**：Task 1 的 `settleInputs`。
- **Produces**：
  - `Element readOnly(boolean on)`；`Engine.readOnly(int index, boolean on)`（设在节点上，`settleInputs` 写进 `TextEdit.readOnly`）。
  - `Input.focusedEdit()`：获得焦点、未禁用的输入框（含只读）；`activeEdit()` 再排除只读。
  - `Tester.textInputActive()`：`engine.textActive()`。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ReadOnlyTest {
    final TextRef q = new TextRef("hello");
    final boolean[] readOnly = {true};
    final int[] submits = {0};

    Tester tester() {
        return Tester.of(c -> {
            if (textInput(c, q).label("in").readOnly(readOnly[0]).submitted()) submits[0]++;
        }, 300, 200);
    }

    public void testItTakesNoTypingButSelectsAndCopies() {
        Tester tt = tester();
        tt.click("in");
        Check.isTrue(tt.focused("in"), "it takes the focus");
        tt.type("x");
        tt.key(Shortcut.of(Key.BACKSPACE));
        tt.setClipboard("pasted");
        tt.command(EditCommand.PASTE);
        tt.command(EditCommand.CUT);
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal("hello", q.get());
        Check.equal(0, submits[0]);
        tt.key(Shortcut.of(Key.END));                        // plain End is not taken: the caret stays
        tt.key(Shortcut.of(Mod.SHIFT, Key.HOME));
        tt.key(Shortcut.cmd(Key.C));
        Check.equal("hello", tt.clipboard());
        readOnly[0] = false;
        tt.frame();
        tt.key(Shortcut.of(Key.END));
        tt.type("!");
        Check.equal("hello!", q.get());
    }

    public void testItShowsNoCaretAndTakesNoTextInput() {
        Tester tt = tester();
        tt.click("in");
        Check.isTrue(!tt.textInputActive(), "the input method has nothing to type into");
        Check.equal(Long.MAX_VALUE, tt.engine().nextWake()); // no caret to blink
    }

    public void testTypingBeforeTheFrameThatTurnsItReadOnlyStays() { // Review Focus 1
        readOnly[0] = false;
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.engine().textInsert("!", -1, 0);                  // between frames, while it shows editable
        readOnly[0] = true;
        tt.frame();
        Check.equal("hello!", q.get());
        tt.type("?");
        Check.equal("hello!", q.get());
    }

    public void testTurningReadOnlyWhileComposingDropsTheComposition() { // Review Focus 5
        readOnly[0] = false;
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.compose("ni", 2);
        readOnly[0] = true;
        tt.frame();
        Check.equal("hello", q.get());
        Check.isTrue(tt.hasText("hello"), "shown without the composition");
    }

    public void testInAScrollContainerPlainKeysScrollAndTabSelectsAll() { // Review Focus 4
        ScrollState st = new ScrollState();
        boolean[] pasted = {false};
        Tester tt = Tester.of(c -> {
            if (c.shortcut(Shortcut.cmd(Key.V))) pasted[0] = true;
            scroll(c, st).size(300, 100).children(() -> {
                textInput(c, q).label("in").readOnly(true);
                box(c).height(1000);
            });
        }, 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("in"), "Tab reaches it");
        tt.key(Shortcut.cmd(Key.C));
        Check.equal("hello", tt.clipboard());               // Tab selected all
        tt.key(Shortcut.of(Key.DOWN));
        Check.near(40, st.y());                              // the container scrolled
        tt.command(EditCommand.PASTE);
        Check.isTrue(!pasted[0], "the menu's Paste does not go on to the shortcut");
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test ReadOnlyTest`。Expected：编译失败。
- [ ] **Step 3**：实现细节第 2 条。`TextEdit.wants` 是静态的；加一个 `wantsReadOnly(key, mods)`，`Input.keyDown` 对只读的输入框用它。`TextEdit.command` 对只读只允许 `COPY`、`SELECT_ALL`。`editCommand` 用 `focusedEdit()`，所以只读时命令不再转给快捷键。`updateTextInput` 里，输入框因为变成只读而不再接收文字时，丢掉组字（同禁用）。`settleInputs` 里只读的不显示光标、不请求闪烁帧。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): read-only text inputs`。

---

### Task 3：密码框

**Files:**
- Modify: `TextEdit.java`（`password` 字段、缓存的掩码、下标换算、Copy/Cut 守卫、忽略替换范围）、`Engine.java`（`settleInputs`、`inputGeometry`、`textLength`、`textIn`、`textSelectedRange`、`textMarkedRange`、`textRect`、`textCaretRect`、`textIndexAt`）、`Input.java`（按下和拖动时把掩码下标换回真实下标）、`Element.java`
- Test: `src/test/java/jui/ui/PasswordTest.java`

**Interfaces:**
- **Consumes**：Task 1 的 `settleInputs`、`textSelectionStart/End`。
- **Produces**：`Element password()`；`TextEdit.maskIndex(int real)`、`TextEdit.realIndex(int masked)`、`TextEdit.mask()`。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class PasswordTest {
    final TextRef q = new TextRef("");
    final boolean[] hide = {true};
    final int[] caret = {-1};

    Tester tester() {
        return Tester.of(c -> {
            Element in = textInput(c, q).label("pw");
            if (hide[0]) in.password();
            caret[0] = in.textSelectionEnd();
        }, 300, 200);
    }

    public void testItShowsABulletForEachCharacter() {
        Tester tt = tester();
        tt.click("pw");
        tt.type("a😀中");
        Check.equal("a😀中", q.get());
        Check.isTrue(tt.hasText("•••"), "one bullet a code point: the emoji's two UTF-16 units show one");
        hide[0] = false;
        tt.frame();
        Check.isTrue(tt.hasText("a😀中"), "shown again once password() is not called");
    }

    public void testCopyAndCutDoNothingButPasteWorks() {
        q.set("secret");
        Tester tt = tester();
        tt.click("pw");
        tt.key(Shortcut.cmd(Key.A));
        tt.setClipboard("before");
        tt.command(EditCommand.COPY);
        tt.command(EditCommand.CUT);
        Check.equal("before", tt.clipboard());
        Check.equal("secret", q.get());
        tt.command(EditCommand.PASTE);
        Check.equal("before", q.get());
    }

    public void testTheInputMethodSeesOnlyTheComposition() { // Review Focus 2
        q.set("secret");
        Tester tt = tester();
        tt.click("pw");
        Check.equal(0, tt.engine().textLength());
        tt.compose("ni", 2);
        Check.equal(2, tt.engine().textLength());
        Check.equal("ni", tt.engine().textIn(0, 2));
        int[] r = new int[2];
        Check.isTrue(tt.engine().textMarkedRange(r), "marked");
        Check.equal("[0, 2]", java.util.Arrays.toString(r));
        Rect box = tt.find("pw");
        Check.equal(-1, tt.engine().textIndexAt(box.x() + 20, box.y() + box.height() / 2));
        tt.compose("", 0);
        Check.equal("secretni", q.get());
    }

    public void testAClickBetweenBulletsLandsBetweenCharacters() { // Review Focus 2
        q.set("a😀b");
        Tester tt = tester();
        tt.click("pw");
        tt.key(Shortcut.of(Key.HOME));
        tt.key(Shortcut.of(Key.RIGHT));
        tt.key(Shortcut.of(Key.RIGHT));                       // after the emoji: real index 3, the second bullet's end
        tt.frame();
        Check.equal(3, caret[0]);
        Rect at = tt.textCaret();
        tt.key(Shortcut.of(Key.HOME));
        tt.press(at.x(), at.y() + at.height() / 2);
        tt.release(at.x(), at.y() + at.height() / 2);
        tt.frame();
        Check.equal(3, caret[0]);
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test PasswordTest`。Expected：编译失败。
- [ ] **Step 3**：实现细节第 3 条。掩码缓存在 `TextEdit`：记下上次换算时的显示文字，没变就复用，满足分配门槛。`Tester.textSelection()` 走输入法的查询，对密码框读到的是组字的选区，所以这里的测试用 `textSelectionEnd()`。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): password inputs`。

---

### Task 4：Edit 菜单的 Paste and Match Style 和 Delete

**Files:**
- Modify: `EditCommand.java`、`TextEdit.java`（`command`）、`Input.java`（`editCommand`、`runCommand`）、`MacWindow.java`（视图类的 `pasteAsPlainText:`、`delete:`）、`MacApp.java`（`installMenu`、`addItem` 增加修饰键参数）
- Test: `src/test/java/jui/ui/EditMenuCommandsTest.java`；修改 `src/test/java/jui/platform/mac/MacWindowTextTest.java`

**Interfaces:**
- **Produces**：`EditCommand.PASTE_AND_MATCH_STYLE`、`EditCommand.DELETE`。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class EditMenuCommandsTest {
    final TextRef q = new TextRef("hello");

    public void testPasteAndMatchStylePastes() {
        Tester tt = Tester.of(c -> textInput(c, q).label("in"), 300, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.setClipboard(" world");
        tt.command(EditCommand.PASTE_AND_MATCH_STYLE);
        Check.equal("hello world", q.get());
    }

    public void testDeleteRemovesTheSelectionOnly() {
        Tester tt = Tester.of(c -> textInput(c, q).label("in"), 300, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.command(EditCommand.DELETE);                      // nothing selected: nothing goes
        Check.equal("hello", q.get());
        tt.key(Shortcut.of(Mod.SHIFT, Key.LEFT));
        tt.key(Shortcut.of(Mod.SHIFT, Key.LEFT));
        tt.command(EditCommand.DELETE);
        Check.equal("hel", q.get());
        tt.key(Shortcut.cmd(Key.Z));
        Check.equal("hello", q.get());                       // one undo step
    }

    public void testWithoutAnInputPasteAndMatchStyleGoesToShortcutsAsItsKeys() {
        int[] fired = {0};
        Tester tt = Tester.of(c -> {
            if (c.shortcut(Shortcut.of(Mod.CMD | Mod.ALT | Mod.SHIFT, Key.V))) fired[0]++;
            text(c, "no input");
        }, 300, 200);
        tt.command(EditCommand.PASTE_AND_MATCH_STYLE);
        tt.command(EditCommand.DELETE);
        Check.equal(1, fired[0]);
    }
}
```

`MacWindowTextTest` 的两个测试改为：
- `testTheEditMenuHasTheStandardItems`：前 9 项依次是 Undo、Redo、分隔线、Cut、Copy、Paste、Paste and Match Style、Delete、Select All。Paste and Match Style 的选择子是 `pasteAsPlainText:`，`keyEquivalent` 是 `"v"`，修饰键掩码是 ⌘⌥⇧（`1<<20 | 1<<19 | 1<<17`）；Delete 的选择子是 `delete:`，`keyEquivalent` 是 `""`。
- `testMenuActionsBecomeEditCommands`：视图收到 `pasteAsPlainText:`、`delete:` 时，依次变成 `PASTE_AND_MATCH_STYLE`、`DELETE`。

先确认 `Shortcut.of(int mods, Key key)` 存在；不存在就用已有的写法组合修饰键。

- [ ] **Step 2**：运行 `./build.sh test EditMenuCommandsTest` 和 `./build.sh test MacWindowTextTest`。Expected：编译失败或断言失败。
- [ ] **Step 3**：实现细节第 4 条。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): Paste and Match Style and Delete in the Edit menu`。

---

### Task 5：`onPaste`

**Files:**
- Modify: `ElementState.java`（`onPaste`）、`Engine.java`（`textInput` 清掉它，`onPaste(index, fn)`）、`Input.java`（`runCommand`）、`Element.java`
- Test: `src/test/java/jui/ui/OnPasteTest.java`

**Interfaces:**
- **Consumes**：Task 4 的 `PASTE_AND_MATCH_STYLE`。
- **Produces**：`Element onPaste(java.util.function.Predicate<String> fn)`。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import java.util.ArrayList;
import java.util.List;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class OnPasteTest {
    final TextRef q = new TextRef("ab");
    final List<String> seen = new ArrayList<>();
    final boolean[] take = {false};
    final boolean[] readOnly = {false};

    Tester tester() {
        return Tester.of(c -> textInput(c, q).label("in").readOnly(readOnly[0]).onPaste(s -> {
            seen.add(s);
            return take[0];
        }), 300, 200);
    }

    public void testAPasteNotTakenInserts() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        tt.setClipboard("cd");
        tt.key(Shortcut.cmd(Key.V));
        Check.equal("abcd", q.get());
        Check.equal(List.of("cd"), seen);
    }

    public void testAPasteTakenChangesNothing() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.cmd(Key.A));
        take[0] = true;
        tt.setClipboard("cd");
        tt.key(Shortcut.cmd(Key.V));
        tt.command(EditCommand.PASTE);
        tt.command(EditCommand.PASTE_AND_MATCH_STYLE);
        Check.equal("ab", q.get());
        Check.equal(List.of("cd", "cd", "cd"), seen);
        tt.command(EditCommand.DELETE);                      // the selection stayed
        Check.equal("", q.get());
        tt.key(Shortcut.cmd(Key.Z));
        Check.equal("ab", q.get());                          // and no step of its own
    }

    public void testAnEmptyClipboardIsSeenAsEmpty() {
        Tester tt = tester();
        tt.click("in");
        tt.setClipboard(null);
        tt.command(EditCommand.PASTE);
        Check.equal(List.of(""), seen);
        Check.equal("ab", q.get());
    }

    public void testAReadOnlyInputNeverAsks() {
        readOnly[0] = true;
        Tester tt = tester();
        tt.click("in");
        tt.setClipboard("cd");
        tt.command(EditCommand.PASTE);
        Check.equal(List.of(), seen);
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test OnPasteTest`。Expected：编译失败。
- [ ] **Step 3**：实现细节第 5 条。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): onPaste sees a paste before it goes in`。

---

### Task 6：`onTextKey` 和 `TextKey`

**Files:**
- Create: `src/main/java/jui/ui/TextKey.java`
- Modify: `ElementState.java`（`onTextKey`）、`Engine.java`（`textInput` 清掉它，`onTextKey(index, fn)`）、`Input.java`（`keyDown` 最前面；可复用的 `TextKey` 实现；`typesText`）、`Element.java`
- Test: `src/test/java/jui/ui/OnTextKeyTest.java`

**Interfaces:**
- **Consumes**：Task 2 的 `focusedEdit()`；Task 1 的 `ElementState.ref`。
- **Produces**：`interface TextKey { Key key(); int mods(); String text(); void setText(String s); }`；`Element onTextKey(java.util.function.Predicate<TextKey> fn)`；`Input.typesText(Key key, int mods)`。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import java.util.ArrayList;
import java.util.List;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class OnTextKeyTest {
    final TextRef q = new TextRef("");
    final List<String> seen = new ArrayList<>(), sent = new ArrayList<>();
    final int[] submits = {0}, escapes = {0};
    final TextKey[] kept = {null};

    Tester tester() {
        return Tester.of(c -> {
            if (c.shortcut(Shortcut.of(Key.ESCAPE))) escapes[0]++;
            column(c).children(() -> {
                Element in = textInput(c, q).label("in").onTextKey(k -> {
                    seen.add(k.key() + "/" + k.mods());
                    kept[0] = k;
                    if (k.key() == Key.ENTER && k.mods() == 0) {
                        sent.add(k.text());
                        k.setText("");
                        return true;
                    }
                    return k.key() == Key.TAB && k.mods() == 0;
                });
                if (in.submitted()) submits[0]++;
                button(c, "next");
            });
        }, 300, 200);
    }

    public void testEnterTakenSendsAndClearsWithoutSubmitting() {
        Tester tt = tester();
        tt.click("in");
        tt.type("hi");
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(List.of("hi"), sent);
        Check.equal("", q.get());
        Check.equal(0, submits[0]);
    }

    public void testItSeesWhatWasTypedJustBeforeTheKey() {
        Tester tt = tester();
        tt.click("in");
        tt.engine().textInsert("ab", -1, 0);                 // no frame between
        tt.engine().keyDown(Key.ENTER, 0, false);
        Check.equal(List.of("ab"), sent);
    }

    public void testKeysThatTypeTextNeverReachIt() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.A));
        tt.key(Shortcut.of(Mod.SHIFT, Key.DIGIT1));
        tt.key(Shortcut.of(Key.SPACE));
        tt.key(Shortcut.of(Mod.ALT, Key.E));
        Check.equal(List.of(), seen);
        tt.key(Shortcut.of(Mod.SHIFT, Key.ENTER));
        tt.key(Shortcut.cmd(Key.ENTER));
        Check.equal(List.of("ENTER/" + Mod.SHIFT, "ENTER/" + Mod.CMD), seen);
    }

    public void testTabTakenKeepsTheFocus() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("in"), "taken: the focus stays");
        tt.key(Shortcut.of(Mod.SHIFT, Key.TAB));
        Check.isTrue(!tt.focused("in"), "left: Shift+Tab moves it");
    }

    public void testAShortcutAroundGoesFirstUnlessTheInputEditsWithTheKey() { // Review Focus 3
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.ESCAPE));
        Check.equal(1, escapes[0]);
        Check.equal(List.of(), seen);                         // the window's Escape took it
        tt.key(Shortcut.of(Key.LEFT));
        Check.equal(List.of("LEFT/0"), seen);                 // the input edits with ←: it comes here first
    }

    public void testWhileComposingKeysAreTheInputMethods() { // Review Focus 3
        Tester tt = tester();
        tt.click("in");
        tt.compose("ni", 2);
        tt.key(Shortcut.of(Key.ENTER));
        Check.equal(List.of(), seen);
    }

    public void testAKeptTextKeyFails() {
        Tester tt = tester();
        tt.click("in");
        tt.key(Shortcut.of(Key.LEFT));
        Check.fails(IllegalStateException.class, () -> kept[0].text());
    }
}
```

- [ ] **Step 2**：运行 `./build.sh test OnTextKeyTest`。Expected：编译失败。
- [ ] **Step 3**：实现细节第 6 条。`typesText`：`A`–`Z`、`DIGIT0`–`DIGIT9`、`SPACE`、`MINUS` 到 `BACKQUOTE`、`UNKNOWN`，并且修饰键只有 Shift 和/或 Option。`setText` 用 `TextEdit.setText(s, true)`（并入上一个撤销步骤），写回 `ElementState.ref`，更新 `synced`。`Tester` 走 `engine.keyDown`，所以测试里 Tester 的 `key` 和真实窗口一样经过这里；真实窗口里 ⌘V 这类键先被菜单的快捷键拿走，到不了 `keyDown`，这和 MyGO 一样。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): onTextKey sees a key before the input takes it`。

---

### Task 7：关闭输入法

**Files:**
- Modify: `TextEdit.java`（`inputMethodOff`）、`Engine.java`（`settleInputs`、`textInputMethod()`）、`MacWindow.java`（`Input.textInputMethod`、`key`、`typed`）、`jui/app/Window.java`、`Tester.java`（`compose` 在关闭时什么都不做）、`Element.java`
- Test: `src/test/java/jui/ui/InputMethodTest.java`；在 `src/test/java/jui/platform/mac/MacWindowTextTest.java` 加一个测试

**Interfaces:**
- **Produces**：`Element inputMethod(boolean on)`；`Engine.textInputMethod()`；`MacWindow.Input.textInputMethod()`（默认 `true`）；`static String MacWindow.typed(String characters)`（去掉控制字符后的结果）。

- [ ] **Step 1：写失败的测试**

```java
package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class InputMethodTest {
    final TextRef q = new TextRef("");
    final boolean[] on = {false};

    Tester tester() {
        return Tester.of(c -> textInput(c, q).label("in").inputMethod(on[0]), 300, 200);
    }

    public void testOffItComposesNothingAndKeysType() {
        Tester tt = tester();
        tt.click("in");
        Check.isTrue(tt.textInputActive() && !tt.engine().textInputMethod(), "takes text, without the input method");
        tt.compose("ni", 2);
        Check.equal("", q.get());
        tt.type("j");
        Check.equal("j", q.get());
        on[0] = true;
        tt.frame();
        tt.compose("ni", 2);
        tt.compose("", 0);
        Check.equal("jni", q.get());
    }

    public void testTurningItOffWhileComposingCommits() { // Review Focus 5
        on[0] = true;
        Tester tt = tester();
        tt.click("in");
        tt.compose("ni", 2);
        on[0] = false;
        tt.frame();
        Check.equal("ni", q.get());
    }
}
```

`MacWindowTextTest` 加 `testWithTheInputMethodOffAKeyTypesItsCharacters`：用现有的合成按键事件和记录用的 `Input`（它的 `textInputMethod()` 返回 `false`）：
- 按 `j`（keyCode 38，characters `"j"`）：先记下按键，再记下插入 `"j"`；`interpretHook` 没有被调用；
- 按回车（keyCode 36，characters `"\r"`）：只记下按键，没有插入；
- `MacWindow.typed("\u001b")`、`typed("")` 都是 `""`，`typed("é")` 是 `"é"`。

- [ ] **Step 2**：运行 `./build.sh test InputMethodTest` 和 `./build.sh test MacWindowTextTest`。Expected：编译失败。
- [ ] **Step 3**：实现细节第 8 条。
- [ ] **Step 4**：运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 5**：提交，信息 `feat(ui): an input can turn the input method off`。

---

### Task 8：示例、门槛和文档

**Files:**
- Create: `src/main/java/jui/examples/FormDemo.java`、`src/test/java/jui/examples/FormDemoTest.java`
- Modify: `src/main/java/jui/spike/AllocSpike.java`（`inputs_frame` 场景里一半的输入框加 `password()`，四分之一加 `readOnly(true)`）、`README.md`、`build.sh`（帮助里的示例名）

**Interfaces:**
- **Produces**：**`FormDemo`**（`./build.sh demo Form`），一个窗口：
  - “账号 ID”：只读输入框，可以选中拷贝；
  - “密码”：密码框，旁边的开关“显示密码”切换是否调用 `password()`；
  - “消息”：`onTextKey` 让回车发送、清空输入框，发送的消息列在下面；`onPaste` 拒绝超过 200 个字符的粘贴，并显示一行提示；
  - “命令”：`inputMethod(false)` 的输入框，提示“这里打字不经过输入法”。
- **`FormDemoTest`**：在“消息”里打字并回车，消息出现在列表里、输入框清空；打开“显示密码”，密码以明文显示。
- **README 一节**：做了什么、和 MyGO 不同的地方、测试数目、`./build.sh alloc` 的结果、示例截图 `docs/form-demo.png`、需要手动核对的项目：
  - 只读输入框：能选中、⌘C，菜单的粘贴无效；
  - 密码框：圆点，拼音组字，⌘C 无效；
  - 消息框：回车发送，Shift+回车照常；
  - 关闭输入法的输入框：中文输入法下直接打出字母；
  - Edit 菜单的 Paste and Match Style（⌥⇧⌘V）和 Delete。

- [ ] **Step 1**：写 `FormDemoTest`，运行 `./build.sh test FormDemoTest`。Expected：编译失败。
- [ ] **Step 2**：写 `FormDemo`。运行 `./build.sh test`。Expected：全部通过。
- [ ] **Step 3**：运行 `./build.sh alloc`、`./build.sh listbench`、`./build.sh ffi-check`。Expected：都通过，`inputs_frame` 仍是 0。
- [ ] **Step 4**：`./build.sh native-demo Form --print-window` 截图存成 `docs/form-demo.png`，打开检查：密码框是圆点，只读框没有光标。
- [ ] **Step 5**：写 README，运行 `./build.sh test`，提交，信息 `feat(examples): a form demo; docs: text input options`。

---

## 完成之后

- 整体审查一次（superpowers:executing-plans 的 Final Review），对照本计划的细节和 Review Focus。
- 合并方式由用户决定。

## 后续计划（各自单独写、单独审查）

M4 留下的其余几项，按依赖顺序：
1. **多行文本框 `textArea`**：`TextLayout` 的行表、光标的亲和性（软换行处在上一行末尾还是下一行开头）、按行上下移动、框内滚动和只在需要时滚进视野、多行的选区和组字。依赖本计划的 `settleInputs` 和选项。
2. **右键菜单**：原生 `NSMenu` 的 `contextMenu`（把 M5 的这一项提前做），输入框的默认菜单（剪切、拷贝、粘贴、全选，只读和密码框各自减项），Shift+F10 和菜单键；顺带修 Ctrl+单击的标记在菜单吞掉松开后不清除的问题。
3. **编辑器的底层能力**：元素接管文本输入（`handleTextInput` 和公开的 `TextInputClient`，加上接收原始按键的 `handleInput`），多种样式的富文本 `richText`（区段的字体、颜色、下划线、背景，按区段换字体排版，逐字形上色）。这一项会定下以后编辑器依赖的接口。
