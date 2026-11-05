package jui.jspace.store;

import jui.jspace.Entries;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** The eight settings that really work, in ~/Library/Application Support/jui-jspace/settings.properties. */
public final class Settings {
    public enum Appearance { FOLLOW, LIGHT, DARK }
    public enum Layout { SINGLE, DOUBLE, GRID }
    public enum NewTabAt { HOME, CURRENT }
    public enum Density { COMFORTABLE, COMPACT }

    public Appearance appearance = Appearance.FOLLOW;
    public Layout defaultLayout = Layout.GRID;
    public NewTabAt newTabAt = NewTabAt.HOME;
    public boolean showHidden;
    public boolean foldersFirst = true;
    public Density density = Density.COMFORTABLE;
    public Entries.SortKey defaultSort = Entries.SortKey.NAME;
    public boolean defaultSortAsc = true;
    public boolean restoreWorkspace = true;

    private static Path defaultFile() {
        return Path.of(System.getProperty("user.home"),
                "Library/Application Support/jui-jspace/settings.properties");
    }

    public static Settings load() { return load(defaultFile()); }

    /** Defaults with the workspace file left alone, for tests that navigate around. */
    public static Settings forTests() {
        Settings s = new Settings();
        s.restoreWorkspace = false;
        return s;
    }

    public static Settings load(Path file) {
        Settings s = new Settings();
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                return s;                                        // unreadable settings read as defaults
            }
        }
        s.appearance = enumValue(Appearance.class, p.getProperty("appearance"), s.appearance);
        s.defaultLayout = enumValue(Layout.class, p.getProperty("defaultLayout"), s.defaultLayout);
        s.newTabAt = enumValue(NewTabAt.class, p.getProperty("newTabAt"), s.newTabAt);
        s.showHidden = Boolean.parseBoolean(p.getProperty("showHidden", "false"));
        s.foldersFirst = Boolean.parseBoolean(p.getProperty("foldersFirst", "true"));
        s.density = enumValue(Density.class, p.getProperty("density"), s.density);
        s.defaultSort = enumValue(Entries.SortKey.class, p.getProperty("defaultSort"), s.defaultSort);
        s.defaultSortAsc = Boolean.parseBoolean(p.getProperty("defaultSortAsc", "true"));
        s.restoreWorkspace = Boolean.parseBoolean(p.getProperty("restoreWorkspace", "true"));
        return s;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String v, E fallback) {
        if (v == null) return fallback;
        try { return Enum.valueOf(type, v); } catch (IllegalArgumentException e) { return fallback; }
    }

    public void save() { save(defaultFile()); }

    public void save(Path file) {
        Properties p = new Properties();
        p.setProperty("appearance", appearance.name());
        p.setProperty("defaultLayout", defaultLayout.name());
        p.setProperty("newTabAt", newTabAt.name());
        p.setProperty("showHidden", Boolean.toString(showHidden));
        p.setProperty("foldersFirst", Boolean.toString(foldersFirst));
        p.setProperty("density", density.name());
        p.setProperty("defaultSort", defaultSort.name());
        p.setProperty("defaultSortAsc", Boolean.toString(defaultSortAsc));
        p.setProperty("restoreWorkspace", Boolean.toString(restoreWorkspace));
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                p.store(out, "QSpace (j-ui)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);                  // saving settings failing is fatal-ish; surface it
        }
    }
}
