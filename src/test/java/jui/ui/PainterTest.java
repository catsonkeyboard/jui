package jui.ui;

import jui.testing.Check;
import static jui.ui.Ui.*;

public final class PainterTest {
    public void testCoordinatesAndOpacityAreInherited() {
        Tester tester = Tester.of(context -> column(context).padding(10).children(() ->
                canvas(context).size(100, 80).opacity(0.5f).draw((painter, bounds) -> {
                    Check.near(0, bounds.x());
                    Check.near(100, bounds.width());
                    painter.box(3, 4, 20, 10, Color.rgb(0xFF0000));
                })), 200, 160);
        tester.frame();
        float[] instance = tester.scene().instance(0);
        Check.near(13, instance[0]);
        Check.near(14, instance[1]);
        Check.near(0.5, instance[11], 0.01);
    }

    public void testExpiredPainterDoesNotReactivate() {
        Painter[] saved = new Painter[1];
        Tester tester = Tester.of(context -> {
            canvas(context).size(20, 20).draw((painter, bounds) -> saved[0] = painter);
            canvas(context).size(20, 20).draw((painter, bounds) ->
                    Check.fails(IllegalStateException.class, () -> saved[0].scale()));
        }, 200, 160);
        tester.frame();
        Check.fails(IllegalStateException.class, () -> saved[0].popClip());
    }

    public void testClipCannotEscapeAndExceptionsRestoreAncestors() {
        Tester tester = Tester.of(context -> box(context).size(100, 80).clip().children(() ->
                canvas(context).size(50, 40).draw((painter, bounds) -> {
                    Check.fails(IllegalStateException.class, painter::popClip);
                    painter.clip(0, 0, 5, 5);
                    throw new IllegalStateException("draw failed");
                })), 200, 160);
        Check.fails(IllegalStateException.class, tester::frame);
        tester.scene().box(0, 0, 5, 5, 0, 0xFFFFFFFF);
        Check.isTrue(!tester.scene().batchClipped(tester.scene().batches() - 1), "all clips restored");
    }

    public void testSharedLayoutAndClipboardServices() {
        Context[] saved = new Context[1];
        Tester tester = Tester.of(context -> saved[0] = context, 200, 160);
        tester.frame();
        saved[0].copyText("hello");
        Check.equal("hello", tester.clipboard());
        Check.isTrue(saved[0].layoutText("abc", 13, 400, 100)
                == saved[0].layoutText("abc", 13, 400, 100), "shared cached layout");
    }
}
