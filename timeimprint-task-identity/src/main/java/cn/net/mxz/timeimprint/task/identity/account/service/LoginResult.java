package cn.net.mxz.timeimprint.task.identity.account.service;

/** 登录结果：已授权时带令牌，否则只带挑战。 */
public record LoginResult(
        boolean authorizationRequired,
        String challenge,
        String token,
        String actorKey,
        String nickname,
        String phoneNumber,
        boolean admin) {}
