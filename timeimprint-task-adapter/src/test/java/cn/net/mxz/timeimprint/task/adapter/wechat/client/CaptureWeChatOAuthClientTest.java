package cn.net.mxz.timeimprint.task.adapter.wechat.client;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class CaptureWeChatOAuthClientTest {
    @Test void rejectsBlankState() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new CaptureWeChatOAuthClient().exchange("web", "oid", " "));
    }
}
