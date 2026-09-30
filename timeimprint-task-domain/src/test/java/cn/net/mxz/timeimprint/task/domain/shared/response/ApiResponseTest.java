package cn.net.mxz.timeimprint.task.domain.shared.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ApiResponse record components for P02 refactor safety. */
class ApiResponseTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ApiResponse.class.isRecord());
        List<String> actual = Arrays.stream(ApiResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(
                List.of("code",
                "message",
                "traceId",
                "data"),
                actual);
    }
}
