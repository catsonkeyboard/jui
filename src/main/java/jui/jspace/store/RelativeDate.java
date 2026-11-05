package jui.jspace.store;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** 修改日期的人话格式：今天/昨天/今年/往年，与 QSpace 的列一致。 */
public final class RelativeDate {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("M/d HH:mm");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm");

    public static String format(long epochMillis, ZonedDateTime now) {
        ZonedDateTime t = Instant.ofEpochMilli(epochMillis).atZone(now.getZone());
        ZonedDateTime today = now.toLocalDate().atStartOfDay(now.getZone());
        if (!t.isBefore(today)) return "今天 " + TIME.format(t);
        if (!t.isBefore(today.minusDays(1))) return "昨天 " + TIME.format(t);
        if (t.getYear() == now.getYear()) return YEAR.format(t);
        return FULL.format(t);
    }

    private RelativeDate() {}
}
