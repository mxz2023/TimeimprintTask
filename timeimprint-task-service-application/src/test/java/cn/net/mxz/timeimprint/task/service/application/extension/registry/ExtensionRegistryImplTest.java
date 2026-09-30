package cn.net.mxz.timeimprint.task.service.application.extension.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/** Owner test for ExtensionRegistryImpl. */
class ExtensionRegistryImplTest {

    @Test
    void freezesTypeIdentity() {
        assertEquals("ExtensionRegistryImpl", ExtensionRegistryImpl.class.getSimpleName());
        assertFalse(Modifier.isAbstract(ExtensionRegistryImpl.class.getModifiers()) && ExtensionRegistryImpl.class.isInterface());
        assertFalse(ExtensionRegistryImpl.class.isInterface());
    }
}
