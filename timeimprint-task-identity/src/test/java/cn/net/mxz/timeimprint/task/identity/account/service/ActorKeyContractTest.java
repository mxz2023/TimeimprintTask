package cn.net.mxz.timeimprint.task.identity.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ActorKeyContractTest {

    @Test
    void keepsTheThreeActorKeyShapes() {
        assertEquals("wx-", ActorKeyContract.WECHAT_PREFIX);
        assertEquals("u-", ActorKeyContract.PHONE_PREFIX);
        assertEquals("local-actor", ActorKeyContract.LOCAL_ACTOR);
    }
}
