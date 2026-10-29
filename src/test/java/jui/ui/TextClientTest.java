package jui.ui;

import jui.testing.Check;
import static jui.ui.Ui.*;

public final class TextClientTest {
    static final class Client implements TextInputClient {
        String value = "A😀中";
        int anchor = 4, caret = 1, markedStart = -1, markedEnd, unmarks, resizes;
        boolean throwQuery, badActual, throwMutation;
        public int length() { if (throwQuery) throw new IllegalStateException("client query failed"); return value.length(); }
        public String text(int start, int end, int[] actual) {
            actual[0] = start == 2 ? 1 : start;
            actual[1] = end == 2 ? 3 : end;
            return value.substring(actual[0], actual[1]);
        }
        public void selection(int[] out) { out[0] = anchor; out[1] = caret; }
        public boolean markedRange(int[] out) { out[0] = markedStart; out[1] = markedEnd; return markedStart >= 0; }
        public void replaceText(TextRange range, String text) {
            int start = range == null ? markedStart >= 0 ? markedStart : Math.min(anchor, caret) : range.start();
            int end = range == null ? markedStart >= 0 ? markedEnd : Math.max(anchor, caret) : range.end();
            value = value.substring(0, start) + text + value.substring(end);
            anchor = caret = start + text.length(); markedStart = -1;
            if (throwMutation) throw new IllegalStateException("client mutation failed after changing document");
        }
        public void setMarkedText(TextRange range, String text, TextRange selected) {
            replaceText(range, text);
            markedStart = caret - text.length(); markedEnd = caret;
            anchor = markedStart + selected.start(); caret = markedStart + selected.end();
            if (text.isEmpty()) markedStart = -1;
        }
        public void unmarkText() { markedStart = -1; unmarks++; }
        public boolean boundsFor(int start, int end, float[] out) {
            Check.isTrue(resizes > 0, "resize precedes queries");
            out[0] = start * 8; out[1] = 7; out[2] = 1; out[3] = 16; out[4] = badActual ? Float.NaN : start; out[5] = end;
            return true;
        }
        public int indexAt(float x, float y) { return Math.min(value.length(), Math.max(0, (int) (x / 8))); }
        public void resized(float width, float height) { resizes++; }
    }

    public void testNativeQueriesAndMutationWithoutRebuild() {
        Client client = new Client();
        Tester tester = Tester.of(context -> canvas(context).size(180, 90).margin(10).handleTextInput(client).autoFocus(), 300, 200);
        tester.frame();
        int[] range = new int[2];
        Check.isTrue(tester.engine().textSelectedRange(range), "selection available");
        Check.equal(1, range[0]); Check.equal(3, range[1]);
        Check.equal("😀", tester.engine().textIn(2, 2, range));
        Check.equal(1, range[0]); Check.equal(3, range[1]);
        float[] bounds = new float[6];
        tester.engine().textRect(1, 3, bounds);
        Check.near(18, bounds[0]); Check.near(17, bounds[1]);
        Check.near(3, bounds[4]); Check.near(1, bounds[5]);
        tester.engine().textMark("ni", 1, 0, -1, 0);
        Check.equal("Ani", client.value);
        Check.isTrue(tester.engine().textComposing(), "marked inside document");
        tester.engine().textInsert("你", -1, 0);
        Check.equal("A你", client.value);
        Check.equal(2, tester.engine().textLength());
        tester.frame(); Check.equal(1, client.resizes);
    }

    public void testClientQueryExceptionsInvalidRangesAndThreadGuard() throws InterruptedException {
        Client client = new Client();
        Tester tester = Tester.of(context -> canvas(context).size(180, 90).handleTextInput(client).autoFocus(), 300, 200);
        tester.frame();
        client.throwQuery = true;
        Check.fails(IllegalStateException.class, () -> tester.engine().textLength());
        client.throwQuery = false; Check.equal(4, tester.engine().textLength());
        client.badActual = true;
        Check.fails(IllegalStateException.class, () -> tester.engine().textRect(0, 0, new float[6]));
        client.badActual = false;
        Throwable[] failure = new Throwable[1];
        Thread thread = new Thread(() -> { try { tester.engine().textLength(); } catch (Throwable caught) { failure[0] = caught; } });
        thread.start(); thread.join();
        Check.isTrue(failure[0] instanceof IllegalStateException, "client callbacks stay on UI thread");
        Check.equal(4, tester.engine().textLength());
        client.throwMutation = true;
        Check.fails(IllegalStateException.class, () -> tester.engine().textInsert("x", -1, 0));
        client.throwMutation = false;
        Check.equal("Ax", client.value); Check.equal(2, tester.engine().textLength());
    }

    public void testLocalGeometryUnderScrollAndResize() {
        Client client = new Client(); ScrollState scroll = new ScrollState();
        Tester tester = Tester.of(context -> scroll(context, scroll).fillWidth().height(80).padding(12).children(() ->
                canvas(context).height(180).label("own").handleTextInput(client).autoFocus()), 300, 200);
        tester.frame(); scroll.scrollTo(40); tester.frame();
        Rect element = tester.find("own"); float[] bounds = new float[6];
        tester.engine().textRect(1, 3, bounds);
        Check.near(element.x() + 8, bounds[0]); Check.near(element.y() + 7, bounds[1]);
        Check.equal(1, tester.engine().textIndexAt(element.x() + 8, element.y() + 7));
        Check.isTrue(scroll.y() > 0, "ancestor scrolled");
        int previous = client.resizes; tester.resize(400, 200);
        tester.frame(); Check.equal(previous + 1, client.resizes); tester.frame(); Check.equal(previous + 1, client.resizes);
    }

    public void testDisabledClientReleasesCompositionAndDuplicateBindingFails() {
        Client client = new Client(); boolean[] disabled = {false};
        Tester tester = Tester.of(context -> canvas(context).size(180, 90).handleTextInput(client).autoFocus().disabled(disabled[0]), 300, 200);
        tester.frame(); tester.compose("ni", 2); disabled[0] = true; tester.frame();
        Check.equal(1, client.unmarks); Check.isTrue(!tester.textInputActive(), "disabled client inactive");
        Check.equal(1, ((jui.ui.engine.Host.Memory) tester.engine().host()).discards);
        Tester duplicate = Tester.of(context -> {
            canvas(context).size(100, 50).handleTextInput(client);
            canvas(context).size(100, 50).handleTextInput(client);
        }, 300, 200);
        Check.fails(IllegalStateException.class, duplicate::frame);
    }

    public void testDetachSwapBlurAndCloseCommitExactlyOnce() {
        Client first = new Client(), second = new Client();
        Client[] current = {first};
        Tester tester = Tester.of(context -> canvas(context).size(180, 90).handleTextInput(current[0]).autoFocus(), 300, 200);
        tester.frame();
        tester.engine().textMark("a", 1, 0, -1, 0);
        current[0] = second; tester.frame();
        Check.equal(1, first.unmarks);
        tester.engine().textMark("b", 1, 0, -1, 0);
        tester.engine().windowFocusChanged(false);
        Check.equal(1, second.unmarks);
        tester.engine().textInsert("bad", -1, 0);
        Check.equal("Ab", second.value);
        tester.engine().windowFocusChanged(true);
        tester.engine().textMark("c", 1, 0, -1, 0);
        tester.close(); tester.close();
        Check.equal(2, second.unmarks);
        tester.engine().textInsert("bad", -1, 0);
        Check.equal("Abc", second.value);
    }

    public void testNoInputMethodCommandsAndMenuIdentity() {
        Client first = new Client(), second = new Client();
        Client[] current = {first};
        boolean[] ime = {true};
        java.util.List<EditCommand> commands = new java.util.ArrayList<>();
        int[] chosen = {0};
        Tester tester = Tester.of(context -> {
            Element target = canvas(context).size(180, 90).label("editor").handleTextInput(current[0]).autoFocus()
                    .inputMethod(ime[0]).handleInput(event -> {
                        if (event.kind() != InputEvent.Kind.COMMAND) return false;
                        commands.add(event.command()); return event.command() == EditCommand.PASTE;
                    });
            contextMenu(context, target, menu -> { if (menu.item("change").chosen()) chosen[0]++; });
        }, 300, 200);
        tester.frame();
        tester.compose("ni", 2);
        ime[0] = false; tester.frame();
        Check.equal(1, first.unmarks);
        tester.compose("bad", 3); Check.equal("Ani", first.value);
        tester.command(EditCommand.PASTE_AND_MATCH_STYLE);
        Check.equal(java.util.List.of(EditCommand.PASTE_AND_MATCH_STYLE, EditCommand.PASTE), commands);
        tester.rightClick("editor");
        current[0] = second;
        tester.chooseMenuItem("change");
        Check.equal(0, chosen[0]);
    }

    public void testPasswordBoundsStayInCompositionDocument() {
        TextRef value = new TextRef("secret");
        Tester tester = Tester.of(context -> textInput(context, value).password().autoFocus(), 300, 200);
        tester.frame(); tester.compose("ni", 2);
        float[] rect = new float[6];
        Check.isTrue(tester.engine().textRect(0, 2, rect), "password caret available");
        Check.near(0, rect[5]); Check.near(2, rect[4]);
        Check.equal("ni", tester.engine().textIn(0, 100));
    }

    public void testOrdinaryMenuDoesNotBelongToUnrelatedFocusedClient() {
        Client[] focused = {new Client()};
        int[] chosen = {0};
        Tester tester = Tester.of(context -> {
            canvas(context).size(180, 90).handleTextInput(focused[0]).autoFocus();
            Element target = button(context, "ordinary");
            contextMenu(context, target, menu -> { if (menu.item("action").chosen()) chosen[0]++; });
        }, 300, 200);
        tester.frame(); tester.rightClick("ordinary");
        focused[0] = new Client(); tester.frame();
        Check.isTrue(tester.menu() != null, "unrelated client replacement must not cancel ordinary menu");
        tester.chooseMenuItem("action"); Check.equal(1, chosen[0]);
    }
}
