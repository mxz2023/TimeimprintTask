package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.shared.state.LifecycleCategory;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;

/** Owner test for TransitionPlan. */
class TransitionPlanTest {

    private static final class StubPayload implements ScenarioMutationPayload {}

    @Test
    void freezesRecordComponents() {
        assertTrue(TransitionPlan.class.isRecord());
        List<String> actual = Arrays.stream(TransitionPlan.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(
                List.of(
                        "target",
                        "definitionControlTransition",
                        "instanceStateTransition",
                        "participantChanges",
                        "actionJobIntents",
                        "triggerBindingChanges",
                        "plannedSignalIntents",
                        "scenarioDataMutations",
                        "auditSummary"),
                actual);
    }

    @Test
    void emptyDeclarationWhenNoSideEffects() {
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, 1L, 1L);
        var empty = new TransitionPlan(target, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), "");
        assertTrue(empty.isEmptyDeclaration());
    }

    @Test
    void populatedPlanIsNotEmpty() {
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, 1L, 1L);
        var plan = new TransitionPlan(
                target,
                null,
                new InstanceStateTransition(
                        LifecycleCategory.WAITING, LifecycleCategory.TERMINAL, "PLANNED", "TRIGGERED"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ScenarioDataMutation("s01", "m1", 1, new StubPayload())),
                "audit");
        assertFalse(plan.isEmptyDeclaration());
    }
}
