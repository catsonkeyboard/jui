package jui.ui.engine;

import java.util.ArrayList;
import java.util.List;
import jui.platform.MenuModel;
import jui.ui.EditCommand;
import jui.ui.Menu;

final class MenuState {
    private final Engine engine;
    private Session current;
    private Session pending;
    private final java.util.ArrayDeque<Session> responses = new java.util.ArrayDeque<>();
    private boolean closed;

    MenuState(Engine engine) { this.engine = engine; }

    Node target(long[] chain, int count) {
        for (int index = 0; index < count; index++) {
            Node node = engine.menuNode(chain[index]);
            if (node != null && !node.disabled && (node.contextMenu != null || node.kind == Node.INPUT)) return node;
        }
        return null;
    }

    void ask(Node node, float x, float y) {
        if (closed) return;
        cancel();
        current = new Session(node, x, y, engine.activeTextConnection());
        engine.requestFrame();
    }

    boolean hasWork() { return !responses.isEmpty() || current != null && current.model == null; }

    private Node valid(Session session) {
        Node node = engine.menuNode(session.id);
        if (node == null || node.disabled || node.state != session.owner || node.state.born != session.born
                || session.edit != null && node.state.edit != session.edit
                || node.textClient != session.client
                || session.connection != null && engine.activeTextConnection() != session.connection
                || (session.explicit ? node.contextMenu == null : node.kind != Node.INPUT)) return null;
        return node;
    }

    boolean resolve() {
        boolean consumed = false;
        while (!responses.isEmpty()) {
            Session response = responses.removeFirst();
            Node target = valid(response);
            if (target == null) continue;
            try {
                if (response.choice.command() != null) {
                    engine.menuCommand(response.id, response.choice.command());
                    consumed = true;
                } else if (target.contextMenu != null) {
                    consumed |= Menu.build(target.contextMenu, edits(target), response.choice).chosen();
                }
            } catch (RuntimeException | Error failure) {
                responses.clear();
                cancel();
                throw failure;
            }
        }
        if (consumed) return true;
        Session session = current;
        if (session == null) return false;
        Node node = valid(session);
        if (node == null) { cancel(); return false; }
        try {
            if (session.model == null) {
                session.model = Menu.build(node.contextMenu == null ? Menu::editItems : node.contextMenu, edits(node), null).model();
                if (session.model.visible()) pending = session;
                else current = null;
            }
            return false;
        } catch (RuntimeException | Error failure) {
            cancel();
            throw failure;
        }
    }

    void publish() {
        Session session = current;
        if (session != null && valid(session) == null) { cancel(); return; }
        if (pending == null) return;
        pending = null;
        try {
            engine.host().popupMenu(session.model, session.x, session.y, id -> selected(session, id));
        } catch (RuntimeException | Error failure) {
            cancel();
            throw failure;
        }
    }

    private void selected(Session session, int id) {
        if (current != session || closed) return;
        MenuModel.Item choice = session.model.find(id);
        if (id == 0) { current = pending = null; return; }
        if (session.choice != null || choice == null || !choice.enabled() || choice.kind() != MenuModel.Kind.ITEM) return;
        session.choice = choice;
        responses.addLast(session);
        current = pending = null;
        engine.host().cancelMenu();
        engine.requestFrame();
    }

    private void cancel() {
        current = pending = null;
        engine.host().cancelMenu();
    }

    void close() { closed = true; responses.clear(); cancel(); }

    private static List<MenuModel.Item> edits(Node node) {
        TextEdit edit = node.state == null ? null : node.state.edit;
        if (node.kind != Node.INPUT || edit == null) return List.of();
        List<MenuModel.Item> result = new ArrayList<>();
        boolean selection = edit.selStart() != edit.selEnd();
        if (!edit.readOnly) add(result, "Cut", EditCommand.CUT, selection && !edit.password);
        add(result, "Copy", EditCommand.COPY, selection && !edit.password);
        if (!edit.readOnly) add(result, "Paste", EditCommand.PASTE, true);
        result.add(new MenuModel.Item(0, "", "", MenuModel.Kind.SEPARATOR, false, false, null, null, List.of()));
        add(result, "Select All", EditCommand.SELECT_ALL, !edit.text().isEmpty());
        return result;
    }

    private static void add(List<MenuModel.Item> items, String label, EditCommand command, boolean enabled) {
        items.add(new MenuModel.Item(0, "", label, MenuModel.Kind.ITEM, enabled, false, null, command, List.of()));
    }

    private static final class Session {
        final long id, born;
        final ElementState owner;
        final TextEdit edit;
        final jui.ui.TextInputClient client;
        final TextClientAdapter connection;
        final boolean explicit;
        final float x, y;
        MenuModel model;
        MenuModel.Item choice;

        Session(Node node, float x, float y, TextClientAdapter connection) {
            id = node.id;
            owner = node.state;
            born = owner.born;
            edit = owner.edit;
            client = node.textClient;
            this.connection = connection != null && connection.owner == owner ? connection : null;
            explicit = node.contextMenu != null;
            this.x = x;
            this.y = y;
        }
    }
}
