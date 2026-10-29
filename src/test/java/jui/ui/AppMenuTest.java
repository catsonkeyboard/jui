package jui.ui;

import java.util.List;
import java.util.function.Consumer;

import jui.platform.MenuModel;
import jui.testing.Check;

import static jui.ui.Ui.*;

/** The app's menu bar, as App.menu declares it: what a choice does (engine side). */
public final class AppMenuTest {
    static final Consumer<Menu> BAR = m -> m.submenu("File", f -> {
        if (f.item("New").shortcut(Shortcut.cmd(Key.N)).chosen()) MenuPickTest.news[0]++;
        f.separator();
        if (f.item("Close").shortcut(Shortcut.cmd(Key.W)).disabled(true).chosen()) MenuPickTest.closes[0]++;
    });

    /** Where the picks land; static because the bar builder runs outside any view. */
    static final class MenuPickTest {
        static final int[] news = {0}, closes = {0};
    }

    public void testAChoiceRunsTheBuilderAgainAndReportsItOnce() {
        Tester tt = Tester.of(c -> text(c, "hi"), 100, 100);
        tt.frame();
        MenuModel.Item picked = Menu.build(BAR, List.of(), null).model().items().get(0).children().get(0);
        tt.engine().appMenuChose(BAR, picked);                 // what the menu bar's target reports
        tt.frame();
        Check.equal(1, MenuPickTest.news[0]);
        Check.equal(0, MenuPickTest.closes[0]);
        tt.frame();
        Check.equal(1, MenuPickTest.news[0]);                  // once, not again
    }

    public void testADisabledItemDoesNotReportAndLaterPicksDo() {
        Tester tt = Tester.of(c -> text(c, "hi"), 100, 100);
        tt.frame();
        MenuModel model = Menu.build(BAR, List.of(), null).model();
        MenuModel.Item closed = model.find(model.items().get(0).children().get(2).id());
        Check.isTrue(!closed.enabled(), "the model froze it disabled");
        tt.engine().appMenuChose(BAR, closed);
        tt.frame();
        Check.equal(0, MenuPickTest.closes[0]);                // a disabled item reports nothing
        tt.engine().appMenuChose(BAR, Menu.build(BAR, List.of(), null).model().items().get(0).children().get(0));
        tt.frame();
        Check.equal(2, MenuPickTest.news[0]);                  // picks accumulate
    }
}
