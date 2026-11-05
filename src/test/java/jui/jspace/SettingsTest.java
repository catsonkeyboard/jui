package jui.jspace;

import jui.jspace.store.Settings;
import jui.testing.Check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SettingsTest {
    public void testRoundTrip() throws IOException {
        Path f = Files.createTempFile("jspace-settings", ".properties");
        Settings s = Settings.load(f);
        s.appearance = Settings.Appearance.DARK;
        s.defaultLayout = Settings.Layout.DOUBLE;
        s.newTabAt = Settings.NewTabAt.CURRENT;
        s.showHidden = true;
        s.foldersFirst = false;
        s.density = Settings.Density.COMPACT;
        s.defaultSort = Entries.SortKey.SIZE;
        s.defaultSortAsc = false;
        s.restoreWorkspace = false;
        s.save(f);
        Settings back = Settings.load(f);
        Check.equal(Settings.Appearance.DARK, back.appearance);
        Check.equal(Settings.Layout.DOUBLE, back.defaultLayout);
        Check.equal(Settings.NewTabAt.CURRENT, back.newTabAt);
        Check.isTrue(back.showHidden, "showHidden");
        Check.isTrue(!back.foldersFirst, "foldersFirst");
        Check.equal(Settings.Density.COMPACT, back.density);
        Check.equal(Entries.SortKey.SIZE, back.defaultSort);
        Check.isTrue(!back.defaultSortAsc, "defaultSortAsc");
        Check.isTrue(!back.restoreWorkspace, "restoreWorkspace");
    }

    public void testMissingFileGivesDefaults() {
        Settings s = Settings.load(Path.of("/no/such/settings.properties"));
        Check.equal(Settings.Appearance.FOLLOW, s.appearance);
        Check.equal(Entries.SortKey.NAME, s.defaultSort);
        Check.isTrue(s.foldersFirst, "foldersFirst");
    }

    public void testBadValueFallsBackToDefault() throws IOException {
        Path f = Files.createTempFile("jspace-settings", ".properties");
        Files.writeString(f, "appearance=NEON\n");
        Check.equal(Settings.Appearance.FOLLOW, Settings.load(f).appearance);
    }
}
