package cn.net.mxz.timeimprint.task.identity.account.service;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
class IdentityClientConfigurationContextTest {
    @Test void isConfiguration() {
        assertTrue(IdentityClientConfiguration.class.isAnnotationPresent(Configuration.class));
    }
}
