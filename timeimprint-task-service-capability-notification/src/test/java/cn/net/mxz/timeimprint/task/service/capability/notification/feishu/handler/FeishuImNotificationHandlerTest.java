package cn.net.mxz.timeimprint.task.service.capability.notification.feishu.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuApiException;
import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuApiException.Category;
import cn.net.mxz.timeimprint.task.adapter.feishu.client.FeishuMessageClient;
import cn.net.mxz.timeimprint.task.service.capability.notification.feishu.configuration.FeishuNotificationProperties;
import cn.net.mxz.timeimprint.task.service.extension.action.context.ActionExecutionContext;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionResult;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionHandlerOutcome;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** F03/F04/F05：出站卡片、uuid 与受理号、缺映射与失败分类。 */
class FeishuImNotificationHandlerTest {

    private static final String ACTION_KEY = "INITIAL:" + "A".repeat(43);

    private record Sent(String openId, String card, String uuid) {}

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<Sent> sent = new ArrayList<>();
    private FeishuNotificationProperties properties;
    private FeishuMessageClient ok;

    @BeforeEach
    void setUp() {
        properties = new FeishuNotificationProperties();
        properties.setRecipientMap(Map.of("local-actor", "ou_local"));
        ok = (openId, card, uuid) -> {
            sent.add(new Sent(openId, card, uuid));
            return "om_accepted";
        };
    }

    private FeishuImNotificationHandler handler(FeishuMessageClient client) {
        return new FeishuImNotificationHandler(Optional.ofNullable(client), properties);
    }

    private static ActionExecutionContext ctx(Map<String, Object> fields, Long instanceId) {
        return new ActionExecutionContext(
                9L, 3L, instanceId, 5L, ACTION_KEY, 1, new JsonPayload(new LinkedHashMap<>(fields)), "tok");
    }

    private static Map<String, Object> fields(String scenarioKey, String recipientId) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("scenarioKey", scenarioKey);
        f.put("recipientId", recipientId);
        f.put("title", "喝水");
        f.put("body", "该喝水了");
        f.put("instanceRevision", 7L);
        return f;
    }

    private static List<JsonNode> buttons(JsonNode card) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode el : card.path("body").path("elements")) {
            for (JsonNode col : el.path("columns")) {
                for (JsonNode inner : col.path("elements")) {
                    if ("button".equals(inner.path("tag").asString())) {
                        out.add(inner);
                    }
                }
            }
        }
        return out;
    }

    @Test
    void registersExternalHandlerKeyWithinLeaseBudget() {
        var h = handler(ok);
        assertEquals("feishu_im_notification", h.registrationKey().handlerKey());
        assertEquals(1, h.registrationKey().actionSchemaVersion());
        assertEquals(ActionExecutionMode.EXTERNAL, h.executionMode());
        assertEquals(10, h.timeoutSeconds());
    }

    @Test
    void s02CardCarriesThreeButtonsWithCommandValues() {
        ActionExecutionResult r = handler(ok).execute(ctx(fields("recurring_todo", "local-actor"), 11L));

        assertEquals(ActionHandlerOutcome.SUCCEEDED, r.outcome());
        assertEquals("FEISHU_MESSAGE_SENT", r.outcomeCode());
        assertEquals("message_id=om_accepted", r.safeSummary());
        assertEquals(1, sent.size());
        assertEquals("ou_local", sent.getFirst().openId());
        JsonNode card = mapper.readTree(sent.getFirst().card());
        assertEquals("喝水", card.path("header").path("title").path("content").asString());
        List<JsonNode> buttons = buttons(card);
        assertEquals(3, buttons.size());
        assertEquals(
                List.of("complete", "skip", "snooze"),
                buttons.stream()
                        .map(b -> b.path("behaviors").get(0).path("value").path("commandKey").asString())
                        .toList());
        JsonNode value = buttons.getFirst().path("behaviors").get(0).path("value");
        assertEquals(11L, value.path("instanceId").asLong());
        assertEquals(3L, value.path("definitionId").asLong());
        assertEquals(7L, value.path("revision").asLong());
        assertEquals("callback", buttons.getFirst().path("behaviors").get(0).path("type").asString());
    }

    @Test
    void s01CardIsDisplayOnlyWithoutButtons() {
        Map<String, Object> f = fields("reminder", "local-actor");
        f.remove("instanceRevision");
        ActionExecutionResult r = handler(ok).execute(ctx(f, 11L));

        assertEquals(ActionHandlerOutcome.SUCCEEDED, r.outcome());
        JsonNode card = mapper.readTree(sent.getFirst().card());
        assertTrue(buttons(card).isEmpty());
        assertEquals("该喝水了", card.path("body").path("elements").get(0).path("content").asString());
        assertTrue(!sent.getFirst().card().contains("commandKey"));
    }

    @Test
    void uuidDerivedFromActionKeyIsStableAndAtMostFifty() {
        handler(ok).execute(ctx(fields("reminder", "local-actor"), 11L));
        handler(ok).execute(ctx(fields("reminder", "local-actor"), 11L));
        String uuid = sent.getFirst().uuid();
        assertNotNull(uuid);
        assertTrue(uuid.length() <= 50 && !uuid.isBlank());
        assertEquals(uuid, sent.get(1).uuid());
        assertEquals(FeishuImNotificationHandler.uuidFor(ACTION_KEY), uuid);
        assertTrue(!uuid.equals(FeishuImNotificationHandler.uuidFor(ACTION_KEY + "x")));
    }

    @Test
    void missingRecipientMapIsPermanentFailureAndNothingSent() {
        properties.setRecipientMap(Map.of());
        ActionExecutionResult r = handler(ok).execute(ctx(fields("recurring_todo", "local-actor"), 11L));
        assertEquals(ActionHandlerOutcome.PERMANENT_FAILURE, r.outcome());
        assertEquals("FEISHU_RECIPIENT_UNMAPPED", r.outcomeCode());
        assertTrue(sent.isEmpty());

        Map<String, Object> noId = fields("reminder", "x");
        noId.remove("recipientId");
        ActionExecutionResult noRecipient = handler(ok).execute(ctx(noId, 11L));
        assertEquals("FEISHU_RECIPIENT_UNMAPPED", noRecipient.outcomeCode());
    }

    @Test
    void actionableCardWithoutRevisionOrInstanceIsPermanentFailure() {
        Map<String, Object> f = fields("recurring_todo", "local-actor");
        f.remove("instanceRevision");
        assertEquals("FEISHU_CARD_INVALID", handler(ok).execute(ctx(f, 11L)).outcomeCode());
        assertEquals(
                "FEISHU_CARD_INVALID",
                handler(ok).execute(ctx(fields("recurring_todo", "local-actor"), null)).outcomeCode());
        assertTrue(sent.isEmpty());
    }

    @Test
    void absentClientIsPermanentFailure() {
        ActionExecutionResult r = handler(null).execute(ctx(fields("reminder", "local-actor"), 11L));
        assertEquals(ActionHandlerOutcome.PERMANENT_FAILURE, r.outcome());
        assertEquals("FEISHU_NOT_CONFIGURED", r.outcomeCode());
    }

    @Test
    void sendFailuresMapToOutcomeByCategory() {
        assertEquals(ActionHandlerOutcome.RETRYABLE_FAILURE, failWith(Category.RETRYABLE).outcome());
        assertEquals(ActionHandlerOutcome.PERMANENT_FAILURE, failWith(Category.PERMANENT).outcome());
        ActionExecutionResult unknown = failWith(Category.UNKNOWN);
        assertEquals(ActionHandlerOutcome.UNKNOWN, unknown.outcome());
        assertEquals("FEISHU_UNKNOWN", unknown.outcomeCode());
        assertEquals("boom", unknown.safeSummary());

        FeishuMessageClient broken = (o, c, u) -> {
            throw new IllegalStateException("secret-detail");
        };
        ActionExecutionResult unexpected =
                handler(broken).execute(ctx(fields("reminder", "local-actor"), 11L));
        assertEquals(ActionHandlerOutcome.UNKNOWN, unexpected.outcome());
        assertEquals("FEISHU_UNEXPECTED", unexpected.outcomeCode());
        assertEquals("IllegalStateException", unexpected.safeSummary());
    }

    @Test
    void longFailureSummaryIsTruncated() {
        FeishuMessageClient c = (o, card, u) -> {
            throw new FeishuApiException(Category.PERMANENT, 1, "x".repeat(900), null);
        };
        var r = handler(c).execute(ctx(fields("reminder", "local-actor"), 11L));
        assertEquals(FeishuImNotificationHandler.MAX_SUMMARY_LENGTH, r.safeSummary().length());
    }

    private ActionExecutionResult failWith(Category category) {
        FeishuMessageClient c = (o, card, u) -> {
            throw new FeishuApiException(category, -1, "boom", null);
        };
        return handler(c).execute(ctx(fields("reminder", "local-actor"), 11L));
    }
}
