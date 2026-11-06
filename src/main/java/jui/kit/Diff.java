package jui.kit;

import java.util.ArrayList;
import java.util.List;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's diff: line-level old/new comparison rendered with − and + tints,
 * after gpui-kit's Diff. The match is a longest-common-subsequence walk, so
 * same lines stay paired and edits group sensibly.
 */
public final class Diff {
    /** One rendered line: unchanged, added, or removed. */
    public enum Kind { SAME, ADDED, REMOVED }

    public record Row(Kind kind, String text) {}

    /** The line walk; pure and cheap for document-sized inputs. */
    public static List<Row> lines(String[] oldLines, String[] newLines) {
        int n = oldLines.length, m = newLines.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = oldLines[i].equals(newLines[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<Row> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (oldLines[i].equals(newLines[j])) {
                out.add(new Row(Kind.SAME, oldLines[i]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add(new Row(Kind.REMOVED, oldLines[i]));
                i++;
            } else {
                out.add(new Row(Kind.ADDED, newLines[j]));
                j++;
            }
        }
        while (i < n) out.add(new Row(Kind.REMOVED, oldLines[i++]));
        while (j < m) out.add(new Row(Kind.ADDED, newLines[j++]));
        return out;
    }

    /** Renders the walk: removed lines tinted red, added green, prefix − / +. */
    public static Element diff(Context c, List<Row> rows) {
        return column(c).fillWidth().radius(Kit.RADIUS_SM)
                .background(c.theme().surface())
                .label("kit-diff")
                .children(() -> {
                    for (int k = 0; k < rows.size(); k++) {
                        Row r = rows.get(k);
                        Kit.Tone tone = switch (r.kind()) {
                            case ADDED -> Kit.tone(c, Kit.Variant.SUCCESS);
                            case REMOVED -> Kit.tone(c, Kit.Variant.DANGER);
                            case SAME -> null;
                        };
                        String prefix = r.kind() == Kind.ADDED ? "+ " : r.kind() == Kind.REMOVED ? "− " : "  ";
                        int idx = k;
                        row(c).fillWidth().height(22).padding(0, 8).gap(4)
                                .alignItems(Align.CENTER)
                                .children(() -> {
                                    text(c, prefix).fontSize(11)
                                            .textColor(tone == null ? c.theme().textMuted() : tone.fill());
                                    text(c, r.text()).fontSize(12).textColor(c.theme().text());
                                });
                    }
                });
    }

    private Diff() {}
}
