package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.jspace.store.KindMap;
import jui.jspace.store.RelativeDate;
import jui.testing.Check;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class KindMapTest {
    public void testKinds() {
        Check.equal("文件夹", KindMap.of("x", true));
        Check.equal("磁盘映像", KindMap.of("k.dmg", false));
        Check.equal("Android 应用程序", KindMap.of("a.apk", false));
        Check.equal("PDF 文稿", KindMap.of("a.pdf", false));
        Check.equal("PNG 图像", KindMap.of("a.png", false));
        Check.equal("MPEG-4 影片", KindMap.of("a.mp4", false));
        Check.equal("ZIP 归档", KindMap.of("a.zip", false));
        Check.equal("GL 传输格式", KindMap.of("a.glb", false));
        Check.equal("源代码", KindMap.of("A.java", false));
        Check.equal("文档", KindMap.of("weird.zpr", false));
    }

    public void testIcons() {
        Check.equal(FileEntry.IconType.FOLDER, FileEntry.IconType.of("x", true));
        Check.equal(FileEntry.IconType.PDF, FileEntry.IconType.of("a.pdf", false));
        Check.equal(FileEntry.IconType.GENERIC, FileEntry.IconType.of("weird.zpr", false));
    }

    public void testRelativeDates() {
        ZoneId z = ZoneId.of("Asia/Shanghai");
        ZonedDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0).atZone(z);
        Check.equal("今天 11:16", RelativeDate.format(
                LocalDateTime.of(2026, 11, 5, 11, 16).atZone(z).toInstant().toEpochMilli(), now));
        Check.equal("昨天 17:02", RelativeDate.format(
                LocalDateTime.of(2026, 11, 4, 17, 2).atZone(z).toInstant().toEpochMilli(), now));
        Check.equal("10/2 09:30", RelativeDate.format(
                LocalDateTime.of(2026, 10, 2, 9, 30).atZone(z).toInstant().toEpochMilli(), now));
        Check.equal("2025/12/31 22:18", RelativeDate.format(
                LocalDateTime.of(2025, 12, 31, 22, 18).atZone(z).toInstant().toEpochMilli(), now));
    }
}
