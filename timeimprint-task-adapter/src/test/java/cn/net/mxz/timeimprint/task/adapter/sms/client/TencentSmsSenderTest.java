package cn.net.mxz.timeimprint.task.adapter.sms.client;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
class TencentSmsSenderTest {
    @Test void missingCredentialsDoesNotReportSuccess() {
        assertFalse(new TencentSmsSender("", "").send("13800138000", "123456"));
    }
}
