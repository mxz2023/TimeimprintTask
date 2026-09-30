package cn.net.mxz.timeimprint.task.web.shared.filter;

import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.access.model.TestActorContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("test")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TestActorContextFilter extends OncePerRequestFilter {

    private final String defaultTenantId;
    private final String defaultActorId;

    public TestActorContextFilter(
            @Value("${timeimprint.test.tenant-id:test-tenant}") String defaultTenantId,
            @Value("${timeimprint.test.actor-id:test-actor}") String defaultActorId) {
        this.defaultTenantId = defaultTenantId;
        this.defaultActorId = defaultActorId;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenant = headerOrDefault(request, "X-Debug-Tenant-Id", defaultTenantId);
        String actor = headerOrDefault(request, "X-Debug-Actor-Id", defaultActorId);
        TestActorContextHolder.set(new ActorContext("USER", actor, tenant));
        try {
            filterChain.doFilter(request, response);
        } finally {
            TestActorContextHolder.clear();
        }
    }

    private static String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String v = request.getHeader(name);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return v.trim();
    }
}
