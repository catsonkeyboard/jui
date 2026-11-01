#!/usr/bin/env python3
"""Fail closed on absent/invalid startup or footprint measurements."""
import math
from pathlib import Path
import re
import statistics
import sys

marks = [dict((k, float(v)) for k, v in re.findall(r"(main|launched|ready|committed|frame)=([0-9.]+)", line))
         for line in Path(sys.argv[1]).read_text().splitlines()]
mem = []
for line in Path(sys.argv[2]).read_text().splitlines():
    match = re.fullmatch(r"([0-9.]+)([KMG])", line.strip())
    if not match:
        sys.exit("FAIL: missing/invalid physical footprint")
    mem.append(float(match[1]) * {"K": 1 / 1024, "M": 1, "G": 1024}[match[2]])
runs = int(sys.argv[3])
if runs < 1 or len(marks) != runs or len(mem) != runs or any(len(m) != 5 or not all(math.isfinite(v) and v > 0 for v in m.values()) for m in marks):
    sys.exit("FAIL: incomplete startup measurements")
med = {key: statistics.median(m[key] for m in marks) for key in marks[0]}
footprint = statistics.median(mem)
print("NOTESBENCH " + " ".join(f"{k}={v:.3f}ms" for k, v in med.items()) + f" footprint={footprint:.3f}MB runs={runs} notes=10000")
if med["frame"] >= 200 or footprint >= 80:
    sys.exit("FAIL: Notes startup must be <200ms and idle footprint <80MB")
