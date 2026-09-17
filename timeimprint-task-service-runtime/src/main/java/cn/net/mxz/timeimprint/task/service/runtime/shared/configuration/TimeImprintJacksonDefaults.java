package cn.net.mxz.timeimprint.task.service.runtime.shared.configuration;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared P01-compatible Jackson 3 builder defaults. The Boot {@code JsonMapper} bean is
 * customized only from boot-loader; this helper keeps golden tests aligned with that wiring.
 */
public final class TimeImprintJacksonDefaults {

    private TimeImprintJacksonDefaults() {}

    public static JsonMapper.Builder apply(JsonMapper.Builder builder) {
        return builder
                .configureForJackson2()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }

    public static JsonMapper newMapper() {
        return apply(JsonMapper.builder()).findAndAddModules().build();
    }
}
