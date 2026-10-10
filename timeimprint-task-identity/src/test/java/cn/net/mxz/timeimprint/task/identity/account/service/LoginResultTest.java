package cn.net.mxz.timeimprint.task.identity.account.service;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
class LoginResultTest {
    @Test void challengeDoesNotCarryToken() {
        LoginResult result = new LoginResult(true, "c", null, "a", "n", null, false);
        assertTrue(result.authorizationRequired());
        assertTrue(result.token() == null);
    }
}
