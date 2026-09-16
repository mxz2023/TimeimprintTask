package cn.net.mxz.timeimprint.task.service.kernel.definition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze DefinitionControlTransition record components for P02 refactor safety. */
class DefinitionControlTransitionTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(DefinitionControlTransition.class.isRecord());
        List<String> actual = Arrays.stream(DefinitionControlTransition.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("fromControlState",
                "toControlState"), actual);
    }
}
