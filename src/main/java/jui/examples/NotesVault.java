package jui.examples;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Benchmark vault: read one saved body, then scan names without reading other bodies. */
final class NotesVault {
    final Path root;
    final String savedName, body;
    final FilesDemo.Note selected;

    private NotesVault(Path root, String savedName, String body) {
        this.root = root;
        this.savedName = savedName;
        this.body = body;
        this.selected = note(savedName);
    }

    static NotesVault open(Path root) {
        root = root.toAbsolutePath().normalize();
        try {
            String saved = Files.readString(root.resolve(".last-note")).trim();
            Path selected = root.resolve(saved).normalize();
            if (!selected.getParent().equals(root) || !saved.endsWith(".md"))
                throw new IllegalArgumentException("last note must be a Markdown file inside the vault");
            return new NotesVault(root, saved, Files.readString(selected));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    List<FilesDemo.Note> scan() {
        List<FilesDemo.Note> notes = new ArrayList<>();
        try (var paths = Files.list(root)) {
            paths.filter(p -> p.getFileName().toString().endsWith(".md") && Files.isRegularFile(p))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        notes.add(name.equals(savedName) ? selected : note(name));
                    });
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return notes;
    }

    private static FilesDemo.Note note(String name) {
        long id = 0xcbf29ce484222325L;
        for (int i = 0; i < name.length(); i++) id = (id ^ name.charAt(i)) * 0x100000001b3L;
        return new FilesDemo.Note(id, name.substring(0, name.length() - 3), null);
    }
}
