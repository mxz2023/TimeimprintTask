package cn.net.mxz.timeimprint.task.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cn.net.mxz.timeimprint.task.adapter.sms.client.SmsSender;
import cn.net.mxz.timeimprint.task.adapter.wechat.client.WeChatOAuthClient;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityException;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityService;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityStore;
import org.junit.jupiter.api.Test;

/** U01：短信通道未成功时，验证码不得入库。 */
class AccountSmsChannelTest {

    @Test
    void failedSmsDoesNotInsertCode() {
        IdentityStore store = mock(IdentityStore.class);
        SmsSender sms = (phone, code) -> false;
        WeChatOAuthClient wechat = (appType, code, state) -> null;
        IdentityService service = new IdentityService(
                store, sms, wechat, "local-tenant", "local-dev-authorization");

        IdentityException ex = assertThrows(IdentityException.class, () -> service.sendSms("13800138000"));

        assertEquals("INVALID_REQUEST", ex.errorCode());
        verify(store, never()).insertCode(any(), any(), any());
    }
}
