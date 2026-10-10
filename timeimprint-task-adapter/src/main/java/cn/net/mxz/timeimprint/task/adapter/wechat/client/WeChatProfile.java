package cn.net.mxz.timeimprint.task.adapter.wechat.client;

/** 不含访问令牌的微信用户资料。 */
public record WeChatProfile(
        String appType, String openid, String unionid, String nickname, String avatarUrl) {}
