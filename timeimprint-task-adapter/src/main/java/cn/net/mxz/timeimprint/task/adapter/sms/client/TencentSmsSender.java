package cn.net.mxz.timeimprint.task.adapter.sms.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 腾讯云短信通道。密钥只从环境读取。缺少凭据时发送失败，不把验证码写入库。
 */
public class TencentSmsSender implements SmsSender {

    private final String secretId;
    private final String secretKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public TencentSmsSender(String secretId, String secretKey) {
        this.secretId = secretId;
        this.secretKey = secretKey;
    }

    @Override
    public boolean send(String phoneNumber, String code) {
        if (secretId.isBlank() || secretKey.isBlank()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://sms.tencentcloudapi.com"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"PhoneNumber\":\"" + phoneNumber + "\"}"))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            return false;
        }
    }
}
