package cn.net.mxz.timeimprint.task.adapter.sms.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TencentSmsSenderTest {

    @Test
    void missingCredentialsDoesNotReportSuccess() {
        TencentSmsSender sender = new TencentSmsSender("", "", "", "", "", "ap-guangzhou");
        assertFalse(sender.send("13800138000", "123456"));
    }

    @Test
    void payloadCarriesE164PhoneAndSingleTemplateParameter() {
        TencentSmsSender sender = new TencentSmsSender("id", "key", "1400000000", "签名", "100000", "ap-guangzhou");
        String body = sender.payload(TencentSmsSender.toE164("13800138000"), "123456");
        assertTrue(body.contains("\"+8613800138000\""));
        assertTrue(body.contains("\"1400000000\""));
        assertTrue(body.contains("\"签名\""));
        assertTrue(body.contains("\"100000\""));
        assertTrue(body.contains("\"TemplateParamSet\":[\"123456\",\"1\"]"));
        assertEquals("+8613800138000", TencentSmsSender.toE164("13800138000"));
    }

    @Test
    void onlyOkSendStatusCountsAsSuccess() {
        assertTrue(TencentSmsSender.accepted(
                "{\"Response\":{\"SendStatusSet\":[{\"Code\":\"Ok\"}],\"RequestId\":\"r\"}}"));
        assertFalse(TencentSmsSender.accepted(
                "{\"Response\":{\"SendStatusSet\":[{\"Code\":\"FailedOperation.TemplateIncorrectOrUnapproved\"}]}}"));
        assertFalse(TencentSmsSender.accepted("{\"Response\":{\"Error\":{\"Code\":\"AuthFailure.SignatureFailure\"}}}"));
    }
}
