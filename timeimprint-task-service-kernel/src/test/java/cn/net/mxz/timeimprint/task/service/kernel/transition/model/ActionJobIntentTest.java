package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionJobIntent record components for P02 refactor safety. */
class ActionJobIntentTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ActionJobIntent.class.isRecord());
        List<String> actual = Arrays.stream(ActionJobIntent.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("handlerKey",
                "actionSchemaVersion",
                "actionKey",
                "executionMode",
                "targetType",
                "targetId",
                "availableAt",
                "expiresAt",
                "payload"), actual);
    }
}
