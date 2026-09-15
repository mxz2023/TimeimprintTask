package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.service.application.actor.ActorContextProvider;
import cn.net.mxz.timeimprint.task.service.application.exception.ApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.ActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.model.ParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.model.TriggerBindingRecord;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.port.ParticipantQuery;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TriggerBindingQuery;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskInstanceSnapshot;
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
        return new DefinitionDetail(
                def,
                participantQuery.listDefinitionLevel(definitionId),
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
