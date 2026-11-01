# M6 绘制与性能：验证与整分支审查

日期：2026-11-01。基线 `d3f78cc`（516 测试），分支 `codex/m6-perf`。未合并、未推送，保留用户 `.idea/`。

## 交付提交

| 提交 | 内容 |
|---|---|
| `aa31293` | 可选帧统计：阈值、分段、构建 pass/round、layout 调用、分配和 GC |
| `3bc21a2` | 真实 NotesDemo 视图的 native/JVM shellbench，输入确实更新与预算退出码 |
| `0b6c600` | 磁盘笔记库、已保存正文首帧、后台文件名扫描、5次启动/内存门槛 |
| `c1bdbdf` | 身份/布局/子树/约束快照复用，两宽度测量缓存，开关等价测试 |
| `d7f14c5` | 冷字形回收、当前帧固定、Scene UV刷新、GPU在途纹理与像素读回 |
| `969e3b2` | 六节点七边 GraphDemo、入场/悬停测试，修复 Bash 3.2 空数组兼容 |
| `225aff1` | 可选native呈现观察器、帧关联、严格输入/呈现补充诊断；性能差距明确延后 |

## 最终串行验证

原始输出见[验证输出](2026-11-01-m6-perf-results.txt)。

环境：Apple M5 Pro，64 GiB，macOS 26.6.1，GraalVM 25，native image，scale 2。每个 `build.sh` 命令独占构建目录，依次执行。

| 命令 | 结果 |
|---|---|
| `./build.sh test` | 532 通过、0 失败；布局缓存关闭版本此前亦 525/525（图集/Graph加入前） |
| `./build.sh alloc` | native 框架控件、列表、输入、M5开/关弹层、自有及富文本编辑帧均0B；AREABENCH滚动p95 .061ms、输入p95 .385ms |
| `./build.sh listbench` | 普通：p95 .233ms、max .343ms；256→512²压力：p95 .275ms、max .416ms、86次回收、无full |
| `./build.sh ffi-check` | JVM/native FFI ok，GPU增长读回像素一致 |
| `./build.sh shellbench` | native：steady p95 .034ms / max .056ms，648B/帧；输入p95 .118ms / max .931ms；滚动p95 .101ms / max .131ms；可复用18节点全命中 |
| `./build.sh bench 5 notes` | 进程启动→main 5.244ms→app 70.977ms→window 110.779ms→submit 122.444ms→GPU 126.810ms；空闲3秒footprint 66.9MB，均为5次中位数 |
| `JUI_FRAME_STATS=all build/notes-demo --vault=build/notes-vault --quit-after-first-frame` | 四段及计数/分配/GC一行日志，正常退出0 |
| `./build.sh native-demo Graph --print-window --quit-after-first-frame` | 原生构建、首帧、窗口号与退出0 |

全部实施计划自动门槛达标。输入基准是搜索框真实插入/退格→下一帧CPU paint，逐键断言搜索词更新，**不包含vsync排队、GPU完成或显示器实际出光延迟**。不能把它作为物理端到端延迟证据。

优化前外壳基线：steady p95 .031ms、640B/帧，输入max .943ms，滚动p95 .097ms。缓存提供复用机制和正确性验证；这份很小且含列表/输入易变子树的外壳没有显示速度提升，不宣称加速倍数。

Notes堆配置前5次测量首帧121.532ms、footprint107.2MB；vmmap中VM_ALLOCATE resident50.1MB，GC日志48→4MB仍保留页面。单变量 `-Xmx32m` 实验降至67.4MB，故Notes native默认32MiB堆；其他native程序不变。不是通过强制GC或提前取样达标。每次内存样本必须已载入10000文件名和一篇正文。

## 测试证据与边界

- 帧统计：注入时钟/分配/GC/记录器，阈值边界、反复分段累加、跨帧复位、不可用计数及日志自身分配排除；layoutFrame内统一插桩包含reveal内重排。
- 布局缓存：250轮随机布局树，每轮变化样式、文字、Spans、结构/同级顺序与约束，开/关缓存各跑两遍逐节点比较x/y/w/h、内容尺寸、偏移与文字几何；稳定树必须实际命中。输入、列表、modal专项及现有popover/reveal/滚动回归全部通过。
- 图集：冷字形逐出后重新光栅化逐字节相同；本帧较早发出的字形在中途重排后UV正确。移除UV刷新时该测试先失败，恢复后通过；GPU在前后command均未提交时替换纹理，再提交/等完成、读回两图非空且相同。
- 原生画面：本轮CUA操作实际Graph窗口检查静态分层布局，点击“笔记”，圆点放大且恰好两条邻边高亮，Cmd-Q退出；入场请求和停止由确定时钟测试验证。使用忽略目录下的.app包装供CUA识别，不提交包装。
- 用户完整中文输入法/触控板/菜单/深浅色验收沿用README清单；本轮不冒充全部人工通过。

## 实施裁定

1. 沿用用户指定继续的现有 `codex/m6-perf` 检出，无已跟踪无关修改；`.idea/`不碰。风险：没有增加第二个隔离worktree。
2. 在layoutFrame方法内统一计时，layout调用次数与build round分开；补上reveal内部第三处。风险：日志字段比原勘察清单更细。
3. 数字0作为非负阈值，记录所有正耗时帧；off/空/非法关闭，1为8ms。风险：0不是关闭开关，已文档化。
4. 输入门槛取所有测量样本的最大值≤8.333ms，口径为CPU paint；不推断物理出光。风险：尚无物理端到端证据。
5. 启动按设计§8生成真实磁盘库和已保存状态，普通Notes保留合成数据；首帧后再扫描文件名。风险：磁盘启动与headless合成外壳是不同夹具。
6. Notes native默认32MiB堆，可运行时覆盖。风险：故意超大示例负载可能要增大-Xmx；普通库已实测。
7. 快照归属于Node池槽位，精确核对身份和同序结构，不为每个纯视觉节点新建ElementState。风险：移动槽位时保守miss，仅损失命中率。
8. 当前TextEngine没有释放字体的生命周期API，Engine终身持有同一实例；不添加空转generation。未来字体释放必须同时失效缓存。风险：未来生命周期改造需要遵守该约束。
9. 回收先保留当前与前一帧；不足则仅保留当前帧，所有试排成功后才提交；Scene刷新UV，旧command保留texture。风险：单帧工作集本身超容量仍明确full，不逐出本帧字形。
10. 压力夹具256²起步，上限512²。384²试验无法始终容纳本帧工作集并失败；512²仍有86次回收。风险：不是证明任意容量都能容纳任意画面。
11. Graph采用现有Theme、系统文字、静态分层及单次280ms动画，只做公开Painter验收。风险：没有引入自动图布局或无限缩放。

## 独立审查与用户裁定

独立 reviewer `m6_review` 审查 `d3f78cc..daa2437`，只读源代码/测试和验证日志；未运行构建、未修改检出。无Critical，发现1项Important验收证据缺口和1项Minor性能缺陷。

1. **Important：CPU基准不能证明输入/滚动真实呈现达标。** 接受。补充 native `PresentBench` 与可选 `Window.onPresented`，使用实际drawable `presentedTime`，以drawableID关联Engine帧，另在paint末尾记录该帧实际显示文字与CPU时间；不读取“最新模型”来冒充旧帧内容。0时间明确为跳过帧，不能算成功。新native回调和严格门槛测试加入全套，先红后绿。真实呈现结果未达一帧，详见下节。
2. **Minor：普通scroll首次快照valid/cacheable相互依赖。** 首次被判scrollMoved→blocked，save又只把cacheable设valid，静止后也不会命中。reviewer独立四节点探针确认column从第2帧4/4，scroll始终0/0。只影响复用率，无几何错误或已测CPU门槛失败；记录延后，未悄悄修正或隐藏。

**用户裁定**：说明CPU生成画面与系统呈现是两段时间后，用户明确选择“按原实施计划收尾，显示延迟另记待优化（推荐）”。因此本轮按已接受计划的CPU口径验收；真实呈现目标保留严格失败诊断并转后续，**不是声称已修复或已达标**。相应更新设计§8和计划。

**呈现补测与撤回试验**：最初native键→呈现p95 24.762ms/max33.060ms、滚动间隔p50 25ms/p95 33.333ms。提前setNeedsDisplay、以及事件末尾直接绘制的两个试验均未改善（仍约25–33ms），已恢复提交daa2437的原调度行为，不把无收益试验混入交付。呈现探针、帧关联和采集保留，可复现严格失败。

Objective-C清屏对照（非Java、无布局文字）：120Hz请求下正时间样本23.207–24.678ms；事务同步+flush对照仍14.949–24.665ms。分段观测input→paint仅少量ms，而paint→呈现约15–24ms。对照支持后续应研究系统/渲染呈现管线；不据此断言任何macOS机器都存在相同下限。[Apple presentedTime定义](https://developer.apple.com/documentation/metal/mtldrawable/presentedtime)明确0表示未呈现/跳过，不能用GPU完成或0值伪造成功。

补充裁定：
12. 按用户确认，将本轮验收收敛到原计划CPU门槛；实际呈现≤一帧及刷新率呈现节奏单列后续。代价：原字面物理/系统显示目标未完成。
13. native键延迟夹具仅关闭OS组字，测真实AppKit ASCII键事件；普通Notes不变。代价：这组数据不覆盖IME组字延迟。
14. 新呈现观察器只在显式启用时注册callback及帧映射，常规帧不增加采集对象。代价：补充诊断有自身观测开销，不能直接等同无观测路径。
15. 无收益调度试验撤回，仍使用原按需/显示链路调度。代价：真实呈现性能差距留待后续架构优化。

**Declined to judge逐项裁定**：物理出光无硬件测量，仍不作通过/失败结论；系统drawable呈现已有上面的明确失败数据。完整IME、触控板、所有菜单/外观保留人工清单，不冒充验收完成。NotesVault为“一万文件名＋一篇正文”的基准夹具，不扩为完整笔记应用。

**原生补充操作**：Notes实际窗口中输入ASCII `0002`，列表缩为唯一笔记，点击打开预览；Cmd-P面板中粘贴中文“季度”并筛选，Esc关闭，随后退出，无残留notes-demo进程。仅是中文文本提交，不把粘贴等同IME组字测试。

最后一次补充 `presentbench` 自动复跑因未收齐可见呈现样本而非零退出；保留此前完整1000样本的明确失败数据，未把此运行记为通过。该补充诊断不改变用户已确认的CPU验收范围。

按用户确认的本轮CPU范围可收尾，未合并/未推送。后续项明确为：真实呈现延迟/节奏、普通静止scroll子树缓存命中，以及原有完整人工交互验收。
