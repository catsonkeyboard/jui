# Unified Notes demo implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Follow TDD and run the complete suite before completion.

**Goal:** One Notes demo combines NotesMotion's three-pane styling/animations and Notes' catalog/search/tabs/palette/popover/menus.
**Architecture:** NotesDemo owns one FilesDemo.Note catalog, open tabs, groups, filtering and benchmark/vault entry points. A NotesWorkspace view owns palette tokens, three-pane geometry, animation and drawing helpers. NotesMotion becomes a build-command alias, not another app/model.
**Tech Stack:** Java 25, existing j-ui public APIs, Core Text/Metal, GraalVM native image.
**Spec:** User request in this chat; user selected “跟随系统明暗，保留三栏样式”. Existing contracts: README M5/M6 and notes motion demo; docs/design/2026-10-24-ui-api-design.md §6/8, with CPU acceptance previously confirmed.

## Global constraints
- Keep .idea and other worktrees untouched. Work on codex/unified-notes-demo; do not merge or push.
- One catalog shared by list/search/groups/tabs/palette/new note/menu; retain 10000-note virtualization, NotesVault first-body/async-name scan, forTools and first-frame flags.
- Follow system appearance; do not force Dark Aqua or change system settings.
- Preserve public UI behavior: resizable list, tabs, modal focus/Escape/outside click, popover anchoring, IME, existing shortcuts. New group/filter and pane animations interrupt smoothly and restore their controls.
- Keep readonly preview and memory-only new notes; real date filtering/full editor/storage remain outside scope.
- CPU gates remain: shell p95 <4ms, allocation <=32768B, typing max <=1000/120ms, startup <200ms and idle footprint <80MB. Supplemental presentation gate remains deferred.
- Canonical command demo/native-demo Notes; NotesMotion aliases Notes for existing scripts. No screenshot assets added.

## Review focus
1. Collapsed panes do not steal input; editor restore buttons stay reachable; divider works after collapse/reopen and resize.
2. Search, group/filter switching, new-note creation and palette all select the same Note objects without stale cached filtering or duplicate tabs.
3. Theme switch preserves selection, tabs, query and motion state; custom colors and title text remain readable.
4. Integration does not eagerly build 10000 card views or break vault first-frame behavior; benchmarks use list geometry instead of old hard-coded sidebar coordinates.
5. IME/modal/popover and old CLI options remain usable while autoplay/shortcuts manipulate the unified state.

### Task 1: Shared workspace, interaction and theme
**Files:** NotesDemo.java, new NotesWorkspace.java; NotesDemoTest.java; new NotesWorkspaceTest.java.
**Consumes:** FilesDemo.Note, NotesVault, current NotesDemo fields/tests, NotesMotion motion/icon patterns.
**Produces:** One model/view with labels navigation-pane, notes-pane, notes-list, editor, divider, filter-*, new-group, new-sheet, toggle-navigation, toggle-sheets; existing search/note-row/palette/info labels retained.
- [x] Write failing tests: three panes/theme preservation; interruption and restoring both panes; shared group/new-note/search/palette/tab state and divider drag/keyboard after reopening.
- [x] Run ./build.sh test NotesWorkspaceTest; observe missing merged UI behaviors.
- [x] Implement shared state and workspace with cached light/dark tokens, filter pill and group motion, virtualized cards, persistent editor controls, draggable divider and existing rich preview/overlays.
- [x] Update legacy geometry assumptions in NotesDemoTest to use divider bounds, then ./build.sh test passes all tests.
- [x] Commit task-scoped files.

### Task 2: Canonical entry, compatibility and measurement
**Files:** NotesDemo.main/menus, build.sh, ShellBench.java, PresentBench.java; port NotesMotionDemoTest and delete separate NotesMotionDemo; NotesVaultTest.
**Consumes:** Unified model/view, list bounds label, original CLI/vault/forTools contracts.
**Produces:** Notes canonical runtime with --autoplay/--reduced-motion/--print-window/--vault/--bench/--quit-after-first-frame; NotesMotion build alias.
- [x] Port motion tests to unified Notes, add filtered new-note/tab preservation and keyboard regressions; run failing cases before corresponding fixes.
- [x] Route startup/menu and autoplay through unified state, keep system appearance and remove duplicate app/model.
- [x] Move benchmark scroll coordinates to notes-list bounds; use 64 DIP scroll steps for taller cards without weakening any timing/allocation threshold.
- [x] Run full test, native shellbench and bench 5 notes serially. Fix real regressions, do not silently relax gates.
- [x] Commit task-scoped files.

### Task 3: Documentation and native acceptance
**Files:** README unified demo/commands/manual checklist; review record for this plan.
- [x] Build/start native Notes and compatibility alias. Inspect actual light/dark panes, group/filter motion, restored controls, palette/popover; no screenshots committed.
- [x] Run appropriate final test/alloc/listbench/ffi-check/shellbench/bench checks serially; record actual values and unchanged deferred items.
- [x] One fresh whole-branch read-only reviewer; fix Critical/Important with regression tests, record minor findings.
- [x] Update manual checklist to one Notes entry, commit; keep branch and report remaining native/manual limitations accurately.
