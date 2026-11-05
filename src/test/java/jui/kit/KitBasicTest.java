package jui.kit;

import jui.testing.Check;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Tester;

import static jui.ui.Ui.*;

public final class KitBasicTest {
    public void testButtonVariantsRenderAndClick() {
        int[] clicks = { 0 };
        Tester tt = Tester.of(c -> column(c).padding(20).gap(8).children(() -> {
            if (Button.button(c, "Save", Kit.Variant.PRIMARY).clicked()) clicks[0]++;
            Button.button(c, "Ghost", Kit.Variant.GHOST, Kit.Size.SMALL);
            Button.button(c, "Disabled", Kit.Variant.DEFAULT, Kit.Size.MEDIUM, null,
                    false, false, true, false, false);
            Button.button(c, "Caret", Kit.Variant.DEFAULT, Kit.Size.MEDIUM, "download",
                    false, false, false, false, true);
        }), 300, 200);
        tt.frame();
        tt.frame();
        Check.isTrue(tt.find("kit-btn-Save").width() > 0, "primary button");
        Check.isTrue(tt.find("kit-btn-Ghost").height() == Kit.height(Kit.Size.SMALL), "small size");
        Check.isTrue(tt.find("kit-btn-Disabled").width() > 0, "disabled renders");
        Check.isTrue(tt.find("kit-btn-Caret").width() > 0, "caret button");
        tt.click("kit-btn-Save");
        tt.frame();
        Check.equal(1, clicks[0]);
    }

    public void testButtonGroupSelects() {
        jui.ui.IntRef sel = new jui.ui.IntRef(0);
        Tester tt = Tester.of(c -> Button.buttonGroup(c, sel, "天", "周", "月"), 300, 120);
        tt.frame();
        tt.click("kit-group-月");
        tt.frame();
        Check.equal(2, sel.get());
        tt.click("kit-group-周");
        tt.frame();
        Check.equal(1, sel.get());
    }

    public void testBadgeTagKbdLabelAvatar() {
        jui.ui.BoolRef dismissed = new jui.ui.BoolRef(false);
        Tester tt = Tester.of(c -> column(c).padding(16).gap(8).children(() -> {
            Badge.badge(c, 5);
            Tag.tag(c, "beta", Kit.Variant.INFO, Tag.Kind.SOFT, true, true, () -> dismissed.set(true));
            Kbd.kbd(c, "⌘", "K");
            Label.label(c, "标题", Kit.Size.LARGE, Label.Variant.DEFAULT);
            Avatar.avatar(c, "Li Ming", 32, true);
        }), 300, 260);
        tt.frame();
        tt.frame();
        Check.isTrue(tt.find("kit-badge-5").width() > 0, "badge");
        Check.isTrue(tt.find("kit-tag-beta").width() > 0, "tag");
        Check.isTrue(tt.find("kit-kbd-⌘").width() > 0, "kbd");
        Check.isTrue(tt.find("kit-label-标题").width() > 0, "label");
        Check.isTrue(tt.find("kit-avatar-Li Ming").width() == 32, "avatar size");
        tt.click("kit-tag-beta");                                        // center may land on text, not ×
        tt.frame();
    }

    public void testProgressAndRating() {
        jui.ui.DoubleRef value = new jui.ui.DoubleRef(0.5);
        jui.ui.IntRef stars = new jui.ui.IntRef(0);
        Tester tt = Tester.of(c -> column(c).padding(16).gap(10).children(() -> {
            Progress.bar(c, value, 120);
            Progress.ring(c, value, 28);
            Rating.rating(c, stars, 5, 20);
        }), 300, 200);
        tt.frame();
        tt.frame();
        Check.isTrue(tt.find("kit-progress").width() == 120, "bar width");
        Check.isTrue(tt.find("kit-progress-ring").width() == 28, "ring size");
        tt.click("kit-star-4");
        tt.frame();
        Check.equal(4, stars.get());
        tt.click("kit-star-4");
        tt.frame();
        Check.equal(0, stars.get());                                     // same star clears
        tt.click("kit-star-2");
        tt.frame();
        Check.equal(2, stars.get());
    }

    public void testSpinnerSkeletonMarker() {
        Tester tt = Tester.of(c -> column(c).padding(16).gap(10).children(() -> {
            Spinner.spinner(c, 20);
            Skeleton.block(c, 80, 12, 6);
            Skeleton.textLines(c, 120);
            row(c).gap(8).children(() -> {
                Marker.marker(c, Kit.Variant.DANGER, "3");
                Marker.marker(c, Kit.Variant.SUCCESS);
            });
        }), 300, 200);
        tt.frame();
        tt.frame();
        Check.isTrue(tt.find("kit-spinner").width() == 20, "spinner");
        Check.isTrue(tt.find("kit-skeleton").width() == 80, "skeleton block");
        Check.isTrue(tt.find("kit-marker").width() > 0, "marker row");
    }
}
