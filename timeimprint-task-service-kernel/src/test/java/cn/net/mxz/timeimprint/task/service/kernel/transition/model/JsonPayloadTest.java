package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Owner test: freeze JsonPayload record components for P02 refactor safety. */
class JsonPayloadTest {

    @Test
    void freezesRecordComponents() {
        assertTrue(JsonPayload.class.isRecord());
        List<String> actual = Arrays.stream(JsonPayload.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("fields"), actual);
    }

    @Test
    void copiesFieldsDefensively() {
        Map<String, Object> raw = new java.util.HashMap<>();
        raw.put("k", "v");
        JsonPayload payload = new JsonPayload(raw);
        raw.put("k2", "v2");
        assertEquals(Map.of("k", "v"), payload.fields());
        assertTrue(payload instanceof ScenarioMutationPayload);
    }
}
