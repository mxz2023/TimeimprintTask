package cn.net.mxz.timeimprint.task.adapter.wechat.client;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class WeChatOAuthClientContractTest {
    @Test void captureUsesCodeAsOpenId() {
        WeChatOAuthClient client = new CaptureWeChatOAuthClient();
        assertEquals("oid", client.exchange("web", "oid", "state").openid());
    }
}
