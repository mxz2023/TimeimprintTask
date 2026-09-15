package cn.net.mxz.timeimprint.task.service.application.model;

import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.TaskInstanceSnapshot;
import java.util.List;

public record CreatedDefinitionResult(
        TaskDefinitionSnapshot definition,
        List<ParticipantRecord> participants,
        List<TriggerBindingRecord> bindings,
        List<TaskInstanceSnapshot> instances,
        int signalCount,
        boolean duplicated,
        String responseJsonHint) {}
