package cn.net.mxz.timeimprint.task.service.extension.shared.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InitialDefinitionContext record components for P02 refactor safety. */
class InitialDefinitionContextTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(InitialDefinitionContext.class.isRecord());
        List<String> actual = Arrays.stream(InitialDefinitionContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("definitionSnapshot"), actual);
    }
}
