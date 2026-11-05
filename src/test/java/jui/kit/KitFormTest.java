package jui.kit;

import jui.testing.Check;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.IntRef;
import jui.ui.TextRef;
import jui.ui.Tester;

import static jui.ui.Ui.*;

public final class KitFormTest {
    public void testInputFieldChanges() {
        TextRef name = new TextRef("");
        Tester tt = Tester.of(c -> Input.field(c, "名字", name, "请输入"), 240, 90);
        tt.frame();
        tt.click("kit-input-名字");
        tt.type("Liming");
        tt.frame();
        Check.equal("Liming", name.get());
    }

    public void testNumberInputKeepsBounds() {
        TextRef text = new TextRef("5");
        jui.ui.DoubleRef value = new jui.ui.DoubleRef(5);
        Tester tt = Tester.of(c -> NumberInput.numberInput(c, text, value, 0, 10, 3), 240, 70);
        tt.frame();
        tt.click("kit-number-up");
        tt.frame();
        Check.near(8, value.get(), 0.001);
        Check.equal("8", text.get());
        tt.click("kit-number-up");
        tt.click("kit-number-up");
        tt.frame();
        Check.near(10, value.get(), 0.001);                              // clamped
        tt.click("kit-number-down");
        tt.frame();
        Check.near(7, value.get(), 0.001);
    }

    public void testSelectPicksAndCloses() {
        IntRef sel = new IntRef(-1);
        BoolRef open = new BoolRef(true);
        Tester tt = Tester.of(c -> Select.select(c, sel, open, "北京", "上海"), 220, 180);
        tt.frame();
        tt.frame();
        tt.click("kit-select-opt-上海");
        tt.frame();
        Check.equal(1, sel.get());
        Check.isTrue(!open.get(), "closed after pick");
    }

    public void testComboboxFiltersAndFills() {
        TextRef query = new TextRef("");
        BoolRef open = new BoolRef(true);
        Tester tt = Tester.of(c -> Combobox.combobox(c, query, open,
                "Beijing", "Boston", "Berlin"), 260, 220);
        tt.frame();
        tt.frame();
        Check.isTrue(tt.find("kit-combo-opt-Beijing").width() > 0, "all options shown");
        tt.click("kit-combo");
        tt.type("ber");
        tt.frame();
        tt.frame();
        Check.isTrue(tt.find("kit-combo-opt-Berlin").width() > 0, "filtered match");
        tt.click("kit-combo-opt-Berlin");
        tt.frame();
        Check.equal("Berlin", query.get());
    }

    public void testCalendarSelectsAndPages() {
        IntRef[] seed = Calendar.today();
        IntRef month = seed[0], day = seed[1];
        int startMonth = month.get();
        Tester tt = Tester.of(c -> Calendar.calendar(c, month, day), 260, 260);
        tt.frame();
        int today = day.get();
        tt.click("kit-cal-15");
        tt.frame();
        Check.equal(15, day.get());
        tt.click("kit-cal-next");
        tt.frame();
        Check.equal(startMonth + 1, month.get());
        tt.click("kit-cal-prev");
        tt.frame();
        Check.equal(startMonth, month.get());
        Check.equal(15, day.get());                                      // selection survives paging
    }

    public void testDatePickerAndTimeField() {
        IntRef[] seed = Calendar.today();
        IntRef month = seed[0], day = seed[1];
        BoolRef open = new BoolRef(false);
        IntRef hour = new IntRef(8), minute = new IntRef(30);
        BoolRef hourOpen = new BoolRef(false);
        Tester tt = Tester.of(c -> column(c).padding(10).gap(10).children(() -> {
            DatePicker.datePicker(c, month, day, open);
            TimeField.timeField(c, hour, minute, hourOpen, new BoolRef(false));
        }), 300, 120);
        tt.frame();
        Check.isTrue(tt.find("kit-datepicker-" + (month.get() / 12) + "-"
                + String.format("%02d", 1 + month.get() % 12) + "-"
                + String.format("%02d", day.get())).width() > 0, "date trigger");
        tt.click("kit-time-h");                                      // open the hour dropdown
        tt.frame();
        tt.frame();
        tt.click("kit-time-h-3");
        tt.frame();
        Check.equal(3, hour.get());
    }

    public void testOtpFillsCells() {
        TextRef code = new TextRef("");
        Tester tt = Tester.of(c -> OtpInput.otp(c, code, 6, 30), 300, 120);
        tt.frame();
        tt.click("kit-otp-capture");
        tt.type("123456");
        tt.frame();
        Check.equal("123456", code.get());
    }

    public void testFormSubmits() {
        TextRef email = new TextRef("");
        int[] submits = { 0 };
        Tester tt = Tester.of(c -> Form.form(c, "登录",
                new Form.Field[] {
                        Form.field("邮箱", () -> textInput(c, email).placeholder("you@x.com")
                                .fillWidth().label("kit-input")),
                }, "提交", () -> submits[0]++), 360, 160);
        tt.frame();
        tt.click("kit-input");                                           // the input inside the form
        tt.type("a@b.c");
        tt.frame();
        tt.click("kit-btn-提交");
        tt.frame();
        Check.equal(1, submits[0]);
        Check.equal("a@b.c", email.get());
    }

    public void testEditorAndColorPickerRender() {
        TextRef code = new TextRef("let x = 1;");
        int[] picked = { 0 };
        Tester tt = Tester.of(c -> column(c).padding(12).gap(10).children(() -> {
            Editor.editor(c, code, 60);
            ColorPicker.colorPicker(c, jui.ui.Color.rgb(0x3E6FF2), 180, color -> picked[0]++);
        }), 260, 220);
        tt.frame();
        Check.isTrue(tt.find("kit-editor").width() > 0, "editor box");
        Check.isTrue(tt.find("kit-color-swatch").width() > 0, "swatch");
    }
}
