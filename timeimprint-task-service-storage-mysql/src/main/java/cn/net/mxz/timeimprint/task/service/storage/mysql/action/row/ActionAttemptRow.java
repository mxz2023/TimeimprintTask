package cn.net.mxz.timeimprint.task.service.storage.mysql.action.row;

import java.time.LocalDateTime;

/** DB row for tt_action_attempt. */
public class ActionAttemptRow {
    private Long attemptId;
    private Long actionJobId;
    private Integer attemptNo;
    private String executionToken;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime effectStartedAt;
    private String outcome;
    private String errorClass;
    private String errorCode;
    private String providerReference;
    private String safeSummary;

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }
    public Long getActionJobId() { return actionJobId; }
    public void setActionJobId(Long actionJobId) { this.actionJobId = actionJobId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public String getExecutionToken() { return executionToken; }
    public void setExecutionToken(String executionToken) { this.executionToken = executionToken; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getEffectStartedAt() { return effectStartedAt; }
    public void setEffectStartedAt(LocalDateTime effectStartedAt) { this.effectStartedAt = effectStartedAt; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getErrorClass() { return errorClass; }
    public void setErrorClass(String errorClass) { this.errorClass = errorClass; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }
    public String getSafeSummary() { return safeSummary; }
    public void setSafeSummary(String safeSummary) { this.safeSummary = safeSummary; }
}
