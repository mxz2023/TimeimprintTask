package cn.net.mxz.timeimprint.task.identity.account.service;

/** 微信扫码登录配置；不含 appSecret 或访问令牌。 */
public record WeChatLoginConfig(String appType, String state, String authUrl) {}
