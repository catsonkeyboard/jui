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

## 最终串行验证

环境：Apple M5 Pro，64 GiB，macOS 26.6.1，GraalVM 25，native image，scale 2。每个 `build.sh` 命令独占构建目录，依次执行。

| 命令 | 结果 |
|---|---|
| `./build.sh test` | 530 通过、0 失败；布局缓存关闭版本此前亦 525/525（图集/Graph加入前） |
| `./build.sh alloc` | native 框架控件、列表、输入、M5开/关弹层、自有及富文本编辑帧均0B；AREABENCH滚动p95 .062ms、输入p95 .381ms |
| `./build.sh listbench` | 普通：p95 .239ms、max .422ms；256→512²压力：p95 .287ms、max 1.257ms、86次回收、无full |
| `./build.sh ffi-check` | JVM/native FFI ok，GPU增长读回像素一致 |
| `./build.sh shellbench` | native：steady p95 .035ms / max .067ms，648B/帧；输入p95 .116ms / max .902ms；滚动p95 .101ms / max .161ms；可复用18节点全命中 |
| `./build.sh bench 5 notes` | 进程启动→main 5.778ms→app 68.952ms→window 111.250ms→submit 124.389ms→GPU 128.666ms；空闲3秒footprint 66.6MB，均为5次中位数 |
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

## 独立审查

待整分支独立审查；尚未据此标记最终审查完成。
