package cn.net.mxz.timeimprint.task.service.storage.mysql.row;

import java.time.LocalDateTime;

/** DB row for tt_task_definition. */
public class TaskDefinitionRow {
    private Long definitionId;
    private String tenantId;
    private String scenarioKey;
    private Integer scenarioSchemaVersion;
    private String title;
    private String description;
    private String scenarioConfigJson;
    private byte[] scenarioConfigHash;
    private String controlState;
    private Long controlGeneration;
    private Long revision;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime pausedAt;
    private LocalDateTime retiredAt;

    public Long getDefinitionId() { return definitionId; }
    public void setDefinitionId(Long definitionId) { this.definitionId = definitionId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getScenarioKey() { return scenarioKey; }
    public void setScenarioKey(String scenarioKey) { this.scenarioKey = scenarioKey; }
    public Integer getScenarioSchemaVersion() { return scenarioSchemaVersion; }
    public void setScenarioSchemaVersion(Integer scenarioSchemaVersion) { this.scenarioSchemaVersion = scenarioSchemaVersion; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScenarioConfigJson() { return scenarioConfigJson; }
    public void setScenarioConfigJson(String scenarioConfigJson) { this.scenarioConfigJson = scenarioConfigJson; }
    public byte[] getScenarioConfigHash() { return scenarioConfigHash; }
    public void setScenarioConfigHash(byte[] scenarioConfigHash) { this.scenarioConfigHash = scenarioConfigHash; }
    public String getControlState() { return controlState; }
    public void setControlState(String controlState) { this.controlState = controlState; }
    public Long getControlGeneration() { return controlGeneration; }
    public void setControlGeneration(Long controlGeneration) { this.controlGeneration = controlGeneration; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getPausedAt() { return pausedAt; }
    public void setPausedAt(LocalDateTime pausedAt) { this.pausedAt = pausedAt; }
    public LocalDateTime getRetiredAt() { return retiredAt; }
    public void setRetiredAt(LocalDateTime retiredAt) { this.retiredAt = retiredAt; }
}
