package cn.net.mxz.timeimprint.task.web.shared.filter;

import cn.net.mxz.timeimprint.task.service.application.access.model.ActorContext;
import cn.net.mxz.timeimprint.task.service.application.access.model.TestActorContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 从 Authorization Bearer 向用户服务核对任务调用身份。公开任务接口没有令牌时直接 401。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BearerSessionFilter extends OncePerRequestFilter {

    private final UserSessionLookup userSessions;
    private final String localTenantId;
    private final String localActorId;

    public BearerSessionFilter(
            UserSessionLookup userSessions,
            @Value("${timeimprint.local.tenant-id:local-tenant}") String localTenantId,
            @Value("${timeimprint.local.actor-id:local-actor}") String localActorId) {
        this.userSessions = userSessions;
        this.localTenantId = localTenantId;
        this.localActorId = localActorId;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String token = bearer(request);
        if (token == null) {
            if (requiresToken(request)) {
                writeUnauthenticated(response);
                return;
            }
            if (usesProcessAccount(request)) {
                TestActorContextHolder.set(new ActorContext("USER", localActorId, localTenantId));
                try {
                    chain.doFilter(request, response);
                } finally {
                    TestActorContextHolder.clear();
                }
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        try {
            var session = userSessions.find(token);
            if (session.isEmpty()) {
                writeUnauthenticated(response);
                return;
            }
            proceed(session.get().actorKey(), session.get().tenantId(), request, response, chain);
        } catch (UserSessionLookup.UserDirectoryUnavailable ex) {
            writeUnavailable(response);
        }
    }

    private void proceed(
            String actorKey,
            String tenantId,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {
        TestActorContextHolder.set(new ActorContext("USER", actorKey, tenantId));
        try {
            chain.doFilter(request, response);
        } finally {
            TestActorContextHolder.clear();
        }
    }

    static boolean requiresToken(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            return false;
        }
        return true;
    }

    /** 内部诊断与飞书回调不走用户令牌，仍用本机配置的账号执行。 */
    static boolean usesProcessAccount(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && (path.startsWith("/internal/") || path.startsWith("/callbacks/"));
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static void writeUnauthenticated(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"没有可信调用身份\",\"traceId\":\"none\",\"data\":null}");
    }

    private static void writeUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(503);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write("{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"用户服务暂时不可用\",\"traceId\":\"none\",\"data\":null}");
    }
}
