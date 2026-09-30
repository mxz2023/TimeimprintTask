package cn.net.mxz.timeimprint.task.service.application.definition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze CreateDefinitionCommand record components for P02 refactor safety. */
class CreateDefinitionCommandTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(CreateDefinitionCommand.class.isRecord());
        List<String> actual = Arrays.stream(CreateDefinitionCommand.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("tenantId",
                "actorType",
                "actorId",
                "requestId",
                "scenarioKey",
                "scenarioSchemaVersion",
                "title",
                "description",
                "scenarioConfigJson",
                "participants",
                "triggerBindings",
                "now",
                "windowEnd",
                "traceId"), actual);
    }
}
