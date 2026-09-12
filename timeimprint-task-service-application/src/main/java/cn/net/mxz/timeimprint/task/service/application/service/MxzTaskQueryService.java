package cn.net.mxz.timeimprint.task.service.application.service;

import cn.net.mxz.timeimprint.task.service.application.exception.MxzApplicationException;
import cn.net.mxz.timeimprint.task.service.application.model.MxzActionJobRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.model.MxzTriggerBindingRecord;
import cn.net.mxz.timeimprint.task.service.application.port.ActionJobExecutionPort;
import cn.net.mxz.timeimprint.task.service.application.port.InboxRepository;
import cn.net.mxz.timeimprint.task.service.application.port.ParticipantQuery;
import cn.net.mxz.timeimprint.task.service.application.port.TaskDefinitionRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TaskInstanceRepository;
import cn.net.mxz.timeimprint.task.service.application.port.TriggerBindingQuery;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MxzTaskQueryService {

    public record DefinitionDetail(
            MxzTaskDefinitionSnapshot definition,
            List<MxzParticipantRecord> participants,
            List<MxzTriggerBindingRecord> bindings,
            List<String> allowedCommands) {}

    public record InstanceDetail(
            MxzTaskInstanceSnapshot instance,
            MxzTaskDefinitionSnapshot definition,
            List<MxzParticipantRecord> participants,
            List<MxzActionJobRecord> actions,
            int inboxCount,
            int unreadInboxCount) {}

    private final TaskDefinitionRepository definitionRepository;
    private final TaskInstanceRepository instanceRepository;
    private final ParticipantQuery participantQuery;
    private final TriggerBindingQuery triggerBindingQuery;
    private final ActionJobExecutionPort actionJobExecutionPort;
    private final InboxRepository inboxRepository;

    public MxzTaskQueryService(
            TaskDefinitionRepository definitionRepository,
            TaskInstanceRepository instanceRepository,
            ParticipantQuery participantQuery,
            TriggerBindingQuery triggerBindingQuery,
            ActionJobExecutionPort actionJobExecutionPort,
            InboxRepository inboxRepository) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.participantQuery = participantQuery;
        this.triggerBindingQuery = triggerBindingQuery;
        this.actionJobExecutionPort = actionJobExecutionPort;
        this.inboxRepository = inboxRepository;
    }

    public DefinitionDetail getDefinition(long definitionId) {
        var def = definitionRepository
                .findById(definitionId)
                .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
        return new DefinitionDetail(
                def,
                participantQuery.listDefinitionLevel(definitionId),
                triggerBindingQuery.listByDefinition(definitionId),
                List.of("update", "pause", "resume", "retire"));
    }

    public InstanceDetail getInstance(long instanceId) {
        var inst = instanceRepository
                .findById(instanceId)
                .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "instance"));
        var def = definitionRepository
                .findById(inst.definitionId())
                .orElseThrow(() -> new MxzApplicationException("RESOURCE_NOT_FOUND", "definition"));
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

    public List<MxzTaskInstanceSnapshot> listInstances(long definitionId) {
        return instanceRepository.listByDefinition(definitionId);
    }
}
