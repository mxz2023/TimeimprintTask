package cn.net.mxz.timeimprint.task.adapter.sms.client;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
class SmsSenderContractTest {
    @Test void captureSendsWithoutNetwork() {
        SmsSender sender = new CaptureSmsSender();
        assertTrue(sender.send("13800138000", "123456"));
    }
}
