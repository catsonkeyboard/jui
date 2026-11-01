package jui.ui.engine;

import java.util.Random;
import jui.testing.Check;
import jui.text.TextEngine;
import jui.ui.*;
import static jui.ui.Ui.*;

/** Compare full geometry against the uncached algorithm while inputs and topology change. */
public final class LayoutCacheTest {
    private static Engine engine(TextEngine text, boolean cached) {
        String old = System.getProperty("jui.layoutCache");
        try {
            System.setProperty("jui.layoutCache", cached ? "on" : "off");
            return new Engine(text, Theme.light(), () -> {});
        } finally {
            if (old == null) System.clearProperty("jui.layoutCache"); else System.setProperty("jui.layoutCache", old);
        }
    }

    public void testStableTreeReusesLayoutAndChangedConstraintsInvalidate() {
        Engine a = engine(null, true), b = engine(null, false);
        View view = c -> row(c).fill().gap(7).children(() -> {
            box(c).grow(1).height(20).label("left"); box(c).grow(2).height(30).label("right");
        });
        a.buildAndLayout(view, 300, 100); b.buildAndLayout(view, 300, 100);
        a.buildAndLayout(view, 300, 100); b.buildAndLayout(view, 300, 100);
        Check.isTrue(a.cacheHits() >= 3, "a stable subtree must actually bypass layout");
        Check.equal(0L, b.cacheHits());
        a.buildAndLayout(view, 500, 200); b.buildAndLayout(view, 500, 200);
        for (String label : new String[] {"left", "right"}) Check.equal(b.bounds(label), a.bounds(label));
    }

    public void testRandomTreeMatchesWithStyleTextOrderAndStructureChanges() {
        TextEngine text = new TextEngine("zh-Hans");
        Engine a = engine(text, true), b = engine(text, false);
        Random random = new Random(6104);
        int[] variant = new int[48];
        for (int frame = 0; frame < 250; frame++) {
            for (int change = 0; change < 4; change++) variant[random.nextInt(variant.length)] = random.nextInt(128);
            View view = c -> tree(c, 0, variant);
            float width = 200 + random.nextInt(400), height = 200 + random.nextInt(300);
            for (int repeat = 0; repeat < 2; repeat++) {
                a.buildAndLayout(view, width, height); b.buildAndLayout(view, width, height);
                compareNodes(a, b, 0);
            }
        }
    }

    private static void tree(Context c, int index, int[] v) {
        int value = v[index];
        Element e;
        if (index >= 16) {
            e = (value & 1) == 0 ? text(c.key(index), "text " + value + " 中英换行")
                    : richText(c.key(index), Spans.of(Span.text("span " + value).fontSize(10 + value % 14).bold()));
        } else e = (value & 1) == 0 ? row(c.key(index)) : column(c.key(index));
        e.label("n" + index).padding(value % 5).margin(value % 3).gap(value % 7)
                .minWidth(value % 11).maxWidth(160 + value).minHeight(value % 9)
                .fontSize(12 + value % 8).border(value % 2, Color.rgb(0x123456));
        if ((value & 2) != 0) e.widthPercent(30 + value % 50); else e.grow(value % 3);
        if ((value & 4) != 0) e.height(10 + value % 60);
        if ((value & 8) != 0) e.reverse();
        if ((value & 16) != 0) e.wrap().alignItems(Align.CENTER);
        if ((value & 32) != 0) e.absolute().top(value % 20).left(value % 17);
        if (index < 16) e.children(() -> {
            int first = index * 3 + 1;
            for (int i = 0; i < 3; i++) {
                int child = first + ((value & 64) == 0 ? i : 2 - i);
                if (child < v.length && (v[child] != 127)) tree(c, child, v);
            }
        });
    }

    private static void compareNodes(Engine a, Engine b, int at) {
        Node x = a.node(at), y = b.node(at);
        Check.equal(y.id, x.id);
        Check.near(y.x, x.x); Check.near(y.y, x.y); Check.near(y.w, x.w); Check.near(y.h, x.h);
        Check.near(y.contentW, x.contentW); Check.near(y.contentH, x.contentH);
        Check.near(y.scrollX, x.scrollX); Check.near(y.scrollY, x.scrollY);
        if (y.layout != null) { Check.near(y.layout.width, x.layout.width); Check.near(y.layout.height, x.layout.height); }
        for (int child = y.firstChild; child != Node.NONE; child = b.node(child).nextSibling) compareNodes(a, b, child);
    }

    public void testVolatileSubtreesAndRevealNeverRestoreStaleGeometry() {
        for (boolean cached : new boolean[] {false, true}) {
            String old = System.getProperty("jui.layoutCache");
            System.setProperty("jui.layoutCache", cached ? "on" : "off");
            TextRef value = new TextRef(""); ListState listState = new ListState(); BoolRef open = new BoolRef(false);
            Tester t;
            try {
                t = Tester.of(c -> {
                    textInput(c, value).label("input");
                    list(c, listState, 1000, i -> text(c, "row " + i).height(30)).height(200).label("list");
                    modal(c, open, () -> text(c, "modal body")).width(100);
                }, 300, 400);
            } finally { if (old == null) System.clearProperty("jui.layoutCache"); else System.setProperty("jui.layoutCache", old); }
            try {
                t.frame(); t.click("input"); t.type("changed"); t.frame();
                Check.equal("changed", t.shownText("input"));
                t.scroll("list", 0, 400); t.frame();
                Check.isTrue(listState.first() > 0, "list moved");
                open.set(true); t.frame(); Check.isTrue(t.hasText("modal body"), "modal opened");
                open.set(false); t.frame(); Check.isTrue(!t.hasText("modal body"), "modal removed");
            } finally { t.close(); }
        }
    }
}
