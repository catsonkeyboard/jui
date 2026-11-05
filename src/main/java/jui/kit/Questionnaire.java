package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's questionnaire: titled questions in sequence, each carrying its
 * own control — the survey form gpui-kit's Questionnaire renders.
 */
public final class Questionnaire {
    /** One question: its text and the control that answers it. */
    public record Question(String title, Runnable control) {}

    public static Question question(String title, Runnable control) {
        return new Question(title, control);
    }

    public static Element questionnaire(Context c, String heading, Question... questions) {
        return column(c).fillWidth().gap(10).children(() -> {
            if (heading != null && !heading.isEmpty()) {
                text(c, heading).fontSize(14).bold().textColor(c.theme().text());
            }
            for (Question q : questions) {
                box(c).fillWidth().radius(Kit.RADIUS).background(c.theme().surface())
                        .padding(12, 14).gap(8).children(() -> {
                            text(c, q.title()).fontSize(12.5f).textColor(c.theme().text());
                            q.control().run();
                        });
            }
        });
    }

    private Questionnaire() {}
}
