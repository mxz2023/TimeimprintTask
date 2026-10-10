package cn.net.mxz.timeimprint.task.identity.account.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class IdentityExceptionTest {
    @Test void keepsErrorCode() {
        assertEquals("UNAUTHENTICATED", new IdentityException("UNAUTHENTICATED", "没有可信调用身份").errorCode());
    }
}
