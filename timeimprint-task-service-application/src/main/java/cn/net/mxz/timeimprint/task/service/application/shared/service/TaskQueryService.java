package cn.net.mxz.timeimprint.task.service.application.shared.service;

import cn.net.mxz.timeimprint.task.service.application.shared.port.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.action.model.ActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.model.TriggerBindingRecord;
import cn.net.mxz.timeimprint.task.service.application.action.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.inbox.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.port.ParticipantQuery;
import cn.net.mxz.timeimprint.task.service.application.definition.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.instance.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.shared.port.TriggerBindingQuery;
import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TaskQueryService {

    public record DefinitionDetail(
            TaskDefinitionSnapshot definition,
            List<ParticipantRecord> participants,
            List<TriggerBindingRecord> bindings,
            List<String> allowedCommands) {}

    public record InstanceDetail(
            TaskInstanceSnapshot instance,
            TaskDefinitionSnapshot definition,
            List<ParticipantRecord> participants,
            List<ActionJobRecord> actions,
            int inboxCount,
            int unreadInboxCount) {}

    private final TaskDefinitionRepository definitionRepository;
    private final TaskInstanceRepository instanceRepository;
    private final ParticipantQuery participantQuery;
    private final TriggerBindingQuery triggerBindingQuery;
    private final ActionJobExecutionPort actionJobExecutionPort;
    private final InboxRepository inboxRepository;
    private final ActorContextProvider actorContextProvider;

    public TaskQueryService(
            TaskDefinitionRepository definitionRepository,
            TaskInstanceRepository instanceRepository,
            ParticipantQuery participantQuery,
            TriggerBindingQuery triggerBindingQuery,
            ActionJobExecutionPort actionJobExecutionPort,
            InboxRepository inboxRepository,
            ActorContextProvider actorContextProvider) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.participantQuery = participantQuery;
        this.triggerBindingQuery = triggerBindingQuery;
        this.actionJobExecutionPort = actionJobExecutionPort;
        this.inboxRepository = inboxRepository;
        this.actorContextProvider = actorContextProvider;
    }

    public DefinitionDetail getDefinition(long definitionId) {
        var actor = actorContextProvider.requireCurrentActor();
        var def = definitionRepository
                .findById(definitionId)
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
        if (!actor.tenantKey().equals(def.tenantId())) {
            throw new ApplicationException("RESOURCE_NOT_FOUND", "definition");
        }
        var participants = participantQuery.listDefinitionLevel(definitionId);
        if (participants.stream().noneMatch(p -> actor.principalId().equals(p.principalId()))) {
            throw new ApplicationException("RESOURCE_NOT_FOUND", "definition");
        }
        return new DefinitionDetail(
                def,
                participants,
                triggerBindingQuery.listByDefinition(definitionId),
                List.of("update", "pause", "resume", "retire"));
    }

    public InstanceDetail getInstance(long instanceId) {
        var actor = actorContextProvider.requireCurrentActor();
        var inst = instanceRepository
                .findById(instanceId)
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "instance"));
        var def = definitionRepository
                .findById(inst.definitionId())
                .orElseThrow(() -> new ApplicationException("RESOURCE_NOT_FOUND", "definition"));
        if (!actor.tenantKey().equals(def.tenantId())) {
            throw new ApplicationException("RESOURCE_NOT_FOUND", "instance");
        }
        if (participantQuery.listDefinitionLevel(inst.definitionId()).stream()
                .noneMatch(p -> actor.principalId().equals(p.principalId()))) {
            throw new ApplicationException("RESOURCE_NOT_FOUND", "instance");
        }
        var actions = actionJobExecutionPort.listByInstance(instanceId);
        // inbox counts for this instance across recipients — approximate via actions succeeded
        int inboxCount = (int) actions.stream().filter(a -> "SUCCEEDED".equals(a.status())).count();
        return new InstanceDetail(
                inst,
                def,
                participantQuery.listDefinitionLevel(inst.definitionId()),
                actions,
                inboxCount,
                0);
    }

    public List<TaskInstanceSnapshot> listInstances(long definitionId) {
        return instanceRepository.listByDefinition(definitionId);
    }
}
