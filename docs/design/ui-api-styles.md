# 即时模式 API：四种写法对比

用来决定 j-ui 的 API 采用哪种写法。文中的 Java 代码都是**设计草图**，对应的 API 还没有实现。

为了公平比较，四种写法实现的是同一个 Obsidian 式的界面片段：

- 左边栏：一个搜索框，下面是按搜索词过滤的笔记列表，点击某篇笔记就打开它，当前笔记高亮显示；栏底有一个“新建笔记”按钮。
- 右边内容区：上方是笔记标题和“预览”勾选框，下方是正文。
- 笔记列表的每一行抽成一个自定义控件 `noteRow`，用来观察“自己写的控件”和“内置控件”的写法是否一致。

背景目标：启动快、内存占用低（见上一轮讨论）。

---

## 0. 先看 MyGO 原版（Go）

MyGO 的风格可以概括为**“即时模式的写法，内部保留元素树”**：

| 要素 | MyGO 的做法 |
|---|---|
| 视图 | 一个以应用状态为输入的函数，每帧调用；一帧内可能调用多次（处理事件时改了状态，就立刻重建，最多 3 次） |
| 控件 | 普通函数，第一个参数总是显式的 `*ui.Context`：`ui.Button(c, "保存")` |
| 样式 | 控件返回一个元素（`ui.Element`），在它上面链式设置样式：`.Padding(12).Radius(8)` |
| 子元素 | 用闭包传入：`.Children(func() { ... })`，闭包里继续调用控件函数，自动挂到这个父元素下 |
| 事件 | 两种都行：在构建处直接查询 `if btn.Clicked() { ... }`；或注册回调 `.OnClick(fn)`，在本次构建结束后执行 |
| 数据绑定 | 传指针：`ui.Checkbox(c, &app.done, "完成")`，构造函数返回前就把用户的修改写回 |
| 身份 | 由“父元素 + 在兄弟中的位置”决定；循环里的元素用 `c.Key(id)` 指定 key |
| 控件自己的状态 | `ui.Local(element, "名字", 初始值)`，按元素身份跨帧保存 |
| 内部实现 | 每帧在内存池里重建元素树，然后做 flexbox 布局（有缓存）、命中测试、绘制 |

它的设计来源：
- 构建处直接查询事件和指针绑定，来自 Dear ImGui / egui；
- 布局属性的命名照搬 CSS flexbox；
- 链式样式接近 SwiftUI 的修饰符。

同一个界面片段用 MyGO 写是这样：

```go
func (app *notesApp) view(c *ui.Context) {
	t := c.Theme()
	ui.Row(c).Fill().Children(func() {
		ui.Column(c).Width(260).Padding(12).Gap(8).Background(t.Surface).Children(func() {
			ui.TextInput(c, &app.query).Placeholder("搜索笔记")
			for _, n := range app.filtered() {
				if noteRow(c.Key(n.ID), n, n == app.selected).Clicked() {
					app.selected = n
				}
			}
			ui.Spacer(c)
			if ui.PrimaryButton(c, "新建笔记").Clicked() {
				app.createNote()
			}
		})
		ui.Column(c).Grow(1).Padding(24).Gap(12).Children(func() {
			ui.Row(c).Gap(12).AlignItems(ui.Center).Children(func() {
				ui.Text(c, app.title()).FontSize(22).Bold().Grow(1)
				ui.Checkbox(c, &app.preview, "预览")
			})
			ui.Text(c, app.body()).TextColor(t.TextMuted)
		})
	})
}

// 自定义控件：就是一个普通函数，和 ui.Button 写法一样
func noteRow(c *ui.Context, n *Note, active bool) ui.Element {
	t := c.Theme()
	row := ui.Row(c).Padding(6, 10).Radius(6)
	if active {
		row.Background(t.Accent).TextColor(t.AccentText)
	} else if row.Hovered() {
		row.Background(t.SurfaceHover)
	}
	row.Children(func() { ui.Text(c, n.Title).SingleLine() })
	return row
}
```

**Java 没有的东西**（四种写法都要面对）：
- **没有指针**：不能写 `&app.query`，要换一种绑定方式，见第 5 节。
- **没有值类型**：元素只能是对象，要靠对象池或逃逸分析来避免每帧产生垃圾。
- **lambda 只能捕获不再修改的局部变量**：增强型 `for` 的循环变量没问题，下标循环要先复制一份。
- **没有扩展方法**：第三方不能给框架里的类添加方法，这一点直接影响写法 B。

---

## 1. 写法 A：MyGO 风格（显式上下文 + 静态函数）

**定义**
- 控件是 `Ui` 类里的静态函数，静态导入后直接调用，第一个参数总是显式的 `Context c`。
- 控件返回 `Element`，可以链式设置样式，也可以查询事件。
- 子元素放在 `children(() -> { ... })` 里。
- 状态放在应用对象的字段里，用引用对象（`TextRef`、`BoolRef`）绑定。
- 和 MyGO 一一对应。

```java
import static jui.ui.Ui.*;

final class NotesApp {
    final List<Note> notes = new ArrayList<>();
    final TextRef query = new TextRef("");       // 搜索框的内容
    final BoolRef preview = new BoolRef(false);  // 预览模式
    Note selected;                               // 当前打开的笔记

    void view(Context c) {
        Theme t = c.theme();
        row(c).fill().children(() -> {
            column(c).width(260).padding(12).gap(8).background(t.surface()).children(() -> {
                textInput(c, query).placeholder("搜索笔记");
                for (Note n : filtered()) {
                    if (noteRow(c.key(n.id()), n, n == selected).clicked()) {
                        selected = n;
                    }
                }
                spacer(c);
                if (primaryButton(c, "新建笔记").clicked()) {
                    createNote();
                }
            });
            column(c).grow(1).padding(24).gap(12).children(() -> {
                row(c).gap(12).alignItems(Align.CENTER).children(() -> {
                    text(c, title()).fontSize(22).bold().grow(1);
                    checkbox(c, preview, "预览");
                });
                text(c, body()).textColor(t.textMuted());
            });
        });
    }

    // 自定义控件：一个静态函数，和内置的 button、checkbox 写法完全一样
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
}
```

**优点**
- **和 MyGO 一一对应**：MyGO 的文档、控件设计和踩过的坑都能直接照搬，移植成本最低。
- **自定义控件和内置控件写法完全一致**，都是“传入 `c`、返回 `Element` 的静态函数”。Obsidian 式的应用以后要做插件，插件作者写的控件和框架自带的控件看不出区别。
- **上下文是显式的**：每个控件属于哪个窗口一目了然。多窗口（Obsidian 可以把笔记弹出成独立窗口）、后台线程、测试（不开窗口直接传一个测试用的 `Context`）都不容易出错。
- **事件可以就地处理**：`if (...clicked())` 写在控件旁边，代码和界面结构一一对应；需要时也可以用回调。
- **内存友好**：元素从每个窗口复用的池里取，子元素的 lambda 被调用一次就丢弃，没有额外的树对象。

**缺点**
- **`c` 到处都是**，每个控件调用都多一个参数，视觉上有些啰嗦。
- **IDE 补全要靠静态导入**：输入 `Ui.` 才能看到全部控件；静态导入之后，补全列表里会混进其他静态方法。
- **样式、事件、子元素都在一条链上**，元素一复杂，链会很长。
- **需要引用对象来绑定状态**：应用状态的字段要写成 `TextRef`，而不是普通的 `String`。

---

## 2. 写法 B：作用域对象风格（egui 风格）

**定义**
- 控件是 `Ui` 对象上的方法：`ui.button("保存")`。
- 容器的 lambda 会收到一个**新的、只属于这个容器的 `Ui`**，在它上面调用的控件就挂在这个容器下。这和 Rust egui 的 `ui.horizontal(|ui| { ... })` 一样。
- 没有静态导入，也没有单独的 `c` 参数。

```java
final class NotesApp {
    final TextRef query = new TextRef("");
    final BoolRef preview = new BoolRef(false);
    Note selected;

    void view(Ui ui) {
        Theme t = ui.theme();
        ui.row().fill().show(main -> {
            main.column().width(260).padding(12).gap(8).background(t.surface()).show(side -> {
                side.textInput(query).placeholder("搜索笔记");
                for (Note n : filtered()) {
                    if (NoteRow.show(side.key(n.id()), n, n == selected).clicked()) {
                        selected = n;
                    }
                }
                side.spacer();
                if (side.primaryButton("新建笔记").clicked()) {
                    createNote();
                }
            });
            main.column().grow(1).padding(24).gap(12).show(content -> {
                content.row().gap(12).alignItems(Align.CENTER).show(bar -> {
                    bar.text(title()).fontSize(22).bold().grow(1);
                    bar.checkbox(preview, "预览");
                });
                content.text(body()).textColor(t.textMuted());
            });
        });
    }
}

// 自定义控件：不能写成 ui.noteRow(...)，只能放在另一个类里当静态函数，
// 调用写法和 ui.button(...) 不一样
final class NoteRow {
    static Element show(Ui ui, Note n, boolean active) {
        Theme t = ui.theme();
        Element row = ui.row().padding(6, 10).radius(6);
        if (active) {
            row.background(t.accent()).textColor(t.accentText());
        } else if (row.hovered()) {
            row.background(t.surfaceHover());
        }
        row.show(r -> r.text(n.title()).singleLine());
        return row;
    }
}
```

**优点**
- **IDE 补全最好**：输入 `ui.` 就列出所有内置控件，最容易上手，也最符合一般 Java 开发者的习惯。
- **作用域明确**：每层容器有自己的 `Ui` 变量，元素不会挂错父元素；嵌套结构从参数名就能看出来。
- **没有 `c` 参数**，单个控件调用看起来更干净。

**缺点**
- **自定义控件和内置控件写法不一致**：Java 没有扩展方法，第三方控件只能写成 `NoteRow.show(ui, ...)`，而内置的是 `ui.button(...)`。插件生态里会一直存在两种写法。
- **每层容器都要给 lambda 参数起名**（`main`、`side`、`content`、`bar`），嵌套一深就很啰嗦，也容易误用外层的变量。
- **`Ui` 类会非常大**：几十上百个控件方法都挤在一个类里，或者要拆成很多接口再组合。
- **和 MyGO 差别较大**：参照 MyGO 的设计时，每一处都要转换一遍。

---

## 3. 写法 C：隐式上下文风格（Dear ImGui 风格）

**定义**
- 控件是静态函数，但**不传上下文**：框架在每帧构建时把“当前窗口、当前父元素”放在一个线程级的全局变量里，控件函数直接读取。
- 这和 C++ Dear ImGui 的 `ImGui::Button("保存")` 一样，Jetpack Compose 也是靠隐式上下文。
- 循环里的 key 用 `keyed(id, () -> { ... })` 包一层。

```java
import static jui.ui.Ui.*;

final class NotesApp {
    final TextRef query = new TextRef("");
    final BoolRef preview = new BoolRef(false);
    Note selected;

    void view() {
        Theme t = theme();
        row().fill().children(() -> {
            column().width(260).padding(12).gap(8).background(t.surface()).children(() -> {
                textInput(query).placeholder("搜索笔记");
                for (Note n : filtered()) {
                    keyed(n.id(), () -> {
                        if (noteRow(n, n == selected).clicked()) {
                            selected = n;
                        }
                    });
                }
                spacer();
                if (primaryButton("新建笔记").clicked()) {
                    createNote();
                }
            });
            column().grow(1).padding(24).gap(12).children(() -> {
                row().gap(12).alignItems(Align.CENTER).children(() -> {
                    text(title()).fontSize(22).bold().grow(1);
                    checkbox(preview, "预览");
                });
                text(body()).textColor(t.textMuted());
            });
        });
    }

    // 自定义控件：静态函数，和内置控件写法一致
    static Element noteRow(Note n, boolean active) {
        Theme t = theme();
        Element row = row().padding(6, 10).radius(6);
        if (active) {
            row.background(t.accent()).textColor(t.accentText());
        } else if (row.hovered()) {
            row.background(t.surfaceHover());
        }
        row.children(() -> text(n.title()).singleLine());
        return row;
    }
}
```

**优点**
- **代码最简洁**：没有 `c`，也不用给 lambda 参数起名，读起来最接近界面结构本身。
- **自定义控件和内置控件写法一致**，都是静态函数。
- **熟悉 Dear ImGui 或 Compose 的人一看就懂。**

**缺点**
- **依赖隐藏的全局状态**：控件函数只能在某个窗口构建视图期间调用。如果在后台线程、回调里或构建结束后误调用，编译器发现不了，只会在运行时出错。
- **多窗口容易出错**：哪个窗口是“当前窗口”是隐含的，比如在一个窗口的回调里想更新另一个窗口，就要格外小心。
- **测试要先设置全局状态**，没有显式传参那么直接。
- **key 要多包一层 lambda**，或者靠“对下一个元素生效”这种隐含规则，容易用错。

---

## 4. 写法 D：声明式元素树风格（Compose / SwiftUI / GPUI 风格）

**定义**
- 视图函数**返回**一棵元素树：控件函数只是构造节点，子元素作为参数传入。
- 事件只能通过回调：`.onClick(() -> ...)`。
- 每帧重新构建整棵树，再做布局和绘制。严格说这不算即时模式，更接近 GPUI 的元素树和 React 的写法。

```java
import static jui.ui.Dsl.*;

final class NotesApp {
    String query = "";
    boolean preview;
    Note selected;

    Element view(Theme t) {
        return row(
            column(
                textInput(query, q -> query = q).placeholder("搜索笔记"),
                each(filtered(), n -> noteRow(t, n, n == selected)
                        .key(n.id())
                        .onClick(() -> selected = n)),
                spacer(),
                primaryButton("新建笔记").onClick(this::createNote)
            ).width(260).padding(12).gap(8).background(t.surface()),
            column(
                row(
                    text(title()).fontSize(22).bold().grow(1),
                    checkbox(preview, v -> preview = v, "预览")
                ).gap(12).alignItems(Align.CENTER),
                text(body()).textColor(t.textMuted())
            ).grow(1).padding(24).gap(12)
        ).fill();
    }

    // 自定义控件：返回一个节点；悬停效果要通过框架提供的“悬停时的样式”声明，
    // 因为构建时不能直接问“现在是否悬停”
    static Element noteRow(Theme t, Note n, boolean active) {
        return row(text(n.title()).singleLine())
                .padding(6, 10).radius(6)
                .background(active ? t.accent() : null)
                .textColor(active ? t.accentText() : null)
                .hoverBackground(active ? null : t.surfaceHover());
    }
}
```

**优点**
- **熟悉 React、Flutter、SwiftUI、Compose 的人最容易接受**，视图就是一个返回值，组合和测试都很自然。
- **没有隐藏状态，也没有 `c`**：节点是纯数据，可以存起来、比较、序列化。
- **状态可以是普通字段**，不需要引用对象，靠 `onChange` 回调写回。

**缺点**
- **在 Java 里每帧必然产生垃圾**：子节点要存进父节点，回调要等构建结束后才执行，这些对象都会“逃逸”出创建它的地方，必须真正在堆上分配，帧结束后再由 GC 回收。
  - 节点对象可以放进每帧复用的池里，可变参数数组可以用重载避免；
  - 但被保存下来的回调 lambda（比如捕获了 `n` 的 `() -> selected = n`）每帧都是新对象，无法复用。
  - GPUI 用同样的写法却没有这个问题，原因见第 8 节。
- **失去即时模式的核心写法**：不能在构建时 `if (btn.clicked())` 就地处理事件，悬停和按下效果都要靠额外的声明式属性（比如上面的 `hoverBackground`）。
- **条件和循环写起来别扭**：要借助 `each(...)`、`when(...)`，或者在参数列表里写三元表达式。
- **括号嵌套深，样式离元素的开头很远**：读代码时要先跳到末尾才能看到这个容器的宽度和间距。
- **和 MyGO 的思路完全不同**，没法照着 MyGO 的实现来做。

---

## 5. 另一个维度：状态怎么绑定

Java 没有指针，`ui.Checkbox(c, &app.done, ...)` 这种写法要换一种实现。A、B、C 三种写法都要在下面几种方式里选；D 天然是第 3 种。

| 方式 | 写法 | 优点 | 缺点 |
|---|---|---|---|
| 1. 引用对象 | `final BoolRef done = new BoolRef(false);` `checkbox(c, done, "完成")` | 最接近 MyGO 的指针；控件返回前就写回，下一行代码读到的就是新值；`.changed()` 的语义清楚；每帧不产生垃圾 | 状态字段要写成 `BoolRef`、`TextRef`，不能是普通的 `boolean`；record 类型里放起来不自然 |
| 2. 读写方法 | `checkbox(c, s::showHidden, s::setShowHidden, "显示隐藏文件")` | 状态可以是普通字段或 JavaBean | 每个绑定要写两个方法引用，比较啰嗦；方法引用每帧可能产生小对象 |
| 3. 传值返回新值 | `preview = checkbox(c, preview, "预览").value();` | 状态就是普通字段 | 很容易忘记赋值回去，而且编译器不会提醒；要同时设置样式和取值，就得先存到一个临时变量里 |
| 4. 控件局部状态 | `BoolRef open = local(row, "open", () -> false);` | 控件自己的状态（是否展开、动画进度）不用放进应用状态 | 只适合控件内部的状态，不适合应用数据 |

另外两个跨写法的约定，四种写法都一样：
- **身份**：由“父元素 + 位置”决定，循环里用 key 指定；
- **重建**：处理事件时改了状态就立即重建，一帧最多 3 次。

---

## 6. 对比总结

| 维度 | A：MyGO 风格 | B：作用域对象 | C：隐式上下文 | D：声明式树 |
|---|---|---|---|---|
| 代码简洁程度 | 中（处处有 `c`） | 中（每层要给参数起名） | **高** | 中（括号嵌套深） |
| IDE 补全 | 中（靠静态导入） | **高**（`ui.` 列出全部） | 中 | 中 |
| 自定义控件和内置控件写法一致 | **是** | 否 | **是** | **是** |
| 多窗口、后台线程不易出错 | **是** | **是** | 否 | **是** |
| 测试方便 | **是** | **是** | 一般 | **是** |
| 每帧内存分配（Java 下） | **低**（临时对象多数可被消除，待实测） | **低**（同左） | **低**（同左） | 较高（必然产生垃圾） |
| 事件写法 | 就地查询 + 回调 | 就地查询 + 回调 | 就地查询 + 回调 | 只有回调 |
| 参照 MyGO 实现的难度 | **一一对应** | 每处都要转换 | 接近 | 思路不同 |

## 7. 我的推荐

**推荐写法 A（MyGO 风格），状态绑定用“引用对象 + 控件局部状态”（第 5 节的方式 1 和 4）。**

理由对应你的目标和应用：

1. **是不是即时模式**：你最初就要求即时模式，而 D 不是：事件只能用回调，不能在构建时就地处理。另外在 Java 里，D 每帧必然产生垃圾，A、B、C 有机会做到几乎不分配（见第 8 节）。所以先排除 D。
2. **Obsidian 式的应用要支持插件和自定义控件**：A 和 C 的自定义控件和内置控件写法一致；B 会让第三方控件永远是另一种写法。
3. **多窗口和测试**：Obsidian 支持把笔记弹出成独立窗口。显式的 `c` 比 C 的隐式全局状态更安全，测试时也能直接传入测试用的上下文。
4. **能直接参照 MyGO**：A 和 MyGO 一一对应。MyGO 已经验证过的设计，比如多次构建、key、局部状态、布局缓存，都可以直接照搬，风险最低。

A 的代价是 `c` 参数和静态导入带来的一点啰嗦，我认为在这几个优点面前可以接受。

如果你更看重 IDE 补全和对一般 Java 开发者的友好度，B 是次优选择，代价是自定义控件写法不统一。

---

## 8. 为什么 GPUI 用 D 风格，内存占用也低

GPUI 也是每帧重建一棵元素树、事件用回调，内存却很低。这不是因为 D 风格本身省内存，而是靠 Rust 的几样东西。以下几点已对照 GPUI 源码（zed `crates/gpui`）核实：

1. **每帧的元素放在一块 arena 里，整块清空。**
   - `window.rs` 里是 `ELEMENT_ARENA = Arena::new(1024 * 1024)`，元素用 `arena.alloc(...)` 分配，每次绘制结束调用 `element_arena.clear()`。
   - 分配就是把指针往后移，释放是整块重置：不产生垃圾，不需要 GC，这块内存每帧反复使用，大小基本不变。
2. **Rust 的闭包是普通结构体**，大小在编译期就确定。
   - 事件监听器是 `Box<dyn Fn>`，放在普通堆上；
   - 但它们跟着元素树在帧末被确定性地释放，不会堆积，也不用等 GC。
3. **视图可以缓存**（`Entity::cached` / `AnyView::cached`）。没有调用 `cx.notify()` 的视图会复用上一帧的结果，不重新构建。
4. **GPUI 本身就是混合模式**：
   - 视图（`Entity<T>`）是长期保留的，带着自己的状态；
   - 只有元素树每帧重建，事件通过 `cx.listener(...)` 回调写回视图的状态。
5. **UI 元素本来就不是内存的大头。** 一帧的元素树通常只有几百 KB，Zed 的内存主要花在文本缓冲、语法树和语言服务器上。

**换成 Java 有什么不同**

- Java 没有 arena，也没有值类型。D 风格里每帧的节点、数组和被保存的回调都是堆上的对象，帧结束后变成垃圾，要等 GC 回收。
- 对内存的实际影响没有我最初说的那么大：
  - 年轻代回收短命对象很高效；
  - 空闲时不重画，也就没有垃圾；
  - 主要影响是动画和滚动时分配速率高，GC 更频繁，年轻代也要留得大一点。
- 具体是多少，要在原型里实测才知道。

**A 和 D 在 Java 下的真正差别：对象会不会“逃逸”**

- **A 的临时对象不逃逸**：`children(() -> ...)` 的 lambda 被立即调用后就丢弃；`button(c, "x").clicked()` 返回的元素句柄当场用完。
  - Graal 编译器（native image 用的就是它）的逃逸分析有机会把这些对象完全消掉，根本不分配，但效果取决于能否内联，要实测确认。
  - 元素的数据放在每个窗口复用的池里，作用相当于 GPUI 的 arena。
- **D 的对象必然逃逸**：子节点要存进父节点，回调要留到构建结束后才执行，所以必须真正分配。

**结论**

- 排除 D 的首要理由是“它不是即时模式”，内存只是次要理由。
- 如果你更喜欢 D 的写法（视图返回一棵树，组合方便），在 Java 里也能做：节点用池复用，用重载代替可变参数，再实现 GPUI 那样的视图缓存。代价是实现更复杂，GC 开销要实测。
