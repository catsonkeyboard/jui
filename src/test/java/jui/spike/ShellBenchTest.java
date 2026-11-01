package jui.spike;

import jui.testing.Check;

public final class ShellBenchTest {
    public void testGateRejectsEachExceededBudgetAndMissingMeasurements() {
        Check.isTrue(ShellBench.withinBudget(3.9, 32768, 8.3, 3.9, false), "valid measurements pass");
        Check.isTrue(!ShellBench.withinBudget(4, 0, 1, 1, false), "steady p95 must be below 4 ms");
        Check.isTrue(!ShellBench.withinBudget(1, 32769, 1, 1, false), "whole app allocation budget");
        Check.isTrue(!ShellBench.withinBudget(1, 0, 8.334, 1, false), "typing at 120 Hz");
        Check.isTrue(!ShellBench.withinBudget(1, 0, 1, 4, false), "scroll p95 must be below 4 ms");
        Check.isTrue(!ShellBench.withinBudget(1, 0, 1, 1, true), "no missing glyphs");
        Check.isTrue(!ShellBench.withinBudget(Double.NaN, 0, 1, 1, false), "missing time cannot pass");
        Check.isTrue(!ShellBench.withinBudget(1, -1, 1, 1, false), "unsupported allocation cannot pass");
    }
}
