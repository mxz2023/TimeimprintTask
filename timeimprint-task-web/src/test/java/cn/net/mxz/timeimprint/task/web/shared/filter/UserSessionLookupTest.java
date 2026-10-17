package cn.net.mxz.timeimprint.task.web.shared.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserSessionLookupTest {

    @Test
    void readsTenantAndActorFromUserServiceJson() {
        assertEquals("u-abc", UserSessionLookup.jsonString("{\"tenantKey\":\"local-tenant\",\"actorKey\":\"u-abc\"}", "actorKey"));
        assertEquals("local-tenant", UserSessionLookup.jsonString("{\"tenantKey\":\"local-tenant\",\"actorKey\":\"u-abc\"}", "tenantKey"));
    }

    @Test
    void fixtureTokensResolveOnlyWhenEnabled() {
        UserSessionLookup open = new UserSessionLookup("", "", 2000, true);
        assertEquals("local-actor", open.find("it-token-local-actor").orElseThrow().actorKey());
        assertEquals("actor-a", open.find("it-token-actor-a").orElseThrow().actorKey());
        assertEquals("test-tenant", open.find("it-token-actor-a").orElseThrow().tenantId());
        assertTrue(open.find("not-a-session").isEmpty());
        UserSessionLookup closed = new UserSessionLookup("", "", 2000, false);
        UserSessionLookup.UserDirectoryUnavailable ex = assertThrows(
                UserSessionLookup.UserDirectoryUnavailable.class, () -> closed.find("it-token-local-actor"));
        assertEquals("未配置用户服务地址", ex.getMessage());
    }
}
