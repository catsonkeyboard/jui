package jui.spike;

import java.util.ArrayList;
import jui.examples.EditorSurface;
import jui.render.Scene;
import jui.text.*;
import jui.ui.*;
import jui.ui.engine.Engine;
import static jui.ui.Ui.*;

public final class EditorWorkload implements Runnable, View {
    public static final String SOURCE = ("中英 mixed-font paragraph abcdefghijklmnopqrstuvwxyz 0123456789 ".repeat(3).substring(0, 99) + "\n").repeat(50);
    final TextEngine text;
    final Engine engine;
    final Scene scene = new Scene();
    final GlyphAtlas atlas;
    final EditorSurface editor = new EditorSurface(SOURCE, EditorWorkload::style);

    EditorWorkload(TextEngine text, int initialAtlas) {
        this.text = text; atlas = new GlyphAtlas(initialAtlas, initialAtlas, 4096);
        engine = new Engine(text, Theme.light(), () -> {});
    }
    public static Spans style(String source) {
        ArrayList<Span> spans = new ArrayList<>();
        int paragraph = 0;
        for (int start = 0; start < source.length(); paragraph++) {
            int found = source.indexOf('\n', start), end = found < 0 ? source.length() : found + 1;
            int middle = start + (end - start) / 2;
            float size = switch (paragraph % 3) { case 0 -> 13; case 1 -> 18; default -> 24; };
            spans.add(Span.text(source.substring(start, middle)).fontSize(size).bold().color(Color.rgb(0x335588)));
            spans.add(Span.text(source.substring(middle, end)).fontSize(size).monospace().italic().underline());
            start = end;
        }
        return Spans.of(spans.toArray(Span[]::new));
    }
    public void build(Context context) {
        editor.attach(context);
        Element element = canvas(context).fill().handleTextInput(editor).handleInput(editor.handler).autoFocus().draw(editor.drawing);
        editor.focused = element.focused();
    }
    public void run() {
        engine.buildAndLayout(this, 800, 600);
        scene.begin(2, text, atlas); engine.paint(scene);
        if (atlas.isFull()) throw new IllegalStateException("mixed-font atlas filled");
    }
}
