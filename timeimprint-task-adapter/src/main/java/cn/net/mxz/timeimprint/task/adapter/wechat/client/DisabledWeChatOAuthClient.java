package cn.net.mxz.timeimprint.task.adapter.wechat.client;

/** 未配置微信凭据时拒绝换票，避免在没有密钥时假装登录成功。 */
public class DisabledWeChatOAuthClient implements WeChatOAuthClient {

    @Override
    public WeChatProfile exchange(String appType, String code, String state) {
        throw new IllegalStateException("微信登录尚未配置");
    }
}
