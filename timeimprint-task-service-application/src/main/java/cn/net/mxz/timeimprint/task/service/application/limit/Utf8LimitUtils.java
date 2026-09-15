package cn.net.mxz.timeimprint.task.service.application.limit;

import java.nio.charset.StandardCharsets;

public final class Utf8LimitUtils {

    private Utf8LimitUtils() {}

    public static int utf8ByteLength(String value) {
        if (value == null) {
            return 0;
        }
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
