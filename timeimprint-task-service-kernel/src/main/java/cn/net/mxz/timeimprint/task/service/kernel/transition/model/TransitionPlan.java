package cn.net.mxz.timeimprint.task.service.kernel.transition.model;

import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import java.util.List;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.DefinitionControlTransition;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.participant.model.ParticipantChange;
import cn.net.mxz.timeimprint.task.service.kernel.shared.model.PlannedSignalIntent;

/**
 * 声明式状态迁移计划；一期仅包含七类声明内容，由 application 统一校验并原子提交。
 */
public record TransitionPlan(
        TransitionTarget target,
        DefinitionControlTransition definitionControlTransition,
        InstanceStateTransition instanceStateTransition,
        List<ParticipantChange> participantChanges,
        List<ActionJobIntent> actionJobIntents,
        List<TriggerBindingChange> triggerBindingChanges,
        List<PlannedSignalIntent> plannedSignalIntents,
        List<ScenarioDataMutation> scenarioDataMutations,
        String auditSummary) {

    public TransitionPlan {
        if (target == null) {
            throw new IllegalArgumentException("target required");
        }
        participantChanges = participantChanges == null ? List.of() : List.copyOf(participantChanges);
        actionJobIntents = actionJobIntents == null ? List.of() : List.copyOf(actionJobIntents);
        triggerBindingChanges = triggerBindingChanges == null ? List.of() : List.copyOf(triggerBindingChanges);
        plannedSignalIntents = plannedSignalIntents == null ? List.of() : List.copyOf(plannedSignalIntents);
        scenarioDataMutations = scenarioDataMutations == null ? List.of() : List.copyOf(scenarioDataMutations);
        auditSummary = auditSummary == null ? "" : auditSummary;
    }

    /** 无任何状态或副作用声明时为 true；此类计划不得作为 Applied 提交，应使用 NoChange。 */
    public boolean isEmptyDeclaration() {
        return definitionControlTransition == null
                && instanceStateTransition == null
                && participantChanges.isEmpty()
                && actionJobIntents.isEmpty()
                && triggerBindingChanges.isEmpty()
                && plannedSignalIntents.isEmpty()
                && scenarioDataMutations.isEmpty()
                && auditSummary.isEmpty();
    }
}
