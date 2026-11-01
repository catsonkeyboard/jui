package jui.examples;

import java.nio.file.Files;
import java.nio.file.Path;
import jui.testing.Check;
import jui.ui.Tester;

public final class NotesVaultTest {
    public void testFirstFrameReadsOnlySavedNoteAndScanReadsOnlyFilenames() throws Exception {
        Path root = Files.createTempDirectory("jui-vault-");
        try {
            Files.writeString(root.resolve(".last-note"), "0002 Saved.md");
            Files.writeString(root.resolve("0002 Saved.md"), "# Saved\nOnly this body is read.");
            Files.write(root.resolve("0001 Other.md"), new byte[] {(byte) 0xff}); // unreadable UTF-8 body
            Files.writeString(root.resolve("ignore.txt"), "not a note");
            NotesVault vault = NotesVault.open(root);
            NotesDemo d = new NotesDemo(vault);
            Tester t = Tester.of(d::view, 980, 640);
            try {
                t.frame();
                Check.equal(1, d.notes.size());
                Check.equal("0002 Saved", d.current().title);
                Check.isTrue(t.hasText("# Saved\nOnly this body is read."), "saved body is present in first frame");
                var all = vault.scan();
                Check.equal(2, all.size());
                Check.equal("0001 Other", all.getFirst().title);
                Check.isTrue(all.get(1) == d.current(), "selected identity survives filename scan");
            } finally { t.close(); }
        } finally {
            try (var paths = Files.list(root)) { for (Path p : paths.toList()) Files.delete(p); }
            Files.delete(root);
        }
    }
}
