package cn.net.mxz.timeimprint.task.web.shared.filter;

import cn.net.mxz.timeimprint.task.domain.shared.response.ApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.shared.response.ApiMessages;
import cn.net.mxz.timeimprint.task.domain.shared.response.ApiResponse;
import cn.net.mxz.timeimprint.task.service.application.shared.service.RuntimeAdmission;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A39 / 04：readiness REFUSING 后，尚未进入幂等事务的新写统一 503 RETRY_LATER。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ShutdownWriteRejectFilter extends OncePerRequestFilter {

    private final RuntimeAdmission admission;
    private final JsonMapper objectMapper;

    public ShutdownWriteRejectFilter(RuntimeAdmission admission, JsonMapper objectMapper) {
        this.admission = admission;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!admission.acceptingWrites() && isWrite(request) && !isActuator(request)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            var body = new ApiResponse<>(
                    ApiErrorCodes.RETRY_LATER,
                    ApiMessages.error(
                            ApiErrorCodes.RETRY_LATER, "shutting down; retry with same requestId"),
                    UUID.randomUUID().toString().replace("-", ""),
                    null);
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isWrite(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private static boolean isActuator(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/actuator/");
    }
}
