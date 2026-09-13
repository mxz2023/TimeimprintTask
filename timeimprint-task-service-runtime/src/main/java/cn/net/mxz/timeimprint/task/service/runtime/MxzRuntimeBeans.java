package cn.net.mxz.timeimprint.task.service.runtime;

import cn.net.mxz.timeimprint.task.common.BusinessClock;
import cn.net.mxz.timeimprint.task.common.MxzSystemUtcBusinessClock;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class MxzRuntimeBeans {

    @Bean
    BusinessClock businessClock() {
        return new MxzSystemUtcBusinessClock();
    }

    @Bean
    @Primary
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        // docs/04-API.md: unknown fields and duplicate JSON keys → 400
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
        return mapper;
    }
}
