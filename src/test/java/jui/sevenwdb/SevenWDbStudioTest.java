package jui.sevenwdb;

import io.sevenwdb.client.SevenWCollection;
import io.sevenwdb.client.SevenWDatabase;
import io.sevenwdb.document.BsonDocument;
import jui.testing.Check;
import jui.ui.Tester;

public final class SevenWDbStudioTest {

    public void testStudioLifecycleAndQueryExecution() {
        SevenWDbStudio studio = new SevenWDbStudio();

        // 1. 测试内存数据库连接
        studio.inMemoryCheck.set(true);
        studio.connect();

        // 插入测试数据
        SevenWDatabase db = getDb(studio);
        Check.isTrue(db != null, "database should be connected");

        SevenWCollection<BsonDocument> users = db.getCollection("users");
        users.insert(BsonDocument.of("name", "Alice", "age", 28, "role", "Engineer"));
        users.insert(BsonDocument.of("name", "Bob", "age", 34, "role", "Manager"));
        users.ensureIndex("idx_age", "$.age", false);

        SevenWCollection<BsonDocument> orders = db.getCollection("orders");
        orders.insert(BsonDocument.of("orderId", 101, "amount", 199.5));

        // 刷新集合树
        studio.refreshCollections();
        Check.isTrue(studio.collections.contains("users"), "should have users collection");
        Check.isTrue(studio.collections.contains("orders"), "should have orders collection");

        // 2. 选择表结构查看
        studio.selectCollection("users");
        Check.equal("users", studio.selectedCollection);
        Check.equal(2L, studio.selectedDocCount);
        Check.isTrue(studio.inferredFields.stream().anyMatch(f -> "name".equals(f.name)), "should infer 'name' field");
        Check.isTrue(studio.collectionIndexes.stream().anyMatch(i -> "idx_age".equals(i.name)), "should detect 'idx_age' index");
        Check.isTrue(studio.generatedDdl.get().contains("CREATE INDEX idx_age ON users"), "should generate correct DDL");

        // 3. 执行 SQL 查询测试 (表格视图)
        SevenWDbStudio.QueryTab tab = studio.getCurrentTab();
        tab.sql.set("SELECT name, age FROM users WHERE age >= 30;");
        studio.executeCurrentQuery();

        Check.isTrue(tab.errorMessage == null, "query should succeed without error");
        Check.equal(1, tab.resultDocs.size());
        Check.equal("Bob", tab.resultDocs.get(0).getString("name"));
        Check.isTrue(tab.columns.contains("name"), "columns should contain 'name'");
        Check.isTrue(tab.columns.contains("age"), "columns should contain 'age'");

        // 4. 切换到 JSON 视图
        tab.viewMode.set(1);
        Check.isTrue(tab.jsonOutput.contains("\"name\": \"Bob\""), "json output should format document");

        // 5. 新建查询 Tab
        studio.addQueryTab("SELECT COUNT(*) AS total FROM orders;");
        Check.equal(2, studio.queryTabs.size());
        studio.executeCurrentQuery();
        Check.equal(1, studio.getCurrentTab().resultDocs.size());
        // 测试新建表/集合功能
        studio.newColNameInput.set("products");
        studio.createNewCollection();
        Check.isTrue(studio.collections.contains("products"), "should create and list new 'products' collection");
        Check.equal("products", studio.selectedCollection);

        // 测试 SQL CREATE TABLE
        studio.getCurrentTab().sql.set("CREATE TABLE inventory;");
        studio.executeCurrentQuery();
        Check.isTrue(studio.collections.contains("inventory"), "SQL CREATE TABLE should create and list 'inventory'");


        // 6. 使用 Tester 渲染 UI 视图（验证无崩溃与组件树生成）
        Tester tester = Tester.of(studio::view, 1180, 760);
        Check.isTrue(tester.hasText("⚡ SevenWDB Studio"), "header should render logo");
        Check.isTrue(tester.hasText("users"), "collection list should render users");
        Check.isTrue(tester.hasText("orders"), "collection list should render orders");
        Check.isTrue(tester.hasText("▶ 运行 SQL"), "action bar should render run button");

        // 测试新建数据库
        try {
            java.nio.file.Path tempNewDb = java.nio.file.Files.createTempFile("jui-new-db-", ".db");
            studio.newDbPathInput.set(tempNewDb.toString());
            studio.createNewDatabase();
            Check.isTrue(studio.collections.isEmpty(), "new db should start with 0 collections");
            studio.newColNameInput.set("logs");
            studio.createNewCollection();
            Check.isTrue(studio.collections.contains("logs"), "should create collection in new db");
            studio.disconnect();
            java.nio.file.Files.deleteIfExists(tempNewDb);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 断开连接
        studio.disconnect();
    }

    private static SevenWDatabase getDb(SevenWDbStudio studio) {
        try {
            java.lang.reflect.Field f = SevenWDbStudio.class.getDeclaredField("db");
            f.setAccessible(true);
            return (SevenWDatabase) f.get(studio);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
