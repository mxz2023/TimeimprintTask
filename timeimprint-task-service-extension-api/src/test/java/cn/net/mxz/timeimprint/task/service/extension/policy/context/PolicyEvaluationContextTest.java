package cn.net.mxz.timeimprint.task.service.extension.policy.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze PolicyEvaluationContext record components for P02 refactor safety. */
class PolicyEvaluationContextTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(PolicyEvaluationContext.class.isRecord());
        List<String> actual = Arrays.stream(PolicyEvaluationContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("phase",
                "definitionSnapshot",
                "instanceSnapshot",
                "commandKey",
                "actionJobId"), actual);
    }
}
