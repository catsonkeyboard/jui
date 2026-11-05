package jui.jspace;

import jui.testing.Check;
import jui.ui.Tester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public final class SettingsWindowTest {
    public void testNavAndGeneralPage() {
        SettingsWindow sw = new SettingsWindow(
                new Workspace(new Model(jui.jspace.store.Settings.forTests())),
                () -> {});
        Tester tt = Tester.of(sw.view(), 560, 480);
        for (String id : new String[] { "set-通用", "set-外观", "set-面板", "set-标签页", "set-书签",
                "set-暂存架", "set-传输", "set-扩展", "set-快捷键", "set-关于" }) {
            Check.isTrue(tt.find(id).width() > 0, id);
        }
        Check.isTrue(tt.find("setrow-appearance").width() > 0, "通用页在正中");
        tt.click("set-外观");
        tt.frame();
        Check.isTrue(tt.find("placeholder-page").width() > 0, "外观页是占位");
    }

    public void testTogglePersistsThroughAfterChange() throws IOException {
        Path f = Files.createTempFile("jspace-ws-settings", ".properties");
        jui.jspace.store.Settings st = jui.jspace.store.Settings.load(f);
        Workspace ws = new Workspace(new Model(st), st, Runnable::run);
        AtomicInteger saved = new AtomicInteger();
        SettingsWindow sw = new SettingsWindow(ws, () -> { st.save(f); saved.incrementAndGet(); });
        Tester tt = Tester.of(sw.view(), 560, 480);
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (saved.get() < 1) {
            if (System.nanoTime() > deadline) break;
            tt.click("setrow-showhidden-toggle");
            tt.frame();
        }
        Check.isTrue(saved.get() >= 1, "afterChange ran");
        Check.isTrue(jui.jspace.store.Settings.load(f).showHidden, "persisted");
    }
}
