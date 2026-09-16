package cn.net.mxz.timeimprint.task.service.storage.mysql.action.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionJobRepositoryImpl public method surface for P02 refactor safety. */
class ActionJobRepositoryImplTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(ActionJobRepositoryImpl.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("cancelRunning/4",
                "claimForExecution/4",
                "completeAttempt/7",
                "completeRetryableFailure/5",
                "completeWithToken/6",
                "countRedrives/1",
                "expireIfDue/2",
                "findById/1",
                "findByIdForUpdate/1",
                "findIdForUpdate/1",
                "insertRedrive/4",
                "listAttempts/1",
                "listByInstance/1",
                "listExpiredRunningIds/1",
                "listFiltered/6",
                "listReadyDueIds/2",
                "listReadyDueIdsNewestFirst/2",
                "markCancelled/3",
                "markEffectStarted/3",
                "markSucceeded/4",
                "recoverExpiredLease/2",
                "releasePolicyBlocked/4"), actual);
        assertFalse(actual.isEmpty());
    }
}
