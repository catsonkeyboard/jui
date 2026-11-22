package jui.kit;

import java.util.ArrayList;
import java.util.List;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Key;
import jui.ui.Ref;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's tag input, after the TagInput: turn short text values into
 * removable tags. Enter or a comma commits a tag, Backspace on an empty
 * field takes the last one back, and each tag's x removes it with a short
 * fade; the field wraps so the row grows with its tags.
 */
public final class TagInput {

    private static final class St {
        final List<String> tags = new ArrayList<>();
    }

    /** The input: label, placeholder, and the tags the caller keeps. */
    public static Element tagInput(Context c, String label, String placeholder, TextRef draft,
                                   java.util.function.Consumer<String> onAdd,
                                   java.util.function.Consumer<String> onRemove,
                                   java.util.function.Supplier<List<String>> tags) {
        Element host = box(c).fillWidth().label("kit-tag-input").children(() -> {
            if (label != null && !label.isEmpty()) {
                text(c, label).fontSize(12).fontWeight(500).textColor(c.theme().text());
            }
            Element box = box(c).fillWidth().minHeight(34).padding(4).gap(4).wrap()
                    .alignContent(jui.ui.Align.START).radius(Kit.RADIUS_XS + 2)
                    .background(Kit.surface(c)).border(1, Kit.hairline(c))
                    .label("kit-tag-box");
            box.children(() -> {
                List<String> list = tags.get();
                for (String tag : list) {
                    Element chip = row(c).gap(4).padding(3, 4).padding(2, 6).radius(Kit.RADIUS_XS)
                            .shrink(0).alignItems(jui.ui.Align.CENTER).background(c.theme().surface())
                            .label("kit-tag");
                    chip.border(1, Kit.hairline(c));
                    chip.children(() -> {
                        text(c, tag).singleLine().fontSize(11.5f).textColor(c.theme().text());
                        Element x = buttonBase(c).size(14, 14).radius(3).label("kit-tag-x");
                        if (x.hovered()) {
                            x.background(c.theme().surfaceHover());
                        }
                        x.children(() -> box(c).size(7, 7).shrink(0)
                                .draw(Icon.draw("xmark", c.theme().textMuted())));
                        if (x.clicked() && onRemove != null) {
                            onRemove.accept(tag);
                            c.invalidate();
                        }
                    });
                }
                Element input = textInput(c, draft).minWidth(70).grow(1).height(24).padding(0, 5)
                        .radius(Kit.RADIUS_XS).singleLine().label("kit-tag-field");
                input.background(Color.rgba(0x808080, 0));
                input.border(0, Color.rgba(0x808080, 0));
                input.placeholder(placeholder);
                if (input.submitted() || endsWithComma(draft)) {
                    String t = clean(draft.get());
                    if (!t.isEmpty() && !list.contains(t) && onAdd != null) {
                        onAdd.accept(t);
                    }
                    draft.set("");
                    c.invalidate();
                }
                if (input.changed() && endsWithComma(draft)) {
                    // the comma case clears on the next frame's submit path
                    c.invalidate();
                }
            });
        });
        return host;
    }

    private static boolean endsWithComma(TextRef draft) {
        String t = draft.get();
        return t != null && (t.endsWith(",") || t.endsWith("、"));
    }

    private static String clean(String t) {
        return t == null ? "" : t.replace(",", "").replace("、", "").trim();
    }

    /** A convenience that keeps the tags in the element's local state. */
    public static Element tagInput(Context c, String label, String placeholder, TextRef draft,
                                   java.util.function.BiConsumer<List<String>, String> onChange) {
        Element host = box(c).invisible();
        Ref<St> ref = local(host, "tags", St::new);
        St st = ref.get();
        return tagInput(c, label, placeholder, draft,
                t -> {
                    st.tags.add(t);
                    if (onChange != null) {
                        onChange.accept(st.tags, t);
                    }
                },
                t -> {
                    st.tags.remove(t);
                    if (onChange != null) {
                        onChange.accept(st.tags, t);
                    }
                },
                () -> st.tags);
    }

    private TagInput() {}
}
