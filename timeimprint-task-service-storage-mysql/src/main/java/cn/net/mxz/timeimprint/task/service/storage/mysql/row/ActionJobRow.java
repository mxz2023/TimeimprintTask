package cn.net.mxz.timeimprint.task.service.storage.mysql.row;

import java.time.LocalDateTime;

/** DB row for tt_action_job. */
public class ActionJobRow {
    private Long actionJobId;
    private String tenantId;
    private Long definitionId;
    private Long instanceId;
    private Long transitionId;
    private Long definitionControlGeneration;
    private Long parentActionJobId;
    private Integer redriveNo;
    private String handlerKey;
    private String actionKey;
    private String executionMode;
    private Integer schemaVersion;
    private String targetType;
    private String targetId;
    private String payloadJson;
    private byte[] payloadHash;
    private LocalDateTime availableAt;
    private LocalDateTime expiresAt;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private LocalDateTime nextAttemptAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String executionToken;
    private String outcomeCode;
    private String outcomeSummary;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getActionJobId() { return actionJobId; }
    public void setActionJobId(Long actionJobId) { this.actionJobId = actionJobId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Long getDefinitionId() { return definitionId; }
    public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getTransitionId() { return transitionId; }
    public void setTransitionId(Long transitionId) { this.transitionId = transitionId; }
    public Long getDefinitionControlGeneration() { return definitionControlGeneration; }
    public void setDefinitionControlGeneration(Long definitionControlGeneration) { this.definitionControlGeneration = definitionControlGeneration; }
    public Long getParentActionJobId() { return parentActionJobId; }
    public void setParentActionJobId(Long parentActionJobId) { this.parentActionJobId = parentActionJobId; }
    public Integer getRedriveNo() { return redriveNo; }
    public void setRedriveNo(Integer redriveNo) { this.redriveNo = redriveNo; }
    public String getHandlerKey() { return handlerKey; }
    public void setHandlerKey(String handlerKey) { this.handlerKey = handlerKey; }
    public String getActionKey() { return actionKey; }
    public void setActionKey(String actionKey) { this.actionKey = actionKey; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public byte[] getPayloadHash() { return payloadHash; }
    public void setPayloadHash(byte[] payloadHash) { this.payloadHash = payloadHash; }
    public LocalDateTime getAvailableAt() { return availableAt; }
    public void setAvailableAt(LocalDateTime availableAt) { this.availableAt = availableAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
    public String getExecutionToken() { return executionToken; }
    public void setExecutionToken(String executionToken) { this.executionToken = executionToken; }
    public String getOutcomeCode() { return outcomeCode; }
    public void setOutcomeCode(String outcomeCode) { this.outcomeCode = outcomeCode; }
    public String getOutcomeSummary() { return outcomeSummary; }
    public void setOutcomeSummary(String outcomeSummary) { this.outcomeSummary = outcomeSummary; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
