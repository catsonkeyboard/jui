package jui.sevenwdb;

import io.sevenwdb.client.SevenWCollection;
import io.sevenwdb.client.SevenWDatabase;
import io.sevenwdb.client.query.Query;
import io.sevenwdb.client.sql.BsonDataReader;
import io.sevenwdb.document.BsonDocument;
import io.sevenwdb.document.BsonValue;
import io.sevenwdb.document.json.JsonSerializer;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.*;
import jui.ui.engine.Node;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static jui.ui.Ui.*;

/**
 * 基于 j-ui GPU 桌面 UI 框架的 SevenWDB 可视化查询分析器 (SevenWDB Studio / Query Analyzer).
 *
 * <p>核心能力：
 * <ul>
 *   <li><b>连接管理</b>：连接本地 .db 文件、加密库（密码）或 :memory: 内存数据库；</li>
 *   <li><b>左侧树形导航</b>：展示数据库全局信息、用户集合列表（带文档计数）、系统虚拟集合；</li>
 *   <li><b>表结构与 DDL</b>：查看采样字段类型推断、索引列表（名称、表达式、唯一性）并在线执行 DDL；</li>
 *   <li><b>多标签查询分析</b>：多 Tab 独立 SQL 编辑器、常用 SQL 模板一键填充、执行计划 EXPLAIN；</li>
 *   <li><b>双模结果展示</b>：支持高性能表格视图（动态多列、滚动对齐）与格式化 JSON 视图快速切换；</li>
 *   <li><b>数据库治理</b>：内置物理页自检 (PRAGMA INTEGRITY_CHECK) 与碎片整理紧凑 (REBUILD)。</li>
 * </ul>
 */
public final class SevenWDbStudio {

    // -------------------------------------------------------------------------
    // 状态定义
    // -------------------------------------------------------------------------

    public static final class FieldSchema {
        final String name;
        final String type;
        final String sampleValue;

        FieldSchema(String name, String type, String sampleValue) {
            this.name = name;
            this.type = type;
            this.sampleValue = sampleValue;
        }
    }

    public static final class IndexInfo {
        final String name;
        final String expression;
        final boolean unique;

        IndexInfo(String name, String expression, boolean unique) {
            this.name = name;
            this.expression = expression;
            this.unique = unique;
        }
    }

    public static final class QueryTab {
        final int id;
        final TextRef title;
        final TextRef sql;
        final IntRef viewMode = new IntRef(0); // 0: 表格 Table, 1: JSON, 2: 执行计划 Explain
        final ScrollState scroll = new ScrollState();

        String statusMessage = "就绪 (输入 SQL 后点击运行)";
        String errorMessage = null;
        long elapsedMs = 0;
        List<BsonDocument> resultDocs = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        String jsonOutput = "";
        String explainOutput = "";

        QueryTab(int id, String initialTitle, String initialSql) {
            this.id = id;
            this.title = new TextRef(initialTitle);
            this.sql = new TextRef(initialSql);
        }
    }

    // 主数据库句柄
    private SevenWDatabase db;
    private boolean isConnected = false;
    private String connectionStatus = "未连接 (请输入数据库路径或选择内存库)";

    // 连接设置
    final TextRef dbPathInput = new TextRef("mydb.db");
    final TextRef dbPasswordInput = new TextRef("");
    final BoolRef inMemoryCheck = new BoolRef(false);

    // 新建数据库状态
    final BoolRef showNewDbForm = new BoolRef(false);
    final TextRef newDbPathInput = new TextRef("new_database.db");
    final TextRef newDbPasswordInput = new TextRef("");

    // 新建表/集合状态
    final BoolRef showNewColForm = new BoolRef(false);
    final TextRef newColNameInput = new TextRef("");

    // 界面布局与交互状态
    final FloatRef sidebarWidth = new FloatRef(260.0f);
    final IntRef mainMode = new IntRef(0); // 0: SQL 查询分析器, 1: 表结构与 DDL 管理
    final IntRef schemaSubTab = new IntRef(0); // 0: 字段推断, 1: 索引列表, 2: DDL 语句
    final ScrollState sidebarScroll = new ScrollState();
    final ScrollState schemaScroll = new ScrollState();

    // 集合与表结构状态
    final List<String> collections = new ArrayList<>();
    String selectedCollection = null;
    long selectedDocCount = 0;
    final List<FieldSchema> inferredFields = new ArrayList<>();
    final List<IndexInfo> collectionIndexes = new ArrayList<>();
    final TextRef generatedDdl = new TextRef("");

    // 新建索引表单
    final TextRef newIndexName = new TextRef("");
    final TextRef newIndexExpr = new TextRef("");
    final BoolRef newIndexUnique = new BoolRef(false);
    final BoolRef showNewIndexModal = new BoolRef(false);

    // 查询分析器 Tab 状态
    private int nextTabId = 1;
    final List<QueryTab> queryTabs = new ArrayList<>();
    final IntRef activeTabIndex = new IntRef(0);

    public SevenWDbStudio() {
        // 初始新建一个查询 Tab
        addQueryTab("SELECT * FROM users LIMIT 50");
    }

    // -------------------------------------------------------------------------
    // 数据库操作逻辑
    // -------------------------------------------------------------------------

    public synchronized void connect() {
        disconnect();
        try {
            String path = inMemoryCheck.get() ? ":memory:" : dbPathInput.get().trim();
            if (path.isEmpty()) {
                connectionStatus = "错误: 数据库路径不能为空";
                return;
            }
            String pwd = dbPasswordInput.get().trim();
            if (!pwd.isEmpty() && !inMemoryCheck.get()) {
                db = new SevenWDatabase("Filename=" + path + "; Password=" + pwd);
            } else {
                db = inMemoryCheck.get() ? SevenWDatabase.inMemory() : new SevenWDatabase(path);
            }
            isConnected = true;
            connectionStatus = "已连接: " + (inMemoryCheck.get() ? "纯内存数据库 (:memory:)" : path);
            refreshCollections();
            if (!collections.isEmpty()) {
                selectCollection(collections.get(0));
            }
        } catch (Exception e) {
            isConnected = false;
            connectionStatus = "连接失败: " + e.getMessage();
        }
    }

    public synchronized void disconnect() {
        if (db != null) {
            try {
                db.close();
            } catch (Exception ignored) {}
            db = null;
        }
        isConnected = false;
        connectionStatus = "已断开连接";
        collections.clear();
        selectedCollection = null;
        inferredFields.clear();
        collectionIndexes.clear();
    }
    public synchronized void createNewDatabase() {
        String path = newDbPathInput.get().trim();
        if (path.isEmpty()) {
            connectionStatus = "错误: 新建数据库文件名不能为空";
            return;
        }
        String pwd = newDbPasswordInput.get().trim();
        try {
            disconnect();
            if (!pwd.isEmpty()) {
                db = new SevenWDatabase("Filename=" + path + "; Password=" + pwd);
            } else {
                db = new SevenWDatabase(path);
            }
            isConnected = true;
            connectionStatus = "已创建并连接新库: " + path;
            dbPathInput.set(path);
            showNewDbForm.set(false);
            refreshCollections();
        } catch (Exception e) {
            isConnected = false;
            connectionStatus = "新建数据库失败: " + e.getMessage();
        }
    }

    public synchronized void createNewCollection() {
        if (!isConnected || db == null) return;
        String name = newColNameInput.get().trim();
        if (name.isEmpty()) return;
        try {
            boolean created = db.createCollection(name);
            newColNameInput.set("");
            showNewColForm.set(false);
            refreshCollections();
            selectCollection(name);
        } catch (Exception e) {
            selectCollection(name);
        }
    }


    public synchronized void refreshCollections() {
        if (!isConnected || db == null) return;
        collections.clear();
        for (String name : db.getCollectionNames()) {
            if (!name.startsWith("$")) {
                collections.add(name);
            }
        }
        Collections.sort(collections);
        if (selectedCollection != null && collections.contains(selectedCollection)) {
            selectCollection(selectedCollection);
        } else if (!collections.isEmpty()) {
            selectCollection(collections.get(0));
        }
    }

    public synchronized void selectCollection(String name) {
        if (!isConnected || db == null || name == null) return;
        selectedCollection = name;
        try {
            SevenWCollection<BsonDocument> col = db.getCollection(name);
            selectedDocCount = col.count();

            // 1. 采样推断字段结构 (取前 50 条文档)
            inferredFields.clear();
            Map<String, String> fieldTypes = new LinkedHashMap<>();
            Map<String, String> fieldSamples = new LinkedHashMap<>();

            List<BsonDocument> sampleDocs = col.query().limit(50).toList();
            for (BsonDocument doc : sampleDocs) {
                for (Map.Entry<String, BsonValue> entry : doc.entrySet()) {
                    String k = entry.getKey();
                    BsonValue v = entry.getValue();
                    if (!fieldTypes.containsKey(k)) {
                        fieldTypes.put(k, v.getType().name());
                        fieldSamples.put(k, formatValue(v, 40));
                    }
                }
            }
            for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
                inferredFields.add(new FieldSchema(entry.getKey(), entry.getValue(), fieldSamples.get(entry.getKey())));
            }

            // 2. 获取索引结构 (从 $indexes 虚拟视图提取)
            collectionIndexes.clear();
            try (BsonDataReader idxReader = db.execute("SELECT name, expression, unique FROM $indexes WHERE collection = '" + name + "'")) {
                for (BsonDocument idxDoc : idxReader.toDocuments()) {
                    collectionIndexes.add(new IndexInfo(
                            idxDoc.getString("name"),
                            idxDoc.getString("expression"),
                            idxDoc.getBoolean("unique", false)
                    ));
                }
            } catch (Exception ignored) {}

            // 3. 生成 DDL 脚本预览
            StringBuilder ddl = new StringBuilder();
            ddl.append("-- 集合元数据定义: ").append(name).append("\n");
            ddl.append("-- 当前有效文档总数: ").append(String.format(Locale.ROOT, "%,d", selectedDocCount)).append("\n\n");
            for (IndexInfo idx : collectionIndexes) {
                if ("_id".equals(idx.name)) continue;
                ddl.append(idx.unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ");
                ddl.append(idx.name).append(" ON ").append(name).append(" (").append(idx.expression).append(");\n");
            }
            generatedDdl.set(ddl.toString());

        } catch (Exception e) {
            selectedDocCount = 0;
        }
    }

    public synchronized void executeCurrentQuery() {
        if (!isConnected || db == null) {
            getCurrentTab().errorMessage = "未连接到数据库，请先点击顶部连接";
            return;
        }
        QueryTab tab = getCurrentTab();
        String sql = tab.sql.get().trim();
        if (sql.isEmpty()) {
            tab.errorMessage = "SQL 语句不能为空";
            return;
        }

        tab.errorMessage = null;
        tab.resultDocs.clear();
        tab.columns.clear();
        tab.jsonOutput = "";
        tab.explainOutput = "";

        long start = System.nanoTime();
        try (BsonDataReader reader = db.execute(sql)) {
            tab.elapsedMs = (System.nanoTime() - start) / 1_000_000;
            List<BsonDocument> docs = reader.toDocuments();

            if (!docs.isEmpty()) {
                tab.resultDocs = docs;
                // 提取多列并保证 _id 在最前
                Set<String> colSet = new LinkedHashSet<>();
                boolean hasId = false;
                for (BsonDocument doc : docs) {
                    if (doc.containsKey("_id")) hasId = true;
                }
                if (hasId) colSet.add("_id");
                for (BsonDocument doc : docs) {
                    colSet.addAll(doc.keySet());
                }
                tab.columns = new ArrayList<>(colSet);

                // 生成 JSON 格式化文本 (前 200 条防卡顿)
                StringBuilder jsonSb = new StringBuilder();
                int limitJson = Math.min(docs.size(), 200);
                for (int i = 0; i < limitJson; i++) {
                    jsonSb.append(JsonSerializer.serialize(docs.get(i), true)).append("\n");
                }
                if (docs.size() > 200) {
                    jsonSb.append("// ... 仅预览前 200 条，其余在表格中查看\n");
                }
                tab.jsonOutput = jsonSb.toString();
                tab.statusMessage = String.format(Locale.ROOT, "查询成功: 返回 %,d 行数据 | 耗时: %d ms", docs.size(), tab.elapsedMs);
            } else {
                int affected = reader.getAffectedRows();
                tab.statusMessage = String.format(Locale.ROOT, "执行成功: 影响行数 %,d | 耗时: %d ms", affected, tab.elapsedMs);
            }

            // 若执行了 DDL (CREATE/DROP/RENAME)，自动刷新左侧目录树
            String upper = sql.toUpperCase(Locale.ROOT);
            if (upper.contains("CREATE") || upper.contains("DROP") || upper.contains("RENAME")) {
                refreshCollections();
            }

        } catch (Exception e) {
            tab.elapsedMs = (System.nanoTime() - start) / 1_000_000;
            tab.errorMessage = "执行异常: " + e.getMessage();
        }
    }

    public synchronized void explainCurrentQuery() {
        QueryTab tab = getCurrentTab();
        String sql = tab.sql.get().trim();
        if (!sql.toUpperCase(Locale.ROOT).startsWith("EXPLAIN")) {
            tab.sql.set("EXPLAIN " + sql);
        }
        tab.viewMode.set(1); // 切换至 JSON 查看执行计划
        executeCurrentQuery();
    }

    public void addQueryTab(String initialSql) {
        int id = nextTabId++;
        QueryTab tab = new QueryTab(id, "查询 " + id, initialSql != null ? initialSql : "SELECT * FROM users LIMIT 50;");
        queryTabs.add(tab);
        activeTabIndex.set(queryTabs.size() - 1);
    }

    public void closeQueryTab(int index) {
        if (queryTabs.size() <= 1) return;
        queryTabs.remove(index);
        if (activeTabIndex.get() >= queryTabs.size()) {
            activeTabIndex.set(queryTabs.size() - 1);
        }
    }

    public QueryTab getCurrentTab() {
        if (queryTabs.isEmpty()) {
            addQueryTab("");
        }
        int idx = Math.max(0, Math.min(activeTabIndex.get(), queryTabs.size() - 1));
        return queryTabs.get(idx);
    }

    // -------------------------------------------------------------------------
    // UI 视图渲染 (j-ui 声明式组件树)
    // -------------------------------------------------------------------------

    public void view(Context context) {
        Theme t = context.theme();

        column(context).fill().background(t.background()).children(() -> {
            // 1. 顶部状态与连接控制栏
            renderHeaderToolbar(context, t);
            divider(context);

            // 2. 主体工作区 (左右 Split 可拖拽分栏)
            split(context, sidebarWidth, () -> renderSidebar(context, t), () -> renderMainArea(context, t));
        });
    }

    private void renderHeaderToolbar(Context c, Theme t) {
        row(c).padding(10, 16).gap(12).alignItems(Align.CENTER).children(() -> {
            // Logo / 标头
            text(c, "⚡ SevenWDB Studio").fontSize(18).bold();
            text(c, "|").textColor(t.textMuted());

            // 连接状态指示灯
            box(c).width(8).height(8).radius(4)
                    .background(isConnected ? Color.rgb(0x10b981) : Color.rgb(0x94a3b8));
            if (!isConnected) {
                if (showNewDbForm.get()) {
                    text(c, "新建库:").bold();
                    textInput(c, newDbPathInput).placeholder("新数据库路径 (如 demo.db)").width(200);
                    textInput(c, newDbPasswordInput).placeholder("密码 (可选)").width(100);
                    if (primaryButton(c, "确认创建").clicked()) {
                        createNewDatabase();
                    }
                    if (button(c, "取消").clicked()) {
                        showNewDbForm.set(false);
                    }
                } else {
                    checkbox(c, inMemoryCheck, "内存库 (:memory:)");
                    if (!inMemoryCheck.get()) {
                        textInput(c, dbPathInput).placeholder("数据库文件路径 (如 app.db)").width(220);
                        textInput(c, dbPasswordInput).placeholder("密码 (可选)").width(100);
                    }
                    if (primaryButton(c, "连接数据库").clicked()) {
                        connect();
                    }
                    if (button(c, "+ 新建数据库").clicked()) {
                        showNewDbForm.set(true);
                    }
                }
            } else {
                text(c, connectionStatus).fontSize(13).bold().textColor(Color.rgb(0x10b981));
                spacer(c);
                if (button(c, "+ 新建库").clicked()) {
                    disconnect();
                    showNewDbForm.set(true);
                }
                if (button(c, "PRAGMA 物理自检").clicked()) {
                    addQueryTab("PRAGMA INTEGRITY_CHECK;");
                    executeCurrentQuery();
                }
                if (button(c, "紧凑重建 (REBUILD)").clicked()) {
                    addQueryTab("REBUILD;");
                    executeCurrentQuery();
                }
                if (button(c, "断开连接").clicked()) {
                    disconnect();
                }
            }
        });
    }

    private void renderSidebar(Context c, Theme t) {
        column(c).fill().padding(12).gap(8).background(t.surface()).children(() -> {
            // 集合栏标题与刷新按钮
            row(c).alignItems(Align.CENTER).children(() -> {
                text(c, "数据集合 (COLLECTIONS)").bold().fontSize(12).textColor(t.textMuted());
                spacer(c);
                if (button(c, "+ 新建表").clicked()) {
                    showNewColForm.set(!showNewColForm.get());
                }
                if (button(c, "刷新").clicked()) {
                    refreshCollections();
                }
            });
            if (showNewColForm.get()) {
                row(c).gap(6).padding(4, 0).alignItems(Align.CENTER).children(() -> {
                    textInput(c, newColNameInput).placeholder("表名 (如 products)").grow(1);
                    if (primaryButton(c, "创建").clicked()) {
                        createNewCollection();
                    }
                    if (button(c, "✕").clicked()) {
                        showNewColForm.set(false);
                    }
                });
            }
            divider(c);

            // 集合列表展示
            if (!isConnected) {
                column(c).grow(1).center().children(() -> text(c, "未连接数据库").textColor(t.textMuted()));
            } else if (collections.isEmpty()) {
                column(c).grow(1).center().children(() -> text(c, "当前库中无任何集合").textColor(t.textMuted()));
            } else {
                scroll(c, sidebarScroll).grow(1).children(() -> {
                    column(c).gap(4).children(() -> {
                        for (String colName : collections) {
                            renderCollectionTreeItem(c, t, colName);
                        }
                    });
                });
            }

            divider(c);
            // 底部模式切换
            row(c).gap(6).children(() -> {
                Element qBtn = button(c, "SQL 查询分析").grow(1);
                if (mainMode.get() == 0) qBtn.background(t.accent()).textColor(t.accentText());
                if (qBtn.clicked()) mainMode.set(0);

                Element sBtn = button(c, "表结构 DDL").grow(1);
                if (mainMode.get() == 1) sBtn.background(t.accent()).textColor(t.accentText());
                if (sBtn.clicked()) mainMode.set(1);
            });
        });
    }

    private void renderCollectionTreeItem(Context c, Theme t, String colName) {
        boolean selected = colName.equals(selectedCollection);
        Element item = row(c.key("col_" + colName)).padding(6, 8).radius(4).gap(6)
                .alignItems(Align.CENTER).focusable();

        if (selected) {
            item.background(t.accent()).textColor(t.accentText());
        } else if (item.hovered()) {
            item.background(t.surfaceHover());
        }

        if (item.clicked()) {
            selectCollection(colName);
        }

        // 右键上下文菜单
        contextMenu(c, item, menu -> {
            if (menu.item("查看表数据 (SELECT * LIMIT 100)").chosen()) {
                selectCollection(colName);
                addQueryTab("SELECT * FROM " + colName + " LIMIT 100;");
                mainMode.set(0);
                executeCurrentQuery();
            }
            if (menu.item("查看表结构与索引 (Schema)").chosen()) {
                selectCollection(colName);
                mainMode.set(1);
            }
            if (menu.item("收集表统计信息 (ANALYZE)").chosen()) {
                selectCollection(colName);
                addQueryTab("ANALYZE TABLE " + colName + ";");
                mainMode.set(0);
                executeCurrentQuery();
            }
            menu.separator();
            if (menu.item("删除表 (DROP COLLECTION)").chosen()) {
                if (db != null) {
                    db.dropCollection(colName);
                    refreshCollections();
                }
            }
        });

        item.children(() -> {
            text(c, "📄").fontSize(12);
            Element te = text(c, colName).singleLine().grow(1);
            if (selected) te.bold();
        });
    }

    private void renderMainArea(Context c, Theme t) {
        column(c).fill().padding(12).children(() -> {
            if (mainMode.get() == 0) {
                renderQueryAnalyzer(c, t);
            } else {
                renderSchemaManager(c, t);
            }
        });
    }

    private void renderQueryAnalyzer(Context c, Theme t) {
        QueryTab tab = getCurrentTab();

        column(c).fill().gap(8).children(() -> {
            // 1. 查询 Tab 栏
            row(c).alignItems(Align.CENTER).gap(4).children(() -> {
                for (int i = 0; i < queryTabs.size(); i++) {
                    final int idx = i;
                    QueryTab qTab = queryTabs.get(i);
                    boolean isTabActive = i == activeTabIndex.get();
                    Element tItem = row(c.key("tab_" + qTab.id)).padding(4, 10).radius(4).gap(6).alignItems(Align.CENTER);
                    if (isTabActive) {
                        tItem.background(t.accent()).textColor(t.accentText());
                    } else {
                        tItem.background(t.surface());
                    }
                    if (tItem.clicked()) {
                        activeTabIndex.set(idx);
                    }
                    tItem.children(() -> {
                        Element tit = text(c, qTab.title.get());
                        if (isTabActive) tit.bold();
                        if (queryTabs.size() > 1) {
                            Element closeBtn = buttonBase(c.key("close_" + qTab.id)).padding(0, 4);
                            if (closeBtn.clicked()) closeQueryTab(idx);
                            closeBtn.children(() -> text(c, "×").fontSize(14));
                        }
                    });
                }
                if (button(c, "+").clicked()) {
                    addQueryTab("SELECT * FROM " + (selectedCollection != null ? selectedCollection : "users") + " LIMIT 50;");
                }
            });

            divider(c);

            // 2. SQL 文本编辑器
            column(c).gap(4).children(() -> {
                textArea(c.key("editor_" + tab.id), tab.sql).height(120).placeholder("输入 SQL 语句 (支持 SELECT / INSERT / UPDATE / DELETE / DDL)...");
            });

            // 3. 执行工具条
            row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                if (primaryButton(c, "▶ 运行 SQL").clicked()) {
                    executeCurrentQuery();
                }
                if (button(c, "🔍 执行计划 (EXPLAIN)").clicked()) {
                    explainCurrentQuery();
                }
                spacer(c);
                // 结果呈现模式切换
                row(c).gap(4).children(() -> {
                    Element tblBtn = button(c, "表格展示");
                    if (tab.viewMode.get() == 0) tblBtn.background(t.accent()).textColor(t.accentText());
                    if (tblBtn.clicked()) tab.viewMode.set(0);

                    Element jsonBtn = button(c, "JSON 展开");
                    if (tab.viewMode.get() == 1) jsonBtn.background(t.accent()).textColor(t.accentText());
                    if (jsonBtn.clicked()) tab.viewMode.set(1);
                });
            });

            // 4. 状态栏
            row(c).padding(4, 8).background(t.surface()).radius(4).children(() -> {
                if (tab.errorMessage != null) {
                    text(c, tab.errorMessage).textColor(Color.rgb(0xef4444)).bold();
                } else {
                    text(c, tab.statusMessage).textColor(Color.rgb(0x10b981)).fontSize(12);
                }
            });

            divider(c);

            // 5. 结果展示区
            column(c).grow(1).children(() -> {
                if (tab.viewMode.get() == 0) {
                    renderResultTable(c, t, tab);
                } else {
                    renderResultJson(c, t, tab);
                }
            });
        });
    }

    private void renderResultTable(Context c, Theme t, QueryTab tab) {
        if (tab.resultDocs.isEmpty()) {
            column(c).grow(1).center().children(() -> text(c, "结果集为空").textColor(t.textMuted()));
            return;
        }

        // 双向滚动展示动态表格
        scrollBoth(c, tab.scroll).grow(1).children(() -> {
            column(c).children(() -> {
                // 表头
                row(c).background(t.surface()).padding(6, 8).border(1, t.border()).children(() -> {
                    for (String col : tab.columns) {
                        box(c).width(160).padding(2, 6).children(() -> text(c, col).bold().singleLine());
                    }
                });

                // 数据行
                int rowIdx = 0;
                for (BsonDocument doc : tab.resultDocs) {
                    final boolean alt = (rowIdx++ % 2 == 1);
                    row(c).padding(4, 8).background(alt ? t.surface() : t.background())
                            .border(1, t.border()).alignItems(Align.CENTER).children(() -> {
                                for (String col : tab.columns) {
                                    BsonValue val = doc.get(col);
                                    String textVal = formatValue(val, 32);
                                    box(c).width(160).padding(2, 6).children(() -> {
                                        Element te = text(c, textVal).singleLine().fontSize(12);
                                        if (val == null || val.isNull()) {
                                            te.textColor(t.textMuted());
                                        }
                                    });
                                }
                            });
                }
            });
        });
    }

    private void renderResultJson(Context c, Theme t, QueryTab tab) {
        scroll(c, tab.scroll).grow(1).children(() -> {
            textArea(c.key("json_view_" + tab.id), new TextRef(tab.jsonOutput))
                    .fill()
                    .readOnly(true)
                    .label("JSON 结果");
        });
    }

    private void renderSchemaManager(Context c, Theme t) {
        if (selectedCollection == null) {
            column(c).grow(1).center().children(() -> text(c, "请在左侧选择一个数据集合以查看其结构").textColor(t.textMuted()));
            return;
        }

        column(c).fill().gap(12).children(() -> {
            // 标头信息
            row(c).alignItems(Align.CENTER).children(() -> {
                text(c, "集合: " + selectedCollection).fontSize(20).bold();
                spacer(c);
                text(c, "有效文档总数: " + String.format(Locale.ROOT, "%,d", selectedDocCount)).bold();
            });

            // 切换 Tab (字段、索引、DDL)
            row(c).gap(8).children(() -> {
                Element fBtn = button(c, "字段与类型推断 (Fields)");
                if (schemaSubTab.get() == 0) fBtn.background(t.accent()).textColor(t.accentText());
                if (fBtn.clicked()) schemaSubTab.set(0);

                Element iBtn = button(c, "索引管理 (Indexes)");
                if (schemaSubTab.get() == 1) iBtn.background(t.accent()).textColor(t.accentText());
                if (iBtn.clicked()) schemaSubTab.set(1);

                Element dBtn = button(c, "DDL 脚本 (DDL)");
                if (schemaSubTab.get() == 2) dBtn.background(t.accent()).textColor(t.accentText());
                if (dBtn.clicked()) schemaSubTab.set(2);
            });

            divider(c);

            // 内容视图
            scroll(c, schemaScroll).grow(1).children(() -> {
                if (schemaSubTab.get() == 0) {
                    renderFieldsTab(c, t);
                } else if (schemaSubTab.get() == 1) {
                    renderIndexesTab(c, t);
                } else {
                    renderDdlTab(c, t);
                }
            });
        });
    }

    private void renderFieldsTab(Context c, Theme t) {
        column(c).gap(4).children(() -> {
            row(c).background(t.surface()).padding(6, 8).border(1, t.border()).children(() -> {
                box(c).width(200).children(() -> text(c, "字段名 (Field)").bold());
                box(c).width(160).children(() -> text(c, "BSON 类型 (Type)").bold());
                box(c).grow(1).children(() -> text(c, "采样值 (Sample)").bold());
            });

            for (FieldSchema f : inferredFields) {
                row(c).padding(6, 8).border(1, t.border()).alignItems(Align.CENTER).children(() -> {
                    box(c).width(200).children(() -> {
                        Element ne = text(c, f.name);
                        if ("_id".equals(f.name)) ne.bold();
                    });
                    box(c).width(160).children(() -> text(c, f.type).fontSize(12).textColor(Color.rgb(0x3b82f6)));
                    box(c).grow(1).children(() -> text(c, f.sampleValue).fontSize(12).singleLine());
                });
            }
        });
    }

    private void renderIndexesTab(Context c, Theme t) {
        column(c).gap(8).children(() -> {
            row(c).background(t.surface()).padding(6, 8).border(1, t.border()).children(() -> {
                box(c).width(180).children(() -> text(c, "索引名称").bold());
                box(c).width(260).children(() -> text(c, "索引表达式").bold());
                box(c).width(100).children(() -> text(c, "唯一约束").bold());
                box(c).grow(1).children(() -> text(c, "操作").bold());
            });

            for (IndexInfo idx : collectionIndexes) {
                row(c).padding(6, 8).border(1, t.border()).alignItems(Align.CENTER).children(() -> {
                    box(c).width(180).children(() -> text(c, idx.name));
                    box(c).width(260).children(() -> text(c, idx.expression).fontSize(12));
                    box(c).width(100).children(() -> {
                        Element ue = text(c, idx.unique ? "YES" : "NO");
                        if (idx.unique) ue.bold();
                    });
                    box(c).grow(1).children(() -> {
                        if (!"_id".equals(idx.name)) {
                            if (button(c, "删除").clicked()) {
                                if (db != null) {
                                    db.getCollection(selectedCollection).dropIndex(idx.name);
                                    selectCollection(selectedCollection);
                                }
                            }
                        }
                    });
                });
            }

            divider(c);

            // 新增索引快捷操作
            row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                textInput(c, newIndexName).placeholder("索引名称 (如 idx_age)").width(160);
                textInput(c, newIndexExpr).placeholder("索引表达式 (如 age 或 price * qty)").width(240);
                checkbox(c, newIndexUnique, "唯一索引");
                if (primaryButton(c, "创建索引").clicked()) {
                    String name = newIndexName.get().trim();
                    String expr = newIndexExpr.get().trim();
                    if (!name.isEmpty() && !expr.isEmpty() && db != null) {
                        db.getCollection(selectedCollection).ensureIndex(name, expr, newIndexUnique.get());
                        newIndexName.set("");
                        newIndexExpr.set("");
                        selectCollection(selectedCollection);
                    }
                }
            });
        });
    }

    private void renderDdlTab(Context c, Theme t) {
        column(c).fill().gap(8).children(() -> {
            text(c, "集合 DDL 与索引创建脚本 (可直接复制):").textColor(t.textMuted());
            textArea(c.key("ddl_area"), generatedDdl).height(240).readOnly(true);
        });
    }

    private static String formatValue(BsonValue val, int maxLen) {
        if (val == null || val.isNull()) return "null";
        String s = val.isString() ? "\"" + val.asString() + "\"" : val.toString();
        if (s.length() > maxLen) {
            return s.substring(0, maxLen - 3) + "...";
        }
        return s;
    }

    // -------------------------------------------------------------------------
    // 启动入口
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        SevenWDbStudio studio = new SevenWDbStudio();

        // 若命令行传了数据库文件路径，默认直接预连接
        if (args.length > 0 && !args[0].startsWith("-")) {
            studio.dbPathInput.set(args[0]);
            studio.connect();
        }

        App.run(() -> {
            Window.open(WindowOptions.of("SevenWDB Visual Query Analyzer")
                    .size(1180, 760)
                    .content(studio::view));
        });
    }
}
