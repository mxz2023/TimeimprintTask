package cn.net.mxz.timeimprint.task.web.shared.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.filter.OncePerRequestFilter;

/** 会话过滤器：任务接口必须带令牌。账号接口不由本服务匿名放行。 */
class BearerSessionFilterTest {

    @Test
    void taskApiRequiresTokenAndRegisterDoesNot() {
        assertTrue(OncePerRequestFilter.class.isAssignableFrom(BearerSessionFilter.class));
        MockHttpServletRequest task = new MockHttpServletRequest("POST", "/api/v1/task-definitions");
        MockHttpServletRequest register = new MockHttpServletRequest("POST", "/api/v1/users/register");
        MockHttpServletRequest internal = new MockHttpServletRequest("GET", "/internal/v1/action-jobs/1");
        assertTrue(BearerSessionFilter.requiresToken(task));
        assertTrue(BearerSessionFilter.requiresToken(register));
        assertFalse(BearerSessionFilter.requiresToken(internal));
        MockHttpServletRequest callback = new MockHttpServletRequest("POST", "/callbacks/v1/feishu/card-action");
        assertTrue(BearerSessionFilter.usesProcessAccount(internal));
        assertTrue(BearerSessionFilter.usesProcessAccount(callback));
        assertFalse(BearerSessionFilter.usesProcessAccount(task));
        assertEquals("u-abc", UserSessionLookup.jsonString("{\"tenantKey\":\"local-tenant\",\"actorKey\":\"u-abc\"}", "actorKey"));
        assertEquals("local-tenant", UserSessionLookup.jsonString("{\"tenantKey\":\"local-tenant\",\"actorKey\":\"u-abc\"}", "tenantKey"));
    }
}
