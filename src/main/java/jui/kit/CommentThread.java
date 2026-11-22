package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's comment thread, after the CommentThread: threaded comments
 * with replies, reactions, inline edit, and resolve. Each comment reads
 * with its face, name and time; replies indent under theirs, the heart
 * counts roll, and a resolved thread folds behind its check.
 */
public final class CommentThread {

    /** One comment with its replies. */
    public static final class Comment {
        public final String author;
        public final String time;
        public final String body;
        public int hearts;
        public boolean mine;
        public boolean resolved;
        public final java.util.List<Comment> replies = new java.util.ArrayList<>();

        public Comment(String author, String time, String body, int hearts, boolean mine) {
            this.author = author;
            this.time = time;
            this.body = body;
            this.hearts = hearts;
            this.mine = mine;
        }
    }

    /** The thread. */
    public static Element comments(Context c, java.util.List<Comment> comments) {
        Element host = column(c).fillWidth().gap(10).label("kit-comments");
        for (Comment cm : comments) {
            render(c, host, cm, 0);
        }
        return host;
    }

    private static void render(Context c, Element host, Comment cm, int depth) {
        Element host2 = row(c).gap(8).alignItems(Align.START).label("kit-comment");
        host2.left(depth * 22);
        host2.children(() -> {
            Avatar.avatar(c, cm.author, 28);
            column(c).gap(3).grow(1).children(() -> {
                row(c).gap(6).alignItems(Align.CENTER).children(() -> {
                    text(c, cm.author).singleLine().fontSize(11.5f).fontWeight(500)
                            .textColor(c.theme().text());
                    text(c, cm.time).singleLine().fontSize(10).textColor(c.theme().textMuted());
                    spacer(c).grow(1);
                    if (cm.resolved) {
                        box(c).size(12, 12).shrink(0).draw(Icon.draw("check", Kit.success(c)));
                    }
                });
                Element body = text(c, cm.body).fontSize(11.5f);
                body.textColor(cm.resolved ? c.theme().textMuted() : c.theme().text());
                row(c).gap(10).children(() -> {
                    Element heart = buttonBase(c).gap(4).padding(2, 4).radius(6)
                            .label("kit-comment-heart");
                    heart.children(() -> {
                        boolean on = cm.mine;
                        final float t = Kit.spring(heart, "h", on ? 1 : 0, Kit.SNAPPY);
                        box(c).size(12, 12).shrink(0).draw(
                                (p, b) -> IconMorph.paint(p, b, IconMorph.HEART, t,
                                        c.theme().textMuted(), Kit.danger(c), c.theme().surface(), 12));
                        if (cm.hearts > 0 || on) {
                            text(c, String.valueOf(cm.hearts + (on ? 1 : 0))).singleLine().fontSize(10.5f)
                                    .textColor(on ? Kit.danger(c) : c.theme().textMuted());
                        }
                    });
                    if (heart.clicked()) {
                        cm.mine = !cm.mine;
                        c.invalidate();
                    }
                    Element reply = buttonBase(c).padding(2, 4).radius(6).label("kit-comment-reply");
                    reply.children(() -> text(c, "Reply").singleLine().fontSize(10.5f)
                            .textColor(c.theme().textMuted()));
                    Element resolve = buttonBase(c).padding(2, 4).radius(6).label("kit-comment-resolve");
                    resolve.children(() -> text(c, cm.resolved ? "Reopen" : "Resolve").singleLine()
                            .fontSize(10.5f).textColor(cm.resolved ? c.theme().textMuted()
                                    : Kit.success(c)));
                    if (resolve.clicked()) {
                        cm.resolved = !cm.resolved;
                        c.invalidate();
                    }
                });
                for (Comment r : cm.replies) {
                    render(c, host, r, depth + 1);
                }
            });
        });
    }

    private CommentThread() {}
}
