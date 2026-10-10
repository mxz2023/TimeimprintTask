package cn.net.mxz.timeimprint.task.adapter.wechat.client;

/** 用授权 code 换取微信身份。飞书扫码不在本期实现。 */
public interface WeChatOAuthClient {

    WeChatProfile exchange(String appType, String code, String state);
}
