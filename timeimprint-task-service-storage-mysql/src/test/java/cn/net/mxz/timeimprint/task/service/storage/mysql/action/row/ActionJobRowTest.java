package cn.net.mxz.timeimprint.task.service.storage.mysql.action.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionJobRow public method surface for P02 refactor safety. */
class ActionJobRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(ActionJobRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getActionJobId/0",
                "getActionKey/0",
                "getAttemptCount/0",
                "getAvailableAt/0",
                "getCompletedAt/0",
                "getCreatedAt/0",
                "getDefinitionControlGeneration/0",
                "getDefinitionId/0",
                "getExecutionMode/0",
                "getExecutionToken/0",
                "getExpiresAt/0",
                "getHandlerKey/0",
                "getInstanceId/0",
                "getLeaseOwner/0",
                "getLeaseUntil/0",
                "getMaxAttempts/0",
                "getNextAttemptAt/0",
                "getOutcomeCode/0",
                "getOutcomeSummary/0",
                "getParentActionJobId/0",
                "getPayloadHash/0",
                "getPayloadJson/0",
                "getRedriveNo/0",
                "getSchemaVersion/0",
                "getStatus/0",
                "getTargetId/0",
                "getTargetType/0",
                "getTenantId/0",
                "getTransitionId/0",
                "getUpdatedAt/0",
                "setActionJobId/1",
                "setActionKey/1",
                "setAttemptCount/1",
                "setAvailableAt/1",
                "setCompletedAt/1",
                "setCreatedAt/1",
                "setDefinitionControlGeneration/1",
                "setDefinitionId/1",
                "setExecutionMode/1",
                "setExecutionToken/1",
                "setExpiresAt/1",
                "setHandlerKey/1",
                "setInstanceId/1",
                "setLeaseOwner/1",
                "setLeaseUntil/1",
                "setMaxAttempts/1",
                "setNextAttemptAt/1",
                "setOutcomeCode/1",
                "setOutcomeSummary/1",
                "setParentActionJobId/1",
                "setPayloadHash/1",
                "setPayloadJson/1",
                "setRedriveNo/1",
                "setSchemaVersion/1",
                "setStatus/1",
                "setTargetId/1",
                "setTargetType/1",
                "setTenantId/1",
                "setTransitionId/1",
                "setUpdatedAt/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
