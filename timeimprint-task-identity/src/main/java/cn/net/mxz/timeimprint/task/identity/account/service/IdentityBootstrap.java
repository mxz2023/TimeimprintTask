package cn.net.mxz.timeimprint.task.identity.account.service;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/** 预置本地开发账号和测试账号。令牌固定，只用于本机与自动化测试。 */
@Component
public class IdentityBootstrap implements SmartInitializingSingleton {

    public static final String LOCAL_TOKEN = "it-token-local-actor";
    public static final String TEST_TOKEN = "it-token-test-actor";
    public static final String ACTOR_A_TOKEN = "it-token-actor-a";
    public static final String ACTOR_B_TOKEN = "it-token-actor-b";

    private final IdentityService identity;

    public IdentityBootstrap(IdentityService identity) {
        this.identity = identity;
    }

    @Override
    public void afterSingletonsInstantiated() {
        identity.bootstrap("local-tenant", "local-actor", LOCAL_TOKEN);
        identity.bootstrap("test-tenant", "test-actor", TEST_TOKEN);
        identity.bootstrap("test-tenant", "actor-a", ACTOR_A_TOKEN);
        identity.bootstrap("test-tenant", "actor-b", ACTOR_B_TOKEN);
    }
}
