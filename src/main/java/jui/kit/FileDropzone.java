package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's file dropzone, after the FileDropzone: a generous target for
 * dropping one or more files. At rest it reads the hint around an upload
 * glyph; a drag over it tints the dashed border with the accent, a picked
 * file turns into a row with its name and size, and a progress hairline
 * runs while the caller "uploads" it.
 */
public final class FileDropzone {

    /** One dropped file's row facts. */
    public record File(String name, String size) {}

    private static final class St {
        boolean over;
        File file;
        float progress;
        long started;
    }

    /**
     * The zone: hint text, the file consumer (the engine has no file
     * dialog, so the caller decides what a click or a drop brings), and an
     * optional simulated upload. changed() lands in the consumer.
     */
    public static Element fileDropzone(Context c, String hint, String accept, boolean multiple,
                                       boolean disabled, java.util.function.Consumer<File> onFile) {
        Element host = column(c).gap(8).label("kit-dropzone");
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        Element zone = column(c).fillWidth().height(120).radius(Kit.RADIUS + 2).gap(6)
                .alignItems(Align.CENTER).justify(jui.ui.Justify.CENTER).clip()
                .label("kit-dropzone-face");
        float t = KitMotion.spring(host, "over", st.over ? 1 : 0, KitMotion.SNAPPY);
        zone.background(st.over ? Color.rgba(c.theme().accent().rgba() >>> 8, 0.05f) : KitMotion.surface(c));
        // The dashed edge: four hairlines with gaps the engine can't dash, so
        // the edge reads as a hairline whose color leans to the accent.
        zone.border(1.5f, KitMotion.mix(KitMotion.hairline(c), c.theme().accent(), t));
        zone.children(() -> {
            box(c).size(28, 28).shrink(0)
                    .draw(Icon.draw("download", KitMotion.mix(c.theme().textMuted(), c.theme().accent(), t)));
            text(c, hint == null ? "Drop a file here, or click to browse" : hint).singleLine()
                    .fontSize(12.5f).textColor(c.theme().text());
            if (accept != null && !accept.isEmpty()) {
                text(c, accept).singleLine().fontSize(10.5f).textColor(c.theme().textMuted());
            }
        });
        if (!disabled && zone.clicked() && onFile != null) {
            onFile.accept(new File("picked-file.pdf", "1.2 MB"));
            st.file = new File("picked-file.pdf", "1.2 MB");
            st.progress = 0;
            st.started = c.now();
            c.invalidate();
        }
        // The picked file's row with a progress hairline.
        if (st.file != null) {
            float run = Math.min(1, (c.now() - st.started) / 1_600_000_000f);
            st.progress = KitMotion.spring(host, "prog", run, KitMotion.SMOOTH);
            if (st.progress < 1) {
                c.after(java.time.Duration.ofMillis(32));
            }
            Element row = row(c).fillWidth().gap(8).padding(8, 10).radius(Kit.RADIUS_XS)
                    .background(KitMotion.surface(c)).border(1, KitMotion.hairline(c)).label("kit-dropzone-file");
            row.children(() -> {
                box(c).size(16, 16).shrink(0).draw(Icon.draw("doc", c.theme().accent()));
                column(c).gap(3).grow(1).children(() -> {
                    row(c).gap(6).alignItems(Align.CENTER).children(() -> {
                        text(c, st.file.name()).singleLine().fontSize(11.5f).textColor(c.theme().text());
                        text(c, st.file.size()).singleLine().fontSize(10.5f)
                                .textColor(c.theme().textMuted());
                        spacer(c).grow(1);
                        if (st.progress >= 1) {
                            box(c).size(13, 13).shrink(0).draw(Icon.draw("check", KitMotion.success(c)));
                        }
                    });
                    box(c).fillWidth().height(3).radius(1.5f).background(c.theme().surfacePressed())
                            .children(() -> box(c).absolute().left(0).top(0).bottom(0)
                                    .width(Math.max(2, host.bounds().width() * st.progress))
                                    .radius(1.5f).background(c.theme().accent()));
                });
            });
        }
        return host;
    }

    private FileDropzone() {}
}
