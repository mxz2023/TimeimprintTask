package cn.net.mxz.timeimprint.task.web.shared.filter;

import cn.net.mxz.timeimprint.task.identity.account.service.IdentityException;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityService;
import cn.net.mxz.timeimprint.task.identity.account.service.IdentityUser;
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

/** 从 Authorization Bearer 解析账号。公开任务接口没有令牌时直接 401。调试头无效。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BearerSessionFilter extends OncePerRequestFilter {

    private final IdentityService identity;
    private final String localTenantId;
    private final String localActorId;

    public BearerSessionFilter(
            IdentityService identity,
            @Value("${timeimprint.local.tenant-id:local-tenant}") String localTenantId,
            @Value("${timeimprint.local.actor-id:local-actor}") String localActorId) {
        this.identity = identity;
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
            IdentityUser user = identity.requireSession(token);
            TestActorContextHolder.set(new ActorContext("USER", user.actorKey(), user.tenantId()));
            try {
                chain.doFilter(request, response);
            } finally {
                TestActorContextHolder.clear();
            }
        } catch (IdentityException ex) {
            writeUnauthenticated(response);
        }
    }

    static boolean requiresToken(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            return false;
        }
        if (path.startsWith("/api/v1/users/sms")) {
            return false;
        }
        return !(path.equals("/api/v1/users/register")
                || path.equals("/api/v1/users/login")
                || path.equals("/api/v1/users/login-authorization")
                || path.equals("/api/v1/users/password-reset")
                || path.startsWith("/api/v1/users/wechat/config")
                || path.equals("/api/v1/users/wechat/callback"));
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
}
