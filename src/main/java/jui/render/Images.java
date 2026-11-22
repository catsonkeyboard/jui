package jui.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The kit's doorway into the image atlas: it decodes a picture (pure-Java
 * PNG for now), parks the pixels in the renderer's atlas, and hands back
 * the id a scene draws. Paths decode once and cache, so a re-render costs
 * nothing; the atlas itself belongs to the newest renderer.
 */
public final class Images {

    private static ImageAtlas atlas;
    private static final Map<String, Integer> BY_PATH = new HashMap<>();

    /** Binds the atlas the newest renderer made; handles go stale on the next bind. */
    public static void bind(ImageAtlas a) {
        if (a != atlas) {
            atlas = a;
            BY_PATH.clear();
        }
    }

    /** Whether decoded pictures can land right now. */
    public static boolean available() {
        return atlas != null;
    }

    /**
     * Decodes and adds a picture, or returns its cached id. Only PNG reads
     * today; other formats throw.
     */
    public static int load(Path file) throws IOException {
        String key = file.toAbsolutePath().toString();
        Integer cached = BY_PATH.get(key);
        if (cached != null) {
            return cached;
        }
        int id = decode(Files.newInputStream(file), file.getFileName().toString());
        BY_PATH.put(key, id);
        return id;
    }

    /** Decodes from a stream; the caller names it for the error's sake. */
    public static int decode(InputStream in, String name) throws IOException {
        if (atlas == null) {
            throw new IllegalStateException("no renderer holds an image atlas yet");
        }
        String lower = name == null ? "" : name.toLowerCase();
        Png.Image image;
        if (lower.endsWith(".png") || !lower.contains(".")) {
            image = Png.read(in);
        } else {
            throw new IOException("only PNG reads today: " + name);
        }
        return atlas.add(image.width(), image.height(), image.argb());
    }

    /** Adds already-decoded straight ARGB pixels. */
    public static int add(int width, int height, int[] argb) {
        if (atlas == null) {
            throw new IllegalStateException("no renderer holds an image atlas yet");
        }
        return atlas.add(width, height, argb);
    }

    private Images() {}
}
