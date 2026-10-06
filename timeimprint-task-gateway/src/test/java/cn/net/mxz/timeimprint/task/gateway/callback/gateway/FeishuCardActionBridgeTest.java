package cn.net.mxz.timeimprint.task.gateway.callback.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.net.mxz.timeimprint.task.adapter.feishu.callback.DefaultFeishuCardActionVerifier;
import cn.net.mxz.timeimprint.task.common.time.BusinessClock;
import cn.net.mxz.timeimprint.task.domain.instance.request.InstanceCommandRequest;
import cn.net.mxz.timeimprint.task.domain.instance.view.TaskInstanceView;
import cn.net.mxz.timeimprint.task.domain.shared.view.CommandMetadataView;
import cn.net.mxz.timeimprint.task.domain.shared.view.CommandResultView;
import cn.net.mxz.timeimprint.task.domain.shared.view.Page;
import cn.net.mxz.timeimprint.task.domain.shared.view.ParticipantView;
import cn.net.mxz.timeimprint.task.domain.shared.view.ScenarioMetadataView;
import cn.net.mxz.timeimprint.task.gateway.shared.gateway.TaskGateway;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FeishuCardActionBridgeTest {

    private static final String TOKEN = "tok";
    private static final String OPEN_ID = "ou_alice";
    private static final Instant NOW = Instant.parse("2026-10-06T02:00:00Z");
    private static final String REQUEST_ID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private TaskGateway gateway;
    private FeishuCardActionBridge bridge;

    @BeforeEach
    void setUp() {
        gateway = mock(TaskGateway.class);
        BusinessClock clock = () -> NOW;
        var verifier = new DefaultFeishuCardActionVerifier(TOKEN, null, mapper);
        var settings = new FeishuInboundSettings(Map.of("local-actor", OPEN_ID));
        bridge = new FeishuCardActionBridge(verifier, settings, gateway, clock, mapper);
        when(gateway.getInstance(7L)).thenReturn(instance("7", "100", "recurring_todo", "local-actor"));
        when(gateway.listScenarios(any(), anyInt())).thenReturn(scenarios());
        when(gateway.executeInstanceCommand(anyLong(), any(), any())).thenReturn(result(true));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TaskInstanceView instance(String id, String defId, String scenarioKey, String participant) {
        return new TaskInstanceView(
                id,
                defId,
                scenarioKey,
                1,
                "ACTIVE",
                "PENDING",
                3,
                "title",
                "desc",
                List.of(new ParticipantView("USER", participant, "OWNER", "DEFINITION")),
                null,
                null,
                List.of("complete"),
                null,
                null,
                null,
                null,
                null);
    }

    private static Page<ScenarioMetadataView> scenarios() {
        return new Page<>(
                List.of(
                        new ScenarioMetadataView("reminder", "通用提醒", "1", List.of(1), List.of(), List.of(), List.of()),
                        new ScenarioMetadataView(
                                "recurring_todo",
                                "周期待办",
                                "1",
                                List.of(1),
                                List.of(),
                                List.of(
                                        new CommandMetadataView("complete", List.of(1)),
                                        new CommandMetadataView("skip", List.of(1)),
                                        new CommandMetadataView("snooze", List.of(1))),
                                List.of())),
                null,
                false,
                NOW.toString());
    }

    private CommandResultView result(boolean changed) {
        return new CommandResultView("INSTANCE", "7", 4, changed, null, null);
    }

    private byte[] event(String eventId, String openId, String commandKey, Object createTimeMillis) {
        return eventWithValue(
                eventId,
                openId,
                "{\"commandKey\":\"" + commandKey + "\",\"instanceId\":7,\"definitionId\":100,\"revision\":3}",
                createTimeMillis);
    }

    private byte[] eventWithValue(String eventId, String openId, String valueJson, Object createTimeMillis) {
        String createTime = createTimeMillis == null ? "" : ",\"create_time\":\"" + createTimeMillis + "\"";
        return ("{\"schema\":\"2.0\",\"header\":{\"event_id\":\"" + eventId + "\",\"token\":\"" + TOKEN
                        + "\",\"event_type\":\"card.action.trigger\"" + createTime
                        + "},\"event\":{\"operator\":{\"open_id\":\"" + openId
                        + "\"},\"action\":{\"tag\":\"button\",\"value\":" + valueJson + "}}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private FeishuCardActionBridge.Reply send(byte[] body) {
        return bridge.handle(null, null, null, body);
    }

    private static void assertToast(FeishuCardActionBridge.Reply reply, String type) {
        assertEquals(200, reply.httpStatus());
        assertEquals(type, reply.body().path("toast").path("type").asString());
        assertTrue(reply.body().path("toast").path("content").asString().length() > 0);
    }

    private InstanceCommandRequest executedRequest(String commandKey) {
        ArgumentCaptor<InstanceCommandRequest> captor = ArgumentCaptor.forClass(InstanceCommandRequest.class);
        verify(gateway).executeInstanceCommand(eq(7L), eq(commandKey), captor.capture());
        return captor.getValue();
    }

    // ── verification / protocol ──────────────────────────────────────────────

    @Test
    void forgedTokenIsRejectedWithoutTouchingGateway() {
        byte[] forged = new String(event("e1", OPEN_ID, "complete", null), StandardCharsets.UTF_8)
                .replace(TOKEN, "forged")
                .getBytes(StandardCharsets.UTF_8);
        var reply = send(forged);
        assertEquals(401, reply.httpStatus());
        assertEquals("invalid signature", reply.body().path("error").asString());
        verifyNoInteractions(gateway);
    }

    @Test
    void urlVerificationEchoesChallenge() {
        var reply = send(("{\"challenge\":\"abc123\",\"token\":\"" + TOKEN + "\",\"type\":\"url_verification\"}")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(200, reply.httpStatus());
        assertEquals("abc123", reply.body().path("challenge").asString());
        verifyNoInteractions(gateway);
    }

    @Test
    void otherEventTypesAreAcknowledgedAndIgnored() {
        var reply = send(("{\"schema\":\"2.0\",\"header\":{\"event_id\":\"e\",\"token\":\"" + TOKEN
                        + "\",\"event_type\":\"im.message.receive_v1\"},\"event\":{}}")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(200, reply.httpStatus());
        assertTrue(reply.body().isObject() && reply.body().isEmpty());
        verifyNoInteractions(gateway);
    }

    @Test
    void requestIdIsStableValidUuidPerEvent() {
        String a = FeishuCardActionBridge.requestIdFor("event-1");
        assertEquals(a, FeishuCardActionBridge.requestIdFor("event-1"));
        assertNotEquals(a, FeishuCardActionBridge.requestIdFor("event-2"));
        assertTrue(a.matches(REQUEST_ID_REGEX), a);
    }

    // ── command mapping (F06 / F07 / F08) ────────────────────────────────────

    @Test
    void completeMapsToPlatformCompleteWithExpectedRevision() {
        var reply = send(event("evt-complete", OPEN_ID, "complete", null));
        assertToast(reply, "success");
        InstanceCommandRequest req = executedRequest("complete");
        assertEquals(FeishuCardActionBridge.requestIdFor("evt-complete"), req.requestId());
        assertEquals(3, req.expectedRevision());
        assertEquals(1, req.commandSchemaVersion());
        assertTrue(req.payload().isObject() && req.payload().isEmpty());
    }

    @Test
    void skipUsesFixedReason() {
        var reply = send(event("evt-skip", OPEN_ID, "skip", null));
        assertToast(reply, "success");
        InstanceCommandRequest req = executedRequest("skip");
        assertEquals("飞书卡片跳过", req.payload().path("reason").asString());
        assertEquals(1, req.payload().size());
    }

    @Test
    void snoozeIsEventTimePlusOneHourTruncatedToSeconds() {
        long created = NOW.minusSeconds(30).toEpochMilli() + 789;
        var reply = send(event("evt-snooze", OPEN_ID, "snooze", created));
        assertToast(reply, "success");
        InstanceCommandRequest req = executedRequest("snooze");
        assertEquals("2026-10-06T02:59:30Z", req.payload().path("snoozeUntil").asString());
    }

    @Test
    void snoozeFallsBackToBusinessClockWhenEventTimeMissingOrSkewed() {
        send(event("e-a", OPEN_ID, "snooze", null));
        send(event("e-b", OPEN_ID, "snooze", NOW.minusSeconds(3600).toEpochMilli()));
        send(event("e-c", OPEN_ID, "snooze", "not-a-number"));
        ArgumentCaptor<InstanceCommandRequest> captor = ArgumentCaptor.forClass(InstanceCommandRequest.class);
        verify(gateway, times(3)).executeInstanceCommand(eq(7L), eq("snooze"), captor.capture());
        for (InstanceCommandRequest r : captor.getAllValues()) {
            assertEquals("2026-10-06T03:00:00Z", r.payload().path("snoozeUntil").asString());
        }
    }

    @Test
    void replayOfSameEventIsForwardedWithIdenticalRequestAndPayload() {
        long created = NOW.toEpochMilli();
        send(event("evt-replay", OPEN_ID, "snooze", created));
        send(event("evt-replay", OPEN_ID, "snooze", created));
        ArgumentCaptor<InstanceCommandRequest> captor = ArgumentCaptor.forClass(InstanceCommandRequest.class);
        verify(gateway, times(2)).executeInstanceCommand(eq(7L), eq("snooze"), captor.capture());
        assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
    }

    @Test
    void valueGivenAsJsonStringIsAccepted() {
        String valueAsString = "\"{\\\"commandKey\\\":\\\"complete\\\",\\\"instanceId\\\":\\\"7\\\","
                + "\\\"definitionId\\\":100,\\\"revision\\\":\\\"3\\\"}\"";
        var reply = send(eventWithValue("evt-str", OPEN_ID, valueAsString, null));
        assertToast(reply, "success");
        assertEquals(3, executedRequest("complete").expectedRevision());
    }

    // ── authorization / scenario guard ───────────────────────────────────────

    @Test
    void unmappedOperatorIsRejected() {
        assertToast(send(event("e1", "ou_stranger", "complete", null)), "error");
        verify(gateway, never()).getInstance(anyLong());
        verify(gateway, never()).executeInstanceCommand(anyLong(), any(), any());
    }

    @Test
    void mappedOperatorWhoIsNotAParticipantIsRejected() {
        when(gateway.getInstance(7L)).thenReturn(instance("7", "100", "recurring_todo", "someone-else"));
        assertToast(send(event("e1", OPEN_ID, "complete", null)), "error");
        verify(gateway, never()).executeInstanceCommand(anyLong(), any(), any());
    }

    @Test
    void scenarioWithoutInstanceCommandsIsRejected() {
        when(gateway.getInstance(7L)).thenReturn(instance("7", "100", "reminder", "local-actor"));
        var reply = send(event("e1", OPEN_ID, "complete", null));
        assertToast(reply, "error");
        verify(gateway, never()).executeInstanceCommand(anyLong(), any(), any());
    }

    @Test
    void scenarioLookupFollowsPaging() {
        var first = new Page<>(
                List.of(new ScenarioMetadataView("reminder", "r", "1", List.of(1), List.of(), List.of(), List.of())),
                "reminder",
                true,
                NOW.toString());
        when(gateway.listScenarios(null, 100)).thenReturn(first);
        when(gateway.listScenarios("reminder", 100)).thenReturn(scenarios());
        assertToast(send(event("e1", OPEN_ID, "complete", null)), "success");
    }

    @Test
    void definitionMismatchIsRejected() {
        when(gateway.getInstance(7L)).thenReturn(instance("7", "999", "recurring_todo", "local-actor"));
        assertToast(send(event("e1", OPEN_ID, "complete", null)), "error");
        verify(gateway, never()).executeInstanceCommand(anyLong(), any(), any());
    }

    @Test
    void invalidCardParametersAreRejected() {
        assertToast(send(event("e1", OPEN_ID, "delete", null)), "error");
        assertToast(send(eventWithValue("e2", OPEN_ID, "{\"commandKey\":\"complete\"}", null)), "error");
        assertToast(
                send(eventWithValue(
                        "e3",
                        OPEN_ID,
                        "{\"commandKey\":\"complete\",\"instanceId\":7,\"definitionId\":100,\"revision\":-1}",
                        null)),
                "error");
        assertToast(send(eventWithValue("e4", OPEN_ID, "\"not json\"", null)), "error");
        assertToast(send(event("", OPEN_ID, "complete", null)), "error");
        verify(gateway, never()).executeInstanceCommand(anyLong(), any(), any());
    }

    // ── result mapping (F09) ─────────────────────────────────────────────────

    @Test
    void unchangedResultIsInfoToast() {
        when(gateway.executeInstanceCommand(anyLong(), any(), any())).thenReturn(result(false));
        assertToast(send(event("e1", OPEN_ID, "complete", null)), "info");
    }

    @Test
    void revisionConflictIsWarningToastNotSuccess() {
        doThrow(new ApplicationException("REVISION_CONFLICT", "expectedRevision=3 current=5"))
                .when(gateway)
                .executeInstanceCommand(anyLong(), any(), any());
        var reply = send(event("e1", OPEN_ID, "complete", null));
        assertToast(reply, "warning");
        assertTrue(reply.body().path("toast").path("content").asString().contains("过期"));
    }

    @Test
    void applicationErrorsMapToToasts() {
        Map<String, String> expected = Map.of(
                "STATE_CONFLICT", "warning",
                "INVALID_REQUEST", "warning",
                "IDEMPOTENCY_CONFLICT", "info",
                "RESOURCE_NOT_FOUND", "error",
                "RETRY_LATER", "warning",
                "SOMETHING_ELSE", "error");
        expected.forEach((code, type) -> {
            doThrow(new ApplicationException(code, "msg-" + code))
                    .when(gateway)
                    .executeInstanceCommand(anyLong(), any(), any());
            var reply = send(event("e-" + code, OPEN_ID, "complete", null));
            assertToast(reply, type);
        });
        doThrow(new ApplicationException("STATE_CONFLICT", "snooze 要求场景状态为 PENDING"))
                .when(gateway)
                .executeInstanceCommand(anyLong(), any(), any());
        JsonNode body = send(event("e-msg", OPEN_ID, "snooze", null)).body();
        assertEquals("snooze 要求场景状态为 PENDING", body.path("toast").path("content").asString());
    }

    @Test
    void missingInstanceAndUnexpectedFailureBecomeErrorToasts() {
        doThrow(new ApplicationException("RESOURCE_NOT_FOUND", "instance")).when(gateway).getInstance(7L);
        assertToast(send(event("e1", OPEN_ID, "complete", null)), "error");

        doReturn(instance("7", "100", "recurring_todo", "local-actor")).when(gateway).getInstance(7L);
        doThrow(new IllegalStateException("boom"))
                .when(gateway)
                .executeInstanceCommand(anyLong(), any(), any());
        var reply = send(event("e2", OPEN_ID, "complete", null));
        assertToast(reply, "error");
        assertTrue(!reply.body().toString().contains("boom"), "internal detail must not leak");
    }

    @Test
    void bodyWithoutTokenIsUnauthorized() {
        assertEquals(401, send("{}".getBytes(StandardCharsets.UTF_8)).httpStatus());
        assertEquals(401, send("garbage".getBytes(StandardCharsets.UTF_8)).httpStatus());
        verifyNoInteractions(gateway);
    }
}
