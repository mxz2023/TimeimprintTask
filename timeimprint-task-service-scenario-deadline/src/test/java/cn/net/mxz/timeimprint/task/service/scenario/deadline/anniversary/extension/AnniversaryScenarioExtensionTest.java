package cn.net.mxz.timeimprint.task.service.scenario.deadline.anniversary.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.extension.shared.context.DefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.InitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.shared.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnniversaryScenarioExtensionTest {

    private final AnniversaryScenarioExtension extension = new AnniversaryScenarioExtension();

    @Test
    void rejectsUnknownConfigField() {
        var ctx = new DefinitionConfigValidationContext(
                "anniversary", 1, new JsonPayload(Map.of("kind", "BIRTHDAY", "extra", 1)));
        assertThrows(IllegalArgumentException.class, () -> extension.validateDefinitionConfig(ctx));
    }

    @Test
    void initialPlanSyncsSubject() {
        Instant now = Instant.parse("2026-10-14T01:00:00Z");
        var applied = assertInstanceOf(
                HandlerResult.Applied.class,
                extension.planInitialDefinition(new InitialDefinitionContext(definition(now, "{}"))));
        assertEquals("sync_subject", applied.plan().scenarioDataMutations().getFirst().mutationKey());
    }

    @Test
    void tomorrowNoticeIncludesAge() {
        Instant occurrence = Instant.parse("2026-10-13T01:00:00Z");
        String config = "{\"kind\":\"BIRTHDAY\",\"subjectName\":\"林安\",\"month\":10,\"dayOfMonth\":14,"
                + "\"localTime\":\"09:00:00\",\"startYear\":2000}";
        var result = extension.processSignal(signal(definition(occurrence, config), instance(occurrence)));
        var applied = assertInstanceOf(HandlerResult.Applied.class, result);
        String body = String.valueOf(applied.plan().actionJobIntents().getFirst().payload());
        assertTrue(body.contains("明天是林安的生日，26岁"), body);
        assertEquals("TRIGGERED", applied.plan().instanceStateTransition().toScenarioState());
    }

    private static TaskDefinitionSnapshot definition(Instant now, String config) {
        return new TaskDefinitionSnapshot(3L, "local", "anniversary", 1, "林安", "", config,
                ControlState.ACTIVE, 1L, 1L, now, now);
    }

    private static TaskInstanceSnapshot instance(Instant occurrence) {
        return new TaskInstanceSnapshot(7L, 3L, 1L, 1L, 1L, "occ", occurrence, null,
                LifecycleCategory.WAITING, "PLANNED", 1, "{}", "林安", "", 1L, null);
    }

    private static SignalProcessContext signal(TaskDefinitionSnapshot definition, TaskInstanceSnapshot instance) {
        return new SignalProcessContext(definition, instance, 1L, "calendar", "primary", 1, new JsonPayload(Map.of()));
    }
}
