package cn.net.mxz.timeimprint.task.web.config;

import cn.net.mxz.timeimprint.task.domain.MxzApiErrorCodes;
import cn.net.mxz.timeimprint.task.domain.MxzApiMessages;
import cn.net.mxz.timeimprint.task.domain.MxzApiResponse;
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
 * 强制请求体上限 64KiB（docs/04-API.md）。优先看 Content-Length；超限返回 413 REQUEST_TOO_LARGE。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class MxzRequestBodySizeFilter extends OncePerRequestFilter {

    public static final int MAX_BODY_BYTES = 65_536;

    private final ObjectMapper objectMapper;

    public MxzRequestBodySizeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            int contentLength = request.getContentLength();
            if (contentLength > MAX_BODY_BYTES) {
                writeTooLarge(response);
                return;
            }
            String header = request.getHeader("Content-Length");
            if (header != null && !header.isBlank()) {
                try {
                    long declared = Long.parseLong(header.trim());
                    if (declared > MAX_BODY_BYTES) {
                        writeTooLarge(response);
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through; container / message converter will reject
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new MxzApiResponse<>(
                        MxzApiErrorCodes.REQUEST_TOO_LARGE,
                        MxzApiMessages.error(
                                MxzApiErrorCodes.REQUEST_TOO_LARGE, "request body exceeds 64KiB"),
                        UUID.randomUUID().toString().replace("-", ""),
                        null));
    }
}
