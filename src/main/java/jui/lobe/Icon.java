package jui.lobe;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Painter;
import java.util.Map;
import static jui.ui.Ui.box;

/** Small stroke icon vocabulary. Paths are tinted explicitly on a 24 DIP grid. */
public final class Icon {
    private static final Map<String, float[]> PATHS = Map.ofEntries(
        path("plus", 12,5,12,19, 5,12,19,12),
        path("minus", 5,12,19,12),
        path("check", 5,12,10,17, 10,17,20,7),
        path("close", 6,6,18,18, 18,6,6,18),
        path("chevron-down", 6,9,12,15, 12,15,18,9),
        path("chevron-right", 9,6,15,12, 15,12,9,18),
        path("chevron-left", 15,6,9,12, 9,12,15,18),
        path("arrow-right", 4,12,20,12, 14,6,20,12, 20,12,14,18),
        path("arrow-up", 12,20,12,4, 6,10,12,4, 12,4,18,10),
        path("copy", 9,9,20,9, 20,9,20,20, 20,20,9,20, 9,20,9,9,
                4,15,4,4, 4,4,15,4),
        path("code", 8,6,2,12, 2,12,8,18, 16,6,22,12, 22,12,16,18, 14,4,10,20),
        path("menu", 4,6,20,6, 4,12,20,12, 4,18,20,18),
        path("panel", 4,4,20,4, 20,4,20,20, 20,20,4,20, 4,20,4,4, 10,4,10,20),
        path("grid", 4,4,9,4, 9,4,9,9, 9,9,4,9, 4,9,4,4,
                15,4,20,4, 20,4,20,9, 20,9,15,9, 15,9,15,4,
                4,15,9,15, 9,15,9,20, 9,20,4,20, 4,20,4,15,
                15,15,20,15, 20,15,20,20, 20,20,15,20, 15,20,15,15),
        path("file", 5,3,14,3, 14,3,19,8, 19,8,19,21, 19,21,5,21, 5,21,5,3,
                14,3,14,8, 14,8,19,8, 9,13,15,13, 9,17,15,17),
        path("download", 12,3,12,15, 7,10,12,15, 12,15,17,10,
                4,16,4,21, 4,21,20,21, 20,21,20,16),
        path("send", 3,3,21,12, 21,12,3,21, 3,21,7,12, 7,12,3,3, 7,12,21,12),
        path("sparkles", 12,2,15,9, 15,9,22,12, 22,12,15,15, 15,15,12,22,
                12,22,9,15, 9,15,2,12, 2,12,9,9, 9,9,12,2),
        path("warning", 12,3,22,21, 22,21,2,21, 2,21,12,3, 12,9,12,14, 12,17,12,18),
        path("info", 12,10,12,18, 12,6,12,7),
        path("sun", 12,1,12,4, 12,20,12,23, 1,12,4,12, 20,12,23,12,
                4,4,6,6, 18,18,20,20, 4,20,6,18, 18,6,20,4),
        path("moon", 7,3,5,8, 5,8,6,15, 6,15,12,20, 12,20,19,18,
                19,18,21,14, 21,14,14,15, 14,15,9,10, 9,10,7,3)
    );
    private static Map.Entry<String,float[]> path(String name, float... points) {
        return Map.entry(name, points);
    }
    public static boolean known(String name) { return PATHS.containsKey(name) || name.equals("search"); }
    public static Element icon(Context c, String name, float size, Color color) {
        return box(c).size(size, size).shrink(0).draw(draw(name, color));
    }
    public static Painter.Draw draw(String name, Color color) {
        if (!known(name)) throw new IllegalArgumentException("Unknown lobe-jui icon: " + name);
        return (p,b) -> {
            float u = Math.min(b.width(), b.height()) / 24;
            float x = (b.width()-24*u)/2, y = (b.height()-24*u)/2;
            if (name.equals("search")) {
                // Circle as short connected strokes: one color and stroke width at every scale.
                for (int i=0; i<24; i++) {
                    double a=i*Math.PI/12, z=(i+1)*Math.PI/12;
                    p.line(x+(10+(float)Math.cos(a)*6)*u,y+(10+(float)Math.sin(a)*6)*u,
                            x+(10+(float)Math.cos(z)*6)*u,y+(10+(float)Math.sin(z)*6)*u,1.6f*u,color);
                }
                p.line(x+15*u,y+15*u,x+21*u,y+21*u,1.6f*u,color);
            } else {
                float[] v=PATHS.get(name);
                for(int i=0;i<v.length;i+=4)
                    p.line(x+v[i]*u,y+v[i+1]*u,x+v[i+2]*u,y+v[i+3]*u,1.6f*u,color);
                if(name.equals("sun")) p.circle(x+12*u,y+12*u,4*u,color);
            }
        };
    }
    private Icon() {}
}
