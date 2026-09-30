package cn.net.mxz.timeimprint.task.service.extension.action.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionHandlerKey record components for P02 refactor safety. */
class ActionHandlerKeyTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ActionHandlerKey.class.isRecord());
        List<String> actual = Arrays.stream(ActionHandlerKey.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("handlerKey",
                "actionSchemaVersion"), actual);
    }
}
