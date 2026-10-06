package cn.net.mxz.timeimprint.task.adapter.feishu.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 契约：Token 提供者返回当前 tenant_access_token。 */
class FeishuTokenProviderContractTest {

    @Test
    void returnsTenantAccessToken() {
        FeishuTokenProvider provider = () -> "t-token";
        assertEquals("t-token", provider.tenantAccessToken());
    }
}
