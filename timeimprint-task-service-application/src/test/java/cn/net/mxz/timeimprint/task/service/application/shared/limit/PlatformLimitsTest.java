package cn.net.mxz.timeimprint.task.service.application.shared.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/** Owner test for PlatformLimits. */
class PlatformLimitsTest {

    @Test
    void freezesTypeIdentity() {
        assertEquals("PlatformLimits", PlatformLimits.class.getSimpleName());
        assertFalse(Modifier.isAbstract(PlatformLimits.class.getModifiers()) && PlatformLimits.class.isInterface());
        assertFalse(PlatformLimits.class.isInterface());
    }
}
