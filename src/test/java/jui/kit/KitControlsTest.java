package jui.kit;

import jui.testing.Check;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.IntRef;
import jui.ui.Tester;

import static jui.ui.Ui.*;

public final class KitControlsTest {
    public void testAlertShowsAndCloses() {
        BoolRef open = new BoolRef(true);
        Tester tt = Tester.of(c -> Alert.alert(c, Kit.Variant.DANGER, "Validation Failed",
                "Email is required", true, open), 360, 160);
        tt.frame();
        Check.isTrue(tt.find("kit-alert").width() > 0, "alert visible");
        tt.click("kit-alert-close");
        tt.frame();
        Check.isTrue(!open.get(), "closed by ×");
        tt.frame();
    }

    public void testAccordionSingleOpen() {
        IntRef openIdx = new IntRef(-1);
        Tester tt = Tester.of(c -> Accordion.accordion(c, openIdx, false, true,
                Accordion.item("Section 1", () -> text(c, "Content 1")),
                Accordion.item("Section 2", () -> text(c, "Content 2"))), 300, 200);
        tt.frame();
        Check.isTrue(!tt.texts().contains("Content 1"), "all closed at start");
        tt.click("kit-acc-Section 1");
        tt.frame();
        Check.isTrue(tt.texts().contains("Content 1"), "first opens");
        tt.click("kit-acc-Section 2");
        tt.frame();
        Check.isTrue(tt.texts().contains("Content 2"), "second opens");
        Check.isTrue(!tt.texts().contains("Content 1"), "first closed: single mode");
        Check.equal(1, openIdx.get());
    }

    public void testCollapsibleToggles() {
        BoolRef open = new BoolRef(false);
        Tester tt = Tester.of(c -> Collapsible.collapsible(c, open, "详情", true,
                () -> text(c, "hidden body")), 300, 160);
        tt.frame();
        Check.isTrue(!tt.texts().contains("hidden body"), "closed at first");
        tt.click("kit-collapse-详情");
        tt.frame();
        Check.isTrue(tt.texts().contains("hidden body"), "open after click");
        Check.isTrue(open.get(), "ref updated");
    }

    public void testRadioSelectsOne() {
        IntRef sel = new IntRef(0);
        Tester tt = Tester.of(c -> Radio.radio(c, sel, "A", "B", "C"), 200, 160);
        tt.frame();
        tt.click("kit-radio-B");
        tt.frame();
        Check.equal(1, sel.get());
        tt.click("kit-radio-C");
        tt.frame();
        Check.equal(2, sel.get());
    }

    public void testStepperBoundsAndSteps() {
        jui.ui.DoubleRef v = new jui.ui.DoubleRef(5);
        Tester tt = Tester.of(c -> Stepper.stepper(c, v, 0, 10, 2, "%.0f"), 200, 80);
        tt.frame();
        tt.click("kit-stepper-plus");
        tt.frame();
        Check.near(7, v.get(), 0.001);
        tt.click("kit-stepper-plus");
        tt.click("kit-stepper-plus");
        tt.frame();
        Check.near(10, v.get(), 0.001);                                  // clamped at max
        tt.click("kit-stepper-minus");
        tt.frame();
        Check.near(8, v.get(), 0.001);
    }

    public void testPaginationWindowAndClamp() {
        IntRef page = new IntRef(0);
        Tester tt = Tester.of(c -> Pagination.pagination(c, page, 5), 320, 60);
        tt.frame();
        tt.click("kit-page-3");
        tt.frame();
        Check.equal(2, page.get());
        tt.click("kit-page-next");
        tt.frame();
        Check.equal(3, page.get());
        tt.click("kit-page-next");
        tt.click("kit-page-next");
        tt.frame();
        Check.equal(4, page.get());                                      // clamped at last
    }

    public void testDropdownButtonPicks() {
        BoolRef open = new BoolRef(false);
        IntRef chosen = new IntRef(-1);
        open.set(true);
        Tester tt = Tester.of(c -> DropdownButton.dropdownButton(c, "导出", Kit.Variant.DEFAULT,
                Kit.Size.MEDIUM, open, chosen::set, "PDF", "PNG"), 300, 200);
        tt.frame();
        tt.frame();
        Check.isTrue(tt.find("kit-dd-PDF").width() > 0, "menu open");
        tt.click("kit-dd-PNG");
        tt.frame();
        Check.equal(1, chosen.get());
        Check.isTrue(!open.get(), "menu closed after pick");
    }

    public void testTextViewMarkdownLite() {
        Tester tt = Tester.of(c -> column(c).padding(12).children(() ->
                TextView.textView(c, "Hello **world** and `code`")), 260, 80);
        tt.frame();
        Check.isTrue(tt.texts().contains("Hello world and code"), "markers stripped");
    }

    public void testAttachmentRemove() {
        BoolRef removed = new BoolRef(false);
        Tester tt = Tester.of(c -> Attachment.attachment(c, "report.pdf", "1.2 MB",
                () -> removed.set(true)), 260, 80);
        tt.frame();
        Check.isTrue(tt.find("kit-att-report.pdf").width() > 0, "chip visible");
        tt.click("kit-att-remove-report.pdf");
        tt.frame();
        Check.isTrue(removed.get(), "remove fired");
    }
}
