package cn.net.mxz.timeimprint.task.service.storage.mysql.signal.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskSignalRow public method surface for P02 refactor safety. */
class TaskSignalRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TaskSignalRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getAttemptCount/0",
                "getCreatedAt/0",
                "getDefinitionControlGeneration/0",
                "getDefinitionId/0",
                "getExecutionToken/0",
                "getInstanceId/0",
                "getLeaseOwner/0",
                "getLeaseUntil/0",
                "getMaxAttempts/0",
                "getNextAttemptAt/0",
                "getOccurredAt/0",
                "getParentSignalId/0",
                "getPayloadHash/0",
                "getPayloadJson/0",
                "getProcessStatus/0",
                "getProcessedAt/0",
                "getProviderKey/0",
                "getReceivedAt/0",
                "getRedriveNo/0",
                "getResultCode/0",
                "getResultSummary/0",
                "getSchemaVersion/0",
                "getSignalId/0",
                "getSignalKey/0",
                "getTenantId/0",
                "getTriggerBindingId/0",
                "getUpdatedAt/0",
                "setAttemptCount/1",
                "setCreatedAt/1",
                "setDefinitionControlGeneration/1",
                "setDefinitionId/1",
                "setExecutionToken/1",
                "setInstanceId/1",
                "setLeaseOwner/1",
                "setLeaseUntil/1",
                "setMaxAttempts/1",
                "setNextAttemptAt/1",
                "setOccurredAt/1",
                "setParentSignalId/1",
                "setPayloadHash/1",
                "setPayloadJson/1",
                "setProcessStatus/1",
                "setProcessedAt/1",
                "setProviderKey/1",
                "setReceivedAt/1",
                "setRedriveNo/1",
                "setResultCode/1",
                "setResultSummary/1",
                "setSchemaVersion/1",
                "setSignalId/1",
                "setSignalKey/1",
                "setTenantId/1",
                "setTriggerBindingId/1",
                "setUpdatedAt/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
