package jui.spike;

import jui.testing.Check;

public final class PresentBenchTest {
    public void testNativeGateRejectsSlowInputsDroppedFramesAndUnavailableTimestamps() {
        Check.isTrue(PresentBench.withinBudget(8.0, 8.4, 8.0, 0, 1000, 120), "120 Hz measurements pass");
        Check.isTrue(!PresentBench.withinBudget(9, 8.4, 8, 0, 1000, 120), "key to presented must fit a refresh");
        Check.isTrue(!PresentBench.withinBudget(8, 17, 16.5, 1000, 1000, 120), "60 Hz cannot pass a 120 Hz target");
        Check.isTrue(!PresentBench.withinBudget(8, 8.4, 8, 11, 1000, 120), "more than 1% missed refreshes fails");
        Check.isTrue(!PresentBench.withinBudget(8, Double.NaN, 8, 0, 1000, 120), "missing native timestamps fail");
    }
}
