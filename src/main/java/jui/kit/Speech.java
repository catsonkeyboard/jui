package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's speech: a mic toggle with a live waveform and the transcript it
 * would fill — after gpui-kit's Speech. Actual recognition is not available
 * here (no speech stack behind this framework); the toggle drives the
 * waveform so the interaction reads, and apps wire real recognition in.
 */
public final class Speech {
    public static Element speech(Context c, jui.ui.BoolRef listening, TextRef transcript) {
        return column(c).gap(8).children(() -> {
            row(c).gap(10).alignItems(Align.CENTER).children(() -> {
                Element mic = buttonBase(c).size(34, 34).radius(17)
                        .label("kit-speech-toggle")
                        .alignItems(Align.CENTER)
                        .children(() -> {
                            box(c).size(16, 16).draw(Icon.draw("mic",
                                    listening.get() ? Color.rgb(0xFFFFFF) : c.theme().textMuted()));
                        });
                if (listening.get()) {
                    mic.background(Kit.tone(c, Kit.Variant.DANGER).fill());
                    waveform(c);
                } else if (mic.hovered()) {
                    mic.background(c.theme().surfaceHover());
                }
                if (mic.clicked()) listening.set(!listening.get());
                Label.label(c, listening.get() ? "正在聆听…" : "点击说话",
                        Kit.Size.SMALL, listening.get() ? Label.Variant.DANGER : Label.Variant.MUTED);
            });
            if (!transcript.get().isEmpty()) {
                text(c, transcript.get()).fontSize(12).textColor(c.theme().text());
            }
        });
    }

    /** Five bars breathing with the frame clock, the listening indicator. */
    private static void waveform(Context c) {
        long t0 = c.now();
        box(c).width(44).height(20).alignItems(Align.CENTER)
                .draw((jui.ui.Painter p, jui.ui.Rect b) -> {
                    Color col = c.theme().accent();
                    float n = 5, slot = b.width() / n;
                    for (int i = 0; i < n; i++) {
                        float phase = ((t0 / 120_000_000L) + i * 3) % 8L / 8f;
                        float hgt = 4 + (float) Math.sin(phase * Math.PI * 2) * 3 + 3;
                        float x = b.x() + slot * i + slot / 2;
                        p.line(x, b.y() + b.height() / 2 - hgt / 2,
                                x, b.y() + b.height() / 2 + hgt / 2, slot * 0.5f, col);
                    }
                }).shrink(0);
    }

    private Speech() {}
}
