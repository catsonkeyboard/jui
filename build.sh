#!/usr/bin/env bash
# Builds and runs the macOS spike.
#
#   ./build.sh jvm [--bench]      compile, then run on the JVM (GraalVM's HotSpot)
#   ./build.sh native [flags...]  build the native image build/jui-spike
#   ./build.sh bench [runs] [notes]  # notes: disk vault startup <200ms, idle footprint <80MB
#   ./build.sh bench [runs]       measure startup, memory and size: native image, JVM,
#                                 and the same app in Objective-C (baseline/spike.m)
#   ./build.sh test [Class[#m]]   run the tests in src/test/java
#   ./build.sh alloc              measure per-frame allocation of the UI API (native and JVM)
#   ./build.sh presentbench       native key event to drawable presentation and scroll cadence
#   ./build.sh shellbench         measure full NotesDemo steady frames, allocation, typing and scroll
#   ./build.sh listbench          time scroll frames of a 10000-row list in a native image (the M3 gate)
#   ./build.sh ffi-check          call Objective-C methods of every struct shape, on the JVM and in a native image
#   ./build.sh demo [Name] [args]        run an example on the JVM: Card (default), Counter, Todo, Files, Scroll, Form, TextArea, ContextMenu, EditorPrimitives, Notes or Graph (NotesMotion aliases Notes)
#   ./build.sh native-demo [Name] [args] build an example into build/<name>-demo and run it
#
# JUI_FRAME_STATS=1 logs frames over 8 ms; all logs every frame; a number is ms; off disables.
# Needs GraalVM 25 (GRAALVM_HOME, or found in ~/Library/Java/JavaVirtualMachines)

case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*|Windows_NT) WINDOWS=1 ;;
  *) WINDOWS= ;;
esac
CP_SEP=":"; [[ -n "$WINDOWS" ]] && CP_SEP=";"

find_graalvm() {
  if [[ -n "${GRAALVM_HOME:-}" ]]; then
    echo "$GRAALVM_HOME"
    return
  fi
  if [[ -n "$WINDOWS" ]]; then
    for d in "$USERPROFILE"/jdk/graalvm-community-*; do
      if [[ -x "$d/bin/javac.exe" ]]; then
        echo "$d"
        return
      fi
    done
  else
    for d in "$HOME"/Library/Java/JavaVirtualMachines/graalvm*25*/Contents/Home \
             /Library/Java/JavaVirtualMachines/graalvm*25*/Contents/Home; do
      if [[ -x "$d/bin/native-image" ]]; then
        echo "$d"
        return
      fi
    done
  fi
  echo "GraalVM 25 not found: set GRAALVM_HOME" >&2
  exit 1
}

GRAALVM_HOME=$(find_graalvm)
JAVA="$GRAALVM_HOME/bin/java"
THREAD_FLAG=(); [[ -z "$WINDOWS" ]] && THREAD_FLAG=(-XstartOnFirstThread)
BIN=build/jui-spike
# The oldest macOS the binary and the shader run on. Left alone, native-image
# targets the macOS that builds it, and the binary would not open on older ones.
MACOS_MIN=${MACOS_MIN:-14.0}

compile() {
  rm -rf build/classes
  mkdir -p build/classes/jui/shaders
  cp src/main/resources/jui/shaders/shader.metal build/classes/jui/shaders/
  cp src/main/resources/jui/shaders/shader.hlsl build/classes/jui/shaders/
  if [[ -z "$WINDOWS" ]]; then
    # Precompile the shader, as MyGO does: no compiling at startup.
    xcrun -sdk macosx metal -mmacosx-version-min="$MACOS_MIN" \
      -o build/classes/jui/shaders/shader.metallib src/main/resources/jui/shaders/shader.metal
  fi
  # NativeImageFeature compiles against GraalVM's own module (which --release
  # cannot see); only native-image loads it.
  "$GRAALVM_HOME/bin/javac" --add-modules org.graalvm.nativeimage \
    -d build/classes $(find src/main/java -name '*.java')
}

# native_image MAIN OUT [flags...] builds the compiled classes into a native image.
native_image() {
  local main=$1 out=$2
  shift 2
  local ni="$GRAALVM_HOME/bin/native-image"
  [[ -n "$WINDOWS" ]] && ni="$GRAALVM_HOME/bin/native-image.cmd"
  local macos_min=()
  [[ -z "$WINDOWS" ]] && macos_min=(-H:NativeLinkerOption=-mmacosx-version-min="$MACOS_MIN")
  # Build-time handles fold to direct calls on the mac; the Windows analyzer
  # cannot fold them, so there the handles stay runtime-initialized.
  local init=(--initialize-at-build-time=jui.ffi.Sigs,jui.ffi.Native)
  [[ -n "$WINDOWS" ]] && init=()
  "$ni" \
    --enable-native-access=ALL-UNNAMED \
    --features=jui.ffi.NativeImageFeature \
    "${init[@]}" \
    "${macos_min[@]}" \
    -cp build/classes -o "$out" "$@" "$main"
}

build_native() {
  compile
  native_image jui.spike.Main "$BIN" "$@"
}

# example_name prints the example's name (jui.examples.<Name>Demo): $1 if it
# is not a flag, else Card.
example_name() {
  if [[ $# -gt 0 && "$1" != -* ]]; then
    if [[ "$1" == NotesMotion ]]; then echo Notes; else echo "$1"; fi
  else
    echo Card
  fi
}

# demo runs an example on the JVM.
demo() {
  compile
  local name
  name=$(example_name "$@")
  if [[ $# -gt 0 && "$1" != -* ]]; then shift; fi
  "$JAVA" "${THREAD_FLAG[@]}" --enable-native-access=ALL-UNNAMED -cp build/classes "jui.examples.${name}Demo" "$@"
}

# native_demo builds an example into build/<name>-demo and runs it.
native_demo() {
  compile
  local name lower
  name=$(example_name "$@")
  if [[ $# -gt 0 && "$1" != -* ]]; then shift; fi
  lower=$(echo "$name" | tr '[:upper:]' '[:lower:]')
  # This demo holds filenames and one body; bound the default native heap to
  # its workload. The mac shapes it within 32 MB; the DirectWrite layout
  # churns some 150 MB a reshape for now, so Windows gets more room until
  # the text backend's allocations come down.
  if [[ "$name" == Notes ]]; then
    local heap=33554432
    [[ -n "$WINDOWS" ]] && heap=268435456
    native_image "jui.examples.${name}Demo" "build/${lower}-demo" -R:MaxHeapSize=$heap
  else
    native_image "jui.examples.${name}Demo" "build/${lower}-demo"
  fi > "build/${lower}-demo-build.log"
  "build/${lower}-demo" "$@"
}

# alloc measures what frames of the UI API allocate, in a native image and on the JVM.
alloc() {
  compile
  native_image jui.spike.AllocSpike build/alloc-spike > build/alloc-build.log
  build/alloc-spike
  "$JAVA" "${THREAD_FLAG[@]}" --enable-native-access=ALL-UNNAMED -cp build/classes jui.spike.AllocSpike
}

# listbench builds the scroll frame gate into a native image and runs it.
listbench() {
  compile
  native_image jui.spike.ListBench build/list-bench > build/list-bench-build.log
  build/list-bench
  build/list-bench --small-atlas
}

presentbench() {
  compile
  native_image jui.spike.PresentBench build/present-bench > build/present-bench-build.log
  build/present-bench
}

shellbench() {
  compile
  native_image jui.spike.ShellBench build/shell-bench > build/shell-bench-build.log
  build/shell-bench
  "$JAVA" "${THREAD_FLAG[@]}" --enable-native-access=ALL-UNNAMED -cp build/classes jui.spike.ShellBench
}

# ffi_check calls Objective-C methods that take and return ranges, rects and
# points, on the JVM and in a native image, where a signature left
# unregistered would fail only at its first call.
ffi_check() {
  compile
  "$JAVA" "${THREAD_FLAG[@]}" --enable-native-access=ALL-UNNAMED -cp build/classes jui.spike.FfiCheck
  native_image jui.spike.FfiCheck build/ffi-check > build/ffi-check-build.log
  build/ffi-check
}

run_jvm() {
  "$JAVA" "${THREAD_FLAG[@]}" --enable-native-access=ALL-UNNAMED -cp build/classes jui.spike.Main "$@"
}

# run_tests compiles the tests in src/test/java and runs them with the
# zero-dependency runner; $1, if given, picks a class or a Class#method.
# On Windows the macOS-only tests are left out of the compile.
run_tests() {
  compile
  rm -rf build/test-classes
  mkdir -p build/test-classes
  local sources=()
  if [[ -n "$WINDOWS" ]]; then
    mapfile -t sources < <(find src/test/java \
      -path src/test/java/jui/platform/mac -prune -o \
      -path src/test/java/jui/render/metal -prune -o \
      -path src/test/java/jui/ffi -prune -o \
      -name AtlasGpuTest.java -prune -o \
      -name '*.java' -print)
  else
    mapfile -t sources < <(find src/test/java -name '*.java')
  fi
  "$GRAALVM_HOME/bin/javac" -cp build/classes -d build/test-classes "${sources[@]}"
  "$JAVA" "${THREAD_FLAG[@]}" --enable-native-access=ALL-UNNAMED -Djui.dev=true \
    -cp "build/classes$CP_SEP""build/test-classes" jui.testing.RunTests "$@"
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

bench_notes() {
  local runs=$1 vault out=build/bench-notes.txt memory=build/bench-notes-memory.txt
  vault=$(python3 scripts/notes-fixture.py build/notes-vault)
  compile
  native_image jui.examples.NotesDemo build/notes-demo -R:MaxHeapSize=33554432 > build/notes-demo-build.log
  build/notes-demo --vault="$vault" --quit-after-first-frame > /dev/null
  : > "$out"
  : > "$memory"
  for ((i = 0; i < runs; i++)); do
    build/notes-demo --vault="$vault" --quit-after-first-frame | grep '^BENCH ' >> "$out"
    local pid footprint
    build/notes-demo --vault="$vault" > build/notes-idle.log 2>&1 &
    pid=$!
    sleep 3
    footprint=$(vmmap --summary "$pid" 2>/dev/null | awk '/^Physical footprint:/ {print $3}')
    kill "$pid"
    wait "$pid" 2>/dev/null || true
    grep -q '^VAULT notes=10000$' build/notes-idle.log || { echo 'FAIL: vault not fully scanned' >&2; return 1; }
    printf '%s\n' "$footprint" >> "$memory"
  done
  python3 scripts/notes-gate.py "$out" "$memory" "$runs"
}

bench() {
  local runs=${1:-10}
  if [[ ${2:-} == notes ]]; then bench_notes "$runs"; return; fi
  build_native
  echo
  echo "Native image: $(du -h "$BIN" | cut -f1) ($(stat -f %z "$BIN") bytes)"
  echo "Medians of $runs runs, in ms from the process's start to: main(), the app launched,"
  echo "the window and Metal ready, the first frame committed, and drawn by the GPU."
  echo "Memory: physical footprint after 3 s idle."
  printf "%-7s %10s %10s %10s %10s %10s %10s\n" mode "main()" launched window committed "1st frame" memory
  measure native "$runs" "$BIN"
  measure jvm "$runs" "$JAVA" "${THREAD_FLAG[@]}" --enable-native-access=ALL-UNNAMED -cp build/classes jui.spike.Main
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
  alloc) alloc ;;
  listbench) listbench ;;
  shellbench) shellbench ;;
  presentbench) presentbench ;;
  ffi-check) ffi_check ;;
  demo) shift; demo "$@" ;;
  native-demo) shift; native_demo "$@" ;;
  *) echo "usage: $0 jvm|native|bench|test|alloc|listbench|shellbench|presentbench|ffi-check|demo|native-demo" >&2; exit 2 ;;
esac
