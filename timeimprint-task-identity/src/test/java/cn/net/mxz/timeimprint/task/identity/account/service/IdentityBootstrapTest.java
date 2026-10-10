package cn.net.mxz.timeimprint.task.identity.account.service;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
class IdentityBootstrapTest {
    @Test void localAndTestTokensDiffer() {
        assertFalse(IdentityBootstrap.LOCAL_TOKEN.equals(IdentityBootstrap.ACTOR_A_TOKEN));
    }
}
