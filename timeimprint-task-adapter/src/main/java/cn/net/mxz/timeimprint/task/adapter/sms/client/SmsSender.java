package cn.net.mxz.timeimprint.task.adapter.sms.client;

/** 发送短信验证码。成功后才允许把验证码写入账号库。 */
public interface SmsSender {

    /**
     * @param phoneNumber 大陆 11 位手机号
     * @param code 六位验证码
     * @return 通道是否确认成功
     */
    boolean send(String phoneNumber, String code);
}
