package cn.net.mxz.timeimprint.task.identity.account.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class IdentityServiceTest {
    @Test void hashesTokenStably() {
        assertEquals(32, IdentityService.sha256("it-token-local-actor").length);
    }
}
