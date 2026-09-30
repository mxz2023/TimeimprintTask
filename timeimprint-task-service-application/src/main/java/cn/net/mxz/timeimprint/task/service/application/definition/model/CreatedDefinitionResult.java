package cn.net.mxz.timeimprint.task.service.application.definition.model;

import cn.net.mxz.timeimprint.task.service.kernel.definition.model.TaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.instance.model.TaskInstanceSnapshot;
import java.util.List;
import cn.net.mxz.timeimprint.task.service.application.shared.model.ParticipantRecord;
import cn.net.mxz.timeimprint.task.service.application.shared.model.TriggerBindingRecord;

public record CreatedDefinitionResult(
        TaskDefinitionSnapshot definition,
        List<ParticipantRecord> participants,
        List<TriggerBindingRecord> bindings,
        List<TaskInstanceSnapshot> instances,
        int signalCount,
        boolean duplicated,
        String responseJsonHint) {}
