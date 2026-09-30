package cn.net.mxz.timeimprint.task.service.application.access.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActorContext record components for P02 refactor safety. */
class ActorContextTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ActorContext.class.isRecord());
        List<String> actual = Arrays.stream(ActorContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("principalType",
                "principalId",
                "tenantKey"), actual);
    }
}
