# 计划三：原生右键菜单实施与整体审查

日期：2026-10-29。分支：`codex/context-menu`；基线：计划二的`6937798`。本会话自行审查完整分支，没有启动独立审查代理。没有合并或推送，计划二人工项保持待办。

## 交付与任务记录

- Task 0：建立分支及执行记录，基线全套423通过；保留未跟踪的.idea。
- Task 1：Menu/MenuItem、不可变MenuModel、子菜单、disabled/checked/shortcut、句柄有效期和一次chosen；API测试先编译失败后通过，全套426。
- Tasks 2–4：共用Input和菜单会话，合并集成；请求/collect/选择/响应、鼠标/键盘、Tester、输入框默认菜单及定向编辑命令。API测试先编译失败，补齐后全套439。
- Tasks 5–6：NSMenu模型、独立target、tag/action、释放、异步post、Ctrl单击重置、common modes、关闭通知；原生模型测试先编译失败，接入后通过。FFI在JVM和native实际调用通过。
- Task 7：ContextMenuDemo测试先编译失败，补齐示例后通过；预建回调接入AllocSpike，普通帧禁止调用builder；新FFM形状纳入FfiCheck。
- Task 8：完成本次整体检查，补充会话竞态、失效Element、普通rightClicked/焦点、排队后关闭、节点回调释放回归；最终453测试通过并重跑三项原生门槛。

## 实施裁定

1. **父级先完成再构建子菜单**：submenu的builder延迟到父builder返回后，在同一次Menu.build内递归运行。这样`submenu(...).disabled(true)`能在子项chosen之前生效；不采用选择时先collect一次、再响应一次的重复调用方案。
2. **项的稳定匹配**：冻结时按深度优先分配数字ID，供NSMenu的tag路由；响应按包含各级位置与label的完整key匹配。前面的其他子树增加条目使平坦数字ID改变，不应误拒绝仍位于原路径的同一叶子；移动或重命名当前路径仍拒绝。
3. **会话token**：Engine以不可复用的Session对象身份检查回调，另检查state实例/born及edit实例；Window的排队任务使用递增generation。不是仅凭元素long id或pool index路由。
4. **选项时机**：菜单工作在view和endPass结束后，先settleInputs再resolve；不得让settle刚产生、view还没看到的changed被endPass清掉。选择产生dirty时沿用正常下一pass写回。
5. **已接受选择独立保存**：选择结果进入独立响应队列，与当前打开请求分离；关闭清空队列，下一帧仍验证目标及当前策略。新菜单不能覆盖已经接受但尚未交给view的动作。
6. **FFI probe的额外upcall**：为不打开系统阻塞菜单就能验证BOOL+word+point+word的实际ABI，增加对应probe upcall形状并由NativeImageFeature自动登记。菜单action和windowWillClose本身仍仅用既有method1。这是对原计划“不新增任何upcall形状”的必要补充。

## 整体审查发现与修复

| 问题 | 失败回归 | 修复 |
|---|---|---|
| 重复取消回调覆盖已经接受的选择 | 正结果后再给0，动作次数预期1、实际0 | 接受后离开当前会话并入响应队列，旧回调不再有资格改结果 |
| 下一帧前的新请求覆盖已接受选择 | 选择one后立即再次右键，动作次数预期1、实际0 | 新请求与已接受结果分离；先派发动作，正常重建后收集新菜单 |
| 未复用的池节点继续持有菜单闭包 | NodePool.reset后旧节点的contextMenu仍非null | reset时清理上一pass所有已用节点的回调，不只在重新open该槽时清理 |

三项均先看到测试失败再修复。完整审查没有发现尚未处理的Critical/Important项；这不表示原生人工交互已验收。

## Review Focus逐项结果

1. **时序与重入**：Host.Memory证明普通帧不调用builder，打开只collect一次，选择自定义项仅响应一次；WindowMenuPostTest证明popup不在调用者栈里跟踪，排队后关闭返回取消。实际NSMenu由MacApp.post显示，action仅记录ID。
2. **迟到结果**：旧会话、重复结果、取消后的结果、目标被prune后复用同key、改菜单路径、改输入模式和关闭均有回归。平台模型不持有Element或node index；保留结果的队列仍重新验证目标。
3. **文本隐私与选区**：只读菜单减项，密码Cut/Copy禁用；打开后变密码不能通过旧Cut导出内容。跨CRLF/emoji的二维右键、选区内保留、选区外折叠、组字提交及changed一次有测试。A菜单Paste在焦点改B后仍只作用于A并调用A的onPaste。
4. **press生命周期**：菜单接管后无click/rightClicked，取消后普通点击正常；没有菜单仍报告rightClicked；普通元素菜单不抢焦点。合成NSEvent测试故意省略Ctrl主按钮UP，下一次普通DOWN正确为button=0。
5. **run loop与资源**：MacWindowTimerTest在tracking mode下实际获得帧，覆盖和cancel正确；FFI smoke也在tracking mode完成延迟回调。NSMenu树和模拟action测试验证tag、enabled/state、快捷键以及20轮释放后owner map归零；系统/显式关闭通知恰好一次。真实系统菜单跟踪中的用户手势仍需人工核对。
6. **不变帧成本**：给100控件和20输入框登记预建回调后native分配仍0 B，idle builder次数为0；普通不变帧不重建快照。节点池回调清理已加入最终性能复跑。

## 最终自动化结果

最终生产实现（含节点池清理）串行运行：

| 检查 | 结果 |
|---|---|
| `./build.sh test` | **453 passed, 0 failed**，基线423 |
| `./build.sh alloc` | native控件、hover控件、列表行、文本输入框均**0.0 B**；多行普通/选区/组字同为0.0 B |
| 菜单登记的idle builder | native及JVM均**0**次；不把事件级打开/选择分配算进steady frame |
| `./build.sh listbench` | p50 **0.184 ms**、p95 **0.247 ms**、max **0.434 ms**，图集未满 |
| `./build.sh ffi-check` | JVM与native均**FFI ok**，真实调用两项新downcall及popup probe upcall |
| `git diff --check` | 通过 |

旧多行门槛继续通过：5000 UTF-16单位的重复中英段落、四个320×160框，预热后1000次。

| native事件＋更新/绘制 | p50 | p95 | max |
|---|---:|---:|---:|
| 文本不变滚动 | 0.045 ms | 0.050 ms | 0.063 ms |
| 插入LF后立即Up | 0.341 ms | 0.371 ms | 1.285 ms |

硬件：Apple M5 Pro、64 GiB、macOS 26.6.1。结果只覆盖所列局部门槛，不代表菜单打开耗时、大文件或完整M5外壳性能承诺。

## 原生示例与验收边界

`./build.sh native-demo ContextMenu --print-window`构建成功并启动，日志打印window编号；这是启动证据，不是交互或外观验收。当前`CGPreflightScreenCaptureAccess()`仍为false，未改权限或绕过限制，未生成系统菜单截图，也未用合成图代替。

手动运行`./build.sh native-demo ContextMenu`核对：
- 右键、Ctrl+单击、取消后的普通单击；系统菜单位置和子菜单、勾选及禁用项。
- Shift+F10/菜单键、应用快捷键与onTextKey竞争；普通元素不抢焦点。
- 中文组字、选区内/外右键、跨行选区、Copy/Paste/Cut/Undo、只读和密码策略。
- 菜单跟踪时定时帧继续、关闭窗口和多窗口不串线。

没有重做计划二人工验收，没有开始richText/编辑器接管输入；没有实现其余M5控件、菜单按钮、本地化或无障碍。旧window/view释放问题保持原有范围，本轮只管理新增菜单资源和必要的关闭通知。
