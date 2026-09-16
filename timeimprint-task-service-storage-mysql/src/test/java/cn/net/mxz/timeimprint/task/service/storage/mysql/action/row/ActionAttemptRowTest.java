package cn.net.mxz.timeimprint.task.service.storage.mysql.action.row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionAttemptRow public method surface for P02 refactor safety. */
class ActionAttemptRowTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(ActionAttemptRow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("getActionJobId/0",
                "getAttemptId/0",
                "getAttemptNo/0",
                "getEffectStartedAt/0",
                "getErrorClass/0",
                "getErrorCode/0",
                "getExecutionToken/0",
                "getFinishedAt/0",
                "getOutcome/0",
                "getProviderReference/0",
                "getSafeSummary/0",
                "getStartedAt/0",
                "setActionJobId/1",
                "setAttemptId/1",
                "setAttemptNo/1",
                "setEffectStartedAt/1",
                "setErrorClass/1",
                "setErrorCode/1",
                "setExecutionToken/1",
                "setFinishedAt/1",
                "setOutcome/1",
                "setProviderReference/1",
                "setSafeSummary/1",
                "setStartedAt/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
