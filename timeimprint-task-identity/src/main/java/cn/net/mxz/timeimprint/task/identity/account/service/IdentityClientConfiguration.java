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
            @Value("${timeimprint.identity.sms.secret-key:}") String secretKey,
            @Value("${timeimprint.identity.sms.sdk-app-id:}") String sdkAppId,
            @Value("${timeimprint.identity.sms.sign-name:}") String signName,
            @Value("${timeimprint.identity.sms.template-id:}") String templateId,
            @Value("${timeimprint.identity.sms.region:ap-guangzhou}") String region) {
        if ("tencent".equals(mode)) {
            requireSmsConfig(secretId, secretKey, sdkAppId, signName, templateId);
            return new TencentSmsSender(secretId, secretKey, sdkAppId, signName, templateId, region);
        }
        return new CaptureSmsSender();
    }

    private static void requireSmsConfig(
            String secretId, String secretKey, String sdkAppId, String signName, String templateId) {
        StringBuilder missing = new StringBuilder();
        appendMissing(missing, "secret-id", secretId);
        appendMissing(missing, "secret-key", secretKey);
        appendMissing(missing, "sdk-app-id", sdkAppId);
        appendMissing(missing, "sign-name", signName);
        appendMissing(missing, "template-id", templateId);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "timeimprint.identity.sms mode tencent requires " + missing);
        }
    }

    private static void appendMissing(StringBuilder missing, String name, String value) {
        if (value == null || value.isBlank()) {
            if (!missing.isEmpty()) {
                missing.append(", ");
            }
            missing.append(name);
        }
    }

    @Bean
    WeChatOAuthClient weChatOAuthClient(@Value("${timeimprint.identity.wechat.mode:capture}") String mode) {
        if ("disabled".equals(mode)) {
            return new DisabledWeChatOAuthClient();
        }
        return new CaptureWeChatOAuthClient();
    }
}
