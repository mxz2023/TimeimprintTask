package cn.net.mxz.timeimprint.task.service.capability.calendar.schedule.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CalendarConfigExceptionTest {

    @Test
    void invalidCarriesReasonKeyAndUnsupportedSchemaUsesItsCode() {
        CalendarConfigException invalid = CalendarConfigException.invalid("TW_FIELD", "必须包含 timeWindow");
        assertEquals("INVALID_REQUEST", invalid.errorCode());
        assertTrue(invalid.getMessage().startsWith("TW_FIELD："));

        CalendarConfigException unsupported = CalendarConfigException.unsupportedSchema();
        assertEquals("UNSUPPORTED_SCHEMA_VERSION", unsupported.errorCode());
        assertTrue(unsupported.getMessage().contains("schemaVersion 3"));
    }
}
