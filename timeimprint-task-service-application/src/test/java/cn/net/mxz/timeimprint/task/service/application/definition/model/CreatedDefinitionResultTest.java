package cn.net.mxz.timeimprint.task.service.application.definition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze CreatedDefinitionResult record components for P02 refactor safety. */
class CreatedDefinitionResultTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(CreatedDefinitionResult.class.isRecord());
        List<String> actual = Arrays.stream(CreatedDefinitionResult.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("definition",
                "participants",
                "bindings",
                "instances",
                "signalCount",
                "duplicated",
                "responseJsonHint"), actual);
    }
}
