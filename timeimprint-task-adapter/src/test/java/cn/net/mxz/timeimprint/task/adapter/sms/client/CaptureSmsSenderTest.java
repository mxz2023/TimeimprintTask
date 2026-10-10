package cn.net.mxz.timeimprint.task.adapter.sms.client;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class CaptureSmsSenderTest {
    @Test void remembersLastCode() {
        CaptureSmsSender sender = new CaptureSmsSender();
        sender.send("13800138001", "654321");
        assertEquals("654321", CaptureSmsSender.lastCode("13800138001"));
    }
}
