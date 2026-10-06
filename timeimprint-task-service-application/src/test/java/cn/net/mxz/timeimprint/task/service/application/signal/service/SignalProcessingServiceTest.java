package cn.net.mxz.timeimprint.task.service.application.signal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ParticipantRecord;
import cn.net.mxz.timeimprint.task.service.extension.action.registry.DeliveryChannel;
import cn.net.mxz.timeimprint.task.service.extension.action.result.ActionExecutionMode;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ActionJobIntent;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionTarget;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Owner test: freeze SignalProcessingService public method surface for P02 refactor safety. */
class SignalProcessingServiceTest {

    @Test
    void freezesPublicMethodSurface() {
        List<String> actual = Arrays.stream(SignalProcessingService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .map(m -> m.getName() + "/" + m.getParameterCount())
                .sorted()
                .distinct()
                .collect(Collectors.toList());
        assertEquals(List.of("executeClaimedAction/2",
                "processSignal/1"), actual);
        assertFalse(actual.isEmpty());
    }

    private static final DeliveryChannel IN_APP = new DeliveryChannel(
            "IN_APP", "in_app_notification", ActionExecutionMode.LOCAL_TRANSACTIONAL, "in_app_notification");
    private static final DeliveryChannel FEISHU =
            new DeliveryChannel("FEISHU", "feishu_im_notification", ActionExecutionMode.EXTERNAL, "FEISHU");

    private static SignalProcessingService serviceWith(List<DeliveryChannel> channels) {
        return new SignalProcessingService(
                null, null, null, null, null, null, null, null, null, null, null, null, () -> channels);
    }

    private static TransitionPlan planWithNeutralIntent() {
        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("instanceId", 42L);
        fields.put("purpose", "INITIAL");
        fields.put("slotIndex", 0);
        fields.put("actionGeneration", 1);
        ActionJobIntent intent = new ActionJobIntent(
                "in_app_notification",
                1,
                "INITIAL:PENDING_EXPAND",
                "LOCAL_TRANSACTIONAL",
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                new JsonPayload(fields));
        return new TransitionPlan(
                new TransitionTarget(TransitionResourceType.INSTANCE, 42L, 1L),
                null,
                null,
                List.of(),
                List.of(intent),
                List.of(),
                List.of(),
                List.of(),
                "t");
    }

    private static List<ParticipantRecord> owner(String id) {
        return List.of(new ParticipantRecord(1L, 1L, null, "USER", id, "OWNER", "TEST"));
    }

    @Test
    void inAppOnlyKeepsLegacyActionKeyAndHandler() {
        TransitionPlan out = serviceWith(List.of(IN_APP))
                .expandRecipients(planWithNeutralIntent(), "t1", "S01", owner("alice"));
        assertEquals(1, out.actionJobIntents().size());
        ActionJobIntent a = out.actionJobIntents().getFirst();
        assertEquals("in_app_notification", a.handlerKey());
        assertEquals("LOCAL_TRANSACTIONAL", a.executionMode());
        String legacy = "INITIAL:"
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(Sha256.digestUtf8("42:INITIAL:0:1:alice:in_app_notification"));
        assertEquals(legacy, a.actionKey());
        assertEquals("IN_APP", ((JsonPayload) a.payload()).fields().get("channelKey"));
    }

    @Test
    void recipientTimesChannelExpansionUsesDistinctActionKeys() {
        TransitionPlan out = serviceWith(List.of(IN_APP, FEISHU))
                .expandRecipients(planWithNeutralIntent(), "t1", "S01", owner("alice"));
        assertEquals(2, out.actionJobIntents().size());
        ActionJobIntent inApp = out.actionJobIntents().get(0);
        ActionJobIntent feishu = out.actionJobIntents().get(1);
        assertEquals("feishu_im_notification", feishu.handlerKey());
        assertEquals("EXTERNAL", feishu.executionMode());
        assertEquals("FEISHU", ((JsonPayload) feishu.payload()).fields().get("channelKey"));
        assertNotEquals(inApp.actionKey(), feishu.actionKey());
        assertTrue(feishu.actionKey().startsWith("INITIAL:"));
        assertFalse(feishu.actionKey().contains("alice"));
        assertEquals("alice", feishu.targetId());
    }
}
