#!/usr/bin/env python3
"""Generate the design §8 disk fixture under ignored build/, once."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
root.mkdir(parents=True, exist_ok=True)
for i in range(10000):
    note = root / f"{i + 1:05d} · 笔记 会议纪要 Java.md"
    if not note.exists():
        note.write_text(f"# 笔记 {i + 1}\n\n一段中英混排的正文。j-ui startup benchmark.\n\n只读取这一篇，其余正文留在磁盘。\n", encoding="utf-8")
(root / ".last-note").write_text("00001 · 笔记 会议纪要 Java.md", encoding="utf-8")
print(root.resolve())
