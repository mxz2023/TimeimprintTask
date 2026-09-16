package cn.net.mxz.timeimprint.task.service.kernel.transition.revision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze Revisions public method surface for P02 refactor safety. */
class RevisionsTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(Revisions.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("isInitialCreation/2",
                "nextAfter/1",
                "validate/1",
                "validateTransitionFrom/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
