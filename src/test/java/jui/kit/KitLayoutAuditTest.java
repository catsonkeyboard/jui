package jui.kit;

import jui.testing.Check;
import jui.ui.BoolRef;
import jui.ui.IntRef;
import jui.ui.Tester;

import static jui.ui.Ui.*;

/** Regression checks for the immediate-mode layout mistakes found in the kit gallery. */
public final class KitLayoutAuditTest {
    public void testNoComponentLeaksChildrenIntoItsCaller() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath();
        java.nio.file.Path script = root.resolve("scripts/kit-leak-audit.py");
        Check.isTrue(java.nio.file.Files.isRegularFile(script), "audit script at " + script);
        Process p = new ProcessBuilder("python3", script.toString(), "--json",
                root.resolve("src/main/java/jui/kit").toString())
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        int code = p.waitFor();
        Check.isTrue(code == 0, out);
        // One compact JSON line: probe the fields without a JSON dependency.
        Check.isTrue(out.contains("\"leaks\": 0"), out);
        Check.isTrue(out.contains("\"methods\": 248") || out.contains("\"methods\": 249")
                || out.matches("(?s).*\"methods\":\\s*2[0-9][0-9].*"), out);
    }

    public void testRadioCardsLayOutSideBySide() {
        IntRef selected = new IntRef(0);
        Tester tt = Tester.of(c -> RadioCards.radioCards(c, selected, new RadioCards.Card[]{
                new RadioCards.Card("Starter", "For one workspace", "$0", null),
                new RadioCards.Card("Pro", "Everything, unlimited", "$12/mo", null),
                new RadioCards.Card("Team", "Pro plus SSO", "$24/mo", null),
        }, 190, false), 640, 200);
        tt.frame();
        tt.frame();
        Check.isTrue(tt.texts().contains("For one workspace"), "description is not squeezed out");
        Check.isTrue(tt.find("kit-card-Starter").width() > 100, "card has its full width");
        float x0 = tt.find("kit-card-Starter").x();
        float x1 = tt.find("kit-card-Pro").x();
        float x2 = tt.find("kit-card-Team").x();
        Check.isTrue(x1 > x0 + 100 && x2 > x1 + 100, "three cards share one row: " + x0 + "/" + x1 + "/" + x2);
        tt.click("kit-card-Pro");
        tt.frame();
        Check.equal(1, selected.get());
    }
}
