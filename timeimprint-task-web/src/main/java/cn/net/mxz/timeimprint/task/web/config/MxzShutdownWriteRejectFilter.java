package cn.net.mxz.timeimprint.task.web.config;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiMessages;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
import cn.net.mxz.timeimprint.task.service.application.runtime.MxzRuntimeAdmission;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class MxzShutdownWriteRejectFilter extends OncePerRequestFilter {

    private final MxzRuntimeAdmission admission;
    private final ObjectMapper objectMapper;

    public MxzShutdownWriteRejectFilter(MxzRuntimeAdmission admission, ObjectMapper objectMapper) {
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
            var body = new MxzApiResponse<>(
                    MxzApiErrorCodes.RETRY_LATER,
                    MxzApiMessages.error(
                            MxzApiErrorCodes.RETRY_LATER, "shutting down; retry with same requestId"),
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
