#!/usr/bin/env bash
# Builds and runs the macOS spike.
#
#   ./build.sh jvm [--bench]      compile, then run on the JVM (GraalVM's HotSpot)
#   ./build.sh native [flags...]  build the native image build/jui-spike
#   ./build.sh bench [runs]       measure startup, memory and size: native image, JVM,
#                                 and the same app in Objective-C (baseline/spike.m)
#   ./build.sh test [Class[#m]]   run the tests in src/test/java
#
# Needs GraalVM 25 (GRAALVM_HOME, or found in ~/Library/Java/JavaVirtualMachines)
# and Xcode's Metal compiler.
set -euo pipefail
cd "$(dirname "$0")"

find_graalvm() {
  if [[ -n "${GRAALVM_HOME:-}" ]]; then
    echo "$GRAALVM_HOME"
    return
  fi
  for d in "$HOME"/Library/Java/JavaVirtualMachines/graalvm*25*/Contents/Home \
           /Library/Java/JavaVirtualMachines/graalvm*25*/Contents/Home; do
    if [[ -x "$d/bin/native-image" ]]; then
      echo "$d"
      return
    fi
  done
  echo "GraalVM 25 not found: set GRAALVM_HOME" >&2
  exit 1
}

GRAALVM_HOME=$(find_graalvm)
JAVA="$GRAALVM_HOME/bin/java"
BIN=build/jui-spike
# The oldest macOS the binary and the shader run on. Left alone, native-image
# targets the macOS that builds it, and the binary would not open on older ones.
MACOS_MIN=${MACOS_MIN:-14.0}

compile() {
  rm -rf build/classes
  mkdir -p build/classes/jui/shaders
  cp src/main/resources/jui/shaders/shader.metal build/classes/jui/shaders/
  # Precompile the shader, as MyGO does: no compiling at startup.
  xcrun -sdk macosx metal -mmacosx-version-min="$MACOS_MIN" \
    -o build/classes/jui/shaders/shader.metallib src/main/resources/jui/shaders/shader.metal
  # NativeImageFeature compiles against GraalVM's own module (which --release
  # cannot see); only native-image loads it.
  "$GRAALVM_HOME/bin/javac" --add-modules org.graalvm.nativeimage \
    -d build/classes $(find src/main/java -name '*.java')
}

build_native() {
  compile
  "$GRAALVM_HOME/bin/native-image" \
    --enable-native-access=ALL-UNNAMED \
    --features=jui.ffi.NativeImageFeature \
    --initialize-at-build-time=jui.ffi.Sigs,jui.ffi.Native \
    -H:NativeLinkerOption=-mmacosx-version-min="$MACOS_MIN" \
    -cp build/classes -o "$BIN" "$@" jui.spike.Main
}

run_jvm() {
  "$JAVA" -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -cp build/classes jui.spike.Main "$@"
}

# run_tests compiles the tests in src/test/java and runs them with the
# zero-dependency runner; $1, if given, picks a class or a Class#method.
run_tests() {
  compile
  rm -rf build/test-classes
  mkdir -p build/test-classes
  "$GRAALVM_HOME/bin/javac" -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
  "$JAVA" -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -Djui.dev=true \
    -cp build/classes:build/test-classes jui.testing.RunTests "$@"
}

# median reads numbers, one a line.
median() {
  sort -n | awk '{ v[NR] = $1 } END { if (NR % 2) print v[(NR + 1) / 2]; else printf "%.1f\n", (v[NR / 2] + v[NR / 2 + 1]) / 2 }'
}

# measure runs a command $runs times with --bench and prints the medians of its marks.
measure() {
  local label=$1 runs=$2
  shift 2
  "$@" --bench > /dev/null # the first run of a new binary pays for the system's checks
  local out=build/bench-$label.txt
  : > "$out"
  for ((i = 0; i < runs; i++)); do
    "$@" --bench | grep '^BENCH' >> "$out"
  done
  printf "%-7s" "$label"
  for key in main launched ready committed frame; do
    printf " %10s" "$(sed -E "s/.* $key=([0-9.]+).*/\1/" "$out" | median)"
  done
  printf " %10s\n" "$(idle_memory "$@")"
}

# idle_memory runs a command, and prints its physical footprint once it has
# idled 3 seconds, as macOS's Activity Monitor counts memory.
idle_memory() {
  "$@" > /dev/null 2>&1 &
  local pid=$!
  sleep 3
  vmmap --summary "$pid" 2> /dev/null | awk '/^Physical footprint:/ { print $3 }'
  kill "$pid"
  wait "$pid" 2> /dev/null || true
}

bench() {
  local runs=${1:-10}
  build_native
  echo
  echo "Native image: $(du -h "$BIN" | cut -f1) ($(stat -f %z "$BIN") bytes)"
  echo "Medians of $runs runs, in ms from the process's start to: main(), the app launched,"
  echo "the window and Metal ready, the first frame committed, and drawn by the GPU."
  echo "Memory: physical footprint after 3 s idle."
  printf "%-7s %10s %10s %10s %10s %10s %10s\n" mode "main()" launched window committed "1st frame" memory
  measure native "$runs" "$BIN"
  measure jvm "$runs" "$JAVA" -XstartOnFirstThread --enable-native-access=ALL-UNNAMED -cp build/classes jui.spike.Main
  # The control: the same app in Objective-C, for what macOS itself costs.
  clang -fobjc-arc -O2 -mmacosx-version-min=14.0 -framework AppKit -framework Metal -framework QuartzCore \
    baseline/spike.m -o build/objc-spike
  measure objc "$runs" build/objc-spike build/classes/jui/shaders/shader.metallib
}

case "${1:-native}" in
  jvm) shift; compile; run_jvm "$@" ;;
  native) shift || true; build_native "$@" ;;
  bench) shift; bench "$@" ;;
  test) shift; run_tests "$@" ;;
  *) echo "usage: $0 jvm|native|bench|test" >&2; exit 2 ;;
esac
