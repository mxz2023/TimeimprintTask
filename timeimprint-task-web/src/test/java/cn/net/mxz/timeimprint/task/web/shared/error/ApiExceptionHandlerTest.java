package cn.net.mxz.timeimprint.task.web.shared.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze ApiExceptionHandler public method surface for P02 refactor safety. */
class ApiExceptionHandlerTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(ApiExceptionHandler.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("handleApp/1",
                "handleMedia/1",
                "handleMethod/1",
                "handleMissing/1",
                "handleOther/1",
                "handleUnreadable/1",
                "handleValid/1"), actual);
        assertFalse(actual.isEmpty());
    }
}
