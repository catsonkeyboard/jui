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

## M1 分配实测（2026-10-24）

设计文档第 8 节要求在写控件之前，先在 native image 上测清楚 UI API 每帧分配多少内存。`./build.sh alloc` 会把 `jui.spike.AllocSpike` 编译成 native image 运行，再在 JVM 上跑一次作为参照。

测量方法：
- 统计的是主线程分配的字节数（`ThreadMXBean.getCurrentThreadAllocatedBytes`，native image 里也能用）；
- 每个场景先预热 2 万帧，再测 1000 帧，扣除空帧后取平均；
- 每一帧都包括构建和布局。

| 场景（单位：字节） | 第一次 native | 启用句柄复用后 native | JVM 参照 |
|---|---|---|---|
| 空帧（整帧） | 0 | 0 | 0 |
| `Element` 句柄（每个元素） | 24 | **0** | 0 |
| 捕获变量的 `children` lambda（每个容器） | 64 | 16 | 0 |
| 临时 `BoolRef` 传给控件（每次） | 64 | 16 | 0 |
| `key(long)`（每行） | 24 | 0 | 0 |
| `key(Object)`，传入装箱的 Long（每行） | 24 | 0 | 24 |
| 模拟外壳整帧（200 句柄 + 60 lambda 容器 + 10 临时引用 + 30 带 key 行） | 10000 | **1120** | 0 |

原始输出：

```
ALLOC mode=native empty_frame=0 handles=24.0 lambdas=64.0 temprefs=64.0 key_long=24.0 key_object=24.0 shell_frame=10000
ALLOC mode=native empty_frame=0 handles=0.0 lambdas=16.0 temprefs=16.0 key_long=0.0 key_object=0.0 shell_frame=1120
ALLOC mode=jvm empty_frame=0 handles=0.0 lambdas=0.0 temprefs=0.0 key_long=0.0 key_object=24.0 shell_frame=0
```

结论：

- **GraalVM 社区版的 native image 消除不掉 `Element` 句柄**，每个元素 24 字节（HotSpot 的 C2 编译器能消掉）。所以按设计 7.1 的退路启用了句柄复用（`Context.POOLED_HANDLES`）：每个节点编号对应一个固定的 `Element` 对象，每帧更新它的代数。
- **启用后，框架内部的分配为 0**，满足“不能产生和元素数量成正比的分配”。
- **剩下的 16 字节都来自应用代码的写法**：捕获变量的 lambda，以及临时创建的引用对象。模拟外壳整帧 1120 字节，远低于 32 KB 的预算，**公开 API 不需要调整**。
- **复用的代价**：构建结束后再使用 `Element`，仍然会被发现（开发模式下报错）；但如果把 `Element` 存起来，到后面的构建里再用，它可能指向这一帧同一编号上的另一个元素，开发模式也发现不了。所以不要把 `Element` 存进字段。

## 运行

需要 Apple Silicon Mac、Xcode（含 Metal 编译器）和 GraalVM 25。脚本会在 `~/Library/Java/JavaVirtualMachines` 里找 GraalVM，也可以用 `GRAALVM_HOME` 指定。

```bash
./build.sh jvm             # 在 JVM 上运行（开发时用）
./build.sh native          # 编译 native image：build/jui-spike
./build/jui-spike          # 运行 native image
./build.sh bench 10        # 测启动时间、内存和体积（每种模式跑 10 次取中位数）
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
| `jui/mac/MacApp.java` | NSApplication、菜单、应用代理 | `internal/darwin/app.go` |
| `jui/mac/MacWindow.java` | 窗口和自绘视图（layer-backed NSView + `updateLayer`） | `internal/darwin/surface.go` |
| `jui/text/CoreText.java` | Core Foundation、Core Text、Core Graphics 的 FFM 绑定 | `internal/text/coretext_darwin.go` |
| `jui/text/TextEngine.java` | 排版（typesetter、字体回退、断行）和字形光栅化 | 同上 |
| `jui/text/TextLayout.java` | 排版结果：每个字形的字体、编号、位置 | `internal/text/layout.go` |
| `jui/text/GlyphAtlas.java` | 字形图集：货架式打包、记录改动区域 | `internal/scene` 的图集 |
| `jui/metal/MetalRenderer.java` | CAMetalLayer、预编译着色器库、图集纹理、实例化绘制 | `internal/gpu/metal/metal.go` |
| `jui/metal/Scene.java` | 一帧要画的方框、阴影和字形，每个 80 字节 | `internal/scene` |
| `resources/jui/shaders/shader.metal` | 距离场画圆角矩形、边框和阴影，按像素读图集画字形 | `internal/gpu/metal/shader.metal` |
| `jui/spike/Main.java` | 演示窗口、启动计时和文字统计 | — |
| `baseline/spike.m` | 对照组：同样的窗口和方框用 Objective-C 写，按同样口径计时 | — |
