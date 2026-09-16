package cn.net.mxz.timeimprint.task.domain.shared.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test for ApiErrorCodes. */
class ApiErrorCodesTest {

    @Test
    void freezesSection7ErrorCodes() {
        Set<String> actual = Arrays.stream(ApiErrorCodes.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("COMMAND_NOT_SUPPORTED",
                "EXTENSION_NOT_FOUND",
                "FORBIDDEN",
                "IDEMPOTENCY_CONFLICT",
                "INTERNAL_ERROR",
                "INVALID_CURSOR",
                "INVALID_REQUEST",
                "OK",
                "POLICY_REJECTED",
                "REQUEST_TOO_LARGE",
                "RESOURCE_NOT_FOUND",
                "RETRY_LATER",
                "REVISION_CONFLICT",
                "STATE_CONFLICT",
                "UNAUTHENTICATED",
                "UNSUPPORTED_MEDIA_TYPE",
                "UNSUPPORTED_SCHEMA_VERSION"), actual);
    }
}
