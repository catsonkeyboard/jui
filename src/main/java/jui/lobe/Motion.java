package jui.lobe;

import java.time.Duration;
import jui.ui.Element;

/** Action-driven transitions over the engine's retained animation state. */
public final class Motion {
    public static final Duration FAST=Duration.ofMillis(120), NORMAL=Duration.ofMillis(200);
    public static float transition(Element element,String key,float target) {
        return element.animate(key,target,NORMAL);
    }
    public static float easeOut(float value) {
        float t=Math.max(0,Math.min(1,value));
        return 1-(1-t)*(1-t)*(1-t);
    }
    private Motion() {}
}
