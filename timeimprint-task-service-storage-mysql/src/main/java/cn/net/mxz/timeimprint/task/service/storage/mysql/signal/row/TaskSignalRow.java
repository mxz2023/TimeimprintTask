package cn.net.mxz.timeimprint.task.service.storage.mysql.signal.row;

import java.time.LocalDateTime;

/** DB row for tt_task_signal. */
public class TaskSignalRow {
    private Long signalId;
    private String tenantId;
    private Long definitionId;
    private Long triggerBindingId;
    private Long instanceId;
    private Long definitionControlGeneration;
    private Long parentSignalId;
    private Integer redriveNo;
    private String providerKey;
    private String signalKey;
    private Integer schemaVersion;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private String payloadJson;
    private byte[] payloadHash;
    private String processStatus;
    private Integer attemptCount;
    private Integer maxAttempts;
    private LocalDateTime nextAttemptAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String executionToken;
    private String resultCode;
    private String resultSummary;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getSignalId() { return signalId; }
    public void setSignalId(Long signalId) { this.signalId = signalId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Long getDefinitionId() { return definitionId; }
    public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
    public Long getTriggerBindingId() { return triggerBindingId; }
    public void setTriggerBindingId(Long triggerBindingId) { this.triggerBindingId = triggerBindingId; }
    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Long getDefinitionControlGeneration() { return definitionControlGeneration; }
    public void setDefinitionControlGeneration(Long definitionControlGeneration) { this.definitionControlGeneration = definitionControlGeneration; }
    public Long getParentSignalId() { return parentSignalId; }
    public void setParentSignalId(Long parentSignalId) { this.parentSignalId = parentSignalId; }
    public Integer getRedriveNo() { return redriveNo; }
    public void setRedriveNo(Integer redriveNo) { this.redriveNo = redriveNo; }
    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String providerKey) { this.providerKey = providerKey; }
    public String getSignalKey() { return signalKey; }
    public void setSignalKey(String signalKey) { this.signalKey = signalKey; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public byte[] getPayloadHash() { return payloadHash; }
    public void setPayloadHash(byte[] payloadHash) { this.payloadHash = payloadHash; }
    public String getProcessStatus() { return processStatus; }
    public void setProcessStatus(String processStatus) { this.processStatus = processStatus; }
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
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
