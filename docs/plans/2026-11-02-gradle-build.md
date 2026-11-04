# Gradle 构建迁移

> **For agentic workers:** Use superpowers:executing-plans to implement this single task inline.

**Goal:** 迁移到 Gradle，保留全部 build.sh 命令和参数，支持增量编译。

**Architecture:** Gradle Wrapper 固定 9.4.1；Java 插件管理源码、资源、JAR 和编译依赖，独立 Metal/native-image 任务声明输入输出。build.sh 保留运行和测量逻辑，委托构建给 Gradle。测试沿用现有零依赖运行器。

**Tech Stack:** Gradle Groovy DSL、GraalVM 25、macOS Metal、Bash、Python 集成检查。

**Spec:** 本轮用户要求：迁移 Gradle；保留现有构建、测试和 native demo 命令；支持增量编译。

## Global Constraints

- Apple Silicon/macOS 14+、GraalVM 25 及现有 FFM/native-image 参数保持。
- NotesMotion 别名、Notes 默认 32 MiB 堆、输出二进制路径及参数透传保持。
- 不引入 JUnit、不修改 UI；不重跑无关性能验收；不自动合并或推送。
- 首次 Wrapper 下载需要网络；后续本地无依赖构建可以离线。

## Review Focus

- 源码、Metal、部署版本和 native flags 改变时正确失效；不变时跳过编译。
- 测试筛选、失败退出码和每次执行测试的行为保持。
- clean 后的资源可被 JVM 和 native image 找到；新依赖进入运行/native classpath。
- 自带 JAVA_HOME/GRAALVM_HOME 的用户及路径包含空格时正常。
- 所有原命令仍委托同一份构建配置，没有残留手动 javac/native-image。

### Task 1: Gradle 构建与兼容入口

**Files:** build.gradle、settings.gradle、gradle.properties、gradlew/gradlew.bat、gradle/wrapper/*、build.sh、scripts/test-gradle-build.py、README.md、.gitignore。

**Interfaces:** Gradle classes/test/build/nativeCompile/runtimeClasspath；build.sh 原有命令不变；nativeMain/nativeOutput/nativeArgsJson 为原生构建参数。

- [x] 编写隔离临时目录的实际构建集成检查；迁移前因缺少 Wrapper 失败。
- [x] 添加 Wrapper、Java/Metal/测试/native 任务与输入输出，替换脚本中的手动编译。
- [x] 集成检查验证重复构建 UP-TO-DATE、局部 Java 修改、Metal/部署目标失效、测试筛选和失败退出。
- [x] 全部 Java 测试、JVM 入口、native Notes/NotesMotion 启动和第二次 native 构建跳过验证。
- [x] 更新命令和增量行为说明；一次独立只读审查，修复重要问题后提交。

## 验证结果

- `./gradlew build --offline`：552 passed / 0 failed，JAR 包含 Java 类、shader.metal 和 shader.metallib。
- `JUI_TEST_NATIVE=1 python3 scripts/test-gradle-build.py`：通过（49.212 秒）；临时项目路径含空格；验证 Java 局部重编、Metal/部署版本失效、筛选/失败退出、依赖路径迁移、干净 native 构建、NotesMotion 复用及 native 参数变化重建。
- `./build.sh demo NotesMotion --quit-after-first-frame`、`./build.sh native-demo Notes --quit-after-first-frame`：正常退出。
- `./build.sh jvm --bench`、`./build.sh native -Os` 及原型 native `--bench`：构建/入口冒烟通过，不作为重新验收性能指标。
- 相同参数第二次 `./build.sh native -Os`：Java/Metal/resources/runtimeClasspath/native 均 UP-TO-DATE，Gradle 构建 349 ms；程序启动耗时另计。
- 独立只读审查：无 Critical；1 Important（classpath 路径归一化），已复现 RED→GREEN 并修复。直接 Wrapper 的 JAVA_HOME/GRAALVM_HOME 区别已写入 README，未改全局 JVM 配置。
- 集成检查另发现 Exec 的 args 输入重名，已改为 nativeImageArgs；参数变化的实际 native 重编与再次复用均通过。shell argv 使用 NUL 分隔文件，普通构建不新增 Python 依赖。
- 使用现有检出中的独立分支；没有新建工作树、修改 UI、引入新测试框架或重跑性能门槛。现有 GPU/窗口测试每次执行，编译可以跳过；native-image 有变化时仍是整次 AOT 编译。
