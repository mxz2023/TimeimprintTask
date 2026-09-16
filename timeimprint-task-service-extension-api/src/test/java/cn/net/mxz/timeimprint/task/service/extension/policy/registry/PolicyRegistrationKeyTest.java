package cn.net.mxz.timeimprint.task.service.extension.policy.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze PolicyRegistrationKey record components for P02 refactor safety. */
class PolicyRegistrationKeyTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(PolicyRegistrationKey.class.isRecord());
        List<String> actual = Arrays.stream(PolicyRegistrationKey.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("policyKey",
                "phase"), actual);
    }
}
