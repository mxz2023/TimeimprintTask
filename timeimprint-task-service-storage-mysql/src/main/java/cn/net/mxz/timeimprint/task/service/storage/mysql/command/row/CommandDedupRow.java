package cn.net.mxz.timeimprint.task.service.storage.mysql.command.row;

import java.time.LocalDateTime;

/** DB row for tt_command_dedup. */
public class CommandDedupRow {
    private Long dedupId;
    private String tenantId;
    private String actorId;
    private String operation;
    private String requestId;
    private byte[] requestHash;
    private String processStatus;
    private String resultCode;
    private String resourceType;
    private String resourceId;
    private Long resourceRevision;
    private String responseJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getDedupId() { return dedupId; }
    public void setDedupId(Long dedupId) { this.dedupId = dedupId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public byte[] getRequestHash() { return requestHash; }
    public void setRequestHash(byte[] requestHash) { this.requestHash = requestHash; }
    public String getProcessStatus() { return processStatus; }
    public void setProcessStatus(String processStatus) { this.processStatus = processStatus; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public Long getResourceRevision() { return resourceRevision; }
    public void setResourceRevision(Long resourceRevision) { this.resourceRevision = resourceRevision; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
