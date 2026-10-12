package cn.net.mxz.timeimprint.task.service.storage.mysql.shared.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/** Owner test: freeze SpringTransactionBoundary public method surface for P02 refactor safety. */
class SpringTransactionBoundaryTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(SpringTransactionBoundary.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("afterCommit/1",
                "attemptCount/0",
                "execute/1",
                "isRetryableLockFailure/1",
                "resetAttemptCount/0"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void afterCommitRunsImmediatelyWhenNoTransactionIsOpen() {
        AtomicBoolean ran = new AtomicBoolean();
        PlatformTransactionManager manager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };
        new SpringTransactionBoundary(manager).afterCommit(() -> ran.set(true));
        assertTrue(ran.get());
    }
}
