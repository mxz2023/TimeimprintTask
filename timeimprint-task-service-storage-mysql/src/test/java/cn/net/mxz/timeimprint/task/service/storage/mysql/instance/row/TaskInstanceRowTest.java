package cn.net.mxz.timeimprint.task.service.storage.mysql.instance.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskInstanceRow public method surface for P02 refactor safety. */
class TaskInstanceRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TaskInstanceRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getCreatedAt/0",
                "getDefinitionControlGeneration/0",
                "getDefinitionId/0",
                "getDescriptionSnapshot/0",
                "getDueAt/0",
                "getInstanceId/0",
                "getLifecycleCategory/0",
                "getOccurrenceAt/0",
                "getOccurrenceKey/0",
                "getRevision/0",
                "getScenarioSchemaVersion/0",
                "getScenarioSnapshotJson/0",
                "getScenarioState/0",
                "getScheduleGeneration/0",
                "getSnapshotHash/0",
                "getTerminalAt/0",
                "getTitleSnapshot/0",
                "getTriggerBindingId/0",
                "getUpdatedAt/0",
                "setCreatedAt/1",
                "setDefinitionControlGeneration/1",
                "setDefinitionId/1",
                "setDescriptionSnapshot/1",
                "setDueAt/1",
                "setInstanceId/1",
                "setLifecycleCategory/1",
                "setOccurrenceAt/1",
                "setOccurrenceKey/1",
                "setRevision/1",
                "setScenarioSchemaVersion/1",
                "setScenarioSnapshotJson/1",
                "setScenarioState/1",
                "setScheduleGeneration/1",
                "setSnapshotHash/1",
                "setTerminalAt/1",
                "setTitleSnapshot/1",
                "setTriggerBindingId/1",
                "setUpdatedAt/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
