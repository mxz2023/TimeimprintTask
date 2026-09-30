package cn.net.mxz.timeimprint.task.boot.configuration.json;

import cn.net.mxz.timeimprint.task.service.runtime.shared.configuration.TimeImprintJacksonDefaults;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single global Jackson 3 customization point for the composition root (03 §1.1).
 */
@Configuration
public class JacksonJsonConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer timeImprintJacksonCompatibility() {
        return TimeImprintJacksonDefaults::apply;
    }
}
