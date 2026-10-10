package cn.net.mxz.timeimprint.task.identity.account.service;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
class IdentityUserTest {
    @Test void activeAndAuthorized() {
        IdentityUser user = new IdentityUser(1, "t", "a", null, "a", null, "n", "ACTIVE", false, LocalDateTime.now());
        assertTrue(user.active());
        assertTrue(user.authorized());
    }
}
