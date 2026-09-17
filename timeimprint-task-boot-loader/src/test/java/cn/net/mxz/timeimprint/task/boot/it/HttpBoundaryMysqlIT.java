package cn.net.mxz.timeimprint.task.boot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.boot.bootstrap.TimeImprintTaskApplication;
import cn.net.mxz.timeimprint.task.web.shared.filter.RequestBodySizeFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 07 §5 公共 HTTP 边界：未知路径/错误方法/媒体类型/空体/畸形 JSON/64KiB/未捕获异常；
 * 统一信封 + traceId；错误体不泄露 SQL/堆栈/凭据；Actuator 仅 health；local 忽略 debug 身份头。
 */
@SpringBootTest(
        classes = TimeImprintTaskApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("mysql-it")
class HttpBoundaryMysqlIT {

    private static final Set<String> ENVELOPE = Set.of("code", "message", "traceId", "data");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC");
        r.add("spring.datasource.username", () -> "tit");
        r.add("spring.datasource.password", () -> "tit_local");
        r.add("timeimprint.local.tenant-id", () -> "local-tenant");
        r.add("timeimprint.local.actor-id", () -> "local-actor");
        r.add("server.address", () -> "127.0.0.1");
        r.add("timeimprint.http-boundary.probe", () -> "true");
        r.add("spring.task.scheduling.enabled", () -> "false");
    }

    @Test
    void unknownPathWrongMethodMediaEmptyMalformedSizeAndProbe() throws Exception {
        // 未知路径
        Envelope unknown = exchange(
                HttpRequest.newBuilder(uri("/api/v1/definitely-not-an-endpoint"))
                        .header("Accept", "application/json")
                        .GET()
                        .build());
        assertEnvelope(unknown, 404, "RESOURCE_NOT_FOUND");

        // 错误方法（E01 仅 GET）
        Envelope badMethod = exchange(
                HttpRequest.newBuilder(uri("/api/v1/task-scenarios"))
                        .header("Accept", "application/json")
                        .DELETE()
                        .build());
        assertEnvelope(badMethod, 400, "INVALID_REQUEST");

        // 不支持的媒体类型
        Envelope media = exchange(
                HttpRequest.newBuilder(uri("/api/v1/task-definitions/preview"))
                        .header("Content-Type", "text/plain")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("x"))
                        .build());
        assertEnvelope(media, 415, "UNSUPPORTED_MEDIA_TYPE");

        // 空体
        Envelope empty = exchange(
                HttpRequest.newBuilder(uri("/api/v1/task-definitions"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build());
        assertEnvelope(empty, 400, "INVALID_REQUEST");

        // 畸形 JSON
        Envelope malformed = exchange(
                HttpRequest.newBuilder(uri("/api/v1/task-definitions"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{not-json"))
                        .build());
        assertEnvelope(malformed, 400, "INVALID_REQUEST");

        // 重复 JSON 键
        String dup = """
                {"requestId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","requestId":"ffffffff-0000-1111-2222-333333333333",
                "scenarioKey":"reminder","scenarioSchemaVersion":1,"title":"x","description":"",
                "scenarioConfig":{},"participants":[],"triggerBindings":[]}
                """;
        Envelope duplicate = exchange(
                HttpRequest.newBuilder(uri("/api/v1/task-definitions"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(dup))
                        .build());
        assertEnvelope(duplicate, 400, "INVALID_REQUEST");

        // 未知顶层字段
        String unknownField = """
                {"requestId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","scenarioKey":"reminder",
                "scenarioSchemaVersion":1,"title":"x","description":"","scenarioConfig":{},
                "participants":[],"triggerBindings":[],"totallyUnknownField":true}
                """;
        Envelope unknownProp = exchange(
                HttpRequest.newBuilder(uri("/api/v1/task-definitions"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(unknownField))
                        .build());
        assertEnvelope(unknownProp, 400, "INVALID_REQUEST");

        // 64KiB+1
        byte[] huge = ("\"" + "x".repeat(RequestBodySizeFilter.MAX_BODY_BYTES) + "\"").getBytes(StandardCharsets.UTF_8);
        assertTrue(huge.length > RequestBodySizeFilter.MAX_BODY_BYTES);
        Envelope tooLarge = exchange(
                HttpRequest.newBuilder(uri("/api/v1/task-definitions"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(huge))
                        .build());
        assertEnvelope(tooLarge, 413, "REQUEST_TOO_LARGE");

        // 未捕获异常 → INTERNAL_ERROR，消息脱敏
        Envelope boom = exchange(
                HttpRequest.newBuilder(uri("/api/v1/_http-boundary-probe/boom"))
                        .header("Accept", "application/json")
                        .GET()
                        .build());
        assertEnvelope(boom, 500, "INTERNAL_ERROR");
        assertEquals("服务内部错误，请稍后重试；响应不含内部细节", boom.body.path("message").asText());
        String raw = boom.rawBody.toLowerCase();
        assertFalse(raw.contains("probe_secret"));
        assertFalse(raw.contains("jdbc:"));
        assertFalse(raw.contains("password"));
        assertFalse(raw.contains("illegalstateexception"));
        assertFalse(raw.contains("\tat "));

        // Actuator：仅 health 暴露
        Envelope env = exchange(
                HttpRequest.newBuilder(uri("/actuator/env")).GET().build());
        assertTrue(env.status == 404 || "RESOURCE_NOT_FOUND".equals(env.body.path("code").asText()), env.rawBody);
        Envelope beans = exchange(
                HttpRequest.newBuilder(uri("/actuator/beans")).GET().build());
        assertTrue(beans.status == 404 || "RESOURCE_NOT_FOUND".equals(beans.body.path("code").asText()), beans.rawBody);
        Envelope live = exchange(
                HttpRequest.newBuilder(uri("/actuator/health/liveness")).GET().build());
        assertEquals(200, live.status, live.rawBody);

        // local：debug 身份头不得覆盖固定 Actor（与 A14 一致；此处只断言请求仍被处理且信封正常）
        Envelope scenarios = exchange(
                HttpRequest.newBuilder(uri("/api/v1/task-scenarios"))
                        .header("Accept", "application/json")
                        .header("X-Debug-Actor-Id", "should-be-ignored")
                        .header("X-Debug-Tenant-Id", "evil-tenant")
                        .GET()
                        .build());
        assertEnvelope(scenarios, 200, "OK");
        assertTrue(scenarios.body.path("data").path("items").isArray());
    }

    private void assertEnvelope(Envelope env, int status, String code) {
        assertEquals(status, env.status, env.rawBody);
        assertEquals(code, env.body.path("code").asText(), env.rawBody);
        assertNotNull(env.body.path("traceId").asText(null), env.rawBody);
        assertFalse(env.body.path("traceId").asText().isBlank());
        Set<String> fields = new java.util.HashSet<>();
        env.body.propertyNames().forEach(fields::add);
        assertEquals(ENVELOPE, fields, env.rawBody);
        assertTrue(env.body.has("data"));
        if (!"OK".equals(code)) {
            assertTrue(env.body.path("data").isNull() || env.body.path("data").isMissingNode(), env.rawBody);
        }
        String lower = env.rawBody.toLowerCase();
        assertFalse(lower.contains("executiontoken"));
        assertFalse(lower.contains("leaseowner"));
        assertFalse(lower.contains("leaseuntil"));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private Envelope exchange(HttpRequest req) throws Exception {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode body;
        try {
            body = objectMapper.readTree(resp.body().isBlank() ? "{}" : resp.body());
        } catch (Exception e) {
            body = objectMapper.createObjectNode().put("parseError", resp.body());
        }
        return new Envelope(resp.statusCode(), body, resp.body());
    }

    private record Envelope(int status, JsonNode body, String rawBody) {}
}
