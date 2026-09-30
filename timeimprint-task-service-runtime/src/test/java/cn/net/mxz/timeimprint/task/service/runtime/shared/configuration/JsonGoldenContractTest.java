package cn.net.mxz.timeimprint.task.service.runtime.shared.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON golden samples aligned with {@link TimeImprintJacksonDefaults} / boot-loader
 * {@code JsonMapperBuilderCustomizer} (P03 / J06–J08).
 */
class JsonGoldenContractTest {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private record Envelope(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("traceId") String traceId,
            @JsonProperty("data") Object data) {}

    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = TimeImprintJacksonDefaults.newMapper();
    }

    @Test
    void rejectsUnknownProperties() {
        String json = "{\"code\":\"OK\",\"message\":\"x\",\"traceId\":\"t\",\"data\":null,\"extra\":1}";
        assertThrows(UnrecognizedPropertyException.class, () -> mapper.readValue(json, Envelope.class));
    }

    @Test
    void rejectsDuplicateKeys() {
        String json = "{\"code\":\"OK\",\"code\":\"FAIL\",\"message\":\"x\",\"traceId\":\"t\",\"data\":null}";
        Exception ex = assertThrows(Exception.class, () -> mapper.readValue(json, Envelope.class));
        assertTrue(
                ex instanceof StreamReadException
                        || ex instanceof JacksonException
                        || (ex.getCause() != null && ex.getCause() instanceof StreamReadException),
                () -> ex.getClass().getName() + ": " + ex.getMessage());
    }

    @Test
    void coercesNumericCodeToStringForEnvelope() throws Exception {
        String json = "{\"code\":1,\"message\":\"x\",\"traceId\":\"t\",\"data\":null}";
        Envelope env = mapper.readValue(json, Envelope.class);
        assertEquals("1", env.code());
    }

    @Test
    void rejectsArrayWhereObjectExpected() {
        String bad = "[{\"code\":\"OK\"}]";
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(bad, Envelope.class));
    }

    @Test
    void serializesErrorEnvelopeWithNullDataAndStableFieldOrder() throws Exception {
        Envelope body = new Envelope("INVALID_REQUEST", "请求无效", "trace-1", null);
        String json = mapper.writeValueAsString(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> asMap = mapper.readValue(json, LinkedHashMap.class);
        assertEquals(List.of("code", "message", "traceId", "data"), List.copyOf(asMap.keySet()));
        assertEquals("INVALID_REQUEST", asMap.get("code"));
        assertEquals("请求无效", asMap.get("message"));
        assertEquals("trace-1", asMap.get("traceId"));
        assertEquals(null, asMap.get("data"));
    }

    @Test
    void writesInstantAsNumericTimestampWithCurrentMapper() throws JacksonException {
        Instant instant = Instant.parse("2026-09-16T10:30:00Z");
        String json = mapper.writeValueAsString(Map.of("at", instant));
        JsonNode node = mapper.readTree(json);
        assertTrue(node.get("at").isNumber(), json);
        assertEquals(instant.getEpochSecond(), node.get("at").longValue());
    }

    @Test
    void keepsEmptyObjectAndArrayDistinct() throws Exception {
        assertEquals("{}", mapper.writeValueAsString(Map.of()));
        assertEquals("[]", mapper.writeValueAsString(List.of()));
    }

    @Test
    void numericIdsRemainJsonNumbersWhenStoredAsLong() throws Exception {
        String json = mapper.writeValueAsString(Map.of("definitionId", 42L, "revision", 7));
        JsonNode node = mapper.readTree(json);
        assertTrue(node.get("definitionId").isIntegralNumber());
        assertEquals(42L, node.get("definitionId").longValue());
        assertEquals(7, node.get("revision").intValue());
    }

    @Test
    void roundTripsNestedMapsWithoutDroppingEmptyChildren() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scenarioConfig", Map.of());
        payload.put("items", List.of());
        String json = mapper.writeValueAsString(payload);
        JsonNode node = mapper.readTree(json);
        assertTrue(node.get("scenarioConfig").isObject());
        assertEquals(0, node.get("scenarioConfig").size());
        assertTrue(node.get("items").isArray());
        assertEquals(0, node.get("items").size());
        assertEquals(List.of("scenarioConfig", "items"), fieldNames(node));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> it = node.propertyNames().iterator();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }
}
