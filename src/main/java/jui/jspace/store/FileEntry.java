package jui.jspace.store;

import java.nio.file.Path;

/** One entry of one directory, as scanned; immutable. */
public record FileEntry(String name, Path path, boolean dir, long size, long mtimeMillis,
                        String kind, IconType icon) {

    public enum IconType {
        FOLDER, DMG, APK, APP, PDF, DOC, IMAGE, VIDEO, AUDIO, ARCHIVE, MODEL3D, CODE, TEXT, GENERIC;

        public static IconType of(String name, boolean dir) {
            if (dir) return FOLDER;
            return switch (ext(name)) {
                case "dmg", "iso" -> DMG;
                case "apk" -> APK;
                case "app" -> APP;
                case "pdf" -> PDF;
                case "doc", "docx", "pages" -> DOC;
                case "png", "jpg", "jpeg", "gif", "webp", "heic", "tiff", "svg" -> IMAGE;
                case "mp4", "mov", "m4v", "avi", "mkv" -> VIDEO;
                case "mp3", "m4a", "wav", "flac", "aac" -> AUDIO;
                case "zip", "rar", "7z", "tar", "gz" -> ARCHIVE;
                case "glb", "gltf", "obj", "usdz" -> MODEL3D;
                case "java", "kt", "py", "js", "ts", "c", "cpp", "h", "go", "rs", "swift", "m", "sh" -> CODE;
                case "txt", "md", "log" -> TEXT;
                default -> GENERIC;
            };
        }

        static String ext(String name) {
            int dot = name.lastIndexOf('.');
            return dot <= 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        }
    }
}
