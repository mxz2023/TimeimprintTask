package cn.net.mxz.timeimprint.task.service.storage.mysql.shared.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class StorageTime {
    private StorageTime() {}

    public static LocalDateTime toUtcLdt(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant toInstant(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return ldt.toInstant(ZoneOffset.UTC);
    }
}
