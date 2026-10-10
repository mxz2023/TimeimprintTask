package cn.net.mxz.timeimprint.task.identity.account.service;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
class IdentityStoreTest {
    @Test void exposesActiveLookup() {
        assertTrue(java.util.Arrays.stream(IdentityStore.class.getMethods()).anyMatch(m -> m.getName().equals("isActive")));
    }
}
