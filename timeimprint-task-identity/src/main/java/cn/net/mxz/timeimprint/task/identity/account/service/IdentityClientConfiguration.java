package cn.net.mxz.timeimprint.task.identity.account.service;

import cn.net.mxz.timeimprint.task.adapter.sms.client.CaptureSmsSender;
import cn.net.mxz.timeimprint.task.adapter.sms.client.SmsSender;
import cn.net.mxz.timeimprint.task.adapter.sms.client.TencentSmsSender;
import cn.net.mxz.timeimprint.task.adapter.wechat.client.CaptureWeChatOAuthClient;
import cn.net.mxz.timeimprint.task.adapter.wechat.client.DisabledWeChatOAuthClient;
import cn.net.mxz.timeimprint.task.adapter.wechat.client.WeChatOAuthClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityClientConfiguration {

    @Bean
    SmsSender smsSender(
            @Value("${timeimprint.identity.sms.mode:capture}") String mode,
            @Value("${timeimprint.identity.sms.secret-id:}") String secretId,
            @Value("${timeimprint.identity.sms.secret-key:}") String secretKey) {
        if ("tencent".equals(mode)) {
            return new TencentSmsSender(secretId, secretKey);
        }
        return new CaptureSmsSender();
    }

    @Bean
    WeChatOAuthClient weChatOAuthClient(@Value("${timeimprint.identity.wechat.mode:capture}") String mode) {
        if ("disabled".equals(mode)) {
            return new DisabledWeChatOAuthClient();
        }
        return new CaptureWeChatOAuthClient();
    }
}
