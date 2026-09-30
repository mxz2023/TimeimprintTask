package cn.net.mxz.timeimprint.task.domain.shared.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze MarkReadRequest record components for P02 refactor safety. */
class MarkReadRequestTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(MarkReadRequest.class.isRecord());
        List<String> actual = Arrays.stream(MarkReadRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("requestId"), actual);
    }
}
