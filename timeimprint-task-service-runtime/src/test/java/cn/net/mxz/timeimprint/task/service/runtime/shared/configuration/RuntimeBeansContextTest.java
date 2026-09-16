package cn.net.mxz.timeimprint.task.service.runtime.shared.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/** Owner test for RuntimeBeans (Spring 组合配置 → ContextTest). */
class RuntimeBeansContextTest {

    @Test
    void freezesTypeIdentity() {
        assertEquals("RuntimeBeans", RuntimeBeans.class.getSimpleName());
        assertFalse(Modifier.isAbstract(RuntimeBeans.class.getModifiers()) && RuntimeBeans.class.isInterface());
        assertFalse(RuntimeBeans.class.isInterface());
    }
}
