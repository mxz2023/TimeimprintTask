package cn.net.mxz.timeimprint.task.boot.fixture;

import cn.net.mxz.timeimprint.task.service.extension.context.DefinitionConfigValidationContext;
import cn.net.mxz.timeimprint.task.service.extension.context.InitialDefinitionContext;
import cn.net.mxz.timeimprint.task.service.extension.context.ScenarioExtensionDescriptor;
import cn.net.mxz.timeimprint.task.service.extension.context.SignalProcessContext;
import cn.net.mxz.timeimprint.task.service.extension.registry.ScenarioExtensionKey;
import cn.net.mxz.timeimprint.task.service.extension.result.HandlerResult;
import cn.net.mxz.timeimprint.task.service.extension.spi.ScenarioExtension;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.domain.mutation.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.domain.plan.TransitionTarget;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * T07 test fixture: proves that a new scenario extension can be added without modifying
 * kernel production sources or platform public DDL. The ApprovalFixture registers "approval"
 * as a ScenarioExtension that emits a ScenarioDataMutation on initial definition — handled by
 * {@link ApprovalDataMaterializer} writing to the test-only table tt_test_approval_data.
 *
 * This class lives in boot-loader test sources only; it must NOT be moved to a production module.
 */
@Component
public class ApprovalFixture implements ScenarioExtension {

    public static final String SCENARIO_KEY = "approval";
    public static final String MUTATION_KEY = "initial_approval";
    public static final int CONTRACT_VERSION = 1;
    public static final Set<String> VALID_STATES = Set.of("PENDING_APPROVAL", "APPROVED", "REJECTED", "CANCELLED");

    @Override
    public ScenarioExtensionKey registrationKey() {
        return new ScenarioExtensionKey(SCENARIO_KEY, CONTRACT_VERSION);
    }

    @Override
    public ScenarioExtensionDescriptor descriptor() {
        return new ScenarioExtensionDescriptor(
                SCENARIO_KEY, CONTRACT_VERSION, List.of(), List.of());
    }

    @Override
    public void validateDefinitionConfig(DefinitionConfigValidationContext context) {
        // No additional config required for the approval fixture
    }

    /**
     * On initial definition, emit a ScenarioDataMutation that triggers
     * {@link ApprovalDataMaterializer} to write a row to tt_test_approval_data.
     * The TransitionPlan has no instance state transition — only the mutation.
     */
    @Override
    public HandlerResult planInitialDefinition(InitialDefinitionContext context) {
        var def = context.definitionSnapshot();
        Map<String, Object> meta = Map.of(
                "definitionId", def.definitionId(),
                "scenarioKey", SCENARIO_KEY,
                "approvalRequired", true);
        var mutation = new ScenarioDataMutation(
                SCENARIO_KEY, MUTATION_KEY, CONTRACT_VERSION, new JsonPayload(meta));
        var plan = new TransitionPlan(
                new TransitionTarget(TransitionResourceType.DEFINITION, def.definitionId(), def.revision()),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(mutation),
                "approval fixture initial mutation");
        return new HandlerResult.Applied(plan);
    }

    @Override
    public HandlerResult processSignal(SignalProcessContext context) {
        return new HandlerResult.NoChange(Map.of("reason", "approval_fixture_no_signal_handler"));
    }

    @Override
    public void validateScenarioState(String scenarioState, int scenarioSchemaVersion) {
        if (scenarioSchemaVersion != 1 || !VALID_STATES.contains(scenarioState)) {
            throw new IllegalArgumentException("INVALID_REQUEST: illegal approval state: " + scenarioState);
        }
    }
}
