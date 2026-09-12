package cn.net.mxz.timeimprint.task.service.application.model;

import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskDefinitionSnapshot;
import cn.net.mxz.timeimprint.task.service.kernel.domain.snapshot.MxzTaskInstanceSnapshot;
import java.util.List;

public record MxzCreatedDefinitionResult(
        MxzTaskDefinitionSnapshot definition,
        List<MxzParticipantRecord> participants,
        List<MxzTriggerBindingRecord> bindings,
        List<MxzTaskInstanceSnapshot> instances,
        int signalCount,
        boolean duplicated,
        String responseJsonHint) {}
