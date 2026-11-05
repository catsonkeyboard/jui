package jui.jspace.store;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Chinese kind names by file extension, as the list view's 种类 column shows. */
public final class KindMap {
    private static final Map<String, String> BY_EXT = Stream.of(new String[][] {
            {"dmg", "磁盘映像"}, {"iso", "磁盘映像"}, {"apk", "Android 应用程序"}, {"app", "应用程序"},
            {"pdf", "PDF 文稿"}, {"doc", "Word 文稿"}, {"docx", "Word 文稿"}, {"pages", "Pages 文稿"},
            {"png", "PNG 图像"}, {"jpg", "JPEG 图像"}, {"jpeg", "JPEG 图像"}, {"gif", "GIF 图像"},
            {"webp", "WebP 图像"}, {"heic", "HEIC 图像"}, {"tiff", "TIFF 图像"}, {"svg", "SVG 文稿"},
            {"mp4", "MPEG-4 影片"}, {"mov", "QuickTime 影片"}, {"m4v", "MPEG-4 影片"}, {"mkv", "Matroska 影片"},
            {"mp3", "MP3 音频"}, {"m4a", "AAC 音频"}, {"wav", "WAV 音频"}, {"flac", "FLAC 音频"},
            {"zip", "ZIP 归档"}, {"rar", "RAR 归档"}, {"7z", "7-Zip 归档"}, {"tar", "TAR 归档"}, {"gz", "GZIP 归档"},
            {"glb", "GL 传输格式"}, {"gltf", "GL 传输格式"}, {"usdz", "USDZ 场景"},
            {"txt", "纯文本"}, {"md", "Markdown 文稿"}, {"rtf", "RTF 文稿"},
    }).collect(Collectors.toUnmodifiableMap(e -> e[0], e -> e[1]));

    public static String of(String fileName, boolean dir) {
        if (dir) return "文件夹";
        String k = BY_EXT.get(FileEntry.IconType.ext(fileName));
        if (k != null) return k;
        return FileEntry.IconType.of(fileName, false) == FileEntry.IconType.CODE ? "源代码" : "文档";
    }

    private KindMap() {}
}
