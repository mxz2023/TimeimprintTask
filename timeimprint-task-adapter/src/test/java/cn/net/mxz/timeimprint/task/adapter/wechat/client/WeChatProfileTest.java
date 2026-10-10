package cn.net.mxz.timeimprint.task.adapter.wechat.client;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class WeChatProfileTest {
    @Test void keepsOpenId() {
        assertEquals("o", new WeChatProfile("web", "o", "", "n", "").openid());
    }
}
