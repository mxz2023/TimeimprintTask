package cn.net.mxz.timeimprint.task.service.runtime.shared.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Owner test for TimeImprintJacksonDefaults. */
class TimeImprintJacksonDefaultsTest {

    @Test
    void buildsImmutableMapperWithUnknownPropertyFailure() throws Exception {
        JsonMapper mapper = TimeImprintJacksonDefaults.newMapper();
        assertNotNull(mapper);
        boolean failed = false;
        try {
            mapper.readTree("{\"a\":1,\"a\":2}");
        } catch (Exception ex) {
            failed = true;
        }
        assertTrue(failed, "duplicate keys must fail under P01-compatible defaults");
    }
}
