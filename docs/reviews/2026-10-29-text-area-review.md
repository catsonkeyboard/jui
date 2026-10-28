# 计划二：多行文本框执行与整体审查

日期：2026-10-29。基线：`e973314`；实施分支：`codex/text-area`。本会话对完整分支进行代码审查与回归验证，没有另行启动独立审查代理。

## 交付与实施裁定

- Tasks 1–2：共享行表、源UTF-16下标、硬换行/空行、二维命中、软换行亲和性、逐行选区。两个任务共用接口，合并提交。
- Tasks 3–4：沿用一个TextEdit，增加多行编辑、视觉导航、目标列及事件间新鲜布局；合并提交，不引入事件队列或帧内递归构建。
- Task 5：`textArea`、`textAreaBase`、`lines(min,max)`，自然高度/显式高度、布局后内部滚动几何和纵向测试查询。
- Task 6：二维鼠标选择、三击视觉行、逐行绘制；只向图集请求可见行及上下各一行。
- Task 7：多行输入法范围/几何、actualStart桥接、计划一选项兼容；旧回调不填写actualStart时保持原请求起点。
- Task 8：TextAreaDemo包含固定备注、回车发送/Shift回车换行的自动增高消息、只读预览，稳定key保留编辑状态。
- Task 9：多行steady-frame分配、5000 UTF-16单位事件耗时门槛、新FFM调用的原生smoke及README已完成；原生示例已构建启动，截图受系统权限阻碍。

测试按相关行为集中在TextAreaTest、TextAreaRegressionTest等类，没有机械地为每种尺寸和滚动分别建文件。字形baseline沿用已有坐标数组，不额外复制进逐行表。

鼠标三击测试按平台规则连续发送count=1/2/3；孤立的首次count=3会被既有引擎重置为1。选区与caret可能同RGB，绘制断言也检查alpha，而不是误把caret算成选区。

## 审查发现与修复

| 问题 | 先失败的证据 | 最终处理 |
|---|---|---|
| 横向折叠选区丢失软换行亲和性 | 回到同一下标却变成下一行开头 | 从被选中的anchor/caret端继承亲和性；正反向均有回归 |
| 光标已可见时，移动没有取消先前滚轮惯性 | 右移后等待300 ms，offset仍从0变为200 | reveal无论是否移动offset都取消该框glide；用户wheel/拖条仍可接管 |
| marked text含CRLF时，提交归一化导致原生替换范围错位 | 预期`ab\nX`，实际`ab\nTX` | 先按原显示文档校正字素边界，再映射提交后的范围；覆盖组字内部/之后的替换及撤销 |
| 反复重排未改变硬段，编辑耗时超标 | 最初native编辑p95约4.75 ms；复用测试出现201次重新shape | 复用有界缓存内的硬段结果再合成整文；相同测试只需shape新增尾段 |

性能优化没有牺牲光标精度：尝试用字形原点代替部分原生位置后，连字/kerning对照暴露误差，因此最终舍弃该方案和额外的font-advances FFM接口。所有光标边界仍由`CTLineGetOffsetForStringIndex`在cache miss时计算。

## Review Focus

1. **事件间布局新鲜**：插入LF后不跑帧，立即Up和继续输入；原生caret查询在下一帧前看到新行。ensureLayout基于最新display及尺寸，纯移动不重新shape。
2. **软换行亲和性**：同下标上下游、行尾命中、End→Home、横向两个停点、选区折叠、纵向目标列、翻页/Emacs别名和撤销均有测试。
3. **换行索引**：空文、尾空行、LF/CRLF/独立CR、emoji与组合字素；actualRange校正及硬段缓存合成后的glyph/line/caret源下标有回归。
4. **滚动争用**：初始文末、wheel后blink不回跳、按键揭示、wheel接管待处理reveal、glide取消、轨道翻页、外层滚动和列表第二轮副作用次数已测试。
5. **选项不退化**：只读、禁用、IME关闭时的组字和changed/discard次数；password在area无效；拒绝粘贴保留选区/历史；回车发送及Tab钩子已验证。原有单行与密码测试仍全部通过。
6. **分配、绘制与所有权**：native静态帧0 B；长文只发射可见glyph；查询复用输出数组；排版缓存同时受条目、glyph和源UTF-16预算约束。CTLine/typesetter/attributed string在排版结束释放，不为查询保留它们。

上述为代码及自动化证据，不等同于实际键盘、鼠标或输入法的原生交互验收。本次没有发现尚未修复的Critical/Important项。

## 最终自动化结果

- `./build.sh test`：**423 passed, 0 failed**（基线375）。
- `./build.sh alloc`：native原有输入框/控件/列表门槛继续通过；多行普通、长选区、静态组字均为每控件**0.0 B**，JVM均为56.0 B；图集未满。
- `./build.sh listbench`：p50 **0.186 ms**、p95 **0.253 ms**、max **1.488 ms**；图集未满。
- `./build.sh ffi-check`：JVM和native均`FFI ok`，确实调用新Core Text offset handle。
- `git diff --check`：通过。

硬件：Apple M5 Pro、64 GiB、arm64、macOS 26.6.1。多行耗时样本为5000 UTF-16单位的重复中英段落，四个320×160框，其中一个聚焦；计时1000次，另有100次预热，编辑内容不累计增长，字符串和计时数组预建。

| native事件＋更新/绘制 | p50 | p95 | max |
|---|---:|---:|---:|
| 文本不变滚动 | 0.045 ms | 0.054 ms | 0.181 ms |
| 插入LF后立即Up | 0.346 ms | 0.455 ms | 1.246 ms |

两者通过p95≤4 ms、max≤8 ms；这些是本轮局部门槛，不是完整应用或大文件的性能承诺。最终生产代码完成后重跑三项原生门槛；其后只补充测试及文档，没有改动生产实现。

## 原生与人工验收边界

`./build.sh native-demo TextArea --print-window`成功构建并启动，打印window编号。`screencapture -o -x -l <编号>`返回`could not create image from window`，只读检查`CGPreflightScreenCaptureAccess()`为false；没有尝试修改权限或绕过限制。Computer Use也无法绑定这个非应用包的可执行进程。因此没有生成截图、没有确认原生外观，也不宣称输入法或拖选交互通过。截图需要具备屏幕录制权限的环境再补。

待用户以`./build.sh native-demo TextArea`核对：第二行/软换行处拼音候选框及提交取消；跨行拖选、Shift/Home/End；滚轮离开光标、纵向拖条；Enter发送/Shift+Enter换行；Edit复制粘贴/撤销；只读和关闭输入法。

已知范围：String整文存储、即时测量所有硬段，不含估高/虚拟文档布局；不支持完整bidi视觉编辑、定时拖选autoscroll、富文本、编辑器接管输入或右键菜单。没有开始计划三；没有合并或推送，也没有把计划一的人工记录改写为本轮结果。
