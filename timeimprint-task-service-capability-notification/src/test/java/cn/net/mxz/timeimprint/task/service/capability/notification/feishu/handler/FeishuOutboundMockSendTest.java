package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.adapter.feishu.auth.CachingFeishuTokenProvider;
import cn.net.mxz.timeimprint.task.adapter.feishu.client.HttpFeishuMessageClient;
import cn.net.mxz.timeimprint.task.service.capability.notification.feishu.configuration.FeishuNotificationProperties;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F03/F04/F05 Mock 发信集成：真实 {@link FeishuImNotificationHandler} + adapter HTTP 客户端 + 本地 HTTP Mock
 * （无外网飞书租户）。
 */
class FeishuOutboundMockSendTest {

    private record Req(String path, String query, String auth, String body) {}

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<Req> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private FeishuImNotificationHandler handler;
    private volatile String messagesReply;

    @BeforeEach
    void setUp() throws IOException {
        messagesReply = "{\"code\":0,\"msg\":\"success\",\"data\":{\"message_id\":\"om_it_1\"}}";
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = ex.getRequestURI().getPath();
            requests.add(new Req(
                    path, ex.getRequestURI().getQuery(), ex.getRequestHeaders().getFirst("Authorization"), body));
            String reply = path.endsWith("/tenant_access_token/internal")
                    ? "{\"code\":0,\"msg\":\"ok\",\"tenant_access_token\":\"t-it\",\"expire\":7200}"
                    : messagesReply;
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();

        FeishuNotificationProperties props = new FeishuNotificationProperties();
        props.setAppId("cli_it");
        props.setAppSecret("secret_it");
        props.setVerificationToken("vt_it");
        props.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setTimeoutSeconds(2);
        props.setRecipientMap(Map.of("local-actor", "ou_it"));
        props.validateWhenFeishuEnabled(FeishuNotificationProperties.OUTBOUND_BUDGET_SECONDS);
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var tokens = new CachingFeishuTokenProvider(
                props.adapterProperties(), props.credentials(), http, mapper, Clock.systemUTC());
        handler = new FeishuImNotificationHandler(
                Optional.of(new HttpFeishuMessageClient(props.adapterProperties(), tokens, http, mapper)), props);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static ActionExecutionContext ctx(String scenarioKey, String recipientId) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("scenarioKey", scenarioKey);
        f.put("recipientId", recipientId);
        f.put("title", "T");
        f.put("body", "B");
        f.put("instanceRevision", 4L);
        return new ActionExecutionContext(1L, 2L, 3L, 4L, "INITIAL:key", 1, new JsonPayload(f), "tok");
    }

    @Test
    void s02SendsTokenThenInteractiveCardWithButtonsAndIdempotencyUuid() {
        var r = handler.execute(ctx("recurring_todo", "local-actor"));

        assertEquals(ActionHandlerOutcome.SUCCEEDED, r.outcome());
        assertEquals("message_id=om_it_1", r.safeSummary());
        List<Req> tokenCalls = requests.stream().filter(q -> q.path().contains("tenant_access_token")).toList();
        List<Req> sendCalls = requests.stream().filter(q -> q.path().equals("/open-apis/im/v1/messages")).toList();
        assertEquals(1, tokenCalls.size());
        assertEquals(1, sendCalls.size());
        assertEquals("receive_id_type=open_id", sendCalls.getFirst().query());
        assertEquals("Bearer t-it", sendCalls.getFirst().auth());
        JsonNode body = mapper.readTree(sendCalls.getFirst().body());
        assertEquals("interactive", body.path("msg_type").asString());
        assertEquals("ou_it", body.path("receive_id").asString());
        assertTrue(body.path("uuid").asString().length() <= 50);
        JsonNode card = mapper.readTree(body.path("content").asString());
        assertEquals(3, countButtons(card));
    }

    @Test
    void s01SendsInteractiveCardWithoutButtons() {
        var r = handler.execute(ctx("reminder", "local-actor"));
        assertEquals(ActionHandlerOutcome.SUCCEEDED, r.outcome());
        List<Req> sendCalls = requests.stream().filter(q -> q.path().equals("/open-apis/im/v1/messages")).toList();
        JsonNode body = mapper.readTree(sendCalls.getFirst().body());
        assertEquals("interactive", body.path("msg_type").asString());
        assertEquals(0, countButtons(mapper.readTree(body.path("content").asString())));
    }

    @Test
    void unmappedRecipientFailsPermanentlyWithoutAnyHttpCall() {
        var r = handler.execute(ctx("reminder", "someone-else"));
        assertEquals(ActionHandlerOutcome.PERMANENT_FAILURE, r.outcome());
        assertEquals("FEISHU_RECIPIENT_UNMAPPED", r.outcomeCode());
        assertEquals(0, requests.size());
    }

    @Test
    void feishuRejectionIsObservableAsPermanentFailure() {
        messagesReply = "{\"code\":230013,\"msg\":\"user not in app scope\"}";
        var r = handler.execute(ctx("reminder", "local-actor"));
        assertEquals(ActionHandlerOutcome.PERMANENT_FAILURE, r.outcome());
        assertEquals("FEISHU_REJECTED", r.outcomeCode());
        assertTrue(r.safeSummary().contains("230013"));
        assertTrue(!r.safeSummary().contains("secret_it") && !r.safeSummary().contains("t-it"));
    }

    private static int countButtons(JsonNode card) {
        List<JsonNode> found = new ArrayList<>();
        for (JsonNode el : card.path("body").path("elements")) {
            for (JsonNode col : el.path("columns")) {
                col.path("elements").forEach(found::add);
            }
        }
        return found.size();
    }
}
