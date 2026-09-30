package cn.net.mxz.timeimprint.task.service.storage.mysql.transition.committer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionPlanCommitter;

/** Owner test: freeze TransitionPlanCommitterImpl public method surface for P02 refactor safety. */
class TransitionPlanCommitterImplTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TransitionPlanCommitterImpl.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("commit/1"), actual);
        assertFalse(actual.isEmpty());
    }

    @Test
    void implementsTransitionPlanCommitterPort() {
        assertTrue(cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionPlanCommitter.class
                .isAssignableFrom(TransitionPlanCommitterImpl.class));
    }
}
