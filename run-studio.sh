#!/usr/bin/env bash
# ==============================================================================
# SevenWDB Studio - 基于 j-ui GPU 桌面框架的可视化查询分析器启动脚本
# ==============================================================================
set -e

# 定位工程目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "$SCRIPT_DIR" == *"/jui" ]]; then
  JUI_DIR="$SCRIPT_DIR"
  SEVENWDB_DIR="$(cd "$SCRIPT_DIR/../sevenwdb" 2>/dev/null || cd "$SCRIPT_DIR/../sevenwdb" && pwd)"
else
  SEVENWDB_DIR="$SCRIPT_DIR"
  JUI_DIR="$(cd "$SCRIPT_DIR/../jui" && pwd)"
fi

# 检查 JDK (优先使用已安装的 OpenJDK 26/GraalVM，要求支持 FFM 内存段 API)
JAVA_BIN=""
if [[ -x "/opt/homebrew/opt/openjdk/bin/java" ]]; then
  JAVA_BIN="/opt/homebrew/opt/openjdk/bin/java"
  JAVAC_BIN="/opt/homebrew/opt/openjdk/bin/javac"
elif [[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
  JAVAC_BIN="$JAVA_HOME/bin/javac"
else
  JAVA_BIN="$(which java)"
  JAVAC_BIN="$(which javac)"
fi

echo "================================================================="
echo "       🚀 启动 SevenWDB Studio 可视化查询分析器 (Powered by j-ui)    "
echo "================================================================="
echo "Java 运行环境 : $("$JAVA_BIN" -version 2>&1 | head -n 1)"
echo "SevenWDB 路径    : $SEVENWDB_DIR"
echo "j-ui 路径     : $JUI_DIR"
echo "-----------------------------------------------------------------"

# 1. 确保 SevenWDB 已编译打包
if [[ ! -f "$SEVENWDB_DIR/target/sevenwdb-1.0.0-SNAPSHOT.jar" ]]; then
  echo "正在构建 SevenWDB jar 包..."
  (cd "$SEVENWDB_DIR" && mvn -q -DskipTests package)
fi
SEVENWDB_JAR="$SEVENWDB_DIR/target/sevenwdb-1.0.0-SNAPSHOT.jar"

# 2. 编译 j-ui 与 SevenWDbStudio
BUILD_DIR="$JUI_DIR/build/studio-classes"
mkdir -p "$BUILD_DIR/jui/shaders"
cp -f "$JUI_DIR/src/main/resources/jui/shaders/shader.metal" "$BUILD_DIR/jui/shaders/"

echo "编译界面与着色器..."
"$JAVAC_BIN" --enable-preview --source 26 \
  -cp "$SEVENWDB_JAR" \
  -d "$BUILD_DIR" \
  $(find "$JUI_DIR/src/main/java" -name "*.java" ! -name "NativeImageFeature.java")

echo "启动 GPU 渲染窗口..."
exec "$JAVA_BIN" \
  --enable-preview \
  --enable-native-access=ALL-UNNAMED \
  -XstartOnFirstThread \
  -cp "$SEVENWDB_JAR:$BUILD_DIR" \
  jui.sevenwdb.SevenWDbStudio "$@"
