# j-ui

在 GPU 上自绘界面的 Java 桌面 UI 框架的技术验证：Java 通过 FFM（`java.lang.foreign`）直接调用 macOS 的 AppKit、Metal 和 Core Text，不写一行 C/Objective-C，再用 GraalVM Native Image 编译成原生可执行文件。

结构参照 [egoist/mygo](https://github.com/egoist/mygo)（MIT）的 `internal/darwin`、`internal/gpu/metal` 和 `internal/text`：MyGO 用 purego 做的事，这里用 FFM 做。

![native image 画出的窗口](docs/screenshot.png)

## 验证结果（2026-10-24）

环境：Apple Silicon，macOS 26.6.1，GraalVM CE 25 Innovation 4（JDK 25.0.4），Retina 屏（缩放 2 倍），窗口 760×560 点。

### 启动、内存、体积

`./build.sh bench 10` 的结果：每种方式跑 10 次取中位数，第一次运行不计入。时间从进程启动算起，单位毫秒；内存是空闲 3 秒后的物理占用（活动监视器的口径）。

| 方式 | 到 main() | 应用启动完成 | 窗口和 Metal 就绪 | 首帧提交 | GPU 画完首帧 | 空闲内存 | 体积 |
|---|---|---|---|---|---|---|---|
| Java native image（含文字） | 5.3 | 67.5 | 111.0 | 128.7 | **133.1** | **45.3 MB** | 13.3 MB |
| Java，在 JVM 上（含文字） | 108.8 | 187.3 | 233.8 | 275.6 | 280.1 | 134.0 MB | 需要 JDK |
| 纯 Objective-C（对照，只画方框） | 4.6 | 62.3 | 101.7 | 109.9 | 114.4 | 37.7 MB | 58 KB |

- 启动耗时的大头是 macOS 自身：启动 AppKit 约 55 毫秒，创建窗口、Metal 设备和管线约 40 毫秒。
- Java native image 画了 239 个字形的中英文，比只画方框的 Objective-C 多约 19 毫秒，其中文字约 10 毫秒（见下表）。
- 计时口径和 MyGO 的 `ContentWindowOpen` 基准一致，都算到“画出第一帧”为止。画面在之后的下一次屏幕刷新时上屏，120Hz 屏最多再晚约 8 毫秒。
- 作为参考，MyGO 文档里的数据是：counter 示例的二进制 8.9 MB，空闲内存在 M5 MacBook 上 37 MB。
- native-image 构建一次约 16 秒，构建过程峰值内存约 1.5 GB；用 `-Os` 编译体积还能再小约 2 MB。

### 文字

演示窗口里共 282 个实例，其中 239 个字形是首帧第一次光栅化进图集的。

| | native image | JVM |
|---|---|---|
| 首帧画文字（排版 + 光栅化 + 进图集） | 10.3 ms | 30.6 ms |
| 其中排版（Core Text） | 6.1 ms | 11.4 ms |
| 其中光栅化 239 个字形（Core Graphics） | 3.9 ms | 11.1 ms |
| 缓存命中后重画整帧 | 0.009 ms | 0.080 ms |
| 一次 FFM 调用 | 6.1 ns | 5.1 ns |

做法和 MyGO 一样：

- **排版**：Core Text 的 typesetter 负责断行，再从每个 run 的属性里取出实际用到的字体，中文会自动回退到苹方。设置了 `zh-Hans` 语言属性来选中文字形，系统字体通过 `NSFont` 取，字重准确。
- **光栅化**：用 Core Graphics 的纯透明度位图上下文画字形，开启字体平滑，并按文字颜色的亮度分 4 档。子像素位置按 `min(5, ⌈100/(3·px)⌉)` 量化，基线对齐到整像素。
- **绘制**：字形放进一张 1024×1024 的 R8 图集，只上传改动的区域。字形四边形和像素网格对齐，着色器直接按像素读取覆盖率。

### 已验证可行的点

- 全部原生调用都走 FFM，没有 JNI，也没有 C 代码：`objc_msgSend` 按签名绑定，结构体按值传递和返回（CGRect、CGSize、CFRange、MTLRegion、MTLClearColor）都可用。
- 在运行时定义 Objective-C 类，方法用 Java 实现（FFM upcall），包括调用父类实现。
- 用 FFM 手工构造 Objective-C block 交给 Metal；Metal 从它自己的线程回调 Java，JVM 和 native image 下都正常。
- native image 的 `main()` 就运行在进程主线程上，AppKit 可以直接用；JVM 下则需要加 `-XstartOnFirstThread`。
- 预编译的 metallib 作为资源打进 native image，运行时通过 `newLibraryWithData:` 加载，不需要在启动时编译着色器。
- Core Text 排版、字体回退、多种字重、自动换行，以及 Core Graphics 光栅化和字形图集，都已在 native image 里跑通。

### 踩到的坑

- **native image 里，FFM 句柄必须放在构建期初始化的类里，否则每次调用要约 1.7 微秒，比 JVM 慢 300 倍。** 原因是运行期初始化的类里的句柄，编译时不是常量，调用会退回到通用的 MethodHandle 路径。这里的做法是 `jui.ffi.Native`：每个签名一个不绑定函数地址的句柄，函数地址在运行期查到后作为第一个参数传入；这个类不持有任何原生地址，所以可以 `--initialize-at-build-time`。改完后一次调用 6 纳秒，首帧文字从 23 毫秒降到 10 毫秒。
- native-image 默认把最低系统版本设成构建机的 macOS 版本（这里是 26.0），程序在旧系统上会打不开。需要加 `-H:NativeLinkerOption=-mmacosx-version-min=13.0`；设置 `MACOSX_DEPLOYMENT_TARGET` 环境变量没有效果。
- native image 链接的是本机 Xcode 的 SDK（27.0），所以窗口自动是 macOS 26 的新外观（标题靠左、标题栏更高）；`java` 启动器是用 SDK 14.5 链接的，窗口还是旧外观。
- 每次重新编译后，第一次运行要多花约 470 毫秒，这是 macOS 对新可执行文件的首次检查。正式发布时要签名和公证。
- `javac --release 25` 看不到 GraalVM 的 `org.graalvm.nativeimage` 模块，编译 Feature 类要改用 `--add-modules`。
- 窗口第一次显示时，那一帧是跟着窗口由系统合成上屏的，Metal 报告的 `presentedTime` 为 0，所以计时改用 command buffer 的完成时间。
- 原生签名、回调目标和资源都要在构建期登记。这里统一放在 `Sigs` 和 `NativeImageFeature` 里，写一次就够了，JVM 和 native image 用的是同一份代码。

### 还没验证的

- 彩色字形（emoji）：需要再加一张彩色图集，目前直接跳过不画。
- 图集满了以后的扩容和回收：MyGO 会重新打包、扩容，并释放不再使用的字形；这里没有做。
- 和 AppKit 自己画的文字逐像素对比：MyGO 做过，这里只是肉眼看起来一致。
- 输入法、文本编辑、从右到左的文字。
- 拖动改变窗口大小时的同步呈现路径：代码已经写了，没有手动拖动测试过。
- Intel Mac：GraalVM 的 FFM 不支持 macOS x64。

## M1：即时模式 API 骨架（2026-10-25）

按[设计文档](docs/design/2026-10-24-ui-api-design.md)和 [M1 计划](docs/plans/2026-10-24-m1-engine-skeleton.md)实现了 MyGO 风格 API 的骨架，包括：
- `Context`、`Element`、节点池、身份和 key；
- `box`、`row`、`column`、`spacer`、`divider`、`text`；
- 不含换行和绝对定位的 flexbox；
- 绘制到 `Scene`、按需出帧；
- `Tester`，以及 `App` 和 `Window`。

验收示例 `jui.examples.CardDemo` 用新 API 重写了原型的卡片，在 native image 下的画面如下：

![M1 卡片示例](docs/m1-card.png)

写法和 MyGO 一一对应（节选）：

```java
public static void view(Context c) {
    box(c).fill().background(PAGE).padding(32).children(() ->
        column(c).grow(1).background(WHITE).border(1, CARD_BORDER).radius(16)
            .shadow(0, 8, 24, 0, CARD_SHADOW).padding(26, 32, 32, 32).children(() -> {
                text(c, "j-ui 文字渲染").fontSize(26).bold().textColor(INK);
                text(c, PARAGRAPH).fontSize(16).textColor(BODY).margin(16, 0, 0, 0);
                ...
                spacer(c);
                row(c).gap(16).children(() -> {
                    button(c, "确定 OK", ACCENT, null, WHITE).shadow(0, 4, 12, 0, ACCENT_SHADOW);
                    button(c, "取消 Cancel", WHITE, BUTTON_BORDER, INK);
                    spacer(c);
                    box(c).size(44, 44).radius(22).background(ROSE);
                });
            }));
}
```

- 测试：`./build.sh test`，47 个测试全部通过，用的是仓库里的零依赖运行器。
- 运行：`./build.sh demo`（JVM）或 `./build.sh native-demo`（native image）。
- native image 下：体积 12.8 MB，空闲内存 45.2 MB。
- 分配：native image 下，包括文字在内的整帧（构建、布局、绘制），框架内部不分配；卡片示例整帧 200 字节，全部来自示例自己的 lambda。详见下一节。

## M1 分配实测（2026-10-24）

设计文档第 8 节要求在写控件之前，先在 native image 上测清楚 UI API 每帧分配多少内存。`./build.sh alloc` 会把 `jui.spike.AllocSpike` 编译成 native image 运行，再在 JVM 上跑一次作为参照。

测量方法：
- 统计的是主线程分配的字节数（`ThreadMXBean.getCurrentThreadAllocatedBytes`，native image 里也能用）；
- 每个场景先预热 2 万帧，再测 1000 帧，扣除空帧后取平均；
- 下表的场景每帧包括构建和布局；最后两个场景还包括绘制文字。

| 场景（单位：字节） | 第一次 native | 启用句柄复用后 native | JVM 参照 |
|---|---|---|---|
| 空帧（整帧） | 0 | 0 | 0 |
| `Element` 句柄（每个元素） | 24 | **0** | 0 |
| 捕获变量的 `children` lambda（每个容器） | 64 | 16 | 0 |
| 临时 `BoolRef` 传给控件（每次） | 64 | 16 | 0 |
| `key(long)`（每行） | 24 | 0 | 0 |
| `key(Object)`，传入装箱的 Long（每行） | 24 | 0 | 24 |
| 模拟外壳整帧（200 句柄 + 60 lambda 容器 + 10 临时引用 + 30 带 key 行） | 10000 | **1120** | 0 |
| 纯文字视图整帧，含绘制（5 段文字，157 个实例，没有 lambda） | 12616 → 2792 → **0** | — | 0 |
| 卡片示例整帧，含绘制 | — | **200** | 544 |

最后两行是代码审查之后补测的。纯文字那一行的三个数字依次是：修复前；字形图集改用 `long` 键之后；`shadeOf` 改用查找表之后（`Math.pow` 在 native image 里每次调用都会分配）。排版缓存的命中原来也会分配一个键对象，同样改成了 `long` 键。

原始输出（最后一行 native 是全部修复之后的结果）：

```
ALLOC mode=native empty_frame=0 handles=24.0 lambdas=64.0 temprefs=64.0 key_long=24.0 key_object=24.0 shell_frame=10000
ALLOC mode=native empty_frame=0 handles=0.0 lambdas=16.0 temprefs=16.0 key_long=0.0 key_object=0.0 shell_frame=1120 text_frame=0 text_instances=157 card_frame=200
ALLOC mode=jvm empty_frame=0 handles=0.0 lambdas=0.0 temprefs=32.0 key_long=32.0 key_object=56.0 shell_frame=320 text_frame=0 text_instances=157 card_frame=544
```

`./build.sh alloc` 现在也是一道检查：native image 下，纯文字视图平均每个实例分配超过 0.5 字节，或卡片示例整帧超过 32 KB，就返回失败。

结论：

- **GraalVM 社区版的 native image 消除不掉 `Element` 句柄**，每个元素 24 字节（HotSpot 的 C2 编译器能消掉）。所以按设计 7.1 的退路，正式版（开发模式关闭时）复用句柄（`Context.POOLED_HANDLES`）：每个节点编号对应一个固定的 `Element` 对象，每帧更新它的代数。开发模式仍然为每个元素新建句柄，这样即使把 `Element` 存起来、在后面的构建里再用，也能被发现。
- **框架内部的分配为 0，包括文字的排版和绘制**，满足“不能产生和元素数量成正比的分配”。
- **剩下的分配都来自应用代码的写法**：捕获变量的 lambda 和临时创建的引用对象，各 16 字节。模拟外壳整帧 1120 字节，卡片示例整帧 200 字节，都远低于 32 KB 的预算，**公开 API 不需要调整**。
- 不要把 `Element` 存进字段：正式版不检查这种误用，它可能会修改到这一帧同一编号上的另一个元素。

## M2：交互（2026-10-25）

按 [M2 计划](docs/plans/2026-10-25-m2-interaction.md)实现了交互，行为对照 MyGO（commit `8049993`）的 `ui/input.go`、`ui/runtime.go` 和 `ui/widgets.go`。

**指针**
- 悬停、按下、点击、双击、右键（或 Control 加单击）、拖动。
- 对应的查询：`hovered()`、`pressed()`、`clicked()`、`clicks()`、`doubleClicked()`、`rightClicked()`、`dragX()`/`dragY()`。
- 按住期间悬停状态冻结，在别处松开不算点击。

**重建规则**
- 某一遍构建报告了输入或执行了回调，就再建一遍，一帧最多 3 遍。
- 每次输入只报告一次。
- 回调 `onClick`、`onChange` 在这一遍的视图建完后执行，所以可以修改正在遍历的列表。

**焦点和键盘**
- 点击聚焦；Tab、Shift+Tab 按构建顺序循环；回车和空格激活获得焦点的元素。
- 焦点环只在用键盘切换焦点后显示。
- `Handle` 可以从任意动作请求焦点，`autoFocus()` 让元素出现时就获得焦点。

**快捷键**
- `element.shortcut(...)` 在焦点位于该元素内部时报告，`c.shortcut(...)` 是窗口级的。
- 获得焦点的元素优先于窗口；修饰键必须完全一致。

**控件**
- `button`、`primaryButton`、`checkbox`、`toggle`（开关）、`slider`。
- 无外观的基础版：`buttonBase`、`toggleBase`、`sliderBase`。
- 外观照 MyGO，主题改用 MyGO 的浅色和深色配色。

**局部状态和其他**
- `local`、`localBool`、`localFloat`，跟着元素的身份走，元素不再出现就回收。
- `disabled()`（子元素继承）、`opacity()`、`bounds()`。

**明暗切换**：默认跟随系统外观，系统切换时窗口跟着重画。

**macOS 平台**
- 视图接收鼠标、键盘、修饰键事件，同 MyGO 的 `internal/darwin/surface.go`。
- 窗口代理报告窗口是否在前台，外观变化时视图会收到通知。

两个验收示例在 native image 下的画面如下（这台 Mac 当时是深色外观）：

| 计数器 `./build.sh demo Counter` | 待办清单 `./build.sh demo Todo` |
|---|---|
| ![计数器](docs/m2-counter.png) | ![待办清单](docs/m2-todo.png) |

- **计数器**：照 MyGO 的 `examples/counter-native`，加了一个步长滑块。按钮和 ↑/↓ 都按步长计数，计数为 0 时“重置”禁用。
- **待办清单**：每条待办一个复选框，行按 id 作 key，“删除”的回调直接从列表里移除，开关隐藏已完成的，⌘N 新增。没有文字输入（M4 才做），所以待办的标题是固定的。
- **测试**：`./build.sh test`，138 个测试全部通过。Tester 覆盖了点击、Tab、空格、方向键和快捷键。
- **分配**：控件帧（40 个按钮、40 个复选框、20 个滑块）在 native image 下，框架内部每个控件分配 0 字节，指针悬停在按钮上时也是 0；卡片整帧仍是 200 字节（`./build.sh alloc`）。
- **已手动核对通过**：用真实鼠标和键盘试过两个示例，悬停变色、点击、焦点环、方向键计数，以及切换系统外观都正常。从 AppKit 事件到引擎的路径另由 `MacWindowInputTest` 用真实的 `NSEvent` 自动测试。

**推迟到后面的**
- `cursor(Cursor)`、`onSubmit`/`submitted()` 推到 M4，和文本输入一起做；
- `c.now()`、`c.after(...)`、`Tester.advance` 推到 M3，和 display link 一起做；
- 开关的滑动动画推到 M3；
- 勾选标记先用文字“✓”，M6 有了线段图元再换成描边。

## 运行

需要 Apple Silicon Mac、Xcode（含 Metal 编译器）和 GraalVM 25。脚本会在 `~/Library/Java/JavaVirtualMachines` 里找 GraalVM，也可以用 `GRAALVM_HOME` 指定。

```bash
./build.sh test            # 运行全部测试（零依赖运行器）；./build.sh test FlexLayoutTest 只跑一个类
./build.sh demo            # 卡片示例，在 JVM 上运行；./build.sh demo Counter、./build.sh demo Todo 运行 M2 的示例
./build.sh native-demo     # 同上，编译成 native image（build/<name>-demo）后运行，也接受示例名
./build.sh alloc           # 实测 UI API 每帧的分配（native image 和 JVM）
./build.sh jvm             # 原型，在 JVM 上运行
./build.sh native          # 原型，编译成 native image：build/jui-spike
./build.sh bench 10        # 原型的启动时间、内存和体积（每种模式跑 10 次取中位数）
```

## 代码结构

| 文件 | 作用 | 对应 MyGO |
|---|---|---|
| `jui/ffi/Sigs.java` | 所有原生函数签名集中登记，native image 构建时据此生成调用桩 | — |
| `jui/ffi/Native.java` | 每个签名一个不绑定地址的句柄，构建期初始化，调用才快 | purego 的 `SyscallN` |
| `jui/ffi/ObjC.java` | Objective-C 运行时封装：类、选择子、`objc_msgSend`、NSString、自动释放池 | `internal/darwin/objc.go` |
| `jui/ffi/ObjCClass.java` | 运行时定义 Objective-C 类，方法由 Java 实现（FFM upcall） | `classDef` / `method` |
| `jui/ffi/Blocks.java` | 在原生内存里手工构造 Objective-C block，由 Java 实现 | `objc.NewBlock` |
| `jui/ffi/Libc.java` | 计时用的 libc 函数：进程启动时间、是否在主线程 | — |
| `jui/ffi/NativeImageFeature.java` | 告诉 native-image 要登记的签名、回调目标和资源 | — |
| `jui/platform/mac/MacApp.java` | NSApplication、菜单、应用代理 | `internal/darwin/app.go` |
| `jui/platform/mac/MacWindow.java` | 窗口和自绘视图（layer-backed NSView + `updateLayer`） | `internal/darwin/surface.go` |
| `jui/text/CoreText.java` | Core Foundation、Core Text、Core Graphics 的 FFM 绑定 | `internal/text/coretext_darwin.go` |
| `jui/text/TextEngine.java` | 排版（typesetter、字体回退、断行）和字形光栅化 | 同上 |
| `jui/text/TextLayout.java` | 排版结果：每个字形的字体、编号、位置 | `internal/text/layout.go` |
| `jui/text/GlyphAtlas.java` | 字形图集：货架式打包、记录改动区域 | `internal/scene` 的图集 |
| `jui/render/metal/MetalRenderer.java` | CAMetalLayer、预编译着色器库、图集纹理、实例化绘制 | `internal/gpu/metal/metal.go` |
| `jui/render/Scene.java` | 一帧要画的方框、阴影和字形，每个 80 字节 | `internal/scene` |
| `resources/jui/shaders/shader.metal` | 距离场画圆角矩形、边框和阴影，按像素读图集画字形 | `internal/gpu/metal/shader.metal` |
| `jui/spike/Main.java` | 演示窗口、启动计时和文字统计 | — |
| `jui/spike/AllocSpike.java` | 在 native image 上实测 UI API 每帧的分配 | — |
| `baseline/spike.m` | 对照组：同样的窗口和方框用 Objective-C 写，按同样口径计时 | — |
| `jui/ui/{Ui,Context,Element,View}.java` | 公开 API：控件静态函数、每个窗口一个的上下文、元素句柄 | `ui` 包 |
| `jui/ui/{Color,Theme,Align,Justify,Rect}.java` | 公开的值类型 | 同上 |
| `jui/ui/{BoolRef,IntRef,FloatRef,DoubleRef,Ref}.java` | 引用对象：控件读写应用状态 | 指针绑定 |
| `jui/ui/{Key,Mod,Shortcut,Handle}.java` | 按键、修饰键、快捷键、持久的焦点句柄 | `ui/keys.go`、`ui.Handle` |
| `jui/ui/Tester.java` | 不开窗口测试视图 | `ui.Tester` |
| `jui/ui/engine/{Engine,Node,NodePool,Identity,Dev}.java` | 每帧流程、节点池、身份、开发模式检查 | `ui/runtime.go` 等 |
| `jui/ui/engine/FlexLayout.java` | flexbox（单行，CSS §9 的算法） | `ui/layout.go` |
| `jui/ui/engine/Paint.java` | 把节点树绘制到 `Scene`：悬停和按下的背景、透明度、焦点环、滑块 | `ui/paint.go` |
| `jui/ui/engine/{ElementState,StateTable}.java` | 按身份保存的元素状态：上一帧的位置、没报告的输入、局部状态 | `ui/context.go` 的 `state` |
| `jui/ui/engine/{HitList,Input,Actions}.java` | 命中测试列表、指针和键盘的处理、焦点、快捷键路由、回调 | `ui/input.go`、`ui/actions.go` |
| `jui/platform/mac/MacKeys.java` | AppKit 的按键码和修饰键映射 | `internal/darwin/surface.go` |
| `jui/util/LongMap.java` | 以 `long` 为键、查找和删除都不分配的哈希表 | — |
| `jui/app/{App,Window,WindowOptions}.java` | 应用和窗口 | `mygo.App`、`mygo.NewWindow` |
| `jui/examples/CardDemo.java` | M1 验收示例 | — |
| `jui/examples/{CounterDemo,TodoDemo}.java` | M2 验收示例：计数器和待办清单 | `examples/counter-native` |
| `src/test/java/jui/testing/` | 零依赖的断言和测试运行器 | — |
