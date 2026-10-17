package cn.net.mxz.timeimprint.task.identity.account.service;

/** 用户服务返回的 actor_key 形状。微信登录是 wx-，手机注册是 u-，预置账号是 local-actor。 */
public final class ActorKeyContract {

    public static final String WECHAT_PREFIX = "wx-";
    public static final String PHONE_PREFIX = "u-";
    public static final String LOCAL_ACTOR = "local-actor";

    private ActorKeyContract() {}
}
