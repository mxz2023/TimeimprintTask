package cn.net.mxz.timeimprint.task.service.storage.mysql.definition.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskDefinitionRow public method surface for P02 refactor safety. */
class TaskDefinitionRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TaskDefinitionRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getControlGeneration/0",
                "getControlState/0",
                "getCreatedAt/0",
                "getCreatedBy/0",
                "getDefinitionId/0",
                "getDescription/0",
                "getPausedAt/0",
                "getRetiredAt/0",
                "getRevision/0",
                "getScenarioConfigHash/0",
                "getScenarioConfigJson/0",
                "getScenarioKey/0",
                "getScenarioSchemaVersion/0",
                "getTenantId/0",
                "getTitle/0",
                "getUpdatedAt/0",
                "getUpdatedBy/0",
                "setControlGeneration/1",
                "setControlState/1",
                "setCreatedAt/1",
                "setCreatedBy/1",
                "setDefinitionId/1",
                "setDescription/1",
                "setPausedAt/1",
                "setRetiredAt/1",
                "setRevision/1",
                "setScenarioConfigHash/1",
                "setScenarioConfigJson/1",
                "setScenarioKey/1",
                "setScenarioSchemaVersion/1",
                "setTenantId/1",
                "setTitle/1",
                "setUpdatedAt/1",
                "setUpdatedBy/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
