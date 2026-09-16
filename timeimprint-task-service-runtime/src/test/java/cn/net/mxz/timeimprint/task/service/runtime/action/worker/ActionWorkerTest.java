package cn.net.mxz.timeimprint.task.service.runtime.action.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ActionWorker public method surface for P02 refactor safety. */
class ActionWorkerTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(ActionWorker.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("claimBatchSize/0",
                "executeAction/1",
                "executeLocalWithForcedCasFailure/1",
                "pollAndExecute/0",
                "pollOnceForTests/0"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void exposesPollClaimAndExecuteEntryPointsForSplit() {
        List<String> names = Arrays.stream(ActionWorker.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .distinct()
                .toList();
        assertTrue(names.contains("pollAndExecute"));
        assertTrue(names.contains("claimBatchSize"));
        assertTrue(names.contains("executeAction"));
    }
}
