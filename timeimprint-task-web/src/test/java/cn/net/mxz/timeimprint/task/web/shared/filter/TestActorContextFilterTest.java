package cn.net.mxz.timeimprint.task.web.shared.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.OncePerRequestFilter;

/** Owner test for TestActorContextFilter. */
class TestActorContextFilterTest {

    @Test
    void isTestProfileFilterWithTenantAndActorDefaults() {
        assertTrue(OncePerRequestFilter.class.isAssignableFrom(TestActorContextFilter.class));
        Profile profile = TestActorContextFilter.class.getAnnotation(Profile.class);
        assertEquals("test", profile.value()[0]);
        Constructor<?> ctor = Arrays.stream(TestActorContextFilter.class.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 2)
                .findFirst()
                .orElseThrow();
        assertEquals(String.class, ctor.getParameterTypes()[0]);
        assertEquals(String.class, ctor.getParameterTypes()[1]);
    }
}
