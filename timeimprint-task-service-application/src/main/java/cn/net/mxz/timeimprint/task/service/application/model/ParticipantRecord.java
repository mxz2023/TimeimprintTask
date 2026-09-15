package cn.net.mxz.timeimprint.task.service.application.model;

public record ParticipantRecord(
        long participantId,
        long definitionId,
        Long instanceId,
        String principalType,
        String principalId,
        String roleCode,
        String sourceCode) {}
