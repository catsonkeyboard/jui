# 计划四：编辑器底层能力实施与整体审查

日期：2026-10-29。基线`861d0b6`，分支`codex/editor-primitives`。本会话对完整分支自行审查，没有另行启动独立审查代理；不合并、不推送，保留.idea。

## 交付与实施裁定

- Tasks 0–1：先跑453项基线，建立分支；公开canvas、作用域Painter、局部Rect、继承opacity及clip、共享排版和剪贴板服务。
- Task 2：独立有效期InputEvent、raw按键/keyup/修饰键/指针/滚动/命令、捕获及CANCEL；MacWindow新增兼容dispatchKeyDown接口。非组字按键取走后停止interpret或直接characters插入，内置输入框旧路径保持。
- Tasks 3–4：TextRange/TextInputClient、活动连接guard、resize、marked生命周期、局部坐标和实际范围、主Edit与菜单身份。公开bounds为actualStart/actualEnd，原生桥接数组仍为actualEnd/actualStart，显式转换。密码只向IME公开组字，actualRange不再错误使用mask下标。
- Tasks 5–7：不可变Span/Spans及shape规范化；共同Core Text attributed string、硬段复用、源UTF-16、逐glyph实际字号和逐行baseline；Ui/Painter富文本、背景和装饰、可见行裁剪。CFHash分桶后仍CFEqual核对，字体metrics缓存；layout key不含paint样式，hash碰撞核对真实值。
- Task 8：固定和可增长GlyphAtlas分开；自动arena可回收旧CPU像素，坐标/entry不变、完整dirty及generation。生产Metal encode共用于正常窗口和离屏验证；旧command保留资源，新texture整图上传后编码。
- Task 9：EditorSurface只使用公开API，应用自己持有String、选区、组字、有限undo和滚动；EditorPrimitivesDemo提供静态rich预览、取走J和IME开关、显式右键菜单。没有把它做成正式编辑器。
- Task 10：原生多client＋draw、不变rich帧、增长后帧及固定mixed-font事件样本；复跑旧门槛及全部新FFM handle。
- Task 11：完整分支审查、失败回归与修复、末轮测试及文档。新增测试最后一轮只增加覆盖，没有再修改已过原生门槛的生产代码。

**与计划的架构裁定**：自有client使用不可复活的活动adapter；内置TextEdit没有强行实现公开TextInputClient，而是保留现有受焦点、窗口和disabled约束的原生桥接分支，由同一Engine IME入口选择。避免为统一内部类型而重写外置compose及既有privacy/undo语义；公开接口、范围/生命周期契约和回归门槛不降低。

**成本与所有权裁定**：事件级InputEvent/TextRange/undo允许分配，production Painter借用复用，dev独立有效期；不变Spans不在每帧拼接或编码。字体沿用既有retain缓存策略，未新增字体缓存dispose/逐出，不能把布局缓存/图集上限说成字体对象也有界。CPU扩容arena可被GC回收；真实窗口最终window/view/renderer资源dispose仍未专项重构。

## 整体审查发现与修复

| 问题 | 先观察到的失败 | 修复 |
|---|---|---|
| 两参数非正方形图集意外增长 | 8×256固定图集应拒绝宽glyph，却返回Entry | 单独保存scalable，仅显式最大值构造允许增长 |
| Engine关闭后仍调用draw | close后再frame，draw计数从2变3 | closed Engine不再paint；同时显式禁止callback重入paint |
| paint边界落在共享cluster内部时装饰指标丢失 | 共享cluster后半段删除线应为y=9.85，实际落在baseline约13.54 | 装饰收集与区段相交的整个glyph cluster字体指标 |
| 自有示例滚动方向相反 | 文末负向滚动后offset未减小 | 与框架约定一致使用scrollY += dy |
| 普通菜单误绑无关focused client | 替换另一元素的client后普通菜单被取消 | 仅目标自身的活动连接成为菜单会话身份 |
| 超大glyph静默当成空字形 | 超过2048物理像素未抛错，而是EMPTY | 光栅化前检查双精度尺寸并明确报错，避免静默缺字和整数溢出 |

以上均先观察回归失败，再修复。另增加非法actualRange（NaN/非整数）、UI线程、query/mutation异常后的恢复、原生keyup/modifiers、raw未取走滚动、不同代adapter、捕获解绑/blur/prune/异常及重复client绑定覆盖。

SF字体的实际“fi”未必组成单glyph，不能把这项通过测试冒充曾暴露装饰失败；失败回归使用明确的共享cluster模型，真实Core Text连字/kerning位置仍由既有TextLineTest逐点对照。原始滚动新增测试最初写反dy方向，已纠正测试；resize新增测试最初未声明fillWidth，实际尺寸没有变化，已改成明确响应窗口宽度，不为测试修改生产resize规则。

## Review Focus逐项证据

1. **事件独占与单次派发**：MacWindowTextTest合成原生keyDown，证明raw true时既不interpret也不direct insert，下一次false正常恢复，组字保留IME路径；RawInputTest覆盖快捷键优先、Tab取走、keyup/modifiers、右键阻止菜单、捕获到框外与CANCEL。事件旧句柄不能在后续回调重新有效。
2. **连接寿命**：EditorLifecycleTest保存旧adapter，逐次blur/refocus、swap、prune/同key复用及close后直接调用，均不能写入新client；TextClientTest检查marked释放及discard计数、禁用、重复绑定和异常恢复。失效先于unmark回调，不让旧连接复活。
3. **范围与几何**：反向选区、surrogate实际substring、null replacement、组字在文档内及事件间新查询、bounds两种数组顺序有回归；带padding/祖先滚动和resize验证局部/世界坐标，MacWindow测试继续验证屏幕换算及实际NSRange。不可用bounds使用caret fallback，不扫描自有整文替client决定字素。
4. **内置兼容与菜单隔离**：原有TextInput/TextArea、只读、密码、IME-off、onTextKey/onPaste、changed/undo及菜单测试继续通过。内置仍用外置compose，custom own undo；Paste and Match Style仅fallback一次。custom菜单换client失效，普通菜单不绑定无关client。
5. **字体与缓存**：native FFI实际调用CFRange按值设置字体、int traits、Boolean CFEqual、字体metrics；等价副本复用、不同italic不串。人为将不同font放入同hash桶及同shape hash的Aa/BB核对真实值；纯paint变化不shape，mixed baseline/字号、CRLF尾空行和未改硬段复用有覆盖。
6. **绘制、资源与成本**：Painter祖先clip/pop边界和异常恢复、过期句柄、退休节点声明清理；CPU增长保留像素/坐标/full dirty，测试4096上限与font ID耗尽。GPU在增长前编码旧Scene，增长后才提交旧command，与新texture输出逐字节比较，旧glyph非零且一致；command完成、无error、retainedReferences为true。

## 最终自动化结果

生产修复版本`d54a164`，串行运行test、alloc、listbench、ffi-check；之后只追加测试和文档。最终全套测试数以末轮记录为准。

| 检查 | 结果 |
|---|---|
| 全套测试 | 末轮测试记录见README；生产修复后484项全部通过，随后补充生命周期/几何覆盖 |
| native alloc：旧控件/hover/列表/输入框 | 每控件/行均0.0 B；多行普通/选区/组字同为0.0 B |
| native custom client＋draw | 每控件0.0 B |
| native mixed-font rich整帧 | 0 B，亦小于32 KB整帧门槛 |
| native增长后steady frame | 0 B；32起步4次增长至512，最大单图容量262144 B，首次增长帧0.816 ms |
| native listbench | p50 0.177 ms、p95 0.233 ms、max 0.327 ms，742字形，图集未满 |
| JVM/native ffi-check | 均FFI ok；均实际执行GPU增长读回，generations=4，非零像素完全相同 |
| git diff --check | 通过 |

增长帧不是steady性能；单图最大容量不是进程RSS，也不包含同时在途旧纹理的峰值。JVM的custom每控件约96 B、rich/增长后整帧56 B，production目标是native，不把JVM结果改写为0。

固定样本：5000 UTF-16、50硬段、100个合法Spans，system/mono、13/18/24字号及bold/italic，视口800×600、scale=2。100次预热后1000次，不累计扩大文档；LF插入、立即Up和绘制确实共用该mixed-font layout。

| native事件＋更新/绘制 | p50 | p95 | max |
|---|---:|---:|---:|
| mixed-font不变文本滚动 | 0.061 ms | 0.066 ms | 0.094 ms |
| mixed-font插入LF→立即Up | 0.317 ms | 0.332 ms | 0.613 ms |
| 旧多行不变滚动 | 0.047 ms | 0.050 ms | 0.072 ms |
| 旧多行插入LF→立即Up | 0.344 ms | 0.360 ms | 1.081 ms |

均通过p95≤4 ms、max≤8 ms的局部门槛。硬件：Apple M5 Pro、64 GiB、arm64、macOS 26.6.1；不是大文件或完整M6性能承诺。

## 原生启动与人工验收边界

最终生产代码的`./build.sh native-demo EditorPrimitives --print-window`成功构建启动，打印window=21277；构建15.7 s、可执行文件约13.19 MiB。进程随后停止，没有冒充手动点击或关闭窗口验收。只读检查CGPreflightScreenCaptureAccess=false，没有请求修改权限或绕过；没有系统窗口截图。

仍需用户运行`./build.sh native-demo EditorPrimitives`核对：
- 自有表面的拼音候选框位置、组字提交/取消、切焦点和关闭；中文/emoji跨行拖选及方向键。
- 取走J开关确实不漏字，IME开关，以及Edit/显式右键Copy/Paste/Undo。
- 混合字号baseline、mono、可用italic、背景、下划线/删除线；图集增长后旧文字不变。

范围仍不包括完整编辑器、Markdown、rope/虚拟文档、完整bidi、inline链接/对象、彩色emoji、图集eviction/repack、字体缓存回收及完整M6。之前的TextKey有效期、wrapReverse margin、rowsBuilt计数等旧Minor没有夹带修复。计划二/三的人工项继续待办；Form的“全部正常”按用户既有反馈补记，不冒充本轮新手测。

没有遗留已发现的Critical/Important实现问题；人工验收和上述明确排除项不因此视为已完成。保留开发分支，等用户决定合并，不推送。
