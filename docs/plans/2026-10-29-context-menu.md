# 计划三：原生右键菜单 `contextMenu`

**状态**：用户已确认，在`codex/context-menu`分支执行；基线423个测试通过。仅本地分步提交，不自动合并或推送。

**Goal**：提前完成M5中的原生右键菜单，给单行和多行输入框补上默认编辑菜单；不把它扩展为整个M5或编辑器设计。

**基线**：当前分支`codex/text-area`，HEAD为`6937798`；本地`main`仍为`e973314`。计划二实现和审查已经提交，已有记录为423测试及三项原生门槛通过，本次写计划未重新跑测试。计划二的截图和原生人工核对仍待办，不自动改成通过，也不擅自合并到main。

**执行位置**：本计划获准执行后，从上述计划二结果新建`codex/context-menu`，依次实施任务并整体审查。若届时计划二已被用户要求合并，先核对基线，再从包含相同实现的main建立分支。每个逻辑任务测试后本地提交，提交信息遵守现有Co-Authored-By约定；最终等用户决定合并，不推送。

**依据**：[UI API设计](../design/2026-10-24-ui-api-design.md) §6.10、§7.4；[计划一后续范围](2026-10-27-text-input-options.md)；MyGO本地参考版本`80499935`的`ui/menu.go`、`internal/darwin/menu.go`、`ui/input.go`。本次已重新读取菜单核心实现，不以先前研究报告代替当前j-ui代码检查。

## 一、范围与非目标

交付：
1. `Ui.contextMenu(Context, Element, Consumer<Menu>)`；菜单项、分隔线、子菜单、禁用、勾选和快捷键标注。
2. `MenuItem.chosen()`即时模式响应，不在原生跟踪回调里运行应用菜单构建器。
3. 鼠标右键、macOS Ctrl+单击、Shift+F10和菜单键；嵌套元素最内层菜单优先。
4. 单行和多行输入框的默认菜单，自定义菜单的`editItems()`，只读/密码策略。
5. 请求、显示、选择、取消、过期回调、目标消失和窗口关闭的生命周期管理。
6. 修复Ctrl+单击丢失release后污染下一次普通单击的问题；让现有计时器及首帧轮询在菜单跟踪时仍可工作。
7. Tester支持、原生构建/桥接测试、ContextMenuDemo、原生门槛、文档与整体审查。

不交付：
- 应用菜单重构、菜单标题国际化、菜单按钮、工具栏菜单、菜单栏额外角色。
- `split`、`modal`、`popover`、`tabs`，不把它们算作本轮M5进度。
- `handleInput`、`handleTextInput`、richText、可选择普通文本或编辑器；它们留在下一份独立计划。
- MyGO的列表选中模型：j-ui没有统一的行选择状态，右键不能替应用擅自改选。行菜单由应用用稳定key绑定。
- Windows/Linux菜单、RTL菜单、Look Up、Services、拼写和Speech子菜单。
- 顺带修复MacWindow已有的window/view资源释放问题或计划一其余Minor；只补菜单生命周期直接需要的关闭通知。

## 二、公开API与使用约定

遵循现有设计的静态Ui入口，本轮不增加同义的`Element.contextMenu`或`Element.menu`。

```java
Element target = box(context).label("note");
contextMenu(context, target, menu -> {
    if (menu.item("重命名").chosen()) beginRename();
    menu.separator();
    menu.submenu("标记", submenu -> {
        if (submenu.item("置顶").checked(pinned).chosen()) pinned = !pinned;
    });
    if (menu.item("删除").disabled(locked).shortcut(DELETE_NOTE).chosen()) deleteNote();
});
```

公开类型放在`jui.ui`：

| API | 语义 |
|---|---|
| `void Ui.contextMenu(Context, Element, Consumer<Menu>)` | 为本pass的target登记构建器；每次构建都要登记 |
| `MenuItem Menu.item(String label)` | 添加普通叶子项；label不能为null |
| `void Menu.separator()` | 添加分隔线 |
| `MenuItem Menu.submenu(String label, Consumer<Menu>)` | 添加子菜单；允许对子菜单整体disabled |
| `void Menu.editItems()` | 插入本次菜单所针对输入框的编辑项；普通元素上不添加任何项 |
| `MenuItem.disabled(boolean)` | 禁用当前项；子菜单禁用同时阻止选择其后代 |
| `MenuItem.checked(boolean)` | 叶子项显示勾选状态，不替应用切换状态 |
| `MenuItem.shortcut(Shortcut)` | 标注快捷键，不替应用注册窗口/元素快捷键 |
| `boolean MenuItem.chosen()` | 本次选择与该叶子匹配时报告一次，收集菜单时永远false |

- target必须属于同一个Engine且仍处于有效pass；跨窗口、过期Element等误用沿用Dev.fail约定。重复登记同一元素以最后一次为准；传null撤销显式登记，输入框的隐式默认菜单仍保留。
- 登记只写Node字段/flags，不执行构建器，不在每个不变帧创建菜单模型。Node.reset清除回调引用，避免池化节点保留过期视图。
- 显式菜单不自动让普通元素可Tab聚焦；需要键盘入口的元素仍由应用调用`.focusable()`。
- 构建器只在打开时收集一次，以及普通自定义项被选择后的响应阶段调用一次。取消不调用第二次；内置编辑项直接派发EditCommand，不重跑应用构建器。
- Menu/MenuItem只在本次构建器执行期间有效；所有方法均做生命周期检查，不复用一个会在下次重新变为有效的公开句柄。回调返回后的访问在dev抛错，非dev不执行。
- 子菜单构建器在收集和响应阶段都执行；`chosen()`仅叶子有效。一次响应阶段同一项多次查询仍最多返回一次true。
- 快捷键在菜单跟踪期间可由系统作为菜单加速键选择项，但本轮不把它绑定为菜单关闭时的全局快捷键。
- 示例中的构建器可使用lambda；零分配约束针对框架及预建回调的steady frame，不掩盖应用每帧创建捕获lambda的分配。

## 三、事件与文本行为

### 3.1 鼠标和目标选择

- 输入使用最后提交的HitList，从hover链最内层向外找第一个启用的显式菜单或输入框隐式菜单。禁用元素没有菜单；父级后设置disabled也必须生效。
- 命中input时，它的默认菜单优先于祖先显式菜单。要替换某个输入框的默认菜单，须给该输入框本身登记菜单；本轮没有Selectable容器的特殊继承规则。
- macOS在右键press打开，不等release。菜单接管该次press：清理pressedId/pressed/pressPending，不产生clicked/rightClicked，也不遗留拖动；没有菜单的元素保留现有rightClicked语义。
- 普通元素右键不移动键盘焦点；输入框右键获得焦点，不伪造普通单击或提交事件。
- 选择菜单项、按Escape、点外部取消，都不把原press的release补成内容点击。菜单关闭后允许下一个真实单击正常工作。
- Ctrl+主按钮每次DOWN都重新计算`ctrlClick = hasControl`，不是仅在有Control时写true；UP正常清零。测试故意省略上一次UP，验证下一次无Control的主按钮仍为button=0。
- 运行时只持久化元素identity和必要的状态身份，不持久化pool node index、Element或本次view的临时位置。

### 3.2 输入框选区和默认菜单

- 打开输入框菜单前，按已有焦点切换策略结束旧输入框组字；目标输入框若正在组字，先提交并调用discardMarkedText一次，再以新鲜布局做二维命中。
- 命中下标位于现有`[selectionStart, selectionEnd]`内（包含端点，同参考版本）时保留选区；否则折叠到右键命中点。多行按x/y和scrollY定位，单行密码框经mask索引映射。
- 只读框仍可获得焦点并选择，不激活IME、不画caret。密码框仍沿用计划一的mask和IME策略，本轮不改变安全输入策略。
- 键盘打开菜单不移动caret/选区，也不执行鼠标定位。

默认项顺序与macOS参考实现一致，沿用现有Edit菜单的英文标题：

| 输入框 | 项目及启用条件 |
|---|---|
| 普通单行/多行 | Cut、Copy（有选区）、Paste（始终启用）、separator、Select All（文本非空） |
| readOnly | Copy（有选区）、separator、Select All（文本非空） |
| password | 与普通框同顺序，但Cut/Copy禁用且不能泄漏或删除密码；Paste/Select All照常 |
| disabled | 不打开，也不回退为可操作的文本菜单 |

自定义菜单替换默认菜单；空构建器表示不显示，不偷偷回退。`editItems()`把上述项插入当前位置，顺序不变，可以和普通项、子菜单组合。只有分隔线而没有普通项/子菜单的模型不弹出；只有禁用项的菜单可显示。

### 3.3 键盘优先级

- 新增`Key.CONTEXT_MENU`并显式映射macOS keycode `0x6E`；放在既有可打印键范围之外，不破坏当前字母、数字及标点的ordinal范围判断。
- 菜单键必须无修饰，F10必须恰为Shift；其他组合不打开。onTextKey仍先行；它取走就不打开。已登记的焦点链/窗口快捷键先于默认菜单动作。
- 必须让`TextEdit.wants`对新菜单键返回false，避免现有默认“打印字符”分支先吞掉它；同步检查`typesText`和所有涉及Key的switch。
- 未被取走时从focus链寻找最内层可用菜单；没有目标时保留普通快捷键路由。
- 输入框菜单显示在当前caret下方；普通菜单显示在菜单所属元素的`x, y + min(h,32)`。使用最后提交的窗口DIP几何，坐标由平台映射到flipped view，不拿屏幕像素当DIP。
- 菜单打开时caret可能在clip外，继续使用现有夹到输入框边缘的矩形策略；不顺带改变计划二IME约定。

## 四、运行时状态机与过期保护

建议独立`jui.ui.engine.MenuState`，避免把菜单的多个阶段混到Input的大量条件里。

```text
无请求 → asked(identity, textTarget, point, token)
       → 当前视图构建/disabled传播/选项settle
       → collect / 冻结菜单快照
       → 完成最终layout和commit
       → Host.popupMenu（平台排队，不在当前draw/event里跟踪）
       → selected(token, itemId) 或 cancelled(token)
       → 下一次build的响应阶段或定向EditCommand
       → 清理选择；必要时consumed触发正常重建
```

1. **先完整构建再收集**：Node存登记的构建器；在view结束且disabled传播、输入选项settle后解析请求。不能在`Ui.contextMenu`调用点就执行，否则父级稍后disabled、输入框稍后readOnly会产生错误菜单。
2. **重建不丢请求**：asked和选中结果不在buildPass入口清空。一次打开只collect一次，多pass/列表第二轮不能重复显示；提交前若目标消失、被禁用或不再登记菜单则撤销。
3. **响应至多一次**：普通项选择在当前目标的构建器里检查。首次true即标记consumed；回调返回后清掉选择，即使没有匹配项，也不能继续等到另一个pass/帧误匹配。
4. **冻结快照**：平台接收独立不可变菜单树，含每项ID、label、类型、enabled、checked、shortcut、children及内置命令映射；不能把池化Node/Menu句柄交给NSMenu。
5. **项的身份**：每次收集按深度优先顺序给所有条目编号，包括separator/submenu。响应匹配ID、叶子角色、完整祖先路径及labels；改名、改位置、移到另一个同名子菜单都不能触发旧动作。disabled父节点的后代也不可选，哪怕父节点是在submenu构建后才disabled。
6. **会话身份**：每次打开单调递增token；旧回调、重复回调、取消后的回调和新菜单覆盖旧菜单后的结果均丢弃。检查owner的state实例仍为当前实例，不仅检查long id，防止prune后复用身份。
7. **文本命令定向**：绑定打开时的textTarget identity/state/edit实例。焦点后来变成B，A菜单的Paste不得粘到B；A仍有效时命令作用于A且不抢回焦点，A已消失/换模式/禁用则丢弃。执行时重新遵守当前readOnly/password策略。
8. **复用编辑路径**：把`Input.runCommand`改为显式接收目标identity/state，不能继续从focusedId取onPaste。主Edit菜单仍以当前焦点为目标，右键菜单以捕获目标为目标；共享剪贴板、onPaste、撤销和changed写回逻辑。
9. **编辑命令的时机**：原生结果只入队/请求帧；下一帧确认目标有效并结算选项后执行一次。执行导致dirty则请求正常下一pass/帧，沿用原输入框changed约定，不能在原生tracking action里递归buildAndLayout。
10. **异常清理**：构建器或Host出错都在finally清理active/collecting/pending标记；下一次菜单仍可打开。不把异常变成半冻结快照或永久pressed状态。

菜单收集和响应都在UI线程，应用构建器只构建Menu并处理chosen，不在里面再构建Ui元素。响应可能修改view刚遍历过的应用集合，consumed负责下一pass重建。

## 五、平台、run loop和FFM

### 5.1 Host边界和原生菜单所有权

- 增加内部平台模型，例如`jui.platform.MenuModel`，与公开Menu构建器分离；新增`Host.popupMenu(model, x, y, IntConsumer result)`，结果0为取消。打开是事件级操作，可以分配快照、树和回调。
- `Host.Memory`记录模型、位置、结果回调；choose/close后清除持有。Engine仍必须防御保存下来的迟到回调，不能把安全性全交给Memory。
- `Window.popupMenu`用`MacApp.post`异步排队，实际弹出前再检查closed/token。Host方法返回不等于菜单已显示，禁止在`updateLayer`或painter调用栈里进入NSMenu嵌套循环。
- 新建`MacMenus`：所有NSMenu关闭autoenable；设置enabled/state、子菜单和keyEquivalent/modifierMask。每个popup拥有独立target，类只注册一次，selector用唯一前缀如`juiContextMenuItem:`。
- target通过map找到本次session，item.tag保存冻结ID；action只记录选择，不调用应用builder，不直接编辑TextRef。popup退出后至多调用一次result，取消/窗口关闭返回0。
- target必须在整个跟踪期间有强引用；在finally中移除target→session映射，释放root menu、target和本次创建的本地拥有引用。父NSMenu保有item/submenu，避免双重release。
- Window/MacWindow增加菜单所需的最小closed通知；同时覆盖显式Window.close与系统窗口关闭。关闭时cancelTracking、使token失效，posted任务和返回后的选择都无效；不借机重写已有window/view释放策略。

### 5.2 不让菜单跟踪暂停定时帧

现状：display link已加到`NSRunLoopCommonModes`，MacApp.post使用主线程队列；`armTimer`和两处首帧轮询却用默认模式的`performSelector:withObject:afterDelay:`。

- 增加`performSelector:withObject:afterDelay:inModes:`包装，使用现有COMMON_MODES常量构造一次并缓存NSArray。
- 修改armTimer及两处juiPollFrame调度。保留cancelPreviousPerformRequests的取消语义，取消/关闭后不得再唤醒已经关闭的view。
- 不是每帧创建NSArray，也不为这件事引入新的周期线程或NSTimer体系。
- 测试直接在`NSEventTrackingRunLoopMode`泵run loop，证明wake能执行、重设能覆盖、cancel能阻止；不能只测默认模式后宣称菜单跟踪通过。

### 5.3 新下调签名

| 用途 | descriptor，不含Native handle自身的函数地址参数 |
|---|---|
| `BOOL popUpMenuPositioningItem:atLocation:inView:` | `JAVA_BOOLEAN (JAVA_LONG self, JAVA_LONG cmd, JAVA_LONG item, CG_POINT point, JAVA_LONG view)` |
| `void performSelector:withObject:afterDelay:inModes:` | `void (JAVA_LONG self, JAVA_LONG cmd, JAVA_LONG selector, JAVA_LONG object, JAVA_DOUBLE delay, JAVA_LONG modes)` |

都放入Sigs.DOWNCALLS注册链，经Native的build-time handle调用；不在运行时临时建descriptor/handle。CG_POINT用结构参数内存，不能用地址长整型冒充按值结构。确认系统头文件的BOOL与ObjC封装惯例一致。

菜单action、windowWillClose沿用已有method1 upcall形状，不新增upcall ABI。ObjCClass按selector登记全局handler，所以不同类不能用同名selector捕获不同窗口；所有实例通过self路由。

FFI gate实际调用两个新handle：popup形状用专门的Objective-C probe接收point并返回BOOL，验证字段/参数传递，不打开阻塞菜单；调度形状实际调度并在tracking mode验证。真实NSMenu跟踪的鼠标/键盘操作单列人工验收，不把probe当成完整原生交互证明。

## 六、顺序实施任务

### Task 0：分支与执行记录

- [ ] 核对工作区；保留`.idea/`，不提交它；记录计划二未合并/未完人工项。
- [ ] 获准后建立`codex/context-menu`和执行ledger，记录基线；先跑现有全套，不能直接把423当成本次测试结果。

### Task 1：菜单模型与构建器

**Create**：`jui/ui/{Menu,MenuItem}.java`、`jui/platform/MenuModel.java`及`jui.ui.MenuTest`。

- [ ] 先写失败测试：顺序、子树、checked/disabled/shortcut、editItems无文本上下文时为空、收集时chosen为false。
- [ ] 测试父submenu在子项构建后才disabled，后代仍不能选择；保留separator序号，不靠事后删项破坏ID。
- [ ] 实现冻结快照和一次调用生命周期；所有方法的过期句柄与重复chosen都有测试。
- [ ] 针对API测试通过后提交，不为普通steady frame提前创建Menu对象。

### Task 2：引擎会话、登记与Tester

**Modify/Create**：`Ui`、`Node`、`Engine`、`MenuState`、`Host`、`Tester`；`ContextMenuTest`、`MenuLifetimeTest`。

- [ ] 先以Host.Memory测试请求→collect→popup→选择→下帧chosen，取消无响应；不依赖原生弹窗。
- [ ] 加`Tester.menu()`、`chooseMenuItem(String... labels)`、`closeMenu()`和坐标右键助手。路径有歧义时测试助手报错，不随意挑第一项。
- [ ] 一次菜单跨多pass/列表第二轮和blink帧只收集/弹出一次；chosen导致正常重建但副作用只执行一次。
- [ ] 改label/移动子菜单/删除项、禁用目标/父级、目标消失、state被prune再复用同key、重复/过期token均有回归。
- [ ] builder抛异常后active已清理，下一次能打开；空自定义菜单不回退。

### Task 3：右键、焦点与键盘路由

**Modify**：`Input`、`Key`、`TextEdit`、`MacKeys`；`ContextMenuPointerTest`、`ContextMenuKeyTest`、`MacKeysTest`。

- [ ] 最内层优先、input默认优先于祖先、disabled不可打开；普通右键保持焦点。
- [ ] 菜单接管press后无pressed/click/rightClicked；没有菜单仍有rightClicked；取消后下一次单击正常。
- [ ] Shift+F10/Menu key、焦点祖先、无目标和精确修饰键匹配；快捷键/onTextKey先取走的场景都验证。
- [ ] 新菜单键不能被TextEdit.wants吞掉；单行、多行、只读框均能通过键盘打开。

### Task 4：文本菜单与定向编辑命令

**Modify**：`Input`、`MenuState`、`Menu`、必要的`TextEdit/Engine`；`TextContextMenuTest`。

- [ ] 默认顺序、selection非空启用、readOnly、password、空字符串与空剪贴板；Cut/Copy不能泄漏密码或删除它。
- [ ] 单行/多行右键在选区内保留，选区外移动；含emoji、CRLF、软换行、纵向滚动；组字提交和discard恰好一次。
- [ ] 自定义替换默认，editItems混入子菜单仍作用于捕获的输入框；无文本目标时无编辑项。
- [ ] A菜单打开后焦点转B、A换模式、A被移除/禁用、A变只读/密码，验证不会粘到B或绕过新策略。
- [ ] Paste走正确目标的onPaste；多行LF、撤销、changed不重复；主Edit菜单原行为不退化。

### Task 5：原生NSMenu和延迟显示

**Create/Modify**：`MacMenus`、`MacWindow`、`Window`、`ObjC/Sigs/Native`；`MacMenuTest`及必要的Host测试。

- [ ] 测试NSMenu树、autoenable关闭、target/tag、状态、子菜单、快捷键；模拟唯一action selector，不能直接调用应用chosen。
- [ ] popup使用带point的准确ABI；坐标来自flipped view DIP，键盘anchor和鼠标anchor分别测试。
- [ ] post出去再关闭、跟踪期间关闭、旧popup迟到结果、新popup覆盖旧session；owned map和原生引用清理。
- [ ] 构建/释放菜单循环及不同窗口选择互不串线；正常测试不无限阻塞在系统跟踪循环。

### Task 6：Ctrl单击与common-mode计时器

**Modify**：`MacWindow`、`ObjC/Sigs/Native`；`MacWindowMenuTest`、`MacWindowTimerTest`。

- [ ] 先复现Ctrl主按钮DOWN→无UP→普通主按钮DOWN仍被当右键，再修复每次DOWN重新赋值；普通右按钮与主按钮状态不能互相污染。
- [ ] 三处afterDelay改common modes，缓存modes数组；在tracking mode运行wake、覆盖、取消和关闭测试。
- [ ] 原有display link空闲暂停、首帧回调、输入法键路由继续通过，不把所有窗口定时器改成持续刷新。

### Task 7：示例与原生门槛

**Create/Modify**：`ContextMenuDemo`、示例测试、`AllocSpike`、`FfiCheck`、`build.sh`帮助。

- [ ] 示例包含嵌套自定义菜单、disabled/checked/submenu、普通/只读/密码/多行输入；通过editItems混用编辑项，使用稳定key。
- [ ] 自定义项Chosen可更新状态；无callback的普通帧不会执行builder；菜单显示期间模型不因blink反复创建。
- [ ] AllocSpike预建回调，给控件和输入框登记菜单，未打开时native每元素仍0 B（既有0.5 B容差）；打开/选择时允许事件级分配，不把它混入steady frame。
- [ ] FfiCheck覆盖两个新handle；串行执行test、alloc、listbench、ffi-check，不并行清空build/classes。
- [ ] 构建native ContextMenuDemo；若截图权限仍为false，明确登记截图阻碍，不请求或绕过权限，不用合成图冒充系统NSMenu。

### Task 8：文档与整体审查

- [ ] README更新API、即时模式时序、默认文本菜单、键盘优先级、限制和验证结果；设计文档标记只提前完成M5的contextMenu。
- [ ] 全分支对照本计划和下面Review Focus整体审查一次；修复Critical/Important并添加回归，小问题和裁定如实记录。
- [ ] 最终代码重跑全套及受修复影响的原生门槛；测试数和性能数字只填实测值。
- [ ] 报告本地分支与提交、未完成人工项，不合并、不推送，不开始编辑器底层能力计划。

## 七、Review Focus

1. **时序与重入**：菜单跟踪不在draw/build内；多pass和列表第二轮不丢请求、不重复builder；原生action不运行应用代码。
2. **迟到结果**：目标/key/state/edit身份、菜单token、完整项路径都验证；旧结果不能命中新元素、不同窗口或新焦点。
3. **文本隐私与选区**：readOnly/password约束不会被editItems或焦点切换绕过；多行二维命中、组字提交、onPaste与撤销只发生一次。
4. **press生命周期**：菜单吞release后不粘住pressed，也不污染CtrlClick或下一次普通点击；无菜单时仍保留rightClicked。
5. **run loop与资源**：tracking mode下timer和post可推进；关闭会取消菜单/计时器，target/session map清理，ABI在native实际调用。
6. **不变帧成本**：登记不执行builder、不重建菜单树；框架在native steady frame继续0 B，既有文字/列表/滚动门槛不退化。

## 八、人工验收（不得由自动化结果代勾）

- 右键及Ctrl+单击在正确位置弹出原生菜单，Escape/点外面取消后下一次普通单击正常。
- 嵌套菜单、勾选、禁用及子菜单行为；快捷键标注符合macOS样式，窗口原有快捷键不因标注被自动注册。
- Shift+F10/菜单键在普通元素、单行、多行输入上定位正确；与onTextKey/应用快捷键竞争符合计划。
- 在输入框选区内/外右键，跨行选区和组字后的Copy/Paste/Cut/Undo正确；只读/密码菜单遵守限制。
- 菜单打开时等待定时状态更新，菜单取消/关闭窗口后没有卡死或迟到操作；多窗口互不串线。
- 系统菜单截图只能在获准的屏幕录制环境补齐；没有截图或没有实际键盘/鼠标核对时，明确保留待验收。
