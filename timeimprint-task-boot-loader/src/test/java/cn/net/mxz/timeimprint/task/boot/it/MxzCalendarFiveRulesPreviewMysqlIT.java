package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.MxzTimeImprintTaskApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * CAL-01—CAL-05：E02 preview 五规则矩阵（真 HTTP + 真解析链路，不落定义）。
 */
@SpringBootTest(
        classes = MxzTimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class MxzCalendarFiveRulesPreviewMysqlIT {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        registry.add("spring.datasource.username", () -> "tit");
        registry.add("spring.datasource.password", () -> "tit_local");
        registry.add("timeimprint.local.tenant-id", () -> "local-tenant");
        registry.add("timeimprint.local.actor-id", () -> "local-actor");
    }

    @Test
    void previewFiveCalendarRules() throws Exception {
        LocalDate start = LocalDate.now().plusDays(1);
        assertPreviewOk(Map.of(
                "type", "ONCE",
                "localDate", start.toString(),
                "localTime", "10:00:00",
                "zoneId", "Asia/Shanghai"));
        assertPreviewOk(Map.of(
                "type", "DAILY",
                "startDate", start.toString(),
                "localTime", "09:00:00",
                "zoneId", "Asia/Shanghai"));
        assertPreviewOk(Map.of(
                "type", "WEEKLY",
                "startDate", start.toString(),
                "weekday", start.getDayOfWeek().getValue(),
                "localTime", "09:00:00",
                "zoneId", "Asia/Shanghai"));
        assertPreviewOk(Map.of(
                "type", "MONTHLY",
                "startDate", start.withDayOfMonth(1).toString(),
                "dayOfMonth", Math.min(28, start.getDayOfMonth()),
                "localTime", "09:00:00",
                "zoneId", "Asia/Shanghai"));
        assertPreviewOk(Map.of(
                "type", "EVERY_N_DAYS",
                "startDate", start.toString(),
                "intervalDays", 3,
                "localTime", "09:00:00",
                "zoneId", "Asia/Shanghai"));
    }

    private void assertPreviewOk(Map<String, Object> config) throws Exception {
        Map<String, Object> body = Map.of(
                "scenarioKey", "reminder",
                "scenarioSchemaVersion", 1,
                "scenarioConfig", Map.of(),
                "after", java.time.Instant.now().minusSeconds(60).toString(),
                "limit", 3,
                "triggerBindings",
                List.of(Map.of(
                        "bindingKey", "primary",
                        "providerKey", "calendar",
                        "schemaVersion", 1,
                        "config", config)));
        JsonNode preview = post("/api/v1/task-definitions/preview", body);
        assertEquals("OK", preview.path("code").asText(), "preview " + config.get("type") + ": " + preview);
        assertTrue(
                preview.path("data").path("occurrences").size() >= 1,
                "expected occurrences for " + config.get("type") + ": " + preview);
    }

    private JsonNode post(String path, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body());
    }
}
