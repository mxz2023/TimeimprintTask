package cn.net.mxz.timeimprint.task.web.shared.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot 4 defaults to Jackson 3 (tools.jackson); API DTOs still use
 * com.fasterxml.jackson.databind.JsonNode, so force Jackson 2 HTTP conversion.
 */
@Configuration
public class Jackson2WebConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    public Jackson2WebConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter jackson2 = new MappingJackson2HttpMessageConverter(objectMapper);
        converters.removeIf(c -> c.getClass().getName().contains("Jackson")
                && !(c instanceof MappingJackson2HttpMessageConverter));
        converters.add(0, jackson2);
    }
}
