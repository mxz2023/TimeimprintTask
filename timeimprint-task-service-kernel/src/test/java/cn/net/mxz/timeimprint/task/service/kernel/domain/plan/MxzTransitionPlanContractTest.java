package cn.net.mxz.timeimprint.task.service.kernel.domain.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioMutationPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.revision.MxzRevisions;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.ControlState;
import cn.net.mxz.timeimprint.task.service.kernel.domain.state.LifecycleCategory;
import org.junit.jupiter.api.Test;

class MxzTransitionPlanContractTest {

    private static final class StubPayload implements ScenarioMutationPayload {}

    @Test
    void transitionPlanExposesSevenDeclarationCategories() throws NoSuchMethodException {
        var record = TransitionPlan.class.getRecordComponents();
        assertEquals(9, record.length);
        assertEquals("target", record[0].getName());
        assertEquals("definitionControlTransition", record[1].getName());
        assertEquals("instanceStateTransition", record[2].getName());
        assertEquals("participantChanges", record[3].getName());
        assertEquals("actionJobIntents", record[4].getName());
        assertEquals("triggerBindingChanges", record[5].getName());
        assertEquals("plannedSignalIntents", record[6].getName());
        assertEquals("scenarioDataMutations", record[7].getName());
        assertEquals("auditSummary", record[8].getName());
        assertEquals(TransitionPlan.class.getMethod("isEmptyDeclaration").getReturnType(), boolean.class);
    }

    @Test
    void populatedPlanIsNotEmpty() {
        var target = new TransitionTarget(TransitionResourceType.INSTANCE, 1L, 1L);
        var plan = new TransitionPlan(
                target,
                null,
                new InstanceStateTransition(
                        LifecycleCategory.WAITING,
                        LifecycleCategory.TERMINAL,
                        "PLANNED",
                        "TRIGGERED"),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(new ScenarioDataMutation("s01", "m1", 1, new StubPayload())),
                "audit");
        assertFalse(plan.isEmptyDeclaration());
    }

    @Test
    void revisionHelpersSupportInitialFromZero() {
        assertEquals(0L, MxzRevisions.validateTransitionFrom(0L));
        assertTrue(MxzRevisions.isInitialCreation(0L, 1L));
        assertEquals(2L, MxzRevisions.nextAfter(1L));
    }

    @Test
    void controlAndLifecycleEnumsMatchPlatformContract() {
        assertEquals(3, ControlState.values().length);
        assertEquals(3, LifecycleCategory.values().length);
    }
}
