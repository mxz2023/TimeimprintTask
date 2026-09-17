package cn.net.mxz.timeimprint.task.web.shared.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.filter.OncePerRequestFilter;

/** Owner test for RequestBodySizeFilter. */
class RequestBodySizeFilterTest {

    @Test
    void enforces64KiBAndUsesObjectMapper() throws Exception {
        assertTrue(OncePerRequestFilter.class.isAssignableFrom(RequestBodySizeFilter.class));
        assertEquals(65_536, RequestBodySizeFilter.MAX_BODY_BYTES);
        var ctors = RequestBodySizeFilter.class.getDeclaredConstructors();
        assertEquals(1, ctors.length);
        assertEquals(1, ctors[0].getParameterCount());
        assertEquals(
                tools.jackson.databind.json.JsonMapper.class,
                ctors[0].getParameterTypes()[0]);
    }
}
