package cn.net.mxz.timeimprint.task.service.runtime.action.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test for ActionLocalExecutor. */
class ActionLocalExecutorTest {

    @Test
    void freezesPublicExecuteEntry() {
        List<String> actual = Arrays.stream(ActionLocalExecutor.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("execute/2"), actual);
    }
}
