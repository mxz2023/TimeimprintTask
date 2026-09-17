package cn.net.mxz.timeimprint.task.service.storage.mysql.transition.committer;

import cn.net.mxz.timeimprint.task.common.hashing.Sha256;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.shared.limit.PlatformLimits;
import cn.net.mxz.timeimprint.task.service.application.transition.validation.TransitionPlanWriteValidator;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionCommitRequest;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionCommitResult;
import cn.net.mxz.timeimprint.task.service.application.transition.port.TransitionPlanCommitter;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.JsonPayload;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.ScenarioDataMutation;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.DefinitionControlTransition;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.InstanceStateTransition;
import cn.net.mxz.timeimprint.task.service.kernel.participant.model.ParticipantChange;
import cn.net.mxz.timeimprint.task.service.kernel.participant.model.ParticipantChangeKind;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionPlan;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionResourceType;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TransitionTarget;
import cn.net.mxz.timeimprint.task.service.kernel.transition.model.TriggerBindingChange;
import cn.net.mxz.timeimprint.task.service.storage.mysql.definition.mapper.TaskDefinitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.instance.mapper.TaskInstanceMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.participant.mapper.TaskParticipantMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.transition.mapper.TaskTransitionMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.mapper.TriggerBindingMapper;
import cn.net.mxz.timeimprint.task.service.storage.mysql.definition.row.TaskDefinitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.instance.row.TaskInstanceRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.participant.row.TaskParticipantRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.transition.row.TaskTransitionRow;
import cn.net.mxz.timeimprint.task.service.storage.mysql.trigger.row.TriggerBindingRow;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Atomically commits a TransitionPlan following the parent-first lock order.
 * Caller must already hold all relevant locks. Side effects delegated to
 * {@link TransitionSideEffectWriter}; row/JSON helpers to {@link TransitionCommitSupport}.
 */
@Component
public class TransitionPlanCommitterImpl implements TransitionPlanCommitter {

    private final TaskDefinitionMapper definitionMapper;
    private final TriggerBindingMapper triggerMapper;
    private final TaskInstanceMapper instanceMapper;
    private final TaskParticipantMapper participantMapper;
    private final TaskTransitionMapper transitionMapper;
    private final JsonMapper objectMapper;
    private final TransitionCommitSupport support;
    private final TransitionSideEffectWriter sideEffectWriter;

    public TransitionPlanCommitterImpl(
            TaskDefinitionMapper definitionMapper,
            TriggerBindingMapper triggerMapper,
            TaskInstanceMapper instanceMapper,
            TaskParticipantMapper participantMapper,
            TaskTransitionMapper transitionMapper,
            JsonMapper objectMapper,
            TransitionCommitSupport support,
            TransitionSideEffectWriter sideEffectWriter) {
        this.definitionMapper = definitionMapper;
        this.triggerMapper = triggerMapper;
        this.instanceMapper = instanceMapper;
        this.participantMapper = participantMapper;
        this.transitionMapper = transitionMapper;
        this.objectMapper = objectMapper;
        this.support = support;
        this.sideEffectWriter = sideEffectWriter;
    }

    @Override
    public TransitionCommitResult commit(TransitionCommitRequest request) {
        TransitionPlan plan = request.plan();
        TransitionPlanWriteValidator.validateBeforeWrite(plan, objectMapper);
        TransitionTarget target = plan.target();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        int affectedRows = 0;

        TaskDefinitionRow defRow = definitionMapper.selectById(request.definitionId());
        if (defRow == null) {
            throw new IllegalStateException("Definition not found: " + request.definitionId());
        }
        String tenantId = defRow.getTenantId();
        long controlGeneration = defRow.getControlGeneration();

        DefinitionControlTransition defTransition = plan.definitionControlTransition();
        if (defTransition != null) {
            String newControlState = defTransition.toControlState().name();
            long newControlGen = controlGeneration;
            LocalDateTime pausedAt = defRow.getPausedAt();
            LocalDateTime retiredAt = defRow.getRetiredAt();
            if ("PAUSED".equals(newControlState) && !"PAUSED".equals(defRow.getControlState())) {
                pausedAt = now;
                newControlGen = controlGeneration + 1;
            } else if ("RETIRED".equals(newControlState)) {
                retiredAt = now;
                newControlGen = controlGeneration + 1;
            } else if ("ACTIVE".equals(newControlState) && "PAUSED".equals(defRow.getControlState())) {
                newControlGen = controlGeneration + 1;
                pausedAt = null;
            }
            int updated = definitionMapper.updateRevision(
                    request.definitionId(),
                    defRow.getRevision(),
                    defRow.getRevision() + 1,
                    newControlState,
                    newControlGen,
                    "SYSTEM",
                    now,
                    pausedAt,
                    retiredAt);
            if (updated == 0) {
                throw new IllegalStateException("CAS failed for definition " + request.definitionId());
            }
            affectedRows += updated;
            controlGeneration = newControlGen;
        }

        for (TriggerBindingChange tbc : plan.triggerBindingChanges()) {
            List<TriggerBindingRow> bindings = triggerMapper.selectByDefinitionId(request.definitionId());
            TriggerBindingRow binding = bindings.stream()
                    .filter(b -> b.getBindingKey().equals(tbc.bindingKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Binding not found: " + tbc.bindingKey()));
            LocalDateTime nextFireAt = tbc.nextFireAtEpochSecond() != null
                    ? LocalDateTime.ofEpochSecond(tbc.nextFireAtEpochSecond(), 0, ZoneOffset.UTC)
                    : null;
            boolean exhausted = Boolean.TRUE.equals(tbc.exhausted());
            String cursorJson = support.toJson(tbc.configPayload());
            int updated = triggerMapper.updateCursor(
                    binding.getTriggerBindingId(),
                    binding.getRevision(),
                    binding.getRevision() + 1,
                    nextFireAt,
                    cursorJson,
                    exhausted,
                    now);
            if (updated == 0) {
                throw new IllegalStateException("CAS failed for binding " + binding.getTriggerBindingId());
            }
            affectedRows += updated;
        }

        for (ParticipantChange pc : plan.participantChanges()) {
            if (pc.changeKind() == ParticipantChangeKind.ADD) {
                TaskParticipantRow row = new TaskParticipantRow();
                row.setDefinitionId(request.definitionId());
                row.setInstanceId(pc.definitionLevel() ? null : request.instanceId());
                row.setPrincipalType(pc.principalType());
                row.setPrincipalId(pc.principalId());
                row.setRoleCode(pc.roleCode());
                row.setSourceCode("DIRECT");
                row.setMetadataJson("{}");
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                participantMapper.insert(row);
                affectedRows++;
            }
        }

        Long transitionId = null;
        InstanceStateTransition instTransition = plan.instanceStateTransition();

        if (instTransition != null && request.instanceId() != null) {
            TaskInstanceRow instRow = instanceMapper.selectById(request.instanceId());
            if (instRow == null) {
                throw new IllegalStateException("Instance not found: " + request.instanceId());
            }

            TaskTransitionRow tr = support.buildInstanceTransitionRow(request, defRow, instRow, now);
            transitionMapper.insert(tr);
            transitionId = tr.getTransitionId();
            affectedRows++;

            String newLifecycle = instTransition.toLifecycleCategory().name();
            String newState = instTransition.toScenarioState();
            LocalDateTime terminalAt = "TERMINAL".equals(newLifecycle) ? now : null;

            String snapshotJson = instRow.getScenarioSnapshotJson();
            byte[] snapshotHash = instRow.getSnapshotHash();
            if (request.instanceSnapshotJson() != null && !request.instanceSnapshotJson().isBlank()) {
                snapshotJson = request.instanceSnapshotJson();
                snapshotHash = Sha256.digestUtf8(snapshotJson);
            } else {
                for (ScenarioDataMutation mutation : plan.scenarioDataMutations()) {
                    if ("REPLACE_INSTANCE_SNAPSHOT".equals(mutation.mutationKey())
                            && mutation.payload() instanceof JsonPayload jp) {
                        snapshotJson = support.toJson(jp);
                        snapshotHash = Sha256.digestUtf8(snapshotJson);
                        break;
                    }
                }
            }

            int updated = instanceMapper.updateRevision(
                    request.instanceId(),
                    instRow.getRevision(),
                    instRow.getRevision() + 1,
                    newLifecycle,
                    newState,
                    snapshotJson,
                    snapshotHash,
                    terminalAt,
                    now);
            if (updated == 0) {
                throw new IllegalStateException("CAS failed for instance " + request.instanceId());
            }
            affectedRows += updated;

        } else if (defTransition != null || !plan.plannedSignalIntents().isEmpty()
                || !plan.actionJobIntents().isEmpty()
                || !plan.scenarioDataMutations().isEmpty()) {
            TaskTransitionRow tr = support.buildDefinitionTransitionRow(request, defRow, now);
            transitionMapper.insert(tr);
            transitionId = tr.getTransitionId();
            affectedRows++;
        }

        affectedRows = sideEffectWriter.write(
                request, plan, target, defRow, tenantId, controlGeneration, transitionId, now, affectedRows);

        long toRevision;
        if (target.resourceType() == TransitionResourceType.INSTANCE && request.instanceId() != null) {
            TaskInstanceRow r = instanceMapper.selectById(request.instanceId());
            toRevision = r != null ? r.getRevision() : 1L;
        } else {
            TaskDefinitionRow r = definitionMapper.selectById(request.definitionId());
            toRevision = r != null ? r.getRevision() : 1L;
        }

        if (affectedRows > PlatformLimits.MAX_MUTATED_ROWS_PER_WRITE_TX) {
            throw new ApplicationException("INVALID_REQUEST", "mutated row count exceeded limit");
        }

        return new TransitionCommitResult(
                transitionId != null ? transitionId : 0L,
                toRevision,
                affectedRows,
                true);
    }
}
