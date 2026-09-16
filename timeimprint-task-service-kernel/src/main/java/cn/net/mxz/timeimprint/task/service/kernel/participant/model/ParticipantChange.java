package cn.net.mxz.timeimprint.task.service.kernel.participant.model;

/** 参与人增删声明。 */
public record ParticipantChange(
        ParticipantChangeKind changeKind,
        String principalType,
        String principalId,
        String roleCode,
        boolean definitionLevel) {}
