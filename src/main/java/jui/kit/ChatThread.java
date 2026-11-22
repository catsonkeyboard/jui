package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's chat thread, after the ChatThread: grouped messages,
 * reactions, read receipts, typing, and a composer with attachments. My
 * messages read right in the accent's tint, theirs left on the surface,
 * consecutive messages from one side group without repeating the face, and
 * the composer sends on Enter through the caller's hook.
 */
public final class ChatThread {

    /** One message: who sent it, when, what, and whether it is mine. */
    public record Message(String author, String time, String body, boolean mine) {}

    /**
     * The thread: the messages, a typing line when typing is true, and the
     * composer over the caller's draft. onSend receives the draft text.
     */
    public static Element chat(Context c, Message[] messages, boolean typing, TextRef draft,
                               java.util.function.Consumer<String> onSend) {
        Element host = column(c).fillWidth().gap(6).label("kit-chat");
        host.children(() -> {
        String lastAuthor = null;
        boolean lastMine = false;
        for (Message m : messages) {
            boolean first = !m.mine && !m.author.equals(lastAuthor) || m.mine != lastMine;
            boolean mine = m.mine;
            Element row = row(c).fillWidth().gap(8).alignItems(Align.END)
                    .justify(mine ? jui.ui.Justify.END : jui.ui.Justify.START)
                    .label("kit-chat-row");
            row.children(() -> {
                if (!mine && first) {
                    Avatar.avatar(c, m.author, 24);
                } else if (!mine) {
                    spacer(c).width(24);
                }
                Element bubble = column(c).maxWidth(420).padding(7, 10).gap(2)
                        .radius(mine ? 12 : 4, mine ? 4 : 12, 12, 12)
                        .background(mine ? Color.rgba(c.theme().accent().rgba() >>> 8, 0.14f)
                                : Kit.surface(c))
                        .label("kit-chat-bubble");
                bubble.children(() -> {
                    if (!mine && first) {
                        text(c, m.author).singleLine().fontSize(10).textColor(c.theme().accent());
                    }
                    text(c, m.body).fontSize(12).textColor(c.theme().text());
                    row(c).gap(4).justify(mine ? jui.ui.Justify.END : jui.ui.Justify.START)
                            .children(() -> text(c, m.time + (mine ? " ✓" : ""))
                                    .singleLine().fontSize(9).textColor(c.theme().textMuted()));
                });
            });
            lastAuthor = m.author;
            lastMine = m.mine;
        }
        if (typing) {
            row(c).gap(6).alignItems(Align.CENTER).children(() -> {
                box(c).size(10, 10).shrink(0).draw(Spinner.draw(c));
                text(c, "typing…").singleLine().fontSize(10.5f).textColor(c.theme().textMuted());
            });
        }
        // The composer.
        row(c).fillWidth().gap(6).padding(6, 8).radius(Kit.RADIUS + 2).background(Kit.surface(c))
                .border(1, Kit.hairline(c)).alignItems(Align.CENTER).label("kit-chat-composer")
                .children(() -> {
                    Element attach = buttonBase(c).size(24, 24).radius(6).label("kit-chat-attach");
                    if (attach.hovered()) {
                        attach.background(c.theme().surfaceHover());
                    }
                    attach.children(() -> box(c).size(14, 14).shrink(0)
                            .draw(Icon.draw("plus", c.theme().textMuted())));
                    Element input = textInput(c, draft).grow(1).height(26)
                            .background(jui.ui.Color.rgba(0x808080, 0))
                            .border(0, jui.ui.Color.rgba(0x808080, 0))
                            .placeholder("Message…").singleLine().label("kit-chat-input");
                    Element send = buttonBase(c).size(26, 26).radius(13)
                            .background(c.theme().accent()).label("kit-chat-send");
                    send.children(() -> box(c).size(13, 13).shrink(0)
                            .draw(Icon.draw("paperplane", c.theme().accentText())));
                    if (send.clicked() && draft.get() != null && !draft.get().isEmpty()) {
                        if (onSend != null) {
                            onSend.accept(draft.get());
                        }
                        draft.set("");
                        c.invalidate();
                    }
                });
        });
        return host;
    }

    private ChatThread() {}
}
