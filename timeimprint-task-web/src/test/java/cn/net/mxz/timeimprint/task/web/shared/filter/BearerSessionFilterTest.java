package cn.net.mxz.timeimprint.task.web.shared.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.filter.OncePerRequestFilter;

/** 会话过滤器：任务接口必须带令牌，注册登录除外。 */
class BearerSessionFilterTest {

    @Test
    void taskApiRequiresTokenAndRegisterDoesNot() {
        assertTrue(OncePerRequestFilter.class.isAssignableFrom(BearerSessionFilter.class));
        MockHttpServletRequest task = new MockHttpServletRequest("POST", "/api/v1/task-definitions");
        MockHttpServletRequest register = new MockHttpServletRequest("POST", "/api/v1/users/register");
        MockHttpServletRequest internal = new MockHttpServletRequest("GET", "/internal/v1/action-jobs/1");
        assertTrue(BearerSessionFilter.requiresToken(task));
        assertFalse(BearerSessionFilter.requiresToken(register));
        assertFalse(BearerSessionFilter.requiresToken(internal));
        MockHttpServletRequest callback = new MockHttpServletRequest("POST", "/callbacks/v1/feishu/card-action");
        assertTrue(BearerSessionFilter.usesProcessAccount(internal));
        assertTrue(BearerSessionFilter.usesProcessAccount(callback));
        assertFalse(BearerSessionFilter.usesProcessAccount(task));
    }
}
