package cn.net.mxz.timeimprint.task.service.storage.mysql.signal.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TaskSignalRepositoryImpl public method surface for P02 refactor safety. */
class TaskSignalRepositoryImplTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TaskSignalRepositoryImpl.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("claimForProcessing/4",
                "completeWithToken/6",
                "countRedrives/1",
                "findById/1",
                "findByIdForUpdate/1",
                "findIdByTenantProviderAndSignalKey/3",
                "insertRedrive/4",
                "listExpiredRunningIds/1",
                "listReadyDue/2",
                "listReadyDueIds/2",
                "markIgnored/4",
                "markSucceeded/4",
                "recoverExpiredLease/2"), actual);
        assertFalse(actual.isEmpty());
    }
}
