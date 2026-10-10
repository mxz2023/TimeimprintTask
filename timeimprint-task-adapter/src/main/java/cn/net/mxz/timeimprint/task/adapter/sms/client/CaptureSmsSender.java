package cn.net.mxz.timeimprint.task.adapter.sms.client;

import java.util.concurrent.ConcurrentHashMap;

/** 测试与本地替身：不访问腾讯云，把验证码留在进程内供测试读取。 */
public class CaptureSmsSender implements SmsSender {

    private static final ConcurrentHashMap<String, String> LAST = new ConcurrentHashMap<>();

    @Override
    public boolean send(String phoneNumber, String code) {
        LAST.put(phoneNumber, code);
        return true;
    }

    public static String lastCode(String phoneNumber) {
        return LAST.get(phoneNumber);
    }
}
