package cn.net.mxz.timeimprint.task.service.application.access.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TestActorContextHolder public method surface for P02 refactor safety. */
class TestActorContextHolderTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TestActorContextHolder.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("clear/0",
                "get/0",
                "set/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
