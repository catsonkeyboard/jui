# M5 外壳实施与整体审查

日期：2026-10-30。分支 `codex/m5-shell`，基线 main `83d1fb7`（490 测试）。本会话对完整分支自行审查；不合并、不推送，保留 `.idea/`。

## 交付

- `3cf6802` split：两 pane 相接于 1 DIP 线，8 DIP 手柄（按压聚焦、可拖、方向键 ±8 DIP），每侧至少 40 DIP，第一 pane 让位且宽度记忆恢复，changed() 报告。
- `8cc1153` tabs：点击/方向键/Home/End（环绕）选择、焦点跟随、选中项强调条，selected 夹取，changed() 报告。
- `2860719` modal：铺满背景 + 居中面板；点背景/Esc 关闭、dismissed() 报告一次；**焦点圈定**（Tab 限于最上层 modal 内、关闭还原）；Esc 先于应用快捷键给最上层 overlay。
- `d484682` popover：布局期 `placeOn` 按 anchor 本帧盒子定位（下方起点对齐、贴窗上翻/平移）、minWidth=anchor 宽、点外部（anchor 除外）/Esc 关闭、dismissed() 报告。
- `12ad03f` 应用菜单：`App.menu` 注册一次，子菜单 `menuNeedsUpdate:` 懒重建，选择/快捷键后 builder 在焦点窗口下一遍重跑、chosen() 报告一次；焦点窗口经 `MacWindow.Input.engine()` + `windowDidBecomeKey:` 跟踪。
- `6d3aa1c` NotesDemo：6.15 的 Obsidian 式外壳（搜索侧栏 + 1 万条笔记 + split + tabs + ⌘P 命令面板 modal + 信息 popover + 应用菜单）。

## 引擎机制（全部有测试锚定）

`Node.MODAL`（commit 收集打开的 overlay；Esc 路由与 moveFocus 圈定取最上层）、`Node.ESCAPABLE`（popover 同一 Esc 通道，不圈定焦点）、`Node.placeOn`（FlexLayout.placeAbsolute 的布局期附加定位）、`Engine.appMenuChose`（帧内下一 pass 重跑 builder）、`Input.pointerDown` 的 outside 关闭（panel 与 anchor 子树内不关）。覆盖顺序不加新树根：靠既有绘制/命中顺序（absolute 后画、后者在上；命中取 paint 序最后一个）。

## 审查中发现并修复的分配问题（先测出再修）

| 问题 | 观察 | 修复 |
|---|---|---|
| split/tabs 初版用捕获式 `children` lambda | 外壳稳态帧 120 B（关闭）/376 B（modal 开） | 按 button 的 enter/exit 惯例重构，框架内零 lambda |
| modal/popover 每帧 `Color.rgba(...)` 新建 record | 同上 | 静态缓存 MODAL_DIM/MODAL_SHADOW/POPUP_SHADOW |
| tabs 文字颜色每帧 `new Color(int)` | 同上 | 直写 `node.textColor`（int） |
| tabs 的 `local` supplier 捕获 n | 同上 | 非捕获 `() -> new long[0]` + 按需增长 |

修复后 `./build.sh alloc` native：**m5_shell_closed=0 m5_shell_open=0**（场景自身全静态无捕获，测到的即框架份额）。测试侧还纠正过两个测试自身的错误（元素中心在窗外的滚动坐标、拖动后缺 release），不冒充产品修复。

## Review Focus 证据

1. **覆盖层顺序**：ModalTest 点背景关闭、内容不构建；新增 testWhatIsBelowTakesNothingWhileItShows——modal 打开时滚轮不下传（下方容器不动），关闭时正常。
2. **焦点**：ModalTest 四步——Tab 入内、循环不出、环绕、关闭还原到打开前的按钮。
3. **anchor 新鲜度**：placeOn 是布局期机制，每帧按 anchor 本帧盒子定位；PopoverTest 覆盖打开/翻转/平移/重开；anchor 消失由构建顺序保证（应用不建 popover 即不显示，模型见测试）。
4. **菜单会话**：AppMenuTest 选择恰一次、disabled 不报告、多次选择累计；MacWindowTextTest 原生侧：应用子菜单入栏（Edit 之前）、标题/快捷键/delegate 就位。快捷键与点击同一 `juiBarItem:` 路径。
5. **split 让位**：SplitTest——收窄到 80 时第一 pane 收到自身下限、firstWidth 不动、放宽恢复。
6. **分配**：alloc 门禁新增外壳场景（关闭与 modal 打开），native 均 0 B；旧场景不回归（controls/inputs/list 0 B，card 200 B）。

## 最终自动化结果

| 检查 | 结果 |
|---|---|
| 全套测试 | **516 通过 0 失败**（新增 26：Split 6、Tabs 4、Modal 5、Popover 5、AppMenu 2、NotesDemo 3、原生菜单栏 1） |
| native alloc | 外壳关闭/打开均 0 B；controls/inputs/list 0 B；card 200 B |
| native listbench | p50 0.176 ms、p95 0.230 ms、max 0.324 ms，图集 742 未满 |
| JVM/native ffi-check | 均通过（含 GPU 图集增长读回） |
| 原生示例 | `native-demo Notes --print-window` 构建启动成功（window=26672）；进程随后手动停止 |

硬件：Apple M5 Pro、64 GiB、macOS 26.6.1。

## 遗留（入账，不阻塞）

- modal 打开期间，焦点在 modal 外的 CLICKABLE 元素上时 Enter/Space 理论上仍可激活它（焦点通常已随首击入内；未构造出实际场景，记录备查）。
- 菜单栏子菜单的 enabled/checked 视觉状态在「从未打开过」的子菜单里可能滞后（每次展开经 menuNeedsUpdate 刷新；选择报告不受影响，chosen() 按重跑时的状态判定）。
- popover 无 MyGO `AttachTo` 的任意锚点组合与 RTL；split 无 percent/vertical 变体；tabs 无 Base 拆分——均为计划声明的范围外。
- 计划二/三/四及本轮的原生人工核对项仍待用户执行（清单见 README 各节）。

无已发现的 Critical/Important 遗留。保留分支，等用户决定合并；不推送。
