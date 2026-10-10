package cn.net.mxz.timeimprint.task.adapter.wechat.client;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
class DisabledWeChatOAuthClientTest {
    @Test void alwaysRejects() {
        assertThrows(IllegalStateException.class, () -> new DisabledWeChatOAuthClient().exchange("web", "c", "s"));
    }
}
