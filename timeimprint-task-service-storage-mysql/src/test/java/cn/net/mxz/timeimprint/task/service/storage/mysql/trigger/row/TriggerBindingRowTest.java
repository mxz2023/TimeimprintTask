package cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TriggerBindingRow public method surface for P02 refactor safety. */
class TriggerBindingRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TriggerBindingRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getBindingKey/0",
                "getBindingState/0",
                "getConfigHash/0",
                "getConfigJson/0",
                "getCreatedAt/0",
                "getCursorJson/0",
                "getDefinitionId/0",
                "getExhausted/0",
                "getNextFireAt/0",
                "getProviderKey/0",
                "getRevision/0",
                "getScheduleGeneration/0",
                "getSchemaVersion/0",
                "getTriggerBindingId/0",
                "getUpdatedAt/0",
                "setBindingKey/1",
                "setBindingState/1",
                "setConfigHash/1",
                "setConfigJson/1",
                "setCreatedAt/1",
                "setCursorJson/1",
                "setDefinitionId/1",
                "setExhausted/1",
                "setNextFireAt/1",
                "setProviderKey/1",
                "setRevision/1",
                "setScheduleGeneration/1",
                "setSchemaVersion/1",
                "setTriggerBindingId/1",
                "setUpdatedAt/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
