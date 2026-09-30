package cn.net.mxz.timeimprint.task.service.application.access.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze TestActorContextProvider public method surface for P02 refactor safety. */
class TestActorContextProviderTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(TestActorContextProvider.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("currentActor/0",
                "requireCurrentActor/0"), actual);
        assertFalse(actual.isEmpty());
    }
}
