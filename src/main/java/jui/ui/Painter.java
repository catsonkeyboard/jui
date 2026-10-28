package jui.ui;

import jui.render.Scene;
import jui.text.TextLayout;
import jui.ui.engine.Dev;
import jui.ui.engine.Engine;
import jui.ui.engine.Node;

public final class Painter {
    @FunctionalInterface
    public interface Draw { void draw(Painter painter, Rect bounds); }

    private final Engine engine;
    private Scene scene;
    private float originX, originY, opacity;
    private int depth;

    public Painter(Engine engine) { this.engine = engine; }

    private boolean valid() {
        if (scene != null && jui.ffi.Libc.pthreadMainNp() != 0) return true;
        Dev.fail("Painter is valid only during its draw callback on the UI thread");
        return false;
    }

    private int color(Color value) {
        int rgba = value.rgba();
        return (rgba & 0xFFFFFF00) | Math.round((rgba & 255) * opacity);
    }

    public float scale() { return valid() ? scene.scale() : 1; }

    public void box(float x, float y, float width, float height, Color color) {
        roundedBox(x, y, width, height, 0, color);
    }

    public void roundedBox(float x, float y, float width, float height, float radius, Color color) {
        if (valid()) scene.box(originX + x, originY + y, width, height, radius, color(color));
    }

    public void circle(float x, float y, float radius, Color color) {
        roundedBox(x - radius, y - radius, radius * 2, radius * 2, radius, color);
    }

    public void line(float x0, float y0, float x1, float y1, float width, Color color) {
        if (valid()) scene.line(originX + x0, originY + y0, originX + x1, originY + y1, width, color(color));
    }

    public TextLayout text(String text, float x, float y, float size, int weight, Color color, float maxWidth) {
        if (!valid()) return null;
        TextLayout layout = engine.text().layout(text, size, weight, maxWidth);
        text(layout, x, y, color);
        return layout;
    }

    public void text(TextLayout layout, float x, float y, Color color) {
        if (!valid()) return;
        float top = originY + y;
        int first = Math.max(0, layout.lineForY(scene.clipTop() - top) - 1);
        int end = Math.min(layout.lines, layout.lineForY(scene.clipBottom() - top) + 2);
        scene.text(layout, originX + x, top, color(color), first, end);
    }

    public void clip(float x, float y, float width, float height) { clip(x, y, width, height, 0); }

    public void clip(float x, float y, float width, float height, float radius) {
        if (!valid()) return;
        scene.pushClip(originX + x, originY + y, width, height, radius, radius, radius, radius);
        depth++;
    }

    public void popClip() {
        if (!valid()) return;
        if (depth == 0) { Dev.fail("Painter cannot pop an ancestor's clip"); return; }
        scene.popClip();
        depth--;
    }

    public void animationFrame() { if (valid()) engine.invalidate(); }

    public void paint(Node node, Scene scene, float opacity) {
        Painter painter = Dev.ENABLED ? new Painter(engine) : this;
        painter.scene = scene;
        painter.originX = node.x;
        painter.originY = node.y;
        painter.opacity = opacity;
        float half = Math.min(node.w, node.h) / 2;
        float border = node.border;
        scene.pushClip(node.x + border, node.y + border, Math.max(0, node.w - 2 * border), Math.max(0, node.h - 2 * border),
                inner(node.radiusTL, half, border), inner(node.radiusTR, half, border),
                inner(node.radiusBR, half, border), inner(node.radiusBL, half, border));
        Rect bounds = node.state.drawBounds;
        if (bounds == null || bounds.width() != node.w || bounds.height() != node.h) {
            bounds = node.state.drawBounds = new Rect(0, 0, node.w, node.h);
        }
        try {
            node.draw.draw(painter, bounds);
        } finally {
            while (painter.depth > 0) painter.popClip();
            scene.popClip();
            painter.scene = null;
        }
    }

    private static float inner(float radius, float half, float border) {
        return Math.max(0, Math.min(radius, half) - border);
    }
}
