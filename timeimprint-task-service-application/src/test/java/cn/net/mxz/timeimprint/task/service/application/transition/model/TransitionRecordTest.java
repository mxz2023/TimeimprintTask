package cn.net.mxz.timeimprint.task.service.application.transition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TransitionRecord record components for P02 refactor safety. */
class TransitionRecordTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(TransitionRecord.class.isRecord());
        List<String> actual = Arrays.stream(TransitionRecord.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("transitionId",
                "definitionId",
                "instanceId",
                "sourceType",
                "sourceKey",
                "commandKey",
                "fromControlState",
                "toControlState",
                "fromLifecycle",
                "toLifecycle",
                "fromScenarioState",
                "toScenarioState",
                "fromRevision",
                "toRevision",
                "actorType",
                "actorId",
                "traceId",
                "createdAt"), actual);
    }
}
