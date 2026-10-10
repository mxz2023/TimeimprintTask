package cn.net.mxz.timeimprint.task.adapter.sms.client;

import java.net.URI;
import java.util.logging.Logger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 腾讯云短信 SendSms。密钥、应用、签名和模板只从环境读取。
 * 缺少任一项、调用失败或通道未返回 Ok 时，结果为失败，调用方不得把验证码入库。
 */
public class TencentSmsSender implements SmsSender {

    private static final String HOST = "sms.tencentcloudapi.com";
    private static final String ACTION = "SendSms";
    private static final String VERSION = "2021-01-11";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger LOG = Logger.getLogger(TencentSmsSender.class.getName());

    private final String secretId;
    private final String secretKey;
    private final String sdkAppId;
    private final String signName;
    private final String templateId;
    private final String region;
    private final HttpClient http;

    public TencentSmsSender(
            String secretId,
            String secretKey,
            String sdkAppId,
            String signName,
            String templateId,
            String region) {
        this(secretId, secretKey, sdkAppId, signName, templateId, region,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    TencentSmsSender(
            String secretId,
            String secretKey,
            String sdkAppId,
            String signName,
            String templateId,
            String region,
            HttpClient http) {
        this.secretId = blankToEmpty(secretId);
        this.secretKey = blankToEmpty(secretKey);
        this.sdkAppId = blankToEmpty(sdkAppId);
        this.signName = blankToEmpty(signName);
        this.templateId = blankToEmpty(templateId);
        this.region = region == null || region.isBlank() ? "ap-guangzhou" : region;
        this.http = http;
    }

    @Override
    public boolean send(String phoneNumber, String code) {
        if (secretId.isBlank() || secretKey.isBlank() || sdkAppId.isBlank() || signName.isBlank() || templateId.isBlank()) {
            return false;
        }
        if (phoneNumber == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        try {
            String body = payload(toE164(phoneNumber), code);
            long timestamp = Instant.now().getEpochSecond();
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + HOST))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-TC-Action", ACTION)
                    .header("X-TC-Version", VERSION)
                    .header("X-TC-Timestamp", Long.toString(timestamp))
                    .header("X-TC-Region", region)
                    .header("Authorization", authorization(body, timestamp))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && accepted(response.body())) {
                return true;
            }
            LOG.warning("tencent sms rejected, http=" + response.statusCode() + ", code=" + providerCode(response.body()));
            return false;
        } catch (Exception ex) {
            LOG.warning("tencent sms send failed: " + ex.getClass().getSimpleName());
            return false;
        }
    }

    String payload(String e164, String code) {
        ObjectNode body = JSON.createObjectNode();
        body.putArray("PhoneNumberSet").add(e164);
        body.put("SmsSdkAppId", sdkAppId);
        body.put("SignName", signName);
        body.put("TemplateId", templateId);
        body.putArray("TemplateParamSet").add(code).add("1");
        return JSON.writeValueAsString(body);
    }

    static String toE164(String phoneNumber) {
        String digits = phoneNumber.trim();
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("86") && digits.length() == 13) {
            return "+" + digits;
        }
        return "+86" + digits;
    }

    static String providerCode(String body) {
        if (body == null || body.isBlank()) {
            return "empty";
        }
        JsonNode response = JSON.readTree(body).path("Response");
        String error = response.path("Error").path("Code").asText("");
        if (!error.isBlank()) {
            return error;
        }
        JsonNode set = response.path("SendStatusSet");
        if (set.isArray() && !set.isEmpty()) {
            String code = set.get(0).path("Code").asText("");
            return code.isBlank() ? "unknown" : code;
        }
        return "unknown";
    }

    static boolean accepted(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        JsonNode set = JSON.readTree(body).path("Response").path("SendStatusSet");
        return set.isArray() && !set.isEmpty() && "Ok".equals(set.get(0).path("Code").asText());
    }

    private String authorization(String payload, long timestamp) throws Exception {
        String date = DATE.format(Instant.ofEpochSecond(timestamp));
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:" + HOST + "\n";
        String signedHeaders = "content-type;host";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256(payload);
        String credentialScope = date + "/sms/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + sha256(canonicalRequest);
        byte[] secretDate = hmac(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac(secretDate, "sms");
        byte[] secretSigning = hmac(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmac(secretSigning, stringToSign));
        return "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(byte[] key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
