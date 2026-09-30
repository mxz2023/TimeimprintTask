package cn.net.mxz.timeimprint.task.service.kernel.instance.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze InstanceStateTransition record components for P02 refactor safety. */
class InstanceStateTransitionTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(InstanceStateTransition.class.isRecord());
        List<String> actual = Arrays.stream(InstanceStateTransition.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("fromLifecycleCategory",
                "toLifecycleCategory",
                "fromScenarioState",
                "toScenarioState"), actual);
    }
}
