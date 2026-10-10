package cn.net.mxz.timeimprint.task.adapter.wechat.client;

/** 测试替身：把 code 当作 openid，不访问微信。 */
public class CaptureWeChatOAuthClient implements WeChatOAuthClient {

    @Override
    public WeChatProfile exchange(String appType, String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new IllegalStateException("微信授权无效");
        }
        String openid = code.trim();
        return new WeChatProfile(appType, openid, "", "微信用户", "");
    }
}
