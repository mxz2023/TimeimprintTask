package cn.net.mxz.timeimprint.task.service.extension.scenario.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ScenarioExtensionDescriptor record components for P02 refactor safety. */
class ScenarioExtensionDescriptorTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(ScenarioExtensionDescriptor.class.isRecord());
        List<String> actual = Arrays.stream(ScenarioExtensionDescriptor.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("scenarioKey",
                "contractVersion",
                "declaredInstanceCommandKeys",
                "requiredCapabilityKeys"), actual);
    }
}
