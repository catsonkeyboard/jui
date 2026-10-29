package jui.render;

import jui.render.metal.MetalRenderer;

public final class AtlasGpuTest {
    public void testOldGlyphSurvivesTextureGrowthWithCommandsInFlight() { MetalRenderer.checkAtlasGrowth(); }
}
