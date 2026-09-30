package cn.net.mxz.timeimprint.task.web.shared.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.application.shared.service.RuntimeAdmission;
import tools.jackson.databind.json.JsonMapper;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.filter.OncePerRequestFilter;

/** Owner test for ShutdownWriteRejectFilter. */
class ShutdownWriteRejectFilterTest {

    @Test
    void requiresAdmissionAndObjectMapper() {
        assertTrue(OncePerRequestFilter.class.isAssignableFrom(ShutdownWriteRejectFilter.class));
        Constructor<?> ctor = Arrays.stream(ShutdownWriteRejectFilter.class.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 2)
                .findFirst()
                .orElseThrow();
        assertEquals(RuntimeAdmission.class, ctor.getParameterTypes()[0]);
        assertEquals(JsonMapper.class, ctor.getParameterTypes()[1]);
    }
}
