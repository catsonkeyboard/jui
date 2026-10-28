# 计划四：编辑器底层能力

**状态**：用户已批准，在`codex/editor-primitives`分支执行；基线453测试通过。分步本地提交，不自动合并或推送。

**Goal**：让应用能用公开API实现一个自己的文字编辑表面，并用同一套排版/绘制能力显示多种样式的文字。交付接管输入、原始事件、最小Painter与richText，不把普通输入框直接改造成完整编辑器。

**制定时基线**：当前`codex/context-menu`为`861d0b6`，已有记录453测试及三项原生门槛通过；本次写计划不重跑测试。本地main仍未包含计划二、三；其原生人工项及截图仍待办，不自动改成通过。

**执行位置**：批准后从包含计划三结果的基线新建`codex/editor-primitives`，先重跑基线，再依次执行本计划全部任务、分步本地提交、整体审查。若用户先要求合并旧分支，则核对新的main再建分支；不擅自合并或推送，不提交.idea，提交信息遵守已有Co-Authored-By约定。

**依据**：[设计文档](../design/2026-10-24-ui-api-design.md) §6.12、§7.4、§7.5、§8；计划一至三；MyGO本地版本`80499935`的`ui/text_input_client.go`、`ui/handler.go`、`ui/richtext.go`及其Core Text实现。本次已重新查看本地参考和j-ui现有接口；按下面明确的Java约定实现，不机械复制Go的返回值和临时结构。

## 一、范围和必要依赖

本计划有两个交付部分，但共享一条实施分支和最后一次整体审查：

**A：应用自有文字表面**
- `Element.handleTextInput(TextInputClient)`：连接系统文字服务，应用拥有文档、选区、组字、编辑策略和撤销。
- `Element.handleInput(InputHandler)`：同步收到按键、指针、滚动和Edit命令。
- `Ui.canvas`、`Element.draw(Painter.Draw)`、最小公开Painter；几何使用现有TextLayout扩展。
- 自定义client的生命周期、resize通知、局部坐标、剪贴板及与计划三菜单的隔离。

**B：多样式文字**
- 持久、不可变Span/Spans，`Ui.richText`及Painter中的富文本绘制。
- 系统字体/monospace、字号/字重/可用斜体、逐段颜色、背景、下划线和删除线；整段共同换行、共享baseline。
- 修正字体canonical身份和逐glyph尺寸；布局样式与paint样式分离缓存。
- 图集有上限地增长，并安全更换Metal纹理。这是混合字体的交付依赖，不把“等M6”当作缺字的解决方式。

**不交付**：
- 完整编辑器、Markdown解析器、语法高亮器、文件IO、保存/搜索、行号、折叠、Tab缩进、rope、虚拟文档布局、多光标或矩形选区。
- richText的可点击链接、inline子元素、普通文字选择/复制、无障碍、图片/inline object、富格式剪贴板。
- 完整bidi视觉导航、任意命名字体/provider、OpenType features、每段语言、字距、波浪线、skip-ink、装饰自定义厚度/颜色、CJK合成斜体。
- 图集逐出/回收、纹理重排、M6全部图元或所有性能目标；本轮只做位置不变的有界扩容。
- split/modal/popover/tabs、应用菜单本地化，以及与新增路径无关的旧Minor或window/view释放重构。

## 二、公开API

### 2.1 接管文字输入

`TextRange`是不可变记录，半开区间`[start,end)`，单位UTF-16；无效数据抛IllegalArgumentException。null只在replacement参数表示“当前marked范围，没有则当前选区”。

```java
public record TextRange(int start, int end) {}

public interface TextInputClient {
    int length();
    String text(int start, int end, int[] actualStartEnd);
    void selection(int[] anchorCaret);
    boolean markedRange(int[] startEnd);
    void replaceText(TextRange replacement, String text);
    void setMarkedText(TextRange replacement, String text, TextRange selected);
    void unmarkText();
    boolean boundsFor(int start, int end, float[] rectAndRange);
    int indexAt(float x, float y);
    default void resized(float width, float height) {}
}
```

约定：
- client对象跨帧持有；同一client不应同时充当两个视图的client。文档可以共享，视图client及几何各自独立；dev检测同窗口重复绑定，不声称能检查所有跨窗口误用。
- `length/text/selection/markedRange`描述同一份当前文档，**marked text在文档内**；UTF-16下标是包含组字的绝对下标，不把码点、glyph或视觉行号当偏移。
- selection输出anchor/caret，保留反向选区；markedRange输出start/end，不是start/length。没有marked时返回false。
- text返回的String长度必须与actualStartEnd差值相符；client可按自己存储/字素规则校正请求。不可用时返回null。
- bounds输出`{x,y,width,height,actualStart,actualEnd}`，坐标为元素border box内部的局部DIP；空range给caret，非空range可只返回第一个可表示的簇。不可用或离屏返回false；indexAt不可用返回-1。
- `setMarkedText`的selected相对新marked text；替换范围相对修改前文档。空marked text移除当前预编辑片段并结束标记；unmark只去标记并保留文字。原选区如何恢复、如何分组撤销由client负责。
- client拥有字素、CRLF归一化、undo及只读策略。框架只做范围长度/溢出校验，不为了检查自定义文档把它整份复制为String。
- 每次mutating callback后，在下一次原生事件前重新查询selection/marked/caret，通知平台并请求帧；不等一次view重建才看见刚输入的文字。
- 热查询使用调用者预分配out，避免每个blink帧新建TextSelection/Rect。TextRange只用于事件期mutation，允许事件级分配。
- resized在首次提交和border box尺寸变化时，于原生几何查询之前调用；只更新几何，不修改文档/选区或递归构建view。移动、祖先滚动不触发resize，adapter负责坐标转换。
- 所有调用在UI线程，同步、不得阻塞等待UI线程，也不得在回调中重入buildAndLayout。应用从别处改文档仍用window.update/invalidate。

`handleTextInput`仅接入服务，不创建缓冲区或默认编辑器。非null时使元素focusable并使用text cursor；null断开，不擅自删除应用自己的focusable/cursor。只读自有表面可断开client，保留focusable/raw handler和自绘选择。

### 2.2 原始输入

```java
@FunctionalInterface
public interface InputHandler {
    boolean handle(InputEvent event);
}
```

- InputEvent类型：KEY_DOWN、KEY_UP、MODIFIERS_CHANGED、POINTER_DOWN/MOVE/UP/CANCEL、SCROLL、COMMAND。不再额外发送TEXT/COMPOSE；文字只经TextInputClient，避免双重插入。
- 只读getter提供Key、mods、repeat、button、clicks、局部x/y、dx/dy、precise、EditCommand、timeNanos及最近提交的width/height；不相关字段有明确默认值。这里“原始”不是公开NSEvent或硬件扫描码，本轮没有PhysicalKey、zoom或多指手势。
- 返回true表示取走，框架请求帧；false交给祖先handler和现有默认路由。指针DOWN被取走后捕获该button的MOVE/UP，即使离开元素；失焦、消失、禁用或解绑时发一次CANCEL并释放捕获。
- InputEvent每次交付创建独立有效期句柄，结束即失效，所有getter/服务方法都检查；旧句柄在后续回调也不能重新有效。事件级分配允许，不计作不变帧分配。
- 为菜单与编辑器补齐公开服务：`Context.layoutText(String/Spans, size, weight, maxWidth)`使用窗口共享TextEngine；`Context.clipboardText()`/`copyText(String)`访问Host。InputEvent提供同名剪贴板服务以及invalidate，不需要应用越过公开API访问Engine/Host或自己创建TextEngine。
- 内置INPUT不支持再覆盖handleInput/handleTextInput，dev报错；继续用onTextKey/onPaste及既有输入框选项。本轮不把内置key handling改写成应用handler。

### 2.3 公开绘制

```java
canvas(c).fill()
    .handleTextInput(editor)
    .handleInput(editor::input)
    .draw(editor::draw);
```

- canvas是无外观box，无内置文档、滚动或自然文字尺寸；应用明确给size/fill。
- `Painter.Draw`签名保持设计文档的`void draw(Painter painter, Rect localBounds)`；localBounds为`(0,0,w,h)`并缓存到尺寸变化。
- Painter最小操作：box/roundedBox、circle、line、text、richText、绘制已有TextLayout、clip/popClip、scale、animationFrame。圆用现有圆角矩形SDF，不提前增加新shader实例格式。
- 所有坐标相对元素border box，自动转换至窗口；继承祖先clip/opacity/disabled。draw在节点已有box/text之后、children之前执行，限于该元素border内侧，不能盖掉自己的边框；不借此重排原有节点绘制顺序。
- Painter只允许弹出自己push的clip，不能pop祖先clip。callback异常时finally恢复clip及绘制器状态，后续兄弟不被污染；不公开原始Scene。
- 圆角clip继续遵守现有“只记录最内层圆角”的限制，不宣称Painter已解决任意多层圆角交集。
- dev每次draw创建有独立有效期的Painter句柄，过期调用报错；native production使用借用的复用句柄，契约同Element，不承诺检测用户保存生产版池化句柄后再混入后续回调的全部误用。
- 需要连续帧才调用animationFrame；静止caret由应用c.after定时，不让自有表面永久60Hz刷新。

### 2.4 持久富文本

```java
static final Spans PREVIEW = Spans.of(
    Span.text("标题 ").fontSize(22).bold(),
    Span.text("正文 ").color(Color.rgb(0x334155)),
    Span.text("code").monospace().background(Color.rgb(0xE2E8F0)).radius(3)
);

richText(c, PREVIEW);
```

- Span是不可变值，链式方法返回新值；Spans.of防御复制并持有连接后的text、区段边界及layout/paint指纹。示例及benchmark预建Spans，不在每帧生成varargs或Span链。
- 本轮方法：text、fontSize、fontWeight/bold、monospace、italic、color、background、radius、underline、strikethrough。未指定size/weight/color从元素继承；字体仅system和monospace；布尔装饰只开启。
- italic请求原生可用斜体；缺少斜体face的CJK允许保持直立，不伪称已有合成倾斜。普通文字元素的任意font/provider另行设计。
- 拼接边界必须是整文的字素边界，不允许拆代理对或把组合字符拆成两个风格片段；非法Spans数据两种模式均抛IllegalArgumentException。空片段规范化，不用无字形的无限片段绕过缓存预算。
- 每段/每行共享baseline，行高能容纳最大实际字体；共同按段换行，保留源UTF-16、空行、CRLF及soft-wrap affinity。不是把每个Span当独立box或独立排版后拼起来。
- 背景先于glyph，装饰后于glyph；背景按区段在该视觉行的完整行高绘制，radius只圆角，首版不实现MyGO的glyph-hug背景。下划线/删除线用对应字体缓存指标，无skip-ink。
- 颜色边界不强制拆开连字：跨paint区段的单个连字glyph，按cluster起点决定其整glyph颜色；背景/装饰仍按区段几何。这一限制必须文档化，不能用近似caret位置拆连字冒充准确结果。
- richText沿用TEXT的测量、继承样式和现有singleLine语义；空richText自然高度仍为0。它本身不带选择、链接、焦点或可编辑状态。

## 三、路由、生命周期及兼容

### 3.1 按键取走与IME

- 对自有表面，已提交的焦点链/窗口快捷键先于raw KEY_DOWN；被快捷键取走的键不再交给raw handler。raw取走的键不再触发Tab/按钮/滚动/菜单默认动作。
- **明确不同于MyGO**：非组字期间，raw handler取走按键，或已登记快捷键取走自有client的按键，macOS也停止这一次interpretKeyEvents及无IME直接characters插入。否则Vim普通模式吞掉j后仍会收到j的文字。
- MacWindow.Input保留原void keyDown兼容入口，新增有默认实现的dispatchKeyDown返回“抑制原生文字阶段”；Window/Engine用新路径。内置输入框原路径不凭“wants了键”就抑制普通文字，不能修坏键入或Enter。
- IME正在组字时按键仍由IME独占；不把doCommandBySelector的补发变成绕过该规则的raw事件。内置输入框现有补发行为单独保留。
- KEY_UP只通知当时焦点链的raw handler，不注册快捷键或激活控件；不承诺每个KEY_UP必定有一个已交付的KEY_DOWN（IME或焦点变化可能取走前者）。
- 左键仍按现有规则先处理焦点再交raw；右键先交raw，true可阻止计划三contextMenu。滚动raw未取走时，继续原嵌套滚动/惯性逻辑。

### 3.2 client adapter

- 新增稳定的内部client slot与活动连接adapter，事件读取最后提交状态，不读取会被pool复用的Node下标；Node只存本pass声明，commit后发布。
- Engine的IME入口统一经活动adapter，内置TextEdit由适配器包装，**保留原来单独存compose的内部模型**，不为接口一致把它重写成另一套编辑器。
- query/mutation前检查窗口/Engine仍开放、焦点、disabled、state/born、client引用和连接代次。detach、swap、focus loss、blur、prune和close让旧活动连接失效；再次激活建立新连接，不让保存的旧adapter在焦点回来后复活。
- 自有client离开活动连接时，若仍有marked范围，unmark一次，再让Host discardMarkedText一次；失效先于回调，避免重入旧连接。内置readonly/disabled仍按原规则丢组字，关闭IME仍提交，不能统一成自有client的策略。
- inputMethod(false)扩展到已声明handleTextInput的元素，null声明也可以保留该选项。示例先handleTextInput再inputMethod，不擅自改变现有对普通box误用inputMethod的dev检查。
- 自有readonly由应用不连接client并限制handler实现；引擎不猜测client编辑策略，不自动合成TextRef/changed/undo。
- 每次mutation和raw事件处理的finally请求更新并清理有效期；异常由现有原生边界报告，不保证回滚应用已经执行的部分文档修改。旧adapter和pointer capture必须仍可释放，不形成永久活动状态。

### 3.3 几何、范围与现有输入框

- 自有client的几何在border box局部DIP，adapter使用最近提交的元素位置映射窗口DIP，MacWindow再转屏幕；指针逆变换相同。嵌套scroll只改变世界原点，不能把scrollY加两遍。
- custom bounds=false时，普通范围查询返回不可用；更新IME caret使用元素左上角1 DIP宽的fallback。内置输入框继续沿用原来的边缘夹取策略。
- 公开bounds out[4/5]为actualStart/actualEnd；**当前Engine原生桥接out[4/5]为actualEnd/actualStart**，adapter必须显式转换并测试，不能透传弄反。
- 给MacWindow.Input.textIn增加带actualRange out的兼容默认重载，旧Recorder/Host无需立即重写；自有client可校正substring的实际范围，原生NSRange不能继续伪造原请求范围。
- 原生NSNotFound转null replacement；使用long中间值校验start+length，避免整型溢出。自有client的字素调整由client决定，不能整文扫字素来替代其存储策略。
- 内置密码client仍只向IME公开composition文档，不公开真实密码；rect画在mask caret，actualRange却必须是composition坐标。统一边界直接依赖这一点，必要时修正旧mask actualRange Minor并加单独回归，不保留可越界或泄漏的错误。

### 3.4 菜单与关闭

- 主Edit菜单向自有焦点链发送COMMAND，应用handler拥有Copy/Cut/Paste/Undo策略；Paste and Match Style未取走时再尝试Paste一次，仍未取走则走既有快捷键回退，不能循环派发。
- 不给自有client擅自添加默认编辑菜单，因为没有canCopy/readOnly/password元数据。Menu.editItems仍只为内置INPUT提供默认项；自有表面用显式contextMenu和自己的编辑函数，剪贴板通过Context公开服务访问。
- 扩展计划三会话验证：打开自有表面菜单时也捕获client/连接身份，client被替换或解绑后旧菜单不能对同key上的新client执行动作。
- Window.windowClosed调用统一Engine关闭入口：取消菜单、捕获和活动文字连接，清除待派发结果；Tester增加close用于对应测试。保留原closeMenus兼容语义，不把它当作整个Engine关闭。
- NodePool.reset清理新增draw/raw/client声明引用，包括没有重新open的旧节点；状态prune前结束旧连接，不能先reset丢失要释放的client。

## 四、富文本排版、字体及图集

### 4.1 缓存和字体身份

- layout key包含text、layout样式及UTF-16区段边界、基准size/weight、可用宽度；color/background/装饰不进入shape key。布局样式相邻相同可规范化，paint边界另外保存。
- 相同内容但Spans对象不同按值命中；hash碰撞必须比较真实内容，不能只凭指纹。纯颜色变化不增加shapedLines；不变帧不编码、拷贝Span数组或拼接String。
- 延续计划二有界整文缓存和硬段复用；rich paragraph切片要重基font区段，但返回的glyph/line/caret仍是整文源下标。不能让改尾段失去所有未变化硬段的缓存。
- 字体resolver按family/size/weight/italic缓存；系统及monospaced构造沿用现有weight映射，斜体用Core Text symbolic traits副本。
- canonical不再仅用PostScript名+size，采用CFHash分桶加CFEqual核对完整CTFont语义；不同traits必须区分，等价字体副本必须复用，hash碰撞不能串字体。先做native probe守护，不以裸指针当持久字体身份。
- native字体仍按既有TextEngine生命周期保留，不宣称本轮已有无限字号/字体的回收策略；布局缓存和图集上限不等于字体对象缓存上限。

### 4.2 TextLayout/Scene

- 用一份可变attributed string应用layout属性，再让Core Text共同断行；不把颜色写入Core Text属性造成重排。
- 增加每glyph实际font size；Scene的位置量化依实际尺寸，不再一律用layout.size。图集key中的font+scale必须唯一确定量化阶数，避免同一font用不同根字号时复用错误位图。
- LineTable增加公共baseline，供mixed-font装饰和自绘查询；保留每glyph自身baseline偏移。空行也有正确字体baseline；合成硬段时一起加top偏移。
- 增加TextLayout.caretRect(index, upstream, float[4])便利查询；复用既有hitTest、lineAt、selectionRect，不返回每行新Rect列表。
- rich paint按glyph.cluster查当前paint区段，背景/装饰只遍历可见行相交区段；保留一行overhang，最终clip收口。plain路径不强制创建Spans或paint对象。
- Painter.richText与Ui.richText共用TextEngine/Scene实现，不再造一套独立富文本排版；底层Line/Span/Glyph结果对齐。

### 4.3 图集有界增长

- 现有`GlyphAtlas(w,h)`保留固定容量语义，避免改坏tiny/full测试；新增可增长配置，窗口/Tester/富文本性能场景初始1024，最大4096。
- 失败放置时按倍数扩至上限，保留全部entry坐标与key；复制旧像素到新stride，不重排glyph，不使本帧先发射的实例失效。增长后整图dirty并增加generation。
- CPU内存用可回收arena而不是每次增长都遗留global分配；最后一级R8像素为16 MiB。到上限明确isFull/诊断，不静默覆盖旧字；font ID也不得超过16位后取低位别名。
- MetalRenderer按generation更换纹理，整图上传后再编码当前帧。旧command buffer必须保有旧texture；继续使用SDK声明会保留资源的`commandBuffer`，不得切到unretainedReferences版本。释放renderer旧拥有引用，不提前破坏GPU在途资源。
- shader用像素位置读取图集，位置不变扩容无需修改28-float实例格式；仍要测试已有glyph及先写实例在增长后像素/坐标不变。
- 不实现eviction/repack或从满图集自动恢复；不能拿“扩容已做”当作图集完整生命周期完成。

## 五、FFM和资源约束

新增所有descriptor放Sigs，所有handle在Native按项目约定初始化；NativeImageFeature登记后必须在native实际调用：

| 能力 | 调用/签名要求 |
|---|---|
| 应用区段字体 | CFAttributedStringCreateMutableCopy；CFAttributedStringSetAttribute的CFRange按值参数 |
| 字体斜体 | CTFontCreateCopyWithSymbolicTraits：`long(long,double,long,int,int)`，traits/mask不能误用long |
| canonical | CFHash及Boolean CFEqual；分桶碰撞实际比较，不复制字符串充当完整身份 |
| 字体metrics | ascent/descent/underlinePosition/thickness/xHeight/size，按font缓存，不在每个glyph或steady paint下调 |
| GPU验证读回 | MTLTexture的getBytes:bytesPerRow:fromRegion:mipmapLevel:，MTL_REGION仍按值；先等command buffer完成，不读未完成的GPU结果 |
| 新输入 | keyUp及dispatchKeyDown属于Java平台接口；原生method1 ABI复用，不无故新增upcall |

签名以本机SDK头文件逐项核对。临时CTLine/typesetter/attributed string/新font副本遵守Create/Copy/retain所有权；Span数组只存Java值，不持有过期native行。图集增长和异常路径都检查释放。

测试门槛继续区分：不变帧native每元素0 B；事件/首次排版/增长允许分配。Painter的dev句柄不拿来宣称native production分配退化，反过来也不能用native池化掩盖dev有效期测试。

## 六、依次实施任务

### Task 0：基线与分支
- [ ] 核对基线、工作区及旧分支未合并状态；建codex/editor-primitives，记录ledger，重跑现有全套。
- [ ] 不修改旧人工通过记录、不提交.idea；计划获准后才开始以下实现。

### Task 1：公开Painter、canvas与共享服务
**Create/Modify**：Ui、Element、Painter、Node/ElementState/NodePool、Paint、Context。
- [ ] 先失败测试：canvas/draw不存在；局部坐标、opacity、已有clip和draw顺序。
- [ ] 文字测量、矩形/圆/线、已有TextLayout与clip；pop不能越过边界，callback异常后兄弟clip正常。
- [ ] dev旧Painter句柄在下一callback也失效；稳定Rect/production借用句柄，不变帧不分配；清理退休节点声明。
- [ ] Context共享layout及剪贴板服务的Host.Memory测试，应用不接触Engine内部。

### Task 2：raw InputEvent及原生文字抑制
**Create/Modify**：InputHandler、InputEvent、Input、HitList/Node、Engine、Window、MacWindow。
- [ ] 先测试按键优先级、true/false、Tab/Enter/Menu key、KEY_UP、mods、repeat、局部位置。
- [ ] 指针捕获到框外、CANCEL、解绑/禁用/失焦、右键阻止contextMenu、scroll未取走时交外层。
- [ ] 原生合成事件证明取走普通字符后既不interpret也不direct insert；不取走只插一次；组字独占；连续两个键不会沿用上次taken结果。
- [ ] 内置TextInput/TextArea/onTextKey和计划三键盘行为不变；事件句柄所有方法在返回后无效。

### Task 3：client接口、adapter和内置兼容
**Create/Modify**：TextRange、TextInputClient、内部client slot/adapter、Input/Engine、Element。
- [ ] 先用持久fake client测试replace/mark/unmark、正反选区、源UTF-16、null replacement、empty mark、actualRange。
- [ ] builtin经adapter继续保持compose外置模型、单步组字undo、readonly/disabled丢弃、IME-off提交、密码composition-only隐私。
- [ ] 首次resized在几何查询之前，尺寸变化更新一次，事件间几何新鲜；不能靠每次重建view获取布局。
- [ ] focus/blur、detach、swap、same-key复用、prune、close、重新聚焦及抛异常，旧活动adapter无效，unmark/discard计数正确。

### Task 4：原生查询、菜单与自有client整合
**Modify**：MacWindow.Input/Window、Engine查询、MenuState、Tester。
- [ ] textIn实际range兼容重载；bounds两种out顺序显式映射，带scroll/padding的局部↔窗口↔屏幕测试。
- [ ] surrogate/组合字素、CRLF、跨段请求、超大range长度不溢出；查询与mutation都不误写到别的client。
- [ ] Edit命令到handler，Paste and Match Style仅fallback一次；client切换后旧contextMenu选择丢弃。
- [ ] native密码actualRange如需修正，单列“composition文档内范围＋mask caret”回归，不能以统一接口为由暴露密码。
- [ ] Tester可驱动自有client并close；原生upcall里不执行构建view或递归paint。

### Task 5：不可变Span/Spans和字体身份
**Create/Modify**：Span、Spans、TextEngine/CoreText、Sigs/Native及FFI probe。
- [ ] 数据校验、defensive copy、空片段、整文字素边界；相同Spans按值相等、paint与shape指纹分离。
- [ ] 系统/monospace、weight、可用italic；字体等价副本复用、不同traits不串、hash碰撞实际核对。
- [ ] 新C签名先用probe验证；尤其int traits和CFRange，不等绘制时才首次碰到handle。

### Task 6：共同排版与rich paragraph缓存
**Modify**：TextEngine、TextLayout、LineTable、measure路径。
- [ ] 不同大小文字同一baseline；行高容纳大字体，空/尾空行、CRLF仍用源UTF-16。
- [ ] 软换行不按Span各自断开；连字/kerning位置与Core Text逐点对照，不再用glyph原点猜caret。
- [ ] 实际glyph size、line baseline和段合成正确；改颜色不shape，改字号/weight必须shape并影响兄弟布局。
- [ ] 尾段编辑保留未改硬段缓存；plain路径空文本/单行输入旧结果不变，缓存继续受entry/glyph/source预算约束。

### Task 7：rich绘制与公开控件
**Create/Modify**：Ui.richText、Node/Engine/Paint、Scene、Painter.richText。
- [ ] 先用Scene实例断言逐span颜色、背景在glyph前、装饰在后、混合字号量化、跨行片段边界和空行。
- [ ] 颜色跨连字按cluster起点，装饰不画newline stub；不声称RTL或skip-ink。
- [ ] 只处理可见行/区段；离屏不访问atlas，重叠矩形不泄漏clip/opacity；Painter和Ui测量/绘制一致。
- [ ] Spans长期持有，不变帧不新建数组、key字符串或Span；所有旧控件测试继续通过。

### Task 8：图集增长与Metal资源
**Modify**：GlyphAtlas、MetalRenderer、Tester/性能场景配置；固定容量构造保持兼容。
- [ ] tiny atlas固定模式仍full；growth模式至少经历两次增长，原entry、像素、dirty及本帧既有实例仍正确。
- [ ] 到cap/超大glyph/字体编号耗尽明确失败而非覆盖；增长后不变帧再次0 B。
- [ ] Metal切换generation、完整上传和在途旧纹理存活；核对并测试retainedReferences，不能只看Java像素数组就宣称GPU安全。
- [ ] 用实际Scene/pipeline/encode路径离屏渲染；增长前后旧glyph区域有非零像素且读回结果一致，增长前已经编码的帧仍正确，command buffer完成且无error。离屏读回只验证自己的内容，不是系统窗口截图，也不替代人工外观验收。

### Task 9：可用的底层能力示例
**Create**：EditorPrimitivesDemo及测试，应用自有client放示例包，不混入内置输入模型。
- [ ] 一个小型纯文本自有编辑面：click/drag选区、方向键、Backspace、Enter、IME、Copy/Cut/Paste/Select All和有限undo；必须只用公开API。
- [ ] 同窗口静态rich预览，混合系统/mono、大小、粗斜、颜色/背景/装饰；按键取走开关演示不再漏入原生文字。
- [ ] 自有client保证mutation后布局新鲜、组字一次undo；不依赖Element跨帧、每帧重建Spans或自己创建TextEngine。
- [ ] 支持--print-window；不加Markdown解析、文件、网络或真正的编辑器产品功能。

### Task 10：原生分配、性能和FFM门槛
- [ ] AllocSpike加入多个持久client＋draw、持久rich spans、图集增长后静态帧；native每元素仍0 B（既有0.5 B容差），大场景整帧≤32 KB。
- [ ] 固定样本：5000 UTF-16单位、50个含硬换行的中英段落，每段两Span，共100个合法Span，系统/mono、13/18/24三字号及粗/斜样式，视口800×600、scale=2；预热后测1000次，不在测量中无限累加文档。事件导航和绘制必须使用同一份mixed-font布局，不能只在旁边放静态richText而实际测纯文本编辑。
- [ ] 分别测不变滚动和“插入LF→立即Up→更新/绘制”；p50/p95/max，p95≤4ms、max≤8ms，atlas full视为失败，不靠缩小已定样本过关。增长阶段单列次数/峰值，不冒充steady成本。
- [ ] FfiCheck在JVM/native实际走到全部新handle；运行test、alloc、listbench、ffi-check。新字体/图集不能改坏旧文字、列表和多行门槛。
- [ ] 原生示例构建启动；有权限才截图检查。图集增长的Metal验证要有GPU完成/读回或相应资源证据，Java静态图不能代替。

### Task 11：文档和全分支审查
- [ ] README/API设计写明公开契约、相对坐标、native文字抑制、client与内置策略差异、富文本及图集限制；不标记M6全部完成。
- [ ] 全分支整体审查一次，对照下面Focus及每项实施裁定；修Critical/Important并补回归，小问题与未验证项如实记录。
- [ ] 最终代码复跑全套及受修复影响的原生门槛，填实测测试数与硬件，不预写数字。
- [ ] 汇报分步提交与分支，保留人工待办，等用户决定合并；不推送、不顺带开始完整编辑器。

## 七、Review Focus与人工验收

整体审查必须分别给证据：
1. **事件独占与单次派发**：raw true不再生成原生文字；false不双插；已登记shortcut/IME组字优先；keyup、菜单与pointer capture不重复。
2. **活动连接寿命**：旧adapter在swap、失焦后再聚焦、同key复用、close均不能编辑新client；marked释放及Host discard次数准确，异常不会留下旧连接。
3. **UTF-16和几何**：实际range、surrogate/CRLF、marked文档、reverse selection、窗口/元素坐标、首次resize、滚动和事件间新鲜布局，不能只验证最终String。
4. **内置兼容/隐私**：TextInput/TextArea、只读、密码、IME-off、onTextKey/onPaste、contextMenu目标身份及changed/undo没有回归。
5. **rich字体与缓存**：不同traits不canonical混同；paint变化不shape；mixed baseline/glyph size/连字/硬段复用及空行正确；并非把Span拆成box来伪装。
6. **绘制/资源/成本**：clip异常恢复、过期句柄、退休Node引用释放、atlas坐标不变增长、旧GPU纹理在途存活；native稳态0 B及既有门槛有实际证据。

人工核对仍单列：自有表面拼音候选框、组字取消/提交、切焦点和关闭、中文/emoji跨行拖选、按键取走开关、Edit/右键复制粘贴/撤销、混合字体baseline/mono/斜体/装饰、图集增长后旧文字不变。截图、native启动或自动化probe都不能自动勾选这些项目；屏幕录制权限不足时记录阻碍，不绕过或用合成图冒充。
